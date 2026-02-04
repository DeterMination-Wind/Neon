#!/usr/bin/env python3
from __future__ import annotations

import re
import subprocess
from dataclasses import dataclass
from pathlib import Path
from typing import Iterable, List, Optional, Tuple


ROOT = Path(__file__).resolve().parents[1]


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
    return proc.stdout


def git_ls_files() -> List[str]:
    out = run(["git", "ls-files"], cwd=ROOT)
    files = [line.strip() for line in out.splitlines() if line.strip()]
    return files


def dox_name_for_path(relpath: str) -> str:
    # Stable, unique name in repo root. Example:
    #   src/main/java/bektools/BekToolsMod.java
    # -> dox__src__main__java__bektools__BekToolsMod_java_dox.md
    p = relpath.replace("\\", "/")
    p = p.replace("/", "__")
    p = p.replace(".", "_")
    return f"dox__{p}_dox.md"


def is_binary_path(path: Path) -> bool:
    ext = path.suffix.lower()
    return ext in {
        ".png",
        ".jpg",
        ".jpeg",
        ".gif",
        ".webp",
        ".jar",
        ".zip",
        ".exe",
        ".dll",
        ".so",
        ".dylib",
        ".ttf",
        ".otf",
        ".woff",
        ".woff2",
    }


def strip_java_comments(src: str) -> str:
    # Remove block comments first, then line comments.
    src = re.sub(r"/\*.*?\*/", "", src, flags=re.DOTALL)
    src = re.sub(r"//.*?$", "", src, flags=re.MULTILINE)
    return src


@dataclass(frozen=True)
class JavaApi:
    package: Optional[str]
    classes: List[str]
    public_methods: List[str]
    protected_methods: List[str]
    public_fields: List[str]


def _collapse_ws(s: str) -> str:
    return re.sub(r"\s+", " ", s).strip()


def parse_java_api(path: Path) -> JavaApi:
    raw = path.read_text(encoding="utf-8", errors="replace")
    src = strip_java_comments(raw)

    pkg_m = re.search(r"^\s*package\s+([a-zA-Z0-9_.]+)\s*;\s*$", src, flags=re.MULTILINE)
    package = pkg_m.group(1) if pkg_m else None

    # Class/interface/enum declarations (incl. nested).
    classes: List[str] = []
    for m in re.finditer(r"\b(class|interface|enum)\s+([A-Za-z_][A-Za-z0-9_]*)\b", src):
        classes.append(m.group(2))
    # De-dup while preserving order.
    seen = set()
    classes = [c for c in classes if not (c in seen or seen.add(c))]

    # Public/protected methods: allow multi-line signatures by grabbing up to '{' or ';'.
    # This is heuristic but works well for this codebase.
    method_re = re.compile(
        r"\b(?P<vis>public|protected)\s+"
        r"(?P<mods>(?:static|final|synchronized|abstract|native|strictfp)\s+)*"
        r"(?P<ret>[A-Za-z0-9_.$<>,\[\]\s?]+?)\s+"
        r"(?P<name>[A-Za-z_][A-Za-z0-9_]*)\s*"
        r"\((?P<args>[^)]*)\)\s*"
        r"(?P<tail>throws\s+[^;{]+)?\s*"
        r"(?P<end>[;{])",
        flags=re.MULTILINE,
    )

    public_methods: List[str] = []
    protected_methods: List[str] = []
    for m in method_re.finditer(src):
        vis = m.group("vis")
        mods = _collapse_ws(m.group("mods") or "")
        ret = _collapse_ws(m.group("ret"))
        name = m.group("name")
        args = _collapse_ws(m.group("args"))
        tail = _collapse_ws(m.group("tail") or "")
        sig = f"{vis} {((mods + ' ') if mods else '')}{ret} {name}({args})"
        if tail:
            sig += f" {tail}"
        sig = _collapse_ws(sig)
        if vis == "public":
            public_methods.append(sig)
        else:
            protected_methods.append(sig)

    # Constructors (do not have return types; record them as part of the API surface).
    ctor_public: List[str] = []
    ctor_protected: List[str] = []
    if classes:
        ctor_mods = r"(?P<mods>(?:static|final|synchronized|abstract|native|strictfp)\s+)*"
        for cls in classes:
            ctor_re = re.compile(
                rf"\b(?P<vis>public|protected)\s+{ctor_mods}{re.escape(cls)}\s*"
                r"\((?P<args>[^)]*)\)\s*(?P<tail>throws\s+[^;{]+)?\s*(?P<end>[;{])",
                flags=re.MULTILINE,
            )
            for m in ctor_re.finditer(src):
                vis = m.group("vis")
                mods = _collapse_ws(m.group("mods") or "")
                args = _collapse_ws(m.group("args"))
                tail = _collapse_ws(m.group("tail") or "")
                sig = f"{vis} {((mods + ' ') if mods else '')}{cls}({args})"
                if tail:
                    sig += f" {tail}"
                sig = _collapse_ws(sig)
                if vis == "public":
                    ctor_public.append(sig)
                else:
                    ctor_protected.append(sig)

    # Public fields/constants (heuristic: visibility + type + name + ; or =)
    field_re = re.compile(
        r"^\s*public\s+"
        r"(?P<mods>(?:static|final|transient|volatile)\s+)*"
        r"(?P<type>[A-Za-z0-9_.$<>,\[\]\s?]+?)\s+"
        r"(?P<name>[A-Za-z_][A-Za-z0-9_]*)\s*(?:=|;)",
        flags=re.MULTILINE,
    )
    public_fields: List[str] = []
    for m in field_re.finditer(src):
        mods = _collapse_ws(m.group("mods") or "")
        typ = _collapse_ws(m.group("type"))
        name = m.group("name")
        public_fields.append(_collapse_ws(f"public {((mods + ' ') if mods else '')}{typ} {name}"))

    # De-dup while preserving order.
    def dedup(items: Iterable[str]) -> List[str]:
        s = set()
        out: List[str] = []
        for it in items:
            if it in s:
                continue
            s.add(it)
            out.append(it)
        return out

    return JavaApi(
        package=package,
        classes=classes,
        public_methods=dedup(ctor_public + public_methods),
        protected_methods=dedup(ctor_protected + protected_methods),
        public_fields=dedup(public_fields),
    )


