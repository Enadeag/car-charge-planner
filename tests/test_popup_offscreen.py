"""Builds the popup without a display and checks it renders the view model."""

import os

os.environ.setdefault("QT_QPA_PLATFORM", "offscreen")

from datetime import date

from PySide6.QtWidgets import QApplication

from app.tray import Popup, make_icon
from app.view_model import build
from planner.projection import plan
from storage.state import BatteryReading

from conftest import TZ, at, forecast_from


def test_popup_shows_view_model_text(cfg):
    app = QApplication.instance() or QApplication([])
    now = at(date(2026, 7, 3), 7)
    fc = forecast_from([(date(2026, 7, 3), 900), (date(2026, 7, 4), 150)])
    vm = build(plan(fc, cfg, 45, now), now, cfg, BatteryReading(45, now), None, False)
    popup = Popup(TZ)
    popup.update_view(vm)
    assert popup.headline.text() == vm.headline
    assert "Tomorrow: no good window" in popup.outlook.text()
    assert popup.width() >= 380


def test_icons_exist_for_every_state():
    app = QApplication.instance() or QApplication([])
    for state in ("active", "upcoming", "none", "no-forecast"):
        assert not make_icon(state).isNull()


def _popup_and_vm(cfg, state=None):
    from storage.state import State

    now = at(date(2026, 7, 3), 7)
    fc = forecast_from([(date(2026, 7, 3), 900), (date(2026, 7, 4), 900)])
    state = state or State()
    vm = build(plan(fc, cfg, state.battery.pct, now), now, cfg, state.battery, None, False)
    popup = Popup(TZ)
    popup.update_view(vm, state, now.date())
    return popup, vm


def test_set_battery_button_emits_the_entered_value(cfg):
    QApplication.instance() or QApplication([])
    popup, _ = _popup_and_vm(cfg)
    seen = []
    popup.battery_submitted.connect(seen.append)
    popup.battery_input.setValue(42)
    from PySide6.QtWidgets import QPushButton

    next(b for b in popup.findChildren(QPushButton) if b.text() == "Set battery").click()
    assert seen == [42.0]


def test_add_all_day_and_time_range_away(cfg):
    from PySide6.QtCore import QTime
    from PySide6.QtWidgets import QPushButton

    QApplication.instance() or QApplication([])
    popup, _ = _popup_and_vm(cfg)
    seen = []
    popup.away_added.connect(seen.append)
    add = next(b for b in popup.findChildren(QPushButton) if b.text() == "Add")
    popup.away_day.setCurrentIndex(1)  # tomorrow
    add.click()
    assert describe_first(seen) == "Sat 4 Jul, all day"
    popup.away_kind.setCurrentIndex(1)
    popup.away_from.setTime(QTime(10, 0))
    popup.away_to.setTime(QTime(16, 30))
    add.click()
    assert describe_first(seen[1:]) == "Sat 4 Jul, 10:00-16:30"


def describe_first(periods):
    from app.away_input import describe

    return describe(periods[0], TZ)


def test_invalid_range_shows_error_and_emits_nothing(cfg):
    from PySide6.QtCore import QTime
    from PySide6.QtWidgets import QPushButton

    QApplication.instance() or QApplication([])
    popup, _ = _popup_and_vm(cfg)
    seen = []
    popup.away_added.connect(seen.append)
    popup.away_kind.setCurrentIndex(1)
    popup.away_from.setTime(QTime(16, 0))
    popup.away_to.setTime(QTime(10, 0))
    next(b for b in popup.findChildren(QPushButton) if b.text() == "Add").click()
    assert seen == [] and "after the start" in popup.away_error.text()
    assert not popup.away_error.isHidden()


def test_existing_away_is_listed_and_removable(cfg):
    from datetime import time

    from PySide6.QtWidgets import QPushButton

    from app.away_input import make_away
    from storage.state import BatteryReading, State

    QApplication.instance() or QApplication([])
    period = make_away(date(2026, 7, 4), True, time(), time(), TZ)
    popup, _ = _popup_and_vm(cfg, State(BatteryReading(61, at(date(2026, 7, 3), 6)), (period,)))
    removed = []
    popup.away_removed.connect(removed.append)
    next(b for b in popup.findChildren(QPushButton) if b.text() == "Remove").click()
    assert removed == [period]
    assert popup.battery_input.value() == 61


