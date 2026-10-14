"""The day views behind the dashboard's tabs: each planned day with its kind, texts, windows, curve and battery."""

from datetime import date, time, timedelta

from car_charge_planner.day_view import AwayView, Span, build_days
from car_charge_planner.planner.advice import GridAdvice
from car_charge_planner.planner.model import AwayPeriod, AwayRule, DayPlan, Window
from car_charge_planner.planner.projection import plan
from conftest import at, forecast_from, hourly

WEDNESDAY = date(2026, 7, 1)  # the example config is away on Thursdays 06:00-19:00


def sunny_week(cfg, now, battery=50.0, away=()):
    steps = forecast_from([(WEDNESDAY + timedelta(days=n), 900) for n in range(3)])
    days = plan(steps, cfg.system, cfg.planning, battery, now, list(away))
    return steps, days


def test_each_day_is_a_tab_with_its_window_battery_and_curve(cfg):
    now = at(WEDNESDAY, 7)
    steps, days = sunny_week(cfg, now)
    views = build_days(days, steps, None, cfg, (), now)

    assert [(v.tab, v.title, v.kind) for v in views] == [("Today", "Today", "sun"), ("Thu", "Tomorrow", "away"), ("Fri", "Fri 3 Jul", "sun")]
    today = views[0]
    window = days[0].windows[0]
    start = window.start.hour + window.start.minute / 60
    end = window.end.hour + window.end.minute / 60
    assert today.charges[0] == Span(start, end, f"{window.start:%H:%M}-{window.end:%H:%M}")
    assert today.hero == today.charges[0].text
    assert (today.battery_before_pct, today.battery_after_pct) == (days[0].soc_start_pct, days[0].soc_after_charging_pct)
    assert (today.minimum_pct, today.target_pct) == (30, 80)
    assert today.now_h == 7.0 and views[1].now_h is None
    assert today.hours == 24.0
    assert today.solar == tuple((s.start.hour + 0.5, round(s.power_kw, 3)) for s in steps if s.start.date() == WEDNESDAY)


def test_the_away_day_shows_its_recurring_and_entered_away_times(cfg):
    now = at(WEDNESDAY, 7)
    trip = AwayPeriod(at(WEDNESDAY + timedelta(days=1), 20), at(WEDNESDAY + timedelta(days=2), 2))  # into Friday night
    steps, days = sunny_week(cfg, now, away=[trip])
    thursday, friday = build_days(days, steps, None, cfg, [trip], now)[1:]

    assert thursday.away == (AwayView(Span(6.0, 19.0, "06:00-19:00"), "routine"), AwayView(Span(20.0, 24.0, "20:00-24:00"), "manual"))
    assert thursday.hero == "06:00-19:00, 20:00-24:00"
    assert friday.away == (AwayView(Span(0.0, 2.0, "00:00-02:00"), "manual"),)


def test_the_grid_charge_marks_its_day_and_says_when(cfg):
    cfg = cfg.with_system(away_rules=())
    now = at(WEDNESDAY, 7)
    grid = Window(at(WEDNESDAY + timedelta(days=1), 1), at(WEDNESDAY + timedelta(days=1), 3), 10, 2.3, 0.0, 4.6)
    advice = GridAdvice(WEDNESDAY + timedelta(days=1), 22, 8, 4.6, grid)
    days = [DayPlan(WEDNESDAY, 40, (), 40, 30), DayPlan(WEDNESDAY + timedelta(days=1), 30, (), 30, 22)]
    views = build_days(days, [], advice, cfg, (), now)

    assert [v.kind for v in views] == ["none", "grid"]
    assert views[1].grid == Span(1.0, 3.0, "01:00-03:00") and views[1].hero == "01:00-03:00"
    assert views[1].facts == ("2.3 kW (10 A)", "about 4.6 kWh")
    assert views[0].grid is None


def test_a_day_with_the_clock_change_has_25_hours_and_its_curve_follows_real_time(cfg):
    day = date(2026, 10, 25)  # 03:00 summer time becomes 02:00 winter time
    steps = hourly(day, {3: 1.5})  # the fourth real hour: the second 02:00
    days = [DayPlan(day, 50, (), 50, 40)]
    view = build_days(days, steps, None, cfg.with_system(away_rules=()), (), at(day, 1))[0]
    assert view.hours == 25.0
    assert (3.5, 1.5) in view.solar and len(view.solar) == 25


def test_a_recurring_rule_on_another_weekday_does_not_show(cfg):
    cfg = cfg.with_system(away_rules=(AwayRule(weekday=0, start=time(8), end=time(17)),))
    now = at(WEDNESDAY, 7)
    steps, days = sunny_week(cfg, now)
    assert all(v.away == () for v in build_days(days, steps, None, cfg, (), now))
