import os
import subprocess
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

from app import autostart, identity, launcher, notifier

PROJECT = Path(__file__).resolve().parent.parent
PYTHON = Path("/p/.venv/bin/python")


def test_the_icon_is_a_valid_svg_with_the_bolt_on_a_disc():
    root = ET.parse(launcher.ICON_SOURCE).getroot()
    assert root.tag.endswith("svg") and root.get("viewBox") == "0 0 64 64"
    tags = {child.tag.split("}")[-1] for child in root}
    assert {"circle", "polygon"} <= tags


def test_the_entry_names_the_app_icon_command_and_folder():
    text = launcher.desktop_entry(PYTHON, Path("/p"))
    assert "Type=Application" in text and "Name=Car charge planner" in text
    assert "Exec=/p/.venv/bin/python -m app" in text and "Path=/p" in text
    assert f"Icon={identity.ICON_NAME}" in text and "Categories=Utility;" in text
    assert f"StartupWMClass={identity.DESKTOP_ENTRY}" in text and "Terminal=false" in text


def test_install_writes_the_entry_and_the_icon(tmp_path):
    entry, icon = launcher.install(PYTHON, Path("/p"), tmp_path)
    assert entry == tmp_path / "applications" / "car-charge-planner.desktop"
    assert icon == tmp_path / "icons" / "hicolor" / "scalable" / "apps" / "car-charge-planner.svg"
    assert "Exec=/p/.venv/bin/python -m app" in entry.read_text()
    assert icon.read_bytes() == launcher.ICON_SOURCE.read_bytes()


def test_installing_twice_changes_nothing(tmp_path):
    first = launcher.install(PYTHON, Path("/p"), tmp_path)
    before = [p.read_bytes() for p in first]
    second = launcher.install(PYTHON, Path("/p"), tmp_path)
    assert first == second and [p.read_bytes() for p in second] == before


def test_installing_again_with_another_location_updates_the_entry(tmp_path):
    launcher.install(PYTHON, Path("/old"), tmp_path)
    entry, _ = launcher.install(PYTHON, Path("/new"), tmp_path)
    assert "Path=/new" in entry.read_text() and "/old" not in entry.read_text()


def test_uninstall_removes_both_files_and_nothing_else(tmp_path):
    launcher.install(PYTHON, Path("/p"), tmp_path)
    other = tmp_path / "applications" / "someone-else.desktop"
    other.write_text("[Desktop Entry]\n")
    removed = launcher.uninstall(tmp_path)
    assert len(removed) == 2 and all(not p.exists() for p in removed)
    assert other.exists()


def test_uninstalling_nothing_is_fine(tmp_path):
    assert launcher.uninstall(tmp_path) == []


def test_the_installed_icon_is_what_the_notifier_then_uses(tmp_path, monkeypatch):
    monkeypatch.setenv("XDG_DATA_HOME", str(tmp_path))
    assert notifier.default_icon() == notifier.FALLBACK_ICON
    launcher.install(PYTHON, Path("/p"), tmp_path)
    assert notifier.default_icon() == identity.ICON_NAME


def test_every_place_that_names_the_app_agrees(tmp_path):
    assert notifier.DESKTOP_ENTRY == identity.DESKTOP_ENTRY and notifier.ICON_NAME == identity.ICON_NAME
    assert autostart.ENTRY_NAME == f"{identity.DESKTOP_ENTRY}.desktop"
    entry, _ = launcher.install(PYTHON, Path("/p"), tmp_path)
    assert entry.name == autostart.ENTRY_NAME  # the same desktop id in both places
    assert f"Icon={identity.ICON_NAME}" in autostart.desktop_entry(PYTHON, Path("/p"))
    assert f"Name={identity.APP_NAME}" in autostart.desktop_entry(PYTHON, Path("/p"))


def _run(command, data_home):
    env = {**os.environ, "XDG_DATA_HOME": str(data_home)}
    return subprocess.run([sys.executable, "-m", "app", command], cwd=PROJECT, env=env, capture_output=True, text=True, timeout=30)


def test_the_commands_work_end_to_end_without_touching_the_real_menu(tmp_path):
    done = _run("install-launcher", tmp_path)
    assert done.returncode == 0 and "Launcher entry written" in done.stdout
    entry = tmp_path / "applications" / "car-charge-planner.desktop"
    assert f"Exec={sys.executable} -m app" in entry.read_text() and f"Path={PROJECT}" in entry.read_text()
    gone = _run("uninstall-launcher", tmp_path)
    assert gone.returncode == 0 and "Removed" in gone.stdout and not entry.exists()
    again = _run("uninstall-launcher", tmp_path)
    assert "No launcher entry found" in again.stdout


def test_an_unknown_command_lists_the_new_ones(tmp_path):
    done = _run("frobnicate", tmp_path)
    assert done.returncode != 0 and "install-launcher" in done.stderr and "uninstall-launcher" in done.stderr


def test_is_installed_follows_the_entry_file(tmp_path):
    assert not launcher.is_installed(tmp_path)
    launcher.install(PYTHON, Path("/p"), tmp_path)
    assert launcher.is_installed(tmp_path)
    launcher.uninstall(tmp_path)
    assert not launcher.is_installed(tmp_path)
