import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent / "tools"))

from release_notes import release_notes  # noqa: E402

CHANGELOG = """# Changelog

## [Unreleased]

## [0.2.0] - 2026-10-14

Windows support.

### Added
- An installer.

## [0.1.0] - 2026-10-08

First version.

[Unreleased]: https://example.org/compare/v0.2.0...HEAD
[0.2.0]: https://example.org/compare/v0.1.0...v0.2.0
"""


def test_the_notes_are_the_versions_section_and_how_to_install_it():
    notes = release_notes(CHANGELOG, "0.2.0")
    assert notes is not None
    assert notes.startswith("Windows support.\n\n### Added\n- An installer.\n\n### Installing\n")
    assert "First version" not in notes
    assert "car-charge-planner-0.2.0-setup.exe" in notes and "car_charge_planner-0.2.0-py3-none-any.whl" in notes


def test_the_last_section_stops_before_the_link_list():
    notes = release_notes(CHANGELOG, "0.1.0")
    assert notes is not None
    assert notes.startswith("First version.\n\n### Installing\n")
    assert "example.org" not in notes


def test_a_version_without_a_section_or_with_an_empty_one_has_no_notes():
    assert release_notes(CHANGELOG, "0.3.0") is None
    assert release_notes(CHANGELOG, "Unreleased") is None


def test_the_real_changelog_has_notes_for_the_current_version():
    import tomllib

    root = Path(__file__).resolve().parent.parent
    version = tomllib.loads((root / "pyproject.toml").read_text(encoding="utf-8"))["project"]["version"]
    assert release_notes((root / "CHANGELOG.md").read_text(encoding="utf-8"), version) is not None
