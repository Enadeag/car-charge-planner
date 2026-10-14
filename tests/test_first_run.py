"""The first run: the wizard opens by itself, or is offered; Set up, Run set-up again; finishing shows the plan."""

import os

os.environ.setdefault("QT_QPA_PLATFORM", "offscreen")

from datetime import date, timedelta

import pytest
from PySide6.QtWidgets import QPushButton

from car_charge_planner.app_settings import DesktopSettings
from car_charge_planner.app_windows import AppWindows
from car_charge_planner.config.model import first_run_config
from car_charge_planner.controller import Controller
from car_charge_planner.engine import FileStore
from car_charge_planner.forecast.service import ForecastResult
from car_charge_planner.shells.tray import TrayShell
from car_charge_planner.shells.window import WindowShell
from car_charge_planner.storage.state import load_state
from conftest import FakeNotifier, at, engine_for, example_config, forecast_from, settle_controller

DAY = date(2026, 7, 3)


def make(cfg, tmp_path, monkeypatch, fetch=None):
    monkeypatch.setenv("XDG_STATE_HOME", str(tmp_path / "state"))
    steps = forecast_from([(DAY + timedelta(days=i), 900) for i in range(3)])
    result = ForecastResult(steps, at(DAY, 6), False, False, "open-meteo", "Open-Meteo", "Weather data: Open-Meteo.com (CC BY 4.0)")
    calls = []
    monkeypatch.setattr("car_charge_planner.forecast.service.ForecastService.get", lambda self, cfg, now, force=False: (calls.append(cfg), fetch or result)[1])
    notifier = FakeNotifier()
    controller = Controller(cfg, FileStore(tmp_path / "config.toml"), notifier)
    controller.calls = calls
    return controller, notifier


def make_windows(cfg, tmp_path, monkeypatch, shown=None):
    controller, notifier = make(cfg, tmp_path, monkeypatch)
    return AppWindows(controller, (lambda: shown.append(1)) if shown is not None else (lambda: None)), notifier


def test_a_first_start_opens_the_wizard_by_itself(tmp_path, monkeypatch, qapp):
    windows, notifier = make_windows(first_run_config(), tmp_path, monkeypatch)
    windows.offer_setup(background=False)
    assert windows.setup_wizard is not None and windows.setup_wizard.isVisible() and notifier.sent == []
    windows.setup_wizard.close()


def test_a_start_that_is_already_set_up_does_not_open_it(tmp_path, monkeypatch, qapp):
    windows, notifier = make_windows(example_config(), tmp_path, monkeypatch)
    windows.offer_setup(background=False)
    windows.offer_setup(background=True)
    assert windows.setup_wizard is None and notifier.sent == []


def test_clicking_the_offer_opens_the_wizard_and_a_later_click_shows_the_plan(tmp_path, monkeypatch, qapp):
    shown = []
    windows, _ = make_windows(first_run_config(), tmp_path, monkeypatch, shown)
    windows.notification_clicked()
    assert windows.setup_wizard is not None and windows.setup_wizard.isVisible() and shown == []
    windows.setup_wizard.close()
    done, _ = make_windows(example_config(), tmp_path, monkeypatch, shown)
    done.notification_clicked()
    assert shown == [1] and done.setup_wizard is None


@pytest.mark.parametrize("shell_class", [TrayShell, WindowShell])
def test_every_shell_has_a_set_up_entry_in_its_menu_even_when_set_up(shell_class, tmp_path, monkeypatch, qapp):
    controller, _ = make(example_config(), tmp_path, monkeypatch)
    shell = shell_class(controller)
    entry = next(a for a in shell.menu.actions() if a.text() == "Set up...")
    entry.trigger()
    assert shell.windows.setup_wizard is not None and shell.windows.setup_wizard.isVisible()
    shell.windows.setup_wizard.close()


