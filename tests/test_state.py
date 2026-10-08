from datetime import date, time, timedelta

import pytest

from car_charge_planner.away_input import describe, make_away
from car_charge_planner.planner.model import AwayPeriod, ForecastStep, elapsed_hours
from car_charge_planner.planner.projection import plan
from car_charge_planner.storage.state import State, add_away, load_state, remove_away, set_battery

from conftest import TZ, at, forecast_from

DAY = date(2026, 7, 3)
NOW = at(DAY, 7)


def test_battery_round_trips(tmp_path):
    f = tmp_path / "s.json"
    set_battery(61.5, NOW, f)
    reading = load_state(f).battery
    assert reading.pct == 61.5 and reading.entered_at == NOW and not reading.assumed


@pytest.mark.parametrize("bad", [-1, 100.5, 250])
def test_battery_out_of_range_is_rejected_and_not_saved(tmp_path, bad):
    f = tmp_path / "s.json"
    with pytest.raises(ValueError):
        set_battery(bad, NOW, f)
    assert not f.exists()


def test_away_periods_persist_sorted_and_deduplicated(tmp_path):
    f = tmp_path / "s.json"
    later = make_away(date(2026, 7, 5), True, time(), time(), TZ)
    sooner = make_away(date(2026, 7, 4), False, time(10), time(16), TZ)
    add_away(later, NOW, f)
    add_away(sooner, NOW, f)
    add_away(sooner, NOW, f)
    assert load_state(f).away == (sooner, later)


def test_setting_battery_keeps_away_and_vice_versa(tmp_path):
    f = tmp_path / "s.json"
    period = make_away(date(2026, 7, 4), True, time(), time(), TZ)
    add_away(period, NOW, f)
    set_battery(40, NOW, f)
    state = load_state(f)
    assert state.away == (period,) and state.battery.pct == 40


def test_remove_away(tmp_path):
    f = tmp_path / "s.json"
    period = make_away(date(2026, 7, 4), True, time(), time(), TZ)
    add_away(period, NOW, f)
    assert remove_away(period, f).away == ()
    assert load_state(f).away == ()


def test_old_away_periods_are_pruned_when_adding(tmp_path):
    f = tmp_path / "s.json"
    old = make_away(date(2026, 6, 20), True, time(), time(), TZ)
    add_away(old, at(date(2026, 6, 19), 7), f)
    new = make_away(date(2026, 7, 4), True, time(), time(), TZ)
    assert add_away(new, NOW, f).away == (new,)


def test_corrupt_or_missing_state_gives_defaults(tmp_path):
    assert load_state(tmp_path / "none.json") == State()
    bad = tmp_path / "bad.json"
    bad.write_text("{oops")
    assert load_state(bad) == State()


def test_partly_broken_state_keeps_what_is_valid(tmp_path):
    f = tmp_path / "s.json"
    f.write_text('{"battery_pct": 70, "battery_entered_at": %s, "away": [{"start": 1}, "junk"]}' % NOW.timestamp())
    state = load_state(f)
    assert state.battery.pct == 70 and state.away == ()


def test_all_day_away_spans_local_midnight_to_midnight():
    p = make_away(DAY, True, time(9), time(10), TZ)
    assert p.start == at(DAY, 0) and p.end == at(date(2026, 7, 4), 0)


def test_all_day_on_dst_day_is_23_hours():
    p = make_away(date(2026, 3, 29), True, time(), time(), TZ)
    assert elapsed_hours(p.start, p.end) == 23


def test_time_range_must_end_after_start():
    with pytest.raises(ValueError):
        make_away(DAY, False, time(16), time(10), TZ)
    with pytest.raises(ValueError):
        make_away(DAY, False, time(10), time(10), TZ)


def test_describe():
    assert describe(make_away(DAY, True, time(), time(), TZ), TZ) == "Fri 3 Jul, all day"
    assert describe(make_away(DAY, False, time(9), time(17, 30), TZ), TZ) == "Fri 3 Jul, 09:00-17:30"
    multi = AwayPeriod(at(DAY, 18), at(date(2026, 7, 5), 12))
    assert describe(multi, TZ) == "Fri 3 Jul 18:00 - Sun 5 Jul 12:00"


def test_manual_all_day_away_removes_that_days_window(cfg):
    fc = forecast_from([(DAY, 900), (date(2026, 7, 4), 900)])
    away = [make_away(DAY, True, time(), time(), TZ)]
    days = plan(fc, cfg, 40, NOW, away)
    assert days[0].windows == () and days[1].windows


def test_manual_away_range_leaves_the_rest_of_the_day(cfg):
    fc = forecast_from([(DAY, 900)])
    away = [make_away(DAY, False, time(0), time(13), TZ)]
    windows = plan(fc, cfg, 40, NOW, away)[0].windows
    assert windows and all(w.start >= at(DAY, 13) for w in windows)


def test_step_across_the_spring_forward_gap_is_one_hour():
    start = at(date(2026, 3, 29), 1)
    step = ForecastStep(start, (start.astimezone(__import__("datetime").timezone.utc) + timedelta(hours=1)).astimezone(TZ), 0)
    assert step.end.hour == 3 and step.hours == 1.0
