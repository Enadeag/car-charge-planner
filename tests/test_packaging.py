"""The package builds into one installable wheel that carries what the app needs at run time (ticket 04).

The build uses the interpreter's own hatchling and Babel (`--no-build-isolation`): no network. A translation is added to
a copy of the tree, because there is none yet and the point is that the hook compiles whatever is there.
"""

import shutil
import subprocess
import sys
import zipfile
from pathlib import Path

import pytest

ROOT = Path(__file__).resolve().parent.parent
PO = """msgid ""
msgstr ""
"Content-Type: text/plain; charset=utf-8\\n"
"Language: nl\\n"

msgid "Close"
msgstr "Sluiten"
"""


@pytest.fixture(scope="module")
def wheel(tmp_path_factory):
    work = tmp_path_factory.mktemp("build")
    for name in ("pyproject.toml", "hatch_build.py", "README.md"):
        shutil.copy(ROOT / name, work / name)
    shutil.copytree(ROOT / "src", work / "src", ignore=shutil.ignore_patterns("__pycache__", "*.mo"))
    catalogue = work / "src" / "car_charge_planner" / "locale" / "nl" / "LC_MESSAGES"
    catalogue.mkdir(parents=True)
    (catalogue / "car-charge-planner.po").write_text(PO)
    out = work / "dist"
    done = subprocess.run(
        [sys.executable, "-m", "pip", "wheel", ".", "--no-deps", "--no-build-isolation", "-w", str(out), "-q"],
        cwd=work, capture_output=True, text=True, timeout=240,
    )
    assert done.returncode == 0, done.stderr[-1500:]
    built = list(out.glob("*.whl"))
    assert len(built) == 1
    return zipfile.ZipFile(built[0])


def test_the_wheel_has_one_package_and_nothing_stray(wheel):
    tops = {name.split("/")[0] for name in wheel.namelist()}
    assert tops == {"car_charge_planner", "car_charge_planner-0.1.0.dist-info"}


def test_the_compiled_translations_are_built_and_shipped(wheel):
    names = wheel.namelist()
    assert "car_charge_planner/locale/nl/LC_MESSAGES/car-charge-planner.mo" in names
    assert "car_charge_planner/locale/nl/LC_MESSAGES/car-charge-planner.po" in names
    assert "car_charge_planner/locale/car-charge-planner.pot" in names


def test_the_compiled_catalogue_really_translates(wheel, tmp_path):
    target = tmp_path / "locale" / "nl" / "LC_MESSAGES"
    target.mkdir(parents=True)
    (target / "car-charge-planner.mo").write_bytes(wheel.read("car_charge_planner/locale/nl/LC_MESSAGES/car-charge-planner.mo"))
    from car_charge_planner import i18n

    try:
        assert i18n.init(["nl"], tmp_path / "locale") == "nl" and i18n._("Close") == "Sluiten"
    finally:
        i18n.init(languages=[])


def test_the_icon_and_the_package_data_are_in_it(wheel):
    assert "car_charge_planner/assets/car-charge-planner.svg" in wheel.namelist()
    assert "car_charge_planner/py.typed" not in wheel.namelist()  # nothing promised that is not there


def test_no_test_or_scratch_file_is_shipped(wheel):
    assert not [n for n in wheel.namelist() if n.startswith(("tests/", ".scratch/", "docs/")) or "__pycache__" in n]


def test_the_command_and_the_metadata(wheel):
    entry = wheel.read("car_charge_planner-0.1.0.dist-info/entry_points.txt").decode()
    assert "car-charge-planner = car_charge_planner.__main__:main" in entry
    metadata = wheel.read("car_charge_planner-0.1.0.dist-info/METADATA").decode()
    metadata = metadata.replace("Requires-Dist: pyside6-essentials", "Requires-Dist: PySide6-Essentials")  # names are written in lower case
    for needle in ("Name: car-charge-planner", "Requires-Python: >=3.12", "License-Expression: MIT", "Requires-Dist: PySide6-Essentials",
                   "Requires-Dist: jeepney", "Requires-Dist: tzdata", "Classifier: Operating System :: POSIX :: Linux", "Project-URL: Source"):
        assert needle in metadata, needle
    assert "Requires-Dist: pytest" not in metadata.split("Provides-Extra")[0]  # a development tool is not a dependency


def test_the_package_imports_nothing_from_the_old_top_level_names():
    """`app`, `planner`, `forecast` and `storage` were top-level packages; they must not come back."""
    for old in ("app", "planner", "forecast", "storage"):
        assert not (ROOT / old).exists(), f"{old}/ is back at the top level"
        assert not list((ROOT / "src").glob(old)), f"src/{old} is back"