def test_a_hidden_start_at_login_never_opens_it_and_offers_it_in_a_notification_once(tmp_path, monkeypatch, qapp):
    windows, notifier = make_windows(first_run_config(), tmp_path, monkeypatch)
    windows.offer_setup(background=True)
    assert windows.setup_wizard is None
    assert len(notifier.sent) == 1 and notifier.sent[0][0] == "Set-up needed" and "Click here" in notifier.sent[0][1]
    again, second = make_windows(first_run_config(), tmp_path, monkeypatch)  # the next login
    again.offer_setup(background=True)
    assert again.setup_wizard is None and second.sent == []
    assert "set-up-offered" in load_state().seen


def test_a_location_entered_in_settings_counts_as_set_up(tmp_path, monkeypatch, qapp):
    controller, notifier = make(first_run_config(), tmp_path, monkeypatch)
    shell = WindowShell(controller)
    controller.engine.apply_config(controller.engine.cfg.with_system(latitude=52.37, longitude=4.9))  # what saving Settings does
    settle_controller(controller)
    shell.windows.offer_setup(background=False)
    assert shell.windows.setup_wizard is None and controller.engine.vm.state != "needs-setup"


def test_the_popup_of_the_tray_shell_has_a_set_up_button_that_opens_the_wizard(tmp_path, monkeypatch, qapp):
    controller, _ = make(first_run_config(), tmp_path, monkeypatch)
    shell = TrayShell(controller)
    controller.engine.rebuild()
    shell.popup.show()
    button = next(b for b in shell.popup.findChildren(QPushButton) if b.text() == "Set up")
    assert button.isVisible()
    button.click()
    assert shell.windows.setup_wizard.isVisible() and not shell.popup.isVisible()
    shell.windows.setup_wizard.close()


def test_closing_the_wizard_unfinished_leaves_the_app_not_set_up_with_the_button_still_there(tmp_path, monkeypatch, qapp):
    controller, _ = make(first_run_config(), tmp_path, monkeypatch)
    shell = WindowShell(controller)
    shell.show_plan()
    shell.windows.open_setup()
    wizard = shell.windows.setup_wizard
    wizard.confirm_leave = lambda: True
    wizard.pages["welcome"].units.setCurrentIndex(1)
    wizard.next()
    wizard.reject()
    assert not controller.engine.cfg.is_set_up and not (tmp_path / "config.toml").exists()
    assert any(b.text() == "Set up" and b.isVisible() for b in shell.view.findChildren(QPushButton))
    shell.window.hide()


def test_settings_has_run_set_up_again_only_when_the_app_can_open_it(tmp_path, monkeypatch, qapp):
    from car_charge_planner.settings.window import SettingsWindow

    assert SettingsWindow(engine_for(example_config(), tmp_path / "c.toml"), DesktopSettings()).setup_button.isHidden()
    windows, _ = make_windows(example_config(), tmp_path, monkeypatch)
    windows.open_settings()
    assert not windows.settings_window.setup_button.isHidden()
    windows.settings_window.close()


def test_run_set_up_again_opens_the_wizard_filled_in_with_what_is_saved(tmp_path, monkeypatch, qapp):
    windows, _ = make_windows(example_config(), tmp_path, monkeypatch)
    windows.open_settings()
    settings = windows.settings_window
    settings.setup_button.click()
    wizard = windows.setup_wizard
    assert not settings.isVisible() and wizard.isVisible()
    assert wizard.state.config == windows.controller.engine.cfg
    assert wizard.pages["location"] is not None
    wizard.close()


def test_run_set_up_again_asks_before_throwing_away_unsaved_settings(tmp_path, monkeypatch, qapp):
    windows, _ = make_windows(example_config(), tmp_path, monkeypatch)
    windows.open_settings()
    settings = windows.settings_window
    settings.field_tabs[0].inputs["panels.kwp"].setValue(9.0)
    asked = []
    settings.confirm_discard = lambda: (asked.append(1), False)[1]
    settings.setup_button.click()
    assert asked == [1] and settings.isVisible() and windows.setup_wizard is None
    settings.confirm_discard = lambda: (asked.append(1), True)[1]
    settings.setup_button.click()
    assert asked == [1, 1] and not settings.isVisible() and windows.setup_wizard.isVisible()
    windows.setup_wizard.close()
