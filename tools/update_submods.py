#!/usr/bin/env python3
from __future__ import annotations

import argparse
import dataclasses
import datetime as dt
import hashlib
import json
import re
import subprocess
import sys
from pathlib import Path
from typing import Dict, List, Optional, Tuple


ROOT = Path(__file__).resolve().parents[1]
TOOLS = ROOT / "tools"
CONFIG_PATH = TOOLS / "submods.json"
LOCK_PATH = TOOLS / "submods.lock.json"


@dataclasses.dataclass(frozen=True)
class SubMod:
    id: str
    name: str
    local_path: Path
    java_package_dir: Optional[Path]
    extra_java_dirs: List[Path]
    main_class_file: Optional[str]
    bundles_dir: Path
    source_java_dir: Optional[Path] = None
    source_extra_java_dirs: List[Path] = dataclasses.field(default_factory=list)
    source_bundles_dir: Optional[Path] = None
    kotlin_package_dir: Optional[Path] = None
    source_kotlin_dir: Optional[Path] = None
    extra_resource_dirs: List[Path] = dataclasses.field(default_factory=list)
    source_extra_resource_dirs: List[Path] = dataclasses.field(default_factory=list)
    inject_bek_hooks: bool = True
    allow_no_git: bool = False
    track_upstream: bool = True
    repo_url: Optional[str] = None
    branch: Optional[str] = None


@dataclasses.dataclass(frozen=True)
class SourceState:
    source_kind: str
    workspace_head: str
    workspace_dirty: bool
    upstream_ref: Optional[str]
    upstream_head: Optional[str]
    ahead_of_upstream: Optional[int]
    behind_upstream: Optional[int]


def run(args: List[str], cwd: Optional[Path] = None) -> str:
    proc = subprocess.run(
        args,
        cwd=str(cwd) if cwd else None,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        text=True,
        encoding="utf-8",
        errors="replace",
        check=False,
    )
    if proc.returncode != 0:
        raise RuntimeError(f"Command failed ({proc.returncode}): {' '.join(args)}\n{proc.stdout}")
    return proc.stdout.strip()


def load_config() -> List[SubMod]:
    raw = json.loads(CONFIG_PATH.read_text(encoding="utf-8"))
    mods: List[SubMod] = []
    for m in raw.get("mods", []):
        local_path = m.get("localPath")
        if not local_path:
            raise ValueError(f"Missing localPath for mod entry: {m.get('id')}")
        java_package_dir = m.get("javaPackageDir")
        kotlin_package_dir = m.get("kotlinPackageDir")
        if not java_package_dir and not kotlin_package_dir:
            raise ValueError(f"Missing javaPackageDir/kotlinPackageDir for mod entry: {m.get('id')}")
        source_java_dir = m.get("sourceJavaDir")
        source_bundles_dir = m.get("sourceBundlesDir")
        source_kotlin_dir = m.get("sourceKotlinDir")
        mods.append(
            SubMod(
                id=m["id"],
                name=m["name"],
                local_path=(ROOT / local_path).resolve(),
                java_package_dir=ROOT / java_package_dir if java_package_dir else None,
                extra_java_dirs=[ROOT / p for p in m.get("extraJavaDirs", [])],
                main_class_file=m.get("mainClassFile"),
                bundles_dir=ROOT / m["bundlesDir"],
                source_java_dir=Path(source_java_dir) if source_java_dir else None,
                source_extra_java_dirs=[Path(p) for p in m.get("sourceExtraJavaDirs", [])],
                source_bundles_dir=Path(source_bundles_dir) if source_bundles_dir else None,
                kotlin_package_dir=ROOT / kotlin_package_dir if kotlin_package_dir else None,
                source_kotlin_dir=Path(source_kotlin_dir) if source_kotlin_dir else None,
                extra_resource_dirs=[ROOT / p for p in m.get("extraResourceDirs", [])],
                source_extra_resource_dirs=[Path(p) for p in m.get("sourceExtraResourceDirs", [])],
                inject_bek_hooks=bool(m.get("injectBekHooks", True)),
                allow_no_git=bool(m.get("allowNoGit", False)),
                track_upstream=bool(m.get("trackUpstream", True)),
                repo_url=m.get("repoUrl"),
                branch=m.get("branch"),
            )
        )
    return mods


def load_lock() -> dict:
    if LOCK_PATH.exists():
        lock = json.loads(LOCK_PATH.read_text(encoding="utf-8"))
        if lock.get("schema") == 1:
            migrated = {"schema": 2, "generatedAtUtc": lock.get("generatedAtUtc"), "mods": {}}
            for mod_id, entry in lock.get("mods", {}).items():
                head = entry.get("head")
                migrated["mods"][mod_id] = {
                    "sourceKind": "snapshot" if isinstance(head, str) and head.startswith("nogit-") else "git",
                    "syncedHead": head,
                    "workspaceHeadAtSync": head,
                    "workspaceDirtyAtSync": None,
                    "upstreamRef": None,
                    "upstreamHeadAtSync": None,
                    "aheadOfUpstreamAtSync": None,
                    "behindUpstreamAtSync": None,
                    "localPath": entry.get("localPath"),
                }
            return migrated
        return lock
    return {"schema": 2, "generatedAtUtc": None, "mods": {}}


