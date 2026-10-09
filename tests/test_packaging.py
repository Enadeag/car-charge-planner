"""The package builds into one installable wheel with what the app needs at run time, offline (`--no-build-isolation`)."""

import shutil
import subprocess
import sys
import tomllib
import zipfile
from pathlib import Path

import pytest

ROOT = Path(__file__).resolve().parent.parent
VERSION = tomllib.loads((ROOT / "pyproject.toml").read_text(encoding="utf-8"))["project"]["version"]
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
    (catalogue / "car-charge-planner.po").write_text(PO, encoding="utf-8")
    out = work / "dist"
    done = subprocess.run(
        [sys.executable, "-m", "pip", "wheel", ".", "--no-deps", "--no-build-isolation", "-w", str(out), "-q"],
        cwd=work, capture_output=True, text=True, timeout=240,
        encoding="utf-8",
    )
    assert done.returncode == 0, done.stderr[-1500:]
    built = list(out.glob("*.whl"))
    assert len(built) == 1
    return zipfile.ZipFile(built[0])


def test_the_wheel_holds_the_package_its_translations_and_icon_and_no_tests(wheel):
    names = wheel.namelist()
    assert {name.split("/")[0] for name in names} == {"car_charge_planner", f"car_charge_planner-{VERSION}.dist-info"}
    for shipped in (
        "car_charge_planner/locale/nl/LC_MESSAGES/car-charge-planner.mo",
        "car_charge_planner/locale/nl/LC_MESSAGES/car-charge-planner.po",
        "car_charge_planner/locale/car-charge-planner.pot",
        "car_charge_planner/assets/car-charge-planner.svg",
    ):
        assert shipped in names
    assert not [n for n in names if n.startswith(("tests/", ".scratch/", "docs/")) or "__pycache__" in n]


def test_the_compiled_catalogue_really_translates(wheel, tmp_path):
    target = tmp_path / "locale" / "nl" / "LC_MESSAGES"
    target.mkdir(parents=True)
    (target / "car-charge-planner.mo").write_bytes(wheel.read("car_charge_planner/locale/nl/LC_MESSAGES/car-charge-planner.mo"))
    from car_charge_planner import i18n

    try:
        assert i18n.init(["nl"], tmp_path / "locale") == "nl" and i18n._("Close") == "Sluiten"
    finally:
        i18n.init(languages=[])


def test_the_command_and_the_metadata(wheel):
    dist_info = f"car_charge_planner-{VERSION}.dist-info"
    assert "car-charge-planner = car_charge_planner.__main__:main" in wheel.read(f"{dist_info}/entry_points.txt").decode()
    metadata = wheel.read(f"{dist_info}/METADATA").decode()
    metadata = metadata.replace("Requires-Dist: pyside6-essentials", "Requires-Dist: PySide6-Essentials")
    for needle in ("Name: car-charge-planner", f"Version: {VERSION}", "Requires-Python: >=3.12", "License-Expression: MIT", "Requires-Dist: PySide6-Essentials",
                   "Requires-Dist: platformdirs", "Requires-Dist: tzlocal",
                   "Requires-Dist: jeepney; sys_platform == 'linux'", "Requires-Dist: tzdata", "Classifier: Operating System :: POSIX :: Linux", "Project-URL: Source"):
        assert needle in metadata, needle
    assert "Requires-Dist: pytest" not in metadata.split("Provides-Extra")[0]  # the test tools are not run-time requirements
