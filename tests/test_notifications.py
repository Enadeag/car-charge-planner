from datetime import date, timedelta

import pytest

from car_charge_planner.autostart import ENTRY_NAME, desktop_entry, install, uninstall
from car_charge_planner.forecast.service import ForecastResult
from car_charge_planner.notifications import GRID, SOLAR, candidate_windows, due, message
from car_charge_planner.planner.advice import GridAdvice
from car_charge_planner.planner.model import DayPlan, Window
from car_charge_planner.storage.state import load_state, mark_notified
from conftest import TZ, FakeNotifier, TrayApp, at, forecast_from

DAY = date(2026, 7, 3)


def win(start_h, end_h, start_m=0, amps=7, day=DAY):
    return Window(at(day, start_h, start_m), at(day, end_h), amps, amps * 0.23, 7.5, 0.1)


def check(windows, now, already=(), age=timedelta(minutes=5), failed=False, lead=15):
    return due(windows, already, now, lead, age, failed)


def test_announces_inside_the_lead_time_only():
    w = [(SOLAR, win(10, 15))]
    assert check(w, at(DAY, 9, 44)) == []
    assert check(w, at(DAY, 9, 45)) == w
    assert check(w, at(DAY, 9, 59)) == w


def test_does_not_announce_a_window_that_has_started():
    w = [(SOLAR, win(10, 15))]
    assert check(w, at(DAY, 10, 0)) == []
    assert check(w, at(DAY, 11, 0)) == []


def test_live_plan_clips_a_running_window_to_now_so_it_is_not_announced():
    now = at(DAY, 11, 7)
    running = Window(now, at(DAY, 15), 7, 1.6, 6.0, 0.1)
    assert check([(SOLAR, running)], now) == []


def test_already_announced_window_is_skipped_even_if_start_shifted_a_little():
    announced = (win(10, 15),)
    nudged = [(SOLAR, win(10, 15, start_m=5))]
    assert check(nudged, at(DAY, 9, 55), announced) == []


def test_a_different_window_the_same_day_is_still_announced():
    announced = (win(10, 11),)
    later = [(SOLAR, win(16, 18))]
    assert check(later, at(DAY, 15, 50), announced) == later


def test_suppressed_when_forecast_refresh_failed_or_is_old():
    w = [(SOLAR, win(10, 15))]
    assert check(w, at(DAY, 9, 50), failed=True) == []
    assert check(w, at(DAY, 9, 50), age=timedelta(hours=7)) == []
    assert check(w, at(DAY, 9, 50), age=timedelta(hours=5)) == w


def test_lead_time_is_configurable():
    w = [(SOLAR, win(10, 15))]
    assert check(w, at(DAY, 9, 30), lead=30) == w
    assert check(w, at(DAY, 9, 30), lead=15) == []


def test_candidates_include_solar_and_grid_windows_in_time_order():
    solar = win(10, 15)
    grid = Window(at(DAY, 5), at(DAY, 6), 13, 3.0, 0.0, 3.0)
    days = [DayPlan(DAY, 40, (solar,), 55, 40)]
    found = candidate_windows(days, GridAdvice(DAY, 8, 22, 17, grid))
    assert [k for k, _ in found] == [GRID, SOLAR]
    assert candidate_windows(days, GridAdvice(DAY, 8, 22, 17, None)) == [(SOLAR, solar)]
    assert candidate_windows(days, None) == [(SOLAR, solar)]


def test_messages_say_what_to_set(cfg):
    title, body = message(SOLAR, win(10, 15), cfg, TZ)
    assert title == "Charge window 10:00-15:00"
    assert "7 A" in body and "in your charger's app" in body and "solar" in body
    title, body = message(GRID, win(0, 6, amps=13), cfg, TZ)
    assert title == "Grid charge window 00:00-06:00"
    assert "13 A" in body and "30%" in body


def test_notified_windows_persist_and_old_ones_are_pruned(tmp_path):
    f = tmp_path / "s.json"
    mark_notified(win(10, 15, day=date(2026, 7, 1)), at(date(2026, 7, 1), 9, 50), f)
    state = mark_notified(win(10, 15), at(DAY, 9, 50), f)
    assert state.notified == (win(10, 15),)
    assert load_state(f).notified == state.notified


def test_other_state_changes_keep_notified(tmp_path):
    from car_charge_planner.storage.state import set_battery

    f = tmp_path / "s.json"
    mark_notified(win(10, 15), at(DAY, 9, 50), f)
    set_battery(50, at(DAY, 9, 51), f)
    assert len(load_state(f).notified) == 1


@pytest.mark.linux
def test_the_autostart_entry_starts_the_app_hidden_and_suits_both_desktops():
    text = desktop_entry(["/p/.venv/bin/python", "-m", "car_charge_planner"])
    assert "Exec=/p/.venv/bin/python -m car_charge_planner --background\n" in text  # window mode must not pop up at login
    assert "X-KDE-autostart-after=panel" in text and "X-GNOME-Autostart-enabled=true" in text


@pytest.mark.linux
def test_install_and_uninstall_autostart(tmp_path):
    target = install(["/p/.venv/bin/car-charge-planner"], tmp_path / "autostart")
    assert target.name == ENTRY_NAME and target.exists()
    assert uninstall(tmp_path / "autostart") is True
    assert not target.exists()
    assert uninstall(tmp_path / "autostart") is False


def tray_at(cfg, tmp_path, monkeypatch, hour, minute, failed=False):
    """A tray app whose clock stands at hour:minute on the day of a forecast with one sunny day."""
    monkeypatch.setenv("XDG_STATE_HOME", str(tmp_path))
    app = TrayApp(cfg, tmp_path / "config.toml")
    app.result = ForecastResult(forecast_from([(DAY, 900)]), at(DAY, hour, 0), True, failed)
    app.notifier = FakeNotifier()

    class Frozen:
        @staticmethod
        def now(tz=None):
            return at(DAY, hour, minute)

    monkeypatch.setattr("car_charge_planner.controller.datetime", Frozen)
    return app, app.notifier.sent


def test_the_tray_notifies_once_ahead_of_the_window(cfg, tmp_path, monkeypatch, qapp):
    app, sent = tray_at(cfg, tmp_path, monkeypatch, 8, 50)
    app.rebuild()
    assert len(sent) == 1 and sent[0][0].startswith("Charge window 09:00")
    app.rebuild()
    assert len(sent) == 1


def test_the_tray_does_not_notify_again_after_a_restart(cfg, tmp_path, monkeypatch, qapp):
    app, sent = tray_at(cfg, tmp_path, monkeypatch, 8, 50)
    app.rebuild()
    again, sent_again = tray_at(cfg, tmp_path, monkeypatch, 8, 52)
    again.rebuild()
    assert len(sent) == 1 and sent_again == []


@pytest.mark.parametrize("minute, failed", [(30, False), (50, True)], ids=["outside the lead time", "refresh failed"])
def test_the_tray_is_silent(cfg, tmp_path, monkeypatch, qapp, minute, failed):
    app, sent = tray_at(cfg, tmp_path, monkeypatch, 8, minute, failed=failed)
    app.rebuild()
    assert sent == []
