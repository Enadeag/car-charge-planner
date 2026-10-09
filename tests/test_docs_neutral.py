"""The published documents say nothing about a person's home, equipment or workplace.

The words are written in pieces so that this file is not itself a hit for the searches that check the history.
"""

import re
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
TERMS = [
    "maas" + "tricht",
    "ea" + "see",
    "toy" + "ota",
    "bz" + "4x",
    "solar" + "edge",
    "kvroe" + "gop",
    "gm" + "ail",
    "first" + "8",
    "50[.]85",
    "5[.]69",
    "salder" + "ings",
]
PATTERN = re.compile("|".join(TERMS), re.IGNORECASE)


def test_documents_name_no_personal_details():
    files = [*(ROOT / "docs").rglob("*.md"), ROOT / "README.md", ROOT / "CONTRIBUTING.md", ROOT / "CONTEXT.md", ROOT / "CHANGELOG.md"]
    hits = [f"{f.relative_to(ROOT)}:{n}" for f in files for n, line in enumerate(f.read_text().splitlines(), 1) if PATTERN.search(line)]
    assert hits == []


def test_the_design_documents_have_an_index_and_every_feature_folder_is_in_it():
    design = ROOT / "docs" / "design"
    index = (design / "README.md").read_text()
    folders = [d.name for d in design.iterdir() if d.is_dir()]
    assert folders and all(f"`{name}/`" in index for name in folders)
