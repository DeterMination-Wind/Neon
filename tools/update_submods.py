#!/usr/bin/env python3
from __future__ import annotations

import argparse
import dataclasses
import datetime as dt
import hashlib
import json
import os
import re
import shutil
import subprocess
import sys
from pathlib import Path
from typing import Dict, Iterable, List, Optional, Tuple


ROOT = Path(__file__).resolve().parents[1]
TOOLS = ROOT / "tools"
CONFIG_PATH = TOOLS / "submods.json"
LOCK_PATH = TOOLS / "submods.lock.json"


@dataclasses.dataclass(frozen=True)
class SubMod:
    id: str
    name: str
    local_path: Path
    java_package_dir: Path
    main_class_file: str
    bundles_dir: Path
    inject_bek_hooks: bool = True
    allow_no_git: bool = False


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
        mods.append(
            SubMod(
                id=m["id"],
                name=m["name"],
                local_path=(ROOT / local_path).resolve(),
                java_package_dir=ROOT / m["javaPackageDir"],
                main_class_file=m["mainClassFile"],
                bundles_dir=ROOT / m["bundlesDir"],
                inject_bek_hooks=bool(m.get("injectBekHooks", True)),
                allow_no_git=bool(m.get("allowNoGit", False)),
            )
        )
    return mods


def load_lock() -> dict:
    if LOCK_PATH.exists():
        return json.loads(LOCK_PATH.read_text(encoding="utf-8"))
    return {"schema": 1, "generatedAtUtc": None, "mods": {}}


def save_lock(lock: dict) -> None:
    lock["schema"] = 1
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


def nogit_head(repo_dir: Path) -> str:
    """Computes a content hash for directories without git metadata."""
    h = hashlib.sha1()
    include_patterns = [
        "src/main/java/**/*.java",
        "src/main/resources/bundles/bundle*.properties",
        "src/main/resources/mod.json",
    ]
    files: List[Path] = []
    for pat in include_patterns:
        files.extend(repo_dir.glob(pat))

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
    return nogit_head(repo_dir)


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
        return java_src

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

    java_src = ensure_bundled_return(java_src)
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
    for _, repo_dir in mods:
        bundles = repo_dir / "src/main/resources/bundles"
        for p in bundles.glob("bundle*.properties"):
            bundle_names.add(p.name)

    target_dir = ROOT / "src/main/resources/bundles"
    target_dir.mkdir(parents=True, exist_ok=True)

    for name in sorted(bundle_names):
        merged: Dict[str, str] = {}
        origins: Dict[str, str] = {}
        collisions: List[str] = []
        for sm, repo_dir in mods:
            src_path = repo_dir / "src/main/resources/bundles" / name
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
                if k in merged and merged[k] != v:
                    raise RuntimeError(f"Bundle merge collision for {name}: BEK key '{k}' conflicts with upstream.")
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


def copy_java(mod: SubMod, repo_dir: Path) -> None:
    upstream_pkg_dir = repo_dir / mod.java_package_dir.relative_to(ROOT)
    if not upstream_pkg_dir.exists():
        raise FileNotFoundError(f"Upstream java dir not found: {upstream_pkg_dir}")

    target_pkg_dir = mod.java_package_dir
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
        if mod.inject_bek_hooks and src_path.name == mod.main_class_file:
            text = inject_bek_hooks(mod.id, text)
        write_text(dst_path, text)


def main(argv: List[str]) -> int:
    ap = argparse.ArgumentParser(description="Sync Neon with local sub-mod checkouts.")
    ap.add_argument("--check", action="store_true", help="Only check for updates; do not write files.")
    ap.add_argument("--force", action="store_true", help="Force rewrite even if lock matches.")
    args = ap.parse_args(argv)

    mods = load_config()
    lock = load_lock()
    lock_mods: dict = lock.get("mods", {})

    updated_any = False
    resolved: List[Tuple[SubMod, Path, str]] = []

    for sm in mods:
        repo_dir = resolve_local_repo(sm)
        head = source_head(sm, repo_dir)
        resolved.append((sm, repo_dir, head))

        prev = (lock_mods.get(sm.id) or {}).get("head")
        changed = prev != head
        status = "UPDATED" if changed else "OK"
        print(f"[{sm.id}] {sm.name}: {status} ({head[:10]})")

        if args.check:
            continue

        # Always sync Java sources when writing to ensure extra helper files are included.
        # Upstream updates are detected via lock/head for reporting only.
        copy_java(sm, repo_dir)
        if changed:
            updated_any = True

    if args.check:
        return 0

    # Always regenerate bundles when writing, to keep deterministic output.
    merge_bundles([(sm, repo_dir) for (sm, repo_dir, _head) in resolved])

    # Update lock file.
    for sm, _repo_dir, head in resolved:
        lock_mods[sm.id] = {"head": head, "localPath": str(sm.local_path)}
    lock["mods"] = lock_mods
    save_lock(lock)

    print("Wrote:")
    for sm in mods:
        print(f"  - {sm.java_package_dir.as_posix()}/ (java package)")
    print(f"  - {Path('src/main/resources/bundles').as_posix()} (merged)")
    print(f"  - {LOCK_PATH.relative_to(ROOT).as_posix()} (lock)")

    if not updated_any:
        print("No upstream Java changes detected; bundles/lock refreshed.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
