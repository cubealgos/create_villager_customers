#!/usr/bin/env python3
"""Toolchain floors and the spec copy, per docs/spec/contracts/platform-matrix.md.

Read-only: reports, changes nothing. Exit 0 when every check passes, 5 when a toolchain is absent
or below its floor (PLAT-REQ-002), 6 when docs/spec/ differs from the vault copy. Each failure names
the floor and what was found, and looks where the tool actually lives rather than only on PATH.
"""
from __future__ import annotations

import os
import re
import shutil
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
FLOORS = {"java": (25,), "gradle": (9, 5, 1), "just": (1, 58), "python": (3, 12)}


def run(*cmd: str) -> str:
    try:
        out = subprocess.run(cmd, capture_output=True, text=True, timeout=60)
    except (OSError, subprocess.TimeoutExpired) as exc:
        return f"<{exc}>"
    return (out.stdout + out.stderr).strip()


def parse_version(text: str) -> tuple[int, ...] | None:
    match = re.search(r"(\d+)(?:\.(\d+))?(?:\.(\d+))?", text)
    if not match:
        return None
    return tuple(int(part) for part in match.groups() if part is not None)


def check_java() -> tuple[bool, str]:
    java_home = os.environ.get("JAVA_HOME")
    java = str(Path(java_home) / "bin" / "java") if java_home else shutil.which("java")
    where = "JAVA_HOME" if java_home else "PATH"
    if not java or not Path(java).exists():
        return False, f"java: not found on {where} (floor {FLOORS['java'][0]})"
    text = run(java, "-version")
    match = re.search(r'version "(\d+)', text)
    if not match:
        return False, f"java: could not parse version from {java}: {text[:80]}"
    major = int(match.group(1))
    ok = major >= FLOORS["java"][0]
    return ok, f"java: {major} on {where} ({java}); floor {FLOORS['java'][0]}"


def check_wrapper() -> tuple[bool, str]:
    props = ROOT / "gradle" / "wrapper" / "gradle-wrapper.properties"
    if not props.exists():
        return False, "gradle wrapper: gradle/wrapper/gradle-wrapper.properties missing"
    match = re.search(r"gradle-(\d+\.\d+(?:\.\d+)?)-", props.read_text())
    if not match:
        return False, "gradle wrapper: distributionUrl has no recognisable version"
    found = parse_version(match.group(1)) or ()
    ok = found >= FLOORS["gradle"]
    return ok, f"gradle wrapper: {match.group(1)} pinned; floor {'.'.join(map(str, FLOORS['gradle']))}"


def check_tool(name: str, floor_key: str | None, *args: str) -> tuple[bool, str]:
    path = shutil.which(name)
    if not path:
        floor = ".".join(map(str, FLOORS[floor_key])) if floor_key else "any"
        return False, f"{name}: not found on PATH (floor {floor})"
    if not floor_key:
        return True, f"{name}: present ({path})"
    found = parse_version(run(path, *args)) or ()
    floor = FLOORS[floor_key]
    return found >= floor, f"{name}: {'.'.join(map(str, found))} ({path}); floor {'.'.join(map(str, floor))}"


def main_checkout() -> Path:
    """The repository's main checkout, even when run from a worktree under .worktrees/."""
    common = run("git", "-C", str(ROOT), "rev-parse", "--path-format=absolute", "--git-common-dir")
    return Path(common).parent if common and not common.startswith("<") else ROOT


def check_spec_copy() -> tuple[bool | None, str]:
    default = main_checkout().parent / "heimathafen" / "vault" / "projects" / "create_villager_customers" / "spec"
    vault = Path(os.environ.get("VC_VAULT_SPEC", default))
    if not vault.exists():
        return None, f"spec copy: vault not present at {vault}; skipped (set VC_VAULT_SPEC to check)"
    diff = run("diff", "-rq", str(ROOT / "docs" / "spec"), str(vault.resolve()))
    if not diff:
        return True, "spec copy: docs/spec/ is identical to the vault"
    return False, "spec copy: docs/spec/ differs from the vault:\n  " + diff.replace("\n", "\n  ")


def check_map() -> tuple[bool, str]:
    """The map is generated from the source and a stale one fails: run the command, not the function."""
    try:
        proc = subprocess.run([sys.executable, str(ROOT / "tools" / "map.py"), "--root", str(ROOT), "--check"],
                              capture_output=True, text=True, timeout=120)
    except (OSError, subprocess.TimeoutExpired) as exc:
        return False, f"map: could not run tools/map.py ({exc})"
    if proc.returncode == 0:
        return True, "map: docs/map.md and docs/map/ match the source"
    return False, "map: stale; run `just map`:\n  " + (proc.stderr.strip() or proc.stdout.strip()).replace("\n", "\n  ")


def main() -> int:
    results: list[tuple[bool | None, str]] = [
        check_java(),
        check_wrapper(),
        check_tool("just", "just", "--version"),
        check_tool("python3", "python", "--version"),
        check_tool("kontor", None),
        check_map(),
    ]
    spec = check_spec_copy()
    code = 0
    for ok, message in results:
        print(("   ok   " if ok else "  FAIL  ") + message)
        if not ok:
            code = 5
    ok, message = spec
    print(("  skip  " if ok is None else "   ok   " if ok else "  FAIL  ") + message)
    if ok is False and code == 0:
        code = 6
    print("toolchain: all floors met" if code == 0 else f"toolchain: problems found (exit {code})")
    return code


if __name__ == "__main__":
    sys.exit(main())
