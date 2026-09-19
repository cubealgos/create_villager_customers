#!/usr/bin/env python3
"""Print the release notes for a version: its CHANGELOG.md section plus the jar's SHA-256 (REL-REQ-002)."""
import re
import sys
from pathlib import Path


def section(changelog: str, version: str) -> str:
    match = re.search(rf"^## \[{re.escape(version)}\].*?$(.*?)(?=^## \[|\Z)", changelog, re.M | re.S)
    if not match:
        raise SystemExit(f"release_notes: no CHANGELOG.md section for {version}")
    return match.group(1).strip()


def main() -> None:
    version = sys.argv[1]
    body = section(Path("CHANGELOG.md").read_text(), version)
    checksum = Path(f"dist/create_villager_customers-{version}.jar.sha256").read_text().split()[0]
    print(f"# Create Fly: Villager Customers {version}\n")
    print(body)
    print(f"\n## Files\n\n`create_villager_customers-{version}.jar`, SHA-256 `{checksum}`.")
    print("\nRequires Minecraft 26.2, Fabric Loader, Fabric API and Create Fly. MIT.")


if __name__ == "__main__":
    main()