def classify_file(relpath: str) -> str:
    p = relpath.replace("\\", "/")
    if p.endswith(".java"):
        return "Java source"
    if p.endswith(".kt"):
        return "Kotlin source"
    if p.endswith(".py"):
        return "Python script"
    if p.endswith(".bat"):
        return "Batch script"
    if p.endswith(".sh"):
        return "Shell script"
    if p.endswith(".md"):
        return "Markdown"
    if p.endswith(".json"):
        return "JSON"
    if p.endswith(".gradle"):
        return "Gradle build script"
    if p.endswith(".yml") or p.endswith(".yaml"):
        return "YAML"
    if p.endswith(".properties"):
        return "Localization bundle"
    if p.endswith(".png"):
        return "Image asset"
    if p.endswith(".jar"):
        return "Binary (jar)"
    return "Other"


def default_purpose(relpath: str) -> str:
    p = relpath.replace("\\", "/")
    base = Path(p).name
    if base == "mod.json":
        return "Mindustry mod metadata (name/version/entrypoint)."
    if base == "build.gradle":
        return "Gradle build configuration for BEK-Tools packaging."
    if base == "settings.gradle":
        return "Gradle settings (project name / included builds)."
    if p.startswith(".github/workflows/"):
        return "GitHub Actions workflow (CI/release)."
    if p.startswith("src/main/resources/bundles/"):
        return "Localization strings used by the mod UI/settings."
    if p == "tools/update_submods.py":
        return "Maintainer script: sync local submods into this repo."
    if p == "tools/submods.json":
        return "Config for update_submods.py (local repo paths and mapping)."
    if p == "tools/submods.lock.json":
        return "Lockfile recording upstream submod commits and generation time."
    if p.startswith("src/main/java/"):
        return "Mod implementation source code."
    if p.startswith("docs/"):
        return "Documentation asset."
    return "Repository file."


