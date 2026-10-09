import os
import subprocess
import sys
from pathlib import Path

import pytest

from car_charge_planner import autostart, identity, launcher, notifier

PROJECT = Path(__file__).resolve().parent.parent
COMMAND = ["/home/me/.local/bin/car-charge-planner"]  # what pipx puts on the path
MODULE = ["/p/.venv/bin/python", "-m", "car_charge_planner"]  # when there is no installed command


@pytest.mark.linux
def test_the_entry_names_the_app_icon_and_command_and_no_folder():
    text = launcher.desktop_entry(COMMAND)
    assert "Type=Application" in text and "Name=Car charge planner" in text
    assert "Exec=/home/me/.local/bin/car-charge-planner\n" in text
    assert "Path=" not in text  # it works from wherever the desktop starts it
    assert f"Icon={identity.ICON_NAME}" in text and "Categories=Utility;" in text
    assert f"StartupWMClass={identity.DESKTOP_ENTRY}" in text and "Terminal=false" in text


@pytest.mark.linux
def test_install_writes_the_entry_and_the_icon(tmp_path):
    entry, icon = launcher.install(COMMAND, tmp_path)
    assert entry == tmp_path / "applications" / "car-charge-planner.desktop"
    assert icon == tmp_path / "icons" / "hicolor" / "scalable" / "apps" / "car-charge-planner.svg"
    assert "Exec=/home/me/.local/bin/car-charge-planner" in entry.read_text()
    assert icon.read_bytes() == launcher.ICON_SOURCE.read_bytes()


@pytest.mark.linux
def test_installing_twice_changes_nothing(tmp_path):
    first = launcher.install(COMMAND, tmp_path)
    before = [p.read_bytes() for p in first]
    second = launcher.install(COMMAND, tmp_path)
    assert first == second and [p.read_bytes() for p in second] == before


@pytest.mark.linux
def test_installing_again_with_another_command_updates_the_entry(tmp_path):
    launcher.install(MODULE, tmp_path)
    entry, _ = launcher.install(COMMAND, tmp_path)
    assert "Exec=/home/me/.local/bin/car-charge-planner" in entry.read_text() and "/p/.venv" not in entry.read_text()


@pytest.mark.linux
def test_uninstall_removes_both_files_and_nothing_else(tmp_path):
    launcher.install(COMMAND, tmp_path)
    other = tmp_path / "applications" / "someone-else.desktop"
    other.write_text("[Desktop Entry]\n")
    removed = launcher.uninstall(tmp_path)
    assert len(removed) == 2 and all(not p.exists() for p in removed)
    assert other.exists()


@pytest.mark.linux
def test_uninstalling_nothing_is_fine(tmp_path):
    assert launcher.uninstall(tmp_path) == []


@pytest.mark.linux
def test_the_installed_icon_is_what_the_notifier_then_uses(tmp_path, monkeypatch):
    monkeypatch.setenv("XDG_DATA_HOME", str(tmp_path))
    assert notifier.default_icon() == notifier.FALLBACK_ICON
    launcher.install(COMMAND, tmp_path)
    assert notifier.default_icon() == identity.ICON_NAME


@pytest.mark.linux
def test_every_place_that_names_the_app_agrees(tmp_path):
    assert notifier.DESKTOP_ENTRY == identity.DESKTOP_ENTRY and notifier.ICON_NAME == identity.ICON_NAME
    assert autostart.ENTRY_NAME == f"{identity.DESKTOP_ENTRY}.desktop"
    entry, _ = launcher.install(COMMAND, tmp_path)
    assert entry.name == autostart.ENTRY_NAME  # the same desktop id in both places
    assert f"Icon={identity.ICON_NAME}" in autostart.desktop_entry(COMMAND)
    assert f"Name={identity.APP_NAME}" in autostart.desktop_entry(COMMAND)


def _run(command, data_home):
    env = {**os.environ, "XDG_DATA_HOME": str(data_home)}
    return subprocess.run([sys.executable, "-m", "car_charge_planner", command], cwd=PROJECT, env=env, capture_output=True, text=True, timeout=30)


@pytest.mark.linux
def test_the_commands_work_end_to_end_without_touching_the_real_menu(tmp_path):
    done = _run("install-launcher", tmp_path)
    assert done.returncode == 0 and "Launcher entry written" in done.stdout
    entry = tmp_path / "applications" / "car-charge-planner.desktop"
    text = entry.read_text()
    assert "Path=" not in text and "-m app\n" not in text
    exec_line = next(line for line in text.splitlines() if line.startswith("Exec="))
    assert exec_line.endswith("car-charge-planner") or exec_line.endswith("-m car_charge_planner")  # this installation's own command
    gone = _run("uninstall-launcher", tmp_path)
    assert gone.returncode == 0 and "Removed" in gone.stdout and not entry.exists()
    again = _run("uninstall-launcher", tmp_path)
    assert "No launcher entry found" in again.stdout


def test_an_unknown_command_lists_the_new_ones(tmp_path):
    done = _run("frobnicate", tmp_path)
    assert done.returncode != 0 and "install-launcher" in done.stderr and "uninstall-launcher" in done.stderr and "test-notification" in done.stderr


@pytest.mark.linux
def test_is_installed_follows_the_entry_file(tmp_path):
    assert not launcher.is_installed(tmp_path)
    launcher.install(COMMAND, tmp_path)
    assert launcher.is_installed(tmp_path)
    launcher.uninstall(tmp_path)
    assert not launcher.is_installed(tmp_path)


def test_the_launch_command_is_the_installed_command_next_to_the_interpreter(tmp_path, monkeypatch):
    bin_dir = tmp_path / "bin"
    bin_dir.mkdir()
    (bin_dir / "car-charge-planner").write_text("#!/bin/sh\n")
    (bin_dir / "car-charge-planner").chmod(0o755)
    monkeypatch.setattr(sys, "executable", str(bin_dir / "python"))
    assert identity.launch_command() == [str(bin_dir / "car-charge-planner")]


def test_without_an_installed_command_it_is_this_interpreter_running_the_package(tmp_path, monkeypatch):
    monkeypatch.setattr(sys, "executable", str(tmp_path / "python"))
    assert identity.launch_command() == [str(tmp_path / "python"), "-m", "car_charge_planner"]


def test_a_command_from_another_environment_is_not_taken(tmp_path, monkeypatch):
    """A `car-charge-planner` somewhere else on the PATH may be another version: only the one beside this interpreter counts."""
    elsewhere = tmp_path / "elsewhere"
    elsewhere.mkdir()
    (elsewhere / "car-charge-planner").write_text("#!/bin/sh\n")
    (elsewhere / "car-charge-planner").chmod(0o755)
    monkeypatch.setenv("PATH", f"{elsewhere}{os.pathsep}{os.environ['PATH']}")
    monkeypatch.setattr(sys, "executable", str(tmp_path / "python"))
    assert identity.launch_command()[-2:] == ["-m", "car_charge_planner"]


@pytest.mark.linux
def test_a_path_with_a_space_is_quoted_in_the_entry():
    assert identity.exec_line(["/home/me/my apps/car-charge-planner"]) == '"/home/me/my apps/car-charge-planner"'
    assert identity.exec_line(["/p/python", "-m", "car_charge_planner"], "--background") == "/p/python -m car_charge_planner --background"
    assert identity.exec_line(['/a"b/x']) == '"/a\\"b/x"' and identity.exec_line(["/100%/x"]) == "/100%/x"
    assert identity.exec_line(["/a b/100%"]) == '"/a b/100%%"'  # % is special in Exec
