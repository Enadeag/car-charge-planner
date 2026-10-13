"""The notices file that ships with a packaged build names everything it bundles, with each licence."""

import sys
from pathlib import Path

import pytest
from packaging.utils import canonicalize_name

sys.path.insert(0, str(Path(__file__).resolve().parent.parent / "tools"))
import third_party_notices  # noqa: E402


@pytest.fixture(scope="module")
def text():
    return third_party_notices.notices(third_party_notices.requirements(), "PYTHON LICENCE TEXT", None)


def test_every_run_time_requirement_is_listed_with_its_version_and_no_development_tool(text):
    names = {canonicalize_name(d.metadata["Name"]): d.version for d in third_party_notices.requirements()}
    assert {"pyside6-essentials", "shiboken6", "keyring", "platformdirs", "tzlocal", "tzdata", "jaraco-classes", "more-itertools"} <= set(names)
    assert not {"pytest", "babel", "ruff", "pyright", "hatchling"} & set(names)
    for dist in third_party_notices.requirements():
        assert f"{dist.metadata['Name']} {dist.version}" in text


def test_qt_is_under_the_lgpl_with_its_full_text_and_where_to_get_the_source(text):
    assert "LGPL-3.0-only" in text
    assert "https://code.qt.io/cgit/pyside/pyside-setup.git/" in text
    assert "https://download.qt.io/official_releases/qt/" in text
    assert "GNU LESSER GENERAL PUBLIC LICENSE" in text and "GNU GENERAL PUBLIC LICENSE" in text  # the LGPL builds on the GPL


def test_each_packages_own_licence_text_and_pythons_are_carried_along(text):
    keyring_licence = third_party_notices.licence_texts(third_party_notices.distribution("keyring"))
    assert keyring_licence and keyring_licence[0] in text  # the MIT licence asks for its text to go with the copy
    assert "PYTHON LICENCE TEXT" in text


def test_only_the_requirements_of_this_system_are_listed():
    names = {d.metadata["Name"].lower() for d in third_party_notices.requirements()}
    assert ("jeepney" in names) == (sys.platform == "linux")  # D-Bus: not in a Windows or macOS bundle
