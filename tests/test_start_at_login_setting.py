import os

os.environ.setdefault("QT_QPA_PLATFORM", "offscreen")

import pytest

from car_charge_planner.planner.model import load_config
from car_charge_planner.settings.window import SettingsWindow
from conftest import engine_for, example_config

COMMAND = ["/p/car-charge-planner"]


class FakeLogin:
    def __init__(self, enabled=False, fail=False):
        self.enabled, self.fail, self.commands = enabled, fail, []

    def is_enabled(self):
        return self.enabled

    def enable(self, command):
        if self.fail:
            raise PermissionError("access denied")
        self.enabled = True
        self.commands.append(command)
        return "somewhere"

    def disable(self):
        if self.fail:
            raise PermissionError("access denied")
        self.enabled = False
        return True


@pytest.fixture
def make(tmp_path, qapp, monkeypatch):
    monkeypatch.setattr("car_charge_planner.settings.window.launch_command", lambda: COMMAND)

    def make(login):
        window = SettingsWindow(engine_for(example_config(), tmp_path / "config.toml"), login=login)
        window.show()
        return window

    return make


def box(window):
    return window.interface_tab.inputs["interface.start_at_login"]


def test_the_box_shows_what_the_system_says(make):
    assert box(make(FakeLogin(enabled=True))).isChecked()
    assert not box(make(FakeLogin(enabled=False))).isChecked()


def test_ticking_the_box_and_saving_starts_the_app_at_login_hidden(make):
    login = FakeLogin()
    window = make(login)
    box(window).setChecked(True)
    assert not login.enabled  # nothing happens until Save
    assert window.save()
    assert login.enabled and login.commands == [COMMAND]
    assert box(window).isChecked() and not window.is_dirty()


def test_unticking_the_box_and_saving_stops_it(make):
    login = FakeLogin(enabled=True)
    window = make(login)
    box(window).setChecked(False)
    assert window.save()
    assert not login.enabled


def test_the_box_is_not_saved_in_the_config_file(make, tmp_path):
    window = make(FakeLogin())
    box(window).setChecked(True)
    window.save()
    assert load_config(tmp_path / "config.toml") == window.stored


def test_changing_only_the_box_counts_as_an_unsaved_change(make):
    window = make(FakeLogin())
    assert not window.is_dirty()
    box(window).setChecked(True)
    assert window.is_dirty()


def test_a_failure_shows_an_error_and_the_box_goes_back(make, tmp_path):
    login = FakeLogin(fail=True)
    window = make(login)
    box(window).setChecked(True)
    assert not window.save()
    assert "access denied" in window.errors.text() and not window.errors.isHidden()
    assert not box(window).isChecked() and not login.enabled
    assert not (tmp_path / "config.toml").exists()


def test_saving_without_touching_the_box_does_not_touch_the_system(make):
    login = FakeLogin(enabled=True, fail=True)  # would fail if asked
    window = make(login)
    window.interface_tab.inputs["interface.units"].setCurrentIndex(1)
    assert window.save()