def write_doc(relpath: str) -> None:
    src_path = ROOT / relpath
    out_name = dox_name_for_path(relpath)
    out_path = ROOT / out_name

    file_type = classify_file(relpath)
    purpose = default_purpose(relpath)

    lines: List[str] = []
    lines.append(f"# DOX: `{relpath}`")
    lines.append("")
    lines.append(f"- 类型 / Type: **{file_type}**")
    lines.append(f"- 作用 / Purpose: {purpose}")
    lines.append("")

    if is_binary_path(src_path):
        lines.append("## 说明 / Notes")
        lines.append("- 该文件为二进制/资源文件，不在此文档中展开内部内容。")
        lines.append("")
        out_path.write_text("\n".join(lines).rstrip() + "\n", encoding="utf-8", newline="\n")
        return

    if src_path.suffix.lower() == ".java":
        api = parse_java_api(src_path)
        lines.append("## 包与类型 / Package & Types")
        lines.append(f"- `package`: `{api.package}`" if api.package else "- `package`: (none)")
        if api.classes:
            lines.append("- 声明的类型 / Declared types:")
            for c in api.classes:
                lines.append(f"  - `{c}`")
        else:
            lines.append("- 声明的类型 / Declared types: (none found)")
        lines.append("")

        lines.append("## 对外接口 / Public & Protected API")
        if api.public_fields:
            lines.append("- `public` 字段/常量:")
            for f in api.public_fields:
                lines.append(f"  - `{f}`")
        if api.public_methods:
            lines.append("- `public` 方法:")
            for m in api.public_methods:
                lines.append(f"  - `{m}`")
        if api.protected_methods:
            lines.append("- `protected` 方法:")
            for m in api.protected_methods:
                lines.append(f"  - `{m}`")
        if not (api.public_fields or api.public_methods or api.protected_methods):
            lines.append("- (未检测到 public/protected 声明，可能全部为 package-private/private，或签名为多行复杂格式。)")
        lines.append("")

        # Lightweight hints for common patterns in this repo.
        hint: List[str] = []
        text = src_path.read_text(encoding="utf-8", errors="replace")
        if "registerClientCommands" in text:
            hint.append("包含 `registerClientCommands`：提供客户端命令入口。")
        if "bekBuildSettings" in text:
            hint.append("包含 `bekBuildSettings`：供 BEK-Tools 整合设置页调用。")
        if "OverlayUI" in text or "MindustryXOverlayUI" in text:
            hint.append("包含 OverlayUI（MindustryX）集成：窗口可由 OverlayUI 管理。")
        if "Trigger.update" in text or "Trigger.draw" in text or "Trigger.uiDrawEnd" in text:
            hint.append("包含 Trigger 回调：在 update/draw 阶段执行逻辑。")
        if hint:
            lines.append("## 维护要点 / Maintainer Notes")
            for h in hint:
                lines.append(f"- {h}")
            lines.append("")

    elif src_path.suffix.lower() == ".properties":
        # Don't explode by listing all keys; keep it short and consistent.
        name = src_path.name
        lang = "default"
        m = re.match(r"bundle_(.+)\.properties$", name)
        if m:
            lang = m.group(1)
        lines.append("## 本地化 / Localization")
        lines.append(f"- 语言标识 / Locale: `{lang}`")
        lines.append("- 内容为 `key=value` 翻译条目，供设置项与提示文本使用。")
        lines.append("")
    else:
        # Generic: provide quick structure hints.
        if src_path.suffix.lower() in {".json", ".yml", ".yaml", ".gradle", ".md", ".gitattributes", ".gitignore"}:
            lines.append("## 说明 / Notes")
            lines.append("- 本文件为配置/文档类文件：建议只做与本仓库相关的改动，避免破坏构建与发布流程。")
            lines.append("")

    out_path.write_text("\n".join(lines).rstrip() + "\n", encoding="utf-8", newline="\n")


def write_index(dox_files: List[Tuple[str, str]]) -> None:
    # dox_files: (source_relpath, dox_filename)
    out = ROOT / "INDEX_dox.md"
    by_dir = {}
    for src, dox in dox_files:
        d = str(Path(src).parent).replace("\\", "/")
        by_dir.setdefault(d, []).append((src, dox))

    lines: List[str] = []
    lines.append("# DOX Index")
    lines.append("")
    lines.append("该目录下的 `*_dox.md` 文件为 BEK-Tools 仓库的逐文件说明文档（自动生成，可手工补充）。")
    lines.append("")
    lines.append("## 生成方式 / Regeneration")
    lines.append("```bash")
    lines.append("python tools/generate_dox.py")
    lines.append("```")
    lines.append("")

    for d in sorted(by_dir.keys()):
        lines.append(f"## `{d}`")
        for src, dox in sorted(by_dir[d], key=lambda x: x[0]):
            lines.append(f"- `{src}` -> `{dox}`")
        lines.append("")

    out.write_text("\n".join(lines).rstrip() + "\n", encoding="utf-8", newline="\n")


def main() -> None:
    files = git_ls_files()
    dox_files: List[Tuple[str, str]] = []

    # Generate per-file docs.
    for rel in files:
        dox = dox_name_for_path(rel)
        write_doc(rel)
        dox_files.append((rel, dox))

    write_index(dox_files)
    print(f"Generated {len(dox_files)} dox files + INDEX_dox.md in {ROOT}")


if __name__ == "__main__":
    main()
