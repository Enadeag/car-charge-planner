import os
import plistlib
import subprocess
import sys

import pytest

from car_charge_planner import paths
from car_charge_planner.platform import linux, macos, windows

INSTALLED = [r"C:\Users\me\AppData\Local\Programs\Python\Scripts\car-charge-planner.exe"]
MODULE = [r"C:\Program Files\Python 312\python.exe", "-m", "car_charge_planner"]


class FakeRegistry:
    def __init__(self):
        self.values: dict[str, str] = {}

    def get(self, name):
        return self.values.get(name)

    def set(self, name, value):
        self.values[name] = value

    def delete(self, name):
        return self.values.pop(name, None) is not None


def test_windows_writes_the_command_started_hidden_to_the_run_key():
    registry = FakeRegistry()
    windows.WindowsStartAtLogin(registry).enable(INSTALLED)
    assert registry.values == {"Car charge planner": f"{INSTALLED[0]} --background"}


def test_windows_quotes_a_command_with_spaces():
    registry = FakeRegistry()
    windows.WindowsStartAtLogin(registry).enable(MODULE)
    assert registry.values == {"Car charge planner": r'"C:\Program Files\Python 312\python.exe" -m car_charge_planner --background'}


def test_windows_start_at_login_follows_the_registry():
    registry = FakeRegistry()
    login = windows.WindowsStartAtLogin(registry)
    assert not login.is_enabled()
    login.enable(INSTALLED)
    assert login.is_enabled()
    assert login.disable() is True and registry.values == {} and not login.is_enabled()
    assert login.disable() is False


def test_windows_leaves_other_programs_run_values_alone():
    registry = FakeRegistry()
    registry.values["Other program"] = "other.exe"
    login = windows.WindowsStartAtLogin(registry)
    login.enable(INSTALLED)
    login.disable()
    assert registry.values == {"Other program": "other.exe"}


@pytest.mark.windows
def test_the_real_run_key_round_trips():
    key = windows.RunKey()
    name = "Car charge planner test"
    try:
        key.set(name, "x.exe --background")
        assert key.get(name) == "x.exe --background"
        assert key.delete(name) is True and key.get(name) is None and key.delete(name) is False
    finally:
        key.delete(name)


def test_macos_writes_a_launch_agent_that_runs_the_command_hidden_at_login(tmp_path):
    login = macos.MacStartAtLogin(tmp_path / "LaunchAgents")
    where = login.enable(["/Applications/Car charge planner.app/Contents/MacOS/car-charge-planner"])
    agent = plistlib.loads((tmp_path / "LaunchAgents" / "car-charge-planner.plist").read_bytes())
    assert where == str(tmp_path / "LaunchAgents" / "car-charge-planner.plist")
    assert agent == {
        "Label": "car-charge-planner",
        "ProgramArguments": ["/Applications/Car charge planner.app/Contents/MacOS/car-charge-planner", "--background"],
        "RunAtLoad": True,
    }


def test_macos_start_at_login_follows_the_plist(tmp_path):
    login = macos.MacStartAtLogin(tmp_path)
    assert not login.is_enabled()
    login.enable(["/p/python", "-m", "car_charge_planner"])
    assert login.is_enabled()
    assert login.disable() is True and not login.is_enabled() and login.disable() is False


def test_macos_looks_in_the_users_launch_agents_folder(tmp_path, monkeypatch):
    monkeypatch.setattr(paths, "launch_agents_dir", lambda: tmp_path / "Library" / "LaunchAgents")
    macos.MacStartAtLogin().enable(["/p/car-charge-planner"])
    assert (tmp_path / "Library" / "LaunchAgents" / "car-charge-planner.plist").exists()


def test_macos_has_no_menu_entry_to_install_the_bundle_is_it():
    menu = macos.MacMenuEntry()
    assert menu.own_entry is False
    assert menu.install(["/p/car-charge-planner"]) == [] and menu.uninstall() == [] and not menu.is_installed()


def test_linux_autostart_entry_is_found_in_the_users_autostart_folder(tmp_path):
    login = linux.LinuxStartAtLogin()
    assert not login.is_enabled()
    login.enable(["/p/car-charge-planner"])
    assert (paths.shared_config_dir() / "autostart" / "car-charge-planner.desktop").read_text(encoding="utf-8").count("Exec=/p/car-charge-planner --background\n") == 1
    assert login.is_enabled()


def fake_shortcut(made):
    def make(link, target, arguments, description):
        made.append((link.name, target, arguments))
        link.write_bytes(b"lnk")

    return make


def test_windows_menu_entry_is_a_start_menu_shortcut_to_the_command(tmp_path):
    made = []
    menu = windows.WindowsMenuEntry(tmp_path / "Programs", fake_shortcut(made))
    assert not menu.is_installed()
    menu.install(MODULE)
    assert made == [("Car charge planner.lnk", MODULE[0], "-m car_charge_planner")]  # no --background: the menu opens the window
    assert menu.is_installed()
    assert menu.uninstall() == [str(tmp_path / "Programs" / "Car charge planner.lnk")] and not menu.is_installed()
    assert menu.uninstall() == []


@pytest.mark.windows
def test_the_real_shortcut_points_at_the_command(tmp_path):
    menu = windows.WindowsMenuEntry(tmp_path)
    menu.install([sys.executable, "-m", "car_charge_planner"])
    link = tmp_path / "Car charge planner.lnk"
    read = "$s = (New-Object -ComObject WScript.Shell).CreateShortcut($env:LINK); $s.TargetPath; $s.Arguments"
    done = subprocess.run(["powershell.exe", "-NoProfile", "-Command", read], env={**os.environ, "LINK": str(link)}, capture_output=True, text=True, check=True)
    assert done.stdout.splitlines()[:2] == [sys.executable, "-m car_charge_planner"]
