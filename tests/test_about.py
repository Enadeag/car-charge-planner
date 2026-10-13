"""The About dialog, opened from the menus, names the version, the author and the licence."""

import os

os.environ.setdefault("QT_QPA_PLATFORM", "offscreen")

from importlib.metadata import version

import pytest

from car_charge_planner.controller import Controller
from car_charge_planner.shells.tray import TrayShell
from car_charge_planner.shells.window import WindowShell
from conftest import FakeNotifier


@pytest.fixture
def controller(cfg, tmp_path, monkeypatch, qapp):
    monkeypatch.setenv("XDG_STATE_HOME", str(tmp_path))
    monkeypatch.setattr("car_charge_planner.forecast.service.ForecastService.get", lambda *a, **k: None)
    return Controller(cfg, tmp_path / "config.toml", FakeNotifier())


def shown_text(controller):
    dialog = controller.about_dialog
    assert dialog is not None and dialog.isVisible()
    text = dialog.text.text()
    dialog.close()
    return text


def assert_about_content(text):
    assert version("car-charge-planner") in text
    assert "Car charge planner, originally by Kelvin Vroegop" in text
    assert "Copyright © 2026 Kelvin Vroegop" in text
    assert "GNU General Public License v3.0 or later" in text
    assert "https://www.gnu.org/licenses/gpl-3.0.html" in text
    assert "https://github.com/Enadeag/car-charge-planner" in text


def test_the_window_menu_opens_the_about_dialog(controller):
    shell = WindowShell(controller)
    shell.about_action.trigger()
    assert_about_content(shown_text(controller))


def test_the_tray_menu_opens_the_about_dialog(controller):
    shell = TrayShell(controller)
    next(a for a in shell.menu.actions() if a.text().startswith("About")).trigger()
    assert_about_content(shown_text(controller))


def test_opening_it_again_shows_the_same_dialog(controller):
    controller.open_about()
    first = controller.about_dialog
    controller.open_about()
    assert controller.about_dialog is first
    first.close()
