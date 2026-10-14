"""Print the release notes for a version: its CHANGELOG.md section plus how to install it.

    python tools/release_notes.py 0.2.0 > notes.md

Exits with code 1 when CHANGELOG.md has no section for the version, so a release cannot go out without notes.
"""

from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
REPOSITORY = "https://github.com/Enadeag/car-charge-planner"

INSTALLING = """### Installing

- **Windows:** download `car-charge-planner-{version}-setup.exe` and `SHA256SUMS`. Check the download in PowerShell with `(Get-FileHash .\\car-charge-planner-{version}-setup.exe -Algorithm SHA256).Hash` against the line in `SHA256SUMS`. The installer is not code-signed yet, so Windows shows "Windows protected your PC": choose **More info**, then **Run anyway**.
- **Linux:** `pipx install car_charge_planner-{version}-py3-none-any.whl`, see the README.

Full details: [CHANGELOG.md]({repository}/blob/v{version}/CHANGELOG.md).
"""


def section(changelog: str, version: str) -> str | None:
    """The text under `## [version]`, up to the next `## [` heading, or None when there is no such section."""
    match = re.search(rf"^## \[{re.escape(version)}\][^\n]*\n(.*?)(?=^## \[|^\[[^\]]+\]: |\Z)", changelog, re.MULTILINE | re.DOTALL)
    if match is None or not match.group(1).strip():
        return None
    return match.group(1).strip()


def release_notes(changelog: str, version: str) -> str | None:
    body = section(changelog, version)
    if body is None:
        return None
    return f"{body}\n\n{INSTALLING.format(version=version, repository=REPOSITORY)}"


def main(argv: list[str]) -> int:
    if len(argv) != 1:
        print("usage: python tools/release_notes.py <version>", file=sys.stderr)
        return 2
    notes = release_notes((ROOT / "CHANGELOG.md").read_text(encoding="utf-8"), argv[0])
    if notes is None:
        print(f"CHANGELOG.md has no section for {argv[0]}: add one before releasing", file=sys.stderr)
        return 1
    sys.stdout.write(notes)
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