def save_lock(lock: dict) -> None:
    lock["schema"] = 2
    lock["generatedAtUtc"] = dt.datetime.now(dt.timezone.utc).replace(microsecond=0).isoformat().replace("+00:00", "Z")
    LOCK_PATH.write_text(json.dumps(lock, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def resolve_local_repo(mod: SubMod) -> Path:
    """
    Uses an existing local git checkout as the single source of truth.
    This script intentionally does NOT access GitHub/remotes.
    """
    repo_dir = mod.local_path
    if not repo_dir.exists():
        raise FileNotFoundError(f"[{mod.id}] localPath does not exist: {repo_dir}")
    if not (repo_dir / ".git").exists() and not mod.allow_no_git:
        raise FileNotFoundError(f"[{mod.id}] localPath is not a git repo (missing .git): {repo_dir}")
    return repo_dir


def git_head(repo_dir: Path) -> str:
    return run(["git", "rev-parse", "HEAD"], cwd=repo_dir).strip()


def git_has_dirty(repo_dir: Path) -> bool:
    return bool(run(["git", "status", "--porcelain"], cwd=repo_dir))


def git_current_upstream(repo_dir: Path) -> Optional[str]:
    proc = subprocess.run(
        ["git", "rev-parse", "--abbrev-ref", "--symbolic-full-name", "@{u}"],
        cwd=str(repo_dir),
        stdout=subprocess.PIPE,
        stderr=subprocess.DEVNULL,
        text=True,
        encoding="utf-8",
        errors="replace",
        check=False,
    )
    if proc.returncode != 0:
        return None
    value = proc.stdout.strip()
    return value or None


def git_remote_head(repo_dir: Path, upstream_ref: str) -> Optional[str]:
    proc = subprocess.run(
        ["git", "rev-parse", upstream_ref],
        cwd=str(repo_dir),
        stdout=subprocess.PIPE,
        stderr=subprocess.DEVNULL,
        text=True,
        encoding="utf-8",
        errors="replace",
        check=False,
    )
    if proc.returncode != 0:
        return None
    value = proc.stdout.strip()
    return value or None


def git_ahead_behind(repo_dir: Path, upstream_ref: str) -> Tuple[Optional[int], Optional[int]]:
    proc = subprocess.run(
        ["git", "rev-list", "--left-right", "--count", f"HEAD...{upstream_ref}"],
        cwd=str(repo_dir),
        stdout=subprocess.PIPE,
        stderr=subprocess.DEVNULL,
        text=True,
        encoding="utf-8",
        errors="replace",
        check=False,
    )
    if proc.returncode != 0:
        return None, None
    parts = proc.stdout.strip().split()
    if len(parts) != 2:
        return None, None
    return int(parts[0]), int(parts[1])


def repo_join(repo_dir: Path, rel_or_abs: Path) -> Path:
    return rel_or_abs if rel_or_abs.is_absolute() else (repo_dir / rel_or_abs)


def _default_source_main_java_dir(mod: SubMod) -> Path:
    if mod.java_package_dir is None:
        raise ValueError(f"[{mod.id}] javaPackageDir is not configured")
    return mod.java_package_dir.relative_to(ROOT)


def _default_source_extra_java_dirs(mod: SubMod) -> List[Path]:
    return [d.relative_to(ROOT) for d in mod.extra_java_dirs]


def _source_main_java_dir(mod: SubMod) -> Path:
    return mod.source_java_dir if mod.source_java_dir is not None else _default_source_main_java_dir(mod)


def _source_extra_java_dirs(mod: SubMod) -> List[Path]:
    if mod.source_extra_java_dirs:
        if len(mod.source_extra_java_dirs) != len(mod.extra_java_dirs):
            raise ValueError(
                f"[{mod.id}] sourceExtraJavaDirs size {len(mod.source_extra_java_dirs)} "
                f"does not match extraJavaDirs size {len(mod.extra_java_dirs)}"
            )
        return list(mod.source_extra_java_dirs)
    return _default_source_extra_java_dirs(mod)


def _source_bundles_dir(mod: SubMod) -> Path:
    return mod.source_bundles_dir if mod.source_bundles_dir is not None else Path("src/main/resources/bundles")


def _source_kotlin_dir(mod: SubMod) -> Path:
    if mod.kotlin_package_dir is None:
        raise ValueError(f"[{mod.id}] kotlinPackageDir is not configured")
    return mod.source_kotlin_dir if mod.source_kotlin_dir is not None else mod.kotlin_package_dir.relative_to(ROOT)


def _source_extra_resource_dirs(mod: SubMod) -> List[Path]:
    if mod.source_extra_resource_dirs:
        if len(mod.source_extra_resource_dirs) != len(mod.extra_resource_dirs):
            raise ValueError(
                f"[{mod.id}] sourceExtraResourceDirs size {len(mod.source_extra_resource_dirs)} "
                f"does not match extraResourceDirs size {len(mod.extra_resource_dirs)}"
            )
        return list(mod.source_extra_resource_dirs)
    return [d.relative_to(ROOT) for d in mod.extra_resource_dirs]


def nogit_head(mod: SubMod, repo_dir: Path) -> str:
    """Computes a content hash for directories without git metadata."""
    h = hashlib.sha1()
    files: List[Path] = []

    java_dirs: List[Path] = []
    if mod.java_package_dir is not None:
        java_dirs.append(_source_main_java_dir(mod))
    java_dirs += _source_extra_java_dirs(mod)
    for rel in java_dirs:
        src_dir = repo_join(repo_dir, rel)
        if not src_dir.exists():
            continue
        files.extend(src_dir.glob("**/*.java"))

    if mod.kotlin_package_dir is not None:
        src_dir = repo_join(repo_dir, _source_kotlin_dir(mod))
        if src_dir.exists():
            files.extend(src_dir.glob("**/*.kt"))

    bundle_dir = repo_join(repo_dir, _source_bundles_dir(mod))
    if bundle_dir.exists():
        files.extend(bundle_dir.glob("bundle*.properties"))

    for rel in _source_extra_resource_dirs(mod):
        src_dir = repo_join(repo_dir, rel)
        if src_dir.exists():
            files.extend(p for p in src_dir.glob("**/*") if p.is_file())

    for descriptor in ("mod.json", "mod.hjson"):
        descriptor_path = repo_dir / descriptor
        if descriptor_path.exists():
            files.append(descriptor_path)

    files = sorted(set(p for p in files if p.is_file()))
    if not files:
        return "nogit-empty"

    for p in files:
        rel = p.relative_to(repo_dir).as_posix().encode("utf-8")
        h.update(rel)
        h.update(b"\0")
        h.update(p.read_bytes())
        h.update(b"\0")
    return "nogit-" + h.hexdigest()


def source_head(mod: SubMod, repo_dir: Path) -> str:
    if (repo_dir / ".git").exists():
        return git_head(repo_dir)
    return nogit_head(mod, repo_dir)


def source_state(mod: SubMod, repo_dir: Path) -> SourceState:
    if not (repo_dir / ".git").exists():
        return SourceState(
            source_kind="snapshot",
            workspace_head=nogit_head(mod, repo_dir),
            workspace_dirty=False,
            upstream_ref=None,
            upstream_head=None,
            ahead_of_upstream=None,
            behind_upstream=None,
        )

    workspace_head = git_head(repo_dir)
    workspace_dirty = git_has_dirty(repo_dir)
    upstream_ref = git_current_upstream(repo_dir) if mod.track_upstream else None
    upstream_head = None
    ahead = None
    behind = None
    if upstream_ref:
        upstream_head = git_remote_head(repo_dir, upstream_ref)
        ahead, behind = git_ahead_behind(repo_dir, upstream_ref)
    return SourceState(
        source_kind="git",
        workspace_head=workspace_head,
        workspace_dirty=workspace_dirty,
        upstream_ref=upstream_ref,
        upstream_head=upstream_head,
        ahead_of_upstream=ahead,
        behind_upstream=behind,
    )


def short_head(value: Optional[str]) -> str:
    if not value:
        return "-"
    return value[:10]


def summarize_check(sm: SubMod, state: SourceState, lock_entry: dict) -> str:
    synced_head = lock_entry.get("syncedHead") or lock_entry.get("head")
    changed_since_lock = synced_head != state.workspace_head

    parts = [
        "changed-since-lock" if changed_since_lock else "matches-lock",
        "dirty-workspace" if state.workspace_dirty else "clean-workspace",
    ]

    if state.source_kind != "git":
        parts.append("snapshot-source")
    elif not sm.track_upstream:
        parts.append("upstream-check-disabled")
    elif not state.upstream_ref:
        parts.append("no-upstream")
    elif state.behind_upstream and state.behind_upstream > 0:
        parts.append(f"remote+{state.behind_upstream}")
        if state.ahead_of_upstream and state.ahead_of_upstream > 0:
            parts.append(f"local+{state.ahead_of_upstream}")
    elif state.ahead_of_upstream and state.ahead_of_upstream > 0:
        parts.append(f"local+{state.ahead_of_upstream}")
    else:
        parts.append("upstream-in-sync")

    return (
        f"[{sm.id}] {sm.name}: {', '.join(parts)} | "
        f"sync={short_head(synced_head)} workspace={short_head(state.workspace_head)} "
        f"upstream={short_head(state.upstream_head)}"
    )


def read_text(path: Path) -> str:
    return path.read_text(encoding="utf-8", errors="replace")


def write_text(path: Path, text: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text, encoding="utf-8", newline="\n")


def find_lambda_body(src: str, marker_regex: re.Pattern[str]) -> Tuple[int, int, int]:
    """
    Finds `table -> { ... }` body for the first `addCategory(..., table -> { ... });` match.
    Returns:
      (lambda_start, body_open_brace, body_close_brace)
    """
    m = marker_regex.search(src)
    if not m:
        raise ValueError("Could not find addCategory(table -> { ... }) marker.")

    lambda_start = m.start("lambda")
    open_brace = m.start("brace")
    i = open_brace
    depth = 0
    in_string = False
    in_char = False
    esc = False
    while i < len(src):
        ch = src[i]
        if in_string:
            if esc:
                esc = False
            elif ch == "\\":
                esc = True
            elif ch == '"':
                in_string = False
        elif in_char:
            if esc:
                esc = False
            elif ch == "\\":
                esc = True
            elif ch == "'":
                in_char = False
        else:
            if ch == '"':
                in_string = True
            elif ch == "'":
                in_char = True
            elif ch == "{":
                depth += 1
            elif ch == "}":
                depth -= 1
                if depth == 0:
                    close_brace = i
                    break
        i += 1
    else:
        raise ValueError("Unterminated lambda body braces.")

    return lambda_start, open_brace, close_brace


def find_method_close(src: str, method_regex: re.Pattern[str]) -> Optional[int]:
    m = method_regex.search(src)
    if not m:
        return None

    open_brace = src.find("{", m.end() - 1)
    if open_brace == -1:
        return None

    depth = 0
    i = open_brace
    in_string = False
    in_char = False
    esc = False
    while i < len(src):
        ch = src[i]
        if in_string:
            if esc:
                esc = False
            elif ch == "\\":
                esc = True
            elif ch == '"':
                in_string = False
        elif in_char:
            if esc:
                esc = False
            elif ch == "\\":
                esc = True
            elif ch == "'":
                in_char = False
        else:
            if ch == '"':
                in_string = True
            elif ch == "'":
                in_char = True
            elif ch == "{":
                depth += 1
            elif ch == "}":
                depth -= 1
                if depth == 0:
                    return i
        i += 1

    return None


def ensure_bek_build_settings_wrapper(java_src: str) -> str:
    if "public void bekBuildSettings(SettingsMenuDialog.SettingsTable table)" in java_src:
        return java_src

    method_regex = re.compile(
        r"(?:private|protected|public)\s+void\s+buildSettings\s*\(\s*SettingsMenuDialog\.SettingsTable\s+\w+\s*\)\s*\{"
    )
    close = find_method_close(java_src, method_regex)
    if close is None:
        return java_src

    wrapper = (
        "\n\n"
        "    /** Populates a {@link mindustry.ui.dialogs.SettingsMenuDialog.SettingsTable} with this mod's settings. */\n"
        "    public void bekBuildSettings(SettingsMenuDialog.SettingsTable table){\n"
        "        buildSettings(table);\n"
        "    }\n"
    )
    return java_src[: close + 1] + wrapper + java_src[close + 1 :]


def guard_add_category_calls(java_src: str) -> str:
    java_src = re.sub(
        r"^(\s*)(ui\.settings\.addCategory\()",
        r"\1if(!bekBundled) \2",
        java_src,
        flags=re.MULTILINE,
    )
    java_src = re.sub(
        r"^(\s*)(Vars\.ui\.settings\.addCategory\()",
        r"\1if(!bekBundled) \2",
        java_src,
        flags=re.MULTILINE,
    )
    return java_src


def suppress_bundled_update_controls(java_src: str) -> str:
    if "bekBundled" not in java_src or "GithubUpdateCheck" not in java_src:
        return java_src

    out: List[str] = []
    for line in java_src.splitlines(keepends=True):
        newline = ""
        bare = line
        if bare.endswith("\r\n"):
            bare = bare[:-2]
            newline = "\r\n"
        elif bare.endswith("\n"):
            bare = bare[:-1]
            newline = "\n"

        stripped = bare.strip()
        already_guarded = stripped.startswith("if(!bekBundled)") or stripped.startswith("if (!bekBundled)")
        is_update_call = stripped in ("GithubUpdateCheck.applyDefaults();", "GithubUpdateCheck.checkOnce();")
        is_update_setting = (
            "GithubUpdateCheck." in stripped
            and (".checkPref(" in stripped or ".pref(" in stripped)
            and stripped.endswith(";")
        )

        if not already_guarded and (is_update_call or is_update_setting):
            indent = bare[: len(bare) - len(bare.lstrip())]
            out.append(indent + "if(!bekBundled) " + stripped + newline)
        else:
            out.append(line)

    return "".join(out)


def inject_bek_hooks(mod_id: str, java_src: str) -> str:
    # 1) Ensure SettingsMenuDialog import exists if bekBuildSettings is referenced.
    if "mindustry.ui.dialogs.SettingsMenuDialog" not in java_src:
        # Insert near other mindustry.ui imports if possible, otherwise after package line.
        inserted = False
        def _ins(m: re.Match[str]) -> str:
            nonlocal inserted
            inserted = True
            return m.group(0) + "import mindustry.ui.dialogs.SettingsMenuDialog;\n"

        java_src = re.sub(r"(import mindustry\.ui\.[^\n]+\n)", _ins, java_src, count=1)
        if not inserted:
            java_src = re.sub(
                r"(package\s+[a-zA-Z0-9_.]+;\s*\n)",
                r"\1\nimport mindustry.ui.dialogs.SettingsMenuDialog;\n",
                java_src,
                count=1,
            )

    # 2) Add static bekBundled flag after class declaration.
    class_decl = re.search(r"(public\s+class\s+\w+\s+extends\s+(?:mindustry\.mod\.)?Mod\s*\{)", java_src)
    if not class_decl:
        raise ValueError(f"[{mod_id}] Could not find class declaration.")
    insert_at = class_decl.end(1)
    if "public static boolean bekBundled" not in java_src:
        java_src = (
            java_src[:insert_at]
            + "\n    /** When true, this mod is running as a bundled component inside Neon. */\n"
            + "    public static boolean bekBundled = false;\n\n"
            + java_src[insert_at:]
        )

    # 3) Modify registerSettings to early return when bundled, and extract builder into bekBuildSettings.
    # Marker patterns per mod (minor differences in signature/args).
    if mod_id == "rbm":
        marker = re.compile(
            r"ui\.settings\.addCategory\([^;]*?,\s*(?P<lambda>table\s*->\s*(?P<brace>\{))",
            re.MULTILINE,
        )
    else:
        marker = re.compile(
            r"ui\.settings\.addCategory\([^;]*?,\s*(?P<lambda>table\s*->\s*(?P<brace>\{))",
            re.MULTILINE,
        )

    # If we've already converted to this::bekBuildSettings, avoid redoing.
    if "this::bekBuildSettings" in java_src and "public void bekBuildSettings" in java_src:
        # Still ensure bundled early-return exists.
        java_src = ensure_bundled_return(java_src)
        java_src = guard_add_category_calls(java_src)
        java_src = suppress_bundled_update_controls(java_src)
        return java_src

    try:
        lambda_start, open_brace, close_brace = find_lambda_body(java_src, marker)
        body = java_src[open_brace + 1 : close_brace].strip("\n")

        # Replace lambda with method reference.
        java_src = java_src[:lambda_start] + "this::bekBuildSettings" + java_src[close_brace + 1 :]

        # Add method if missing.
        if "public void bekBuildSettings(SettingsMenuDialog.SettingsTable table)" not in java_src:
            method = (
                "\n    /** Populates a {@link mindustry.ui.dialogs.SettingsMenuDialog.SettingsTable} with this mod's settings. */\n"
                + "    public void bekBuildSettings(SettingsMenuDialog.SettingsTable table){\n"
                + body
                + "\n    }\n"
            )

            # Insert method right after registerSettings() method end.
            reg_m = re.search(r"private\s+void\s+registerSettings\s*\(\)\s*\{", java_src)
            if not reg_m:
                raise ValueError(f"[{mod_id}] Could not find registerSettings() to anchor insertion.")
            reg_start = reg_m.start()
            # Find end brace of registerSettings with brace matching.
            reg_open = java_src.find("{", reg_m.end() - 1)
            if reg_open == -1:
                raise ValueError(f"[{mod_id}] registerSettings() has no opening brace.")
            depth = 0
            i = reg_open
            in_string = False
            in_char = False
            esc = False
            while i < len(java_src):
                ch = java_src[i]
                if in_string:
                    if esc:
                        esc = False
                    elif ch == "\\":
                        esc = True
                    elif ch == '"':
                        in_string = False
                elif in_char:
                    if esc:
                        esc = False
                    elif ch == "\\":
                        esc = True
                    elif ch == "'":
                        in_char = False
                else:
                    if ch == '"':
                        in_string = True
                    elif ch == "'":
                        in_char = True
                    elif ch == "{":
                        depth += 1
                    elif ch == "}":
                        depth -= 1
                        if depth == 0:
                            reg_close = i
                            break
                i += 1
            else:
                raise ValueError(f"[{mod_id}] Unterminated registerSettings() braces.")

            insert_method_at = reg_close + 1
            java_src = java_src[:insert_method_at] + method + java_src[insert_method_at:]
    except ValueError:
        # Fallback for modules that already expose buildSettings() via method reference.
        if "this::buildSettings" in java_src:
            java_src = java_src.replace("this::buildSettings", "this::bekBuildSettings")
        java_src = ensure_bek_build_settings_wrapper(java_src)

    java_src = ensure_bundled_return(java_src)
    java_src = guard_add_category_calls(java_src)
    java_src = suppress_bundled_update_controls(java_src)
    return java_src


def inject_overlay_compat_hooks(mod_id: str, java_src: str) -> str:
    if mod_id == "pgmm":
        java_src = java_src.replace(
            "this(vanillaMarkers(), OverlayUiBridge.UNSUPPORTED);",
            "this(vanillaMarkers(), OverlayUiBridge.autoDetect());",
        )
        java_src = java_src.replace(
            "// MindustryX OverlayUI integration is injected by the dedicated mainX entry.",
            "// Optional OverlayUI integration is injected by the dedicated mainX entry or detected in vanilla.",
        )
        return java_src

    if mod_id in {"sp", "rbm", "spdb", "bhk"}:
        java_src = java_src.replace(
            "return OverlayUiBridge.UNSUPPORTED;",
            "return OverlayUiBridge.autoDetect();",
            1,
        )
    return java_src


def ensure_bundled_return(java_src: str) -> str:
    # Insert `if(bekBundled) return;` right after the ui/settings null check.
    # Works for:
    # - `if(ui == null || ui.settings == null) return;`
    # - `if(Vars.ui == null || Vars.ui.settings == null) return;`
    patterns = [
        re.compile(r"(if\s*\(\s*ui\s*==\s*null\s*\|\|\s*ui\.settings\s*==\s*null\s*\)\s*return\s*;)") ,
        re.compile(r"(if\s*\(\s*Vars\.ui\s*==\s*null\s*\|\|\s*Vars\.ui\.settings\s*==\s*null\s*\)\s*return\s*;)")
    ]
    m = None
    for pat in patterns:
        m = pat.search(java_src)
        if m:
            break
    if m is None:
        return java_src
    anchor_end = m.end(1)
    # If already present nearby, skip.
    window = java_src[anchor_end : anchor_end + 200]
    if "if(bekBundled) return;" in window:
        return java_src
    return java_src[:anchor_end] + "\n        if(bekBundled) return;\n" + java_src[anchor_end:]


def parse_properties(text: str) -> Dict[str, str]:
    out: Dict[str, str] = {}
    for raw in text.splitlines():
        line = raw.rstrip("\r")
        if not line.strip():
            continue
        if line.lstrip().startswith("#"):
            continue
        if "=" not in line:
            continue
        k, v = line.split("=", 1)
        k = k.strip()
        if not k:
            continue
        out[k] = v
    return out


def merge_bundles(mods: List[Tuple[SubMod, Path]]) -> None:
    # Collect union of bundle filenames across all mods.
    bundle_names: set[str] = set()
    for sm, repo_dir in mods:
        bundles = repo_join(repo_dir, _source_bundles_dir(sm))
        for p in bundles.glob("bundle*.properties"):
            bundle_names.add(p.name)

    target_dir = ROOT / "src/main/resources/bundles"
    target_dir.mkdir(parents=True, exist_ok=True)

    for name in sorted(bundle_names):
        merged: Dict[str, str] = {}
        origins: Dict[str, str] = {}
        collisions: List[str] = []
        for sm, repo_dir in mods:
            src_path = repo_join(repo_dir, _source_bundles_dir(sm)) / name
            if not src_path.exists():
                continue
            props = parse_properties(read_text(src_path))
            for k, v in props.items():
                if k in merged and merged[k] != v:
                    collisions.append(f"{name}: key '{k}' differs between {origins[k]} and {sm.id}")
                else:
                    merged[k] = v
                    origins[k] = sm.id

        if collisions:
            msg = "\n".join(collisions[:50])
            raise RuntimeError(f"Bundle merge collisions detected for {name}:\n{msg}")

        # Merge BEK-local bundle entries (kept in-repo).
        bek_extra = TOOLS / "bektools-bundles" / name
        if bek_extra.exists():
            extra = parse_properties(read_text(bek_extra))
            for k, v in extra.items():
                # BEK-local bundle entries are treated as explicit overrides.
                # This keeps Neon naming/style decisions deterministic even when
                # upstream modules change their own translations.
                merged[k] = v
                origins[k] = "bek"

        source_ids = " + ".join(sm.id for sm, _ in mods)
        header = [
            "# Auto-merged for Neon",
            f"# Sources: {source_ids}",
            "",
        ]
        lines = header + [f"{k}={merged[k]}" for k in sorted(merged.keys())]
        write_text(target_dir / name, "\n".join(lines) + "\n")


def merge_selected_bundles(mods: List[Tuple[SubMod, Path]]) -> None:
    """Incrementally merge selected child bundles without reading other workspaces."""
    target_dir = ROOT / "src/main/resources/bundles"
    target_dir.mkdir(parents=True, exist_ok=True)

    bundle_names: set[str] = set()
    for sm, repo_dir in mods:
        bundles = repo_join(repo_dir, _source_bundles_dir(sm))
        for p in bundles.glob("bundle*.properties"):
            bundle_names.add(p.name)

    for name in sorted(bundle_names):
        target_path = target_dir / name
        existing_text = read_text(target_path) if target_path.exists() else ""
        merged: Dict[str, str] = parse_properties(existing_text)
        origins: Dict[str, str] = {key: "existing" for key in merged}
        collisions: List[str] = []

        for sm, repo_dir in mods:
            src_path = repo_join(repo_dir, _source_bundles_dir(sm)) / name
            if not src_path.exists():
                continue
            props = parse_properties(read_text(src_path))
            for key, value in props.items():
                if key in merged and merged[key] != value:
                    collisions.append(f"{name}: key '{key}' differs between {origins[key]} and {sm.id}")
                else:
                    merged[key] = value
                    origins[key] = sm.id

        if collisions:
            msg = "\n".join(collisions[:50])
            raise RuntimeError(f"Bundle merge collisions detected for {name}:\n{msg}")

        bek_extra = TOOLS / "bektools-bundles" / name
        if bek_extra.exists():
            extra = parse_properties(read_text(bek_extra))
            for key, value in extra.items():
                merged[key] = value
                origins[key] = "bek"

        source_ids: List[str] = []
        for line in existing_text.splitlines():
            if line.startswith("# Sources:"):
                source_ids = [part.strip() for part in line[len("# Sources:"):].split("+") if part.strip()]
                break
        for sm, _ in mods:
            if sm.id not in source_ids:
                source_ids.append(sm.id)

        header = [
            "# Auto-merged for Neon",
            f"# Sources: {' + '.join(source_ids)}",
            "",
        ]
        lines = header + [f"{key}={merged[key]}" for key in sorted(merged.keys())]
        write_text(target_path, "\n".join(lines) + "\n")


def copy_java(mod: SubMod, repo_dir: Path) -> None:
    source_pairs: List[Tuple[Path, Path]] = []
    if mod.java_package_dir is not None:
        source_pairs.append((mod.java_package_dir, _source_main_java_dir(mod)))
    source_pairs += list(zip(mod.extra_java_dirs, _source_extra_java_dirs(mod)))

    for target_pkg_dir, source_rel in source_pairs:
        upstream_pkg_dir = repo_join(repo_dir, source_rel)
        if not upstream_pkg_dir.exists():
            raise FileNotFoundError(f"Upstream java dir not found: {upstream_pkg_dir}")

        target_pkg_dir.mkdir(parents=True, exist_ok=True)

        # Clear existing .java files in target package to avoid stale leftovers.
        for p in target_pkg_dir.rglob("*.java"):
            try:
                p.unlink()
            except OSError:
                pass

        for src_path in upstream_pkg_dir.rglob("*.java"):
            rel = src_path.relative_to(upstream_pkg_dir)
            dst_path = target_pkg_dir / rel
            text = read_text(src_path)
            if (
                mod.main_class_file
                and mod.java_package_dir is not None
                and target_pkg_dir == mod.java_package_dir
                and src_path.name == mod.main_class_file
            ):
                if mod.inject_bek_hooks:
                    text = inject_bek_hooks(mod.id, text)
                text = inject_overlay_compat_hooks(mod.id, text)
            write_text(dst_path, text)


def copy_kotlin(mod: SubMod, repo_dir: Path) -> None:
    if mod.kotlin_package_dir is None:
        return

    upstream_pkg_dir = repo_join(repo_dir, _source_kotlin_dir(mod))
    if not upstream_pkg_dir.exists():
        raise FileNotFoundError(f"Upstream kotlin dir not found: {upstream_pkg_dir}")

    mod.kotlin_package_dir.mkdir(parents=True, exist_ok=True)
    for p in mod.kotlin_package_dir.rglob("*.kt"):
        try:
            p.unlink()
        except OSError:
            pass

    for src_path in upstream_pkg_dir.rglob("*.kt"):
        rel = src_path.relative_to(upstream_pkg_dir)
        write_text(mod.kotlin_package_dir / rel, read_text(src_path))


def copy_extra_resources(mod: SubMod, repo_dir: Path) -> None:
    for target_dir, source_rel in zip(mod.extra_resource_dirs, _source_extra_resource_dirs(mod)):
        upstream_dir = repo_join(repo_dir, source_rel)
        if not upstream_dir.exists():
            raise FileNotFoundError(f"Upstream resource dir not found: {upstream_dir}")

        target_dir.mkdir(parents=True, exist_ok=True)
        for p in target_dir.rglob("*"):
            if p.is_file():
                try:
                    p.unlink()
                except OSError:
                    pass

        for src_path in upstream_dir.rglob("*"):
            if not src_path.is_file():
                continue
            rel = src_path.relative_to(upstream_dir)
            dst_path = target_dir / rel
            dst_path.parent.mkdir(parents=True, exist_ok=True)
            dst_path.write_bytes(src_path.read_bytes())


def copy_sources(mod: SubMod, repo_dir: Path) -> None:
    copy_java(mod, repo_dir)
    copy_kotlin(mod, repo_dir)
    copy_extra_resources(mod, repo_dir)


def main(argv: List[str]) -> int:
    ap = argparse.ArgumentParser(description="Sync Neon with local sub-mod checkouts.")
    ap.add_argument("--check", action="store_true", help="Only check for updates; do not write files.")
    ap.add_argument("--force", action="store_true", help="Force rewrite even if lock matches.")
    ap.add_argument(
        "--only",
        nargs="+",
        metavar="ID",
        help="Only sync the selected child IDs; unselected workspaces and lock entries are preserved.",
    )
    args = ap.parse_args(argv)

    mods = load_config()
    known_ids = {mod.id for mod in mods}
    selected_ids = set(args.only or [])
    unknown_ids = selected_ids - known_ids
    if unknown_ids:
        raise ValueError(f"Unknown --only child ID(s): {', '.join(sorted(unknown_ids))}")

    selected_mods = [mod for mod in mods if not selected_ids or mod.id in selected_ids]
    if not selected_mods:
        raise ValueError("--only selected no child modules.")

    lock = load_lock()
    lock_mods: dict = lock.get("mods", {})

    updated_any = False
    resolved: List[Tuple[SubMod, Path, SourceState]] = []

    for sm in selected_mods:
        repo_dir = resolve_local_repo(sm)
        state = source_state(sm, repo_dir)
        resolved.append((sm, repo_dir, state))

        lock_entry = lock_mods.get(sm.id) or {}
        print(summarize_check(sm, state, lock_entry))

        if args.check:
            continue

        # Always sync Java sources when writing to ensure extra helper files are included.
        # Upstream updates are detected via lock/head for reporting only.
        copy_sources(sm, repo_dir)
        synced_head = lock_entry.get("syncedHead") or lock_entry.get("head")
        if synced_head != state.workspace_head:
            updated_any = True

    if args.check:
        return 0

    if selected_ids:
        merge_selected_bundles([(sm, repo_dir) for (sm, repo_dir, _state) in resolved])
    else:
        merge_bundles([(sm, repo_dir) for (sm, repo_dir, _state) in resolved])

    # Update only selected lock entries. Unselected child snapshots remain untouched.
    for sm, _repo_dir, state in resolved:
        lock_mods[sm.id] = {
            "sourceKind": state.source_kind,
            "syncedHead": state.workspace_head,
            "workspaceHeadAtSync": state.workspace_head,
            "workspaceDirtyAtSync": state.workspace_dirty,
            "upstreamRef": state.upstream_ref,
            "upstreamHeadAtSync": state.upstream_head,
            "aheadOfUpstreamAtSync": state.ahead_of_upstream,
            "behindUpstreamAtSync": state.behind_upstream,
            "repoUrl": sm.repo_url,
            "branch": sm.branch,
            "localPath": str(sm.local_path),
        }
    lock["mods"] = lock_mods
    save_lock(lock)

    print("Wrote:")
    for sm in selected_mods:
        if sm.java_package_dir is not None:
            print(f"  - {sm.java_package_dir.as_posix()}/ (java package)")
        for extra in sm.extra_java_dirs:
            print(f"  - {extra.as_posix()}/ (java package)")
        if sm.kotlin_package_dir is not None:
            print(f"  - {sm.kotlin_package_dir.as_posix()}/ (kotlin package)")
        for extra in sm.extra_resource_dirs:
            print(f"  - {extra.as_posix()}/ (resources)")
    print(f"  - {Path('src/main/resources/bundles').as_posix()} (merged)")
    print(f"  - {LOCK_PATH.relative_to(ROOT).as_posix()} (lock)")

    if not updated_any:
        print("No local source changes detected since the last synced lock; bundles/lock refreshed.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
