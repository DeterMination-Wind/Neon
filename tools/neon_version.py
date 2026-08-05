#!/usr/bin/env python3
"""Resolve Neon release labels and Mindustry descriptor version codes."""

from __future__ import annotations

import argparse
import json
import re
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
STABLE_RE = re.compile(r"^N(\d+)$", re.IGNORECASE)
BETA_RE = re.compile(r"^B(\d+)\.(\d+)$", re.IGNORECASE)
NUMERIC_RE = re.compile(r"^\d+$")
SEMVER_RE = re.compile(r"^(\d+)\.(\d+)\.(\d+)(?:[-+].*)?$")


def normalize(label: str) -> str:
    value = label.strip()
    if value[:1].lower() == "v":
        value = value[1:].strip()
    return value


def _code_from_match(major: str, build: str = "0") -> int:
    major_number = int(major)
    build_number = int(build)
    if major_number < 0 or build_number < 0:
        raise ValueError("version components must be non-negative")
    if build_number > 9999:
        raise ValueError("beta build number must fit in four digits")
    return major_number * 10000 + build_number


def version_code(label: str) -> int:
    value = normalize(label)

    stable = STABLE_RE.fullmatch(value)
    if stable:
        return _code_from_match(stable.group(1))

    beta = BETA_RE.fullmatch(value)
    if beta:
        if int(beta.group(2)) <= 0:
            raise ValueError("beta build number must be greater than zero")
        return _code_from_match(beta.group(1), beta.group(2))

    if NUMERIC_RE.fullmatch(value):
        return int(value)

    semver = SEMVER_RE.fullmatch(value)
    if semver:
        major, minor, patch = (int(part) for part in semver.groups())
        return major * 10000 + minor * 100 + patch

    raise ValueError(
        f"unsupported Neon version '{label}'; expected N<number>, B<number>.<build>, or legacy x.y.z"
    )


def release_name(label: str) -> str:
    value = normalize(label)

    stable = STABLE_RE.fullmatch(value)
    if stable:
        return f"N{int(stable.group(1))}"

    beta = BETA_RE.fullmatch(value)
    if beta:
        if int(beta.group(2)) <= 0:
            raise ValueError("beta build number must be greater than zero")
        return f"B{int(beta.group(1))}.{int(beta.group(2))}"

    semver = SEMVER_RE.fullmatch(value)
    if semver and int(semver.group(2)) == 0 and int(semver.group(3)) == 0:
        return f"N{int(semver.group(1))}"

    return value


def replace_version(path: Path, pattern: str, code: str) -> None:
    with path.open("r", encoding="utf-8", newline="") as stream:
        text = stream.read()
    updated, count = re.subn(pattern, lambda match: f"{match.group(1)}{code}{match.group(2)}", text, count=1)
    if count != 1:
        raise ValueError(f"expected one version field in {path}")
    with path.open("w", encoding="utf-8", newline="") as stream:
        stream.write(updated)


def set_project_files(code: int) -> None:
    value = str(code)
    replace_version(ROOT / "mod.json", r'("version"\s*:\s*")[^"]*(")', value)
    replace_version(ROOT / "mod.hjson", r'(version\s*:\s*")[^"]*(")', value)
    replace_version(ROOT / "build.gradle", r'(\bversion\s*=\s*")[^"]*(")', value)


def read_project_versions() -> dict[str, str]:
    mod_json = json.loads((ROOT / "mod.json").read_text(encoding="utf-8"))
    hjson_text = (ROOT / "mod.hjson").read_text(encoding="utf-8")
    gradle_text = (ROOT / "build.gradle").read_text(encoding="utf-8")
    hjson_match = re.search(r'version\s*:\s*"([^"]+)"', hjson_text)
    gradle_match = re.search(r'\bversion\s*=\s*"([^"]+)"', gradle_text)
    if hjson_match is None or gradle_match is None:
        raise ValueError("could not read all project version fields")
    return {
        "mod.json": str(mod_json["version"]),
        "mod.hjson": hjson_match.group(1),
        "build.gradle": gradle_match.group(1),
    }


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("label", help="release label or numeric descriptor version")
    parser.add_argument("--code", action="store_true", help="print the numeric descriptor version")
    parser.add_argument("--release-name", action="store_true", help="print the canonical release name")
    parser.add_argument("--set-files", action="store_true", help="write the numeric version to project files")
    parser.add_argument("--check-files", action="store_true", help="verify all project files use this numeric version")
    args = parser.parse_args()

    if sum(bool(flag) for flag in (args.code, args.release_name, args.set_files, args.check_files)) != 1:
        parser.error("choose exactly one of --code, --release-name, --set-files, or --check-files")

    code = version_code(args.label)
    if args.code:
        print(code)
    elif args.release_name:
        print(release_name(args.label))
    elif args.set_files:
        set_project_files(code)
        print(code)
    else:
        versions = read_project_versions()
        expected = str(code)
        mismatches = {path: value for path, value in versions.items() if value != expected}
        if mismatches:
            for path, value in mismatches.items():
                print(f"{path}: {value} (expected {expected})")
            return 1
        print(expected)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