def test_tray_app_round_trip_persists_and_replans(cfg, tmp_path, monkeypatch):
    from datetime import time, timedelta, timezone

    from PySide6.QtWidgets import QApplication as QA

    from app.away_input import make_away
    from app.tray import TrayApp
    from forecast.open_meteo import ForecastResult
    from storage.state import load_state

    monkeypatch.setenv("XDG_STATE_HOME", str(tmp_path))
    QA.instance() or QA([])
    app = TrayApp(cfg, tmp_path / "config.toml")
    day = date(2026, 7, 3)
    app.result = ForecastResult(forecast_from([(day, 900), (date(2026, 7, 4), 900)]), at(day, 6), True, False)

    class FrozenNow:
        @staticmethod
        def now(tz=None):
            return at(day, 7)

    monkeypatch.setattr("app.tray.datetime", FrozenNow)
    app._on_battery(33)
    assert load_state().battery.pct == 33
    assert app.vm.projection.startswith("Battery 33% ->")
    app._on_away_added(make_away(day, True, time(), time(), TZ))
    assert len(load_state().away) == 1
    assert app.vm.outlook[0] == "Today: no good window"
    app._on_away_removed(load_state().away[0])
    assert load_state().away == ()
    assert app.vm.outlook[0] != "Today: no good window"


def test_popup_has_close_buttons_and_stays_open_when_focus_moves(cfg):
    from PySide6.QtWidgets import QPushButton

    QApplication.instance() or QApplication([])
    popup, _ = _popup_and_vm(cfg)
    popup.show()
    for label in ("×", "Close"):
        popup.show()
        next(b for b in popup.findChildren(QPushButton) if b.text() == label).click()
        assert not popup.isVisible()
    popup.show()
    from PySide6.QtCore import QEvent

    popup.changeEvent(QEvent(QEvent.Type.ActivationChange))
    assert popup.isVisible()


def _tray_with_frozen_clock(cfg, tmp_path, monkeypatch, hour, minute, failed=False):
    from app.tray import TrayApp
    from forecast.open_meteo import ForecastResult

    monkeypatch.setenv("XDG_STATE_HOME", str(tmp_path))
    QApplication.instance() or QApplication([])
    day = date(2026, 7, 3)
    app = TrayApp(cfg, tmp_path / "config.toml")
    app.result = ForecastResult(forecast_from([(day, 900)]), at(day, hour, 0), True, failed)
    sent = []
    app.tray.showMessage = lambda title, body, icon, ms: sent.append((title, body))

    class Frozen:
        @staticmethod
        def now(tz=None):
            return at(day, hour, minute)

    monkeypatch.setattr("app.tray.datetime", Frozen)
    return app, sent


def test_tray_notifies_once_ahead_of_the_window_and_not_again(cfg, tmp_path, monkeypatch):
    app, sent = _tray_with_frozen_clock(cfg, tmp_path, monkeypatch, 8, 50)  # window starts 09:00
    app.rebuild()
    assert len(sent) == 1 and sent[0][0].startswith("Charge window 09:00")
    assert "ChargerBrand" in sent[0][1]
    app.rebuild()  # next tick
    assert len(sent) == 1


def test_tray_does_not_renotify_after_a_restart(cfg, tmp_path, monkeypatch):
    app, sent = _tray_with_frozen_clock(cfg, tmp_path, monkeypatch, 8, 50)
    app.rebuild()
    again, sent2 = _tray_with_frozen_clock(cfg, tmp_path, monkeypatch, 8, 52)
    again.rebuild()
    assert len(sent) == 1 and sent2 == []


def test_tray_is_silent_outside_the_lead_time(cfg, tmp_path, monkeypatch):
    app, sent = _tray_with_frozen_clock(cfg, tmp_path, monkeypatch, 8, 30)
    app.rebuild()
    assert sent == []


def test_tray_is_silent_when_the_forecast_refresh_failed(cfg, tmp_path, monkeypatch):
    app, sent = _tray_with_frozen_clock(cfg, tmp_path, monkeypatch, 8, 50, failed=True)
    app.rebuild()
    assert sent == []
