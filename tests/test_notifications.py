from datetime import date, timedelta
from pathlib import Path

from app.autostart import ENTRY_NAME, desktop_entry, install, uninstall
from app.notifications import GRID, SOLAR, candidate_windows, due, message
from planner.advice import GridAdvice
from planner.model import AwayPeriod, DayPlan, Window
from storage.state import load_state, mark_notified, save_state, State

from conftest import TZ, at

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
    assert "7 A" in body and "ChargerBrand" in body and "solar" in body
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
    from storage.state import set_battery

    f = tmp_path / "s.json"
    mark_notified(win(10, 15), at(DAY, 9, 50), f)
    set_battery(50, at(DAY, 9, 51), f)
    assert len(load_state(f).notified) == 1


def test_desktop_entry_has_exec_and_working_directory():
    text = desktop_entry(Path("/p/.venv/bin/python"), Path("/p"))
    assert "Exec=/p/.venv/bin/python -m app" in text
    assert "Path=/p" in text and "Type=Application" in text and "Terminal=false" in text


def test_install_and_uninstall_autostart(tmp_path):
    target = install(Path("/p/.venv/bin/python"), Path("/p"), tmp_path / "autostart")
    assert target.name == ENTRY_NAME and target.exists()
    assert uninstall(tmp_path / "autostart") is True
    assert not target.exists()
    assert uninstall(tmp_path / "autostart") is False
