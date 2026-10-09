from dataclasses import replace
from datetime import UTC, date, datetime, time, timedelta

import pytest

from car_charge_planner.planner.model import AwayPeriod, AwayRule, Config, ForecastStep, load_config
from car_charge_planner.planner.projection import energy_needed_kwh, plan
from car_charge_planner.planner.surplus import amps_for, usable_kw
from car_charge_planner.planner.windows import build_runs, group_runs_by_day, select_windows
from conftest import TZ, at, forecast_from, golden_config, hourly

SUMMER_FRI = date(2026, 7, 3)
SUMMER_THU = date(2026, 7, 2)


def day_plan(cfg, day, peak, soc, away=None, now_hour=0):
    fc = forecast_from([(day, peak)])
    return plan(fc, cfg, soc, at(day, now_hour), away)[0]


def test_clear_summer_day_gives_one_long_window_near_surplus_peak(cfg):
    p = day_plan(cfg, SUMMER_FRI, 900, soc=40)
    assert len(p.windows) == 1
    w = p.windows[0]
    assert 6 <= w.amps <= 32
    assert 2.0 < w.power_kw < 3.2  # peak usable is ~2.6 kW
    assert w.import_kwh <= cfg.planning.import_tolerance_kwh
    assert at(SUMMER_FRI, 8) <= w.start and w.end <= at(SUMMER_FRI, 17)
    assert w.solar_share > 0.95


# after the 0.6 kW baseload and the 0.85 margin: 1.6 kW leaves 0.85 kW (3 A, not chargeable), 2.6 kW leaves
# 1.7 kW (7 A) and 4.4 kW leaves 3.23 kW: 14 A (3.22 kW), while 15 A would import 0.22 kWh an hour
SUNNY = {8: 1.6, 9: 2.6, 10: 4.4, 11: 4.4, 12: 4.4, 13: 4.4, 14: 4.4, 15: 2.6}
QUIET = {hour: 1.6 for hour in range(9, 16)}


def test_golden_sunny_day():
    day = plan(hourly(SUMMER_FRI, SUNNY), golden_config(), 62, at(SUMMER_FRI, 0))[0]
    # need 18% of 70 kWh / 0.9 = 14 kWh: 12.88 kWh by 14:00, the rest in 1.12 / 3.22 h = 21 min, rounded up to 14:25
    assert [(w.start, w.end, w.amps, w.power_kw, w.import_kwh) for w in day.windows] == [
        (at(SUMMER_FRI, 10), at(SUMMER_FRI, 14, 25), 14, 3.22, 0.0)
    ]
    assert day.windows[0].solar_kwh == pytest.approx(3.22 * 53 / 12)
    # 14.22 kWh * 0.9 would reach 80.3%: capped at the 80% target
    assert (day.soc_after_charging_pct, day.driving_pct, day.soc_end_of_day_pct) == pytest.approx((80, 18, 62))


def test_golden_three_days_with_quiet_days_carrying_the_level_over():
    sat, sun = SUMMER_FRI + timedelta(days=1), SUMMER_FRI + timedelta(days=2)
    fc = hourly(SUMMER_FRI, QUIET) + hourly(sat, SUNNY) + hourly(sun, QUIET)
    days = plan(fc, golden_config(), 62, at(SUMMER_FRI, 0))
    assert [d.date for d in days] == [SUMMER_FRI, sat, sun]
    assert days[0].windows == () and days[2].windows == ()
    # Saturday needs 36% of 70 kWh / 0.9 = 28 kWh, more than the five hours at 3.22 kW give
    assert [(w.start, w.end, w.amps) for w in days[1].windows] == [(at(sat, 10), at(sat, 15), 14)]
    assert days[1].windows[0].solar_kwh == pytest.approx(16.1)
    # 16.1 kWh * 0.9 / 70 kWh = 20.7%
    assert [pct for d in days for pct in (d.soc_start_pct, d.soc_after_charging_pct, d.soc_end_of_day_pct)] == pytest.approx(
        [62, 62, 44, 44, 64.7, 46.7, 46.7, 46.7, 28.7]
    )


def test_golden_sunny_day_on_three_phases():
    cfg = golden_config()
    cfg = replace(cfg, charger=replace(cfg.charger, phases=3))
    # 8.1 kW leaves 6.375 kW: 9 A on three phases (6.21 kW); 10 A (6.9 kW) imports too much and the 1.7 kW of the
    # 2.6 kW hours is below the 6 A minimum of 4.14 kW
    fc = hourly(SUMMER_FRI, {9: 2.6, 10: 8.1, 11: 8.1, 12: 8.1, 13: 8.1, 14: 8.1, 15: 2.6})
    day = plan(fc, cfg, 62, at(SUMMER_FRI, 0))[0]
    # 14 kWh: 12.42 kWh by 12:00, the rest in 1.58 / 6.21 h = 15.3 min, rounded up to 12:20
    assert [(w.start, w.end, w.amps, w.power_kw, w.import_kwh) for w in day.windows] == [
        (at(SUMMER_FRI, 10), at(SUMMER_FRI, 12, 20), 9, 6.21, 0.0)
    ]
    assert day.windows[0].solar_kwh == pytest.approx(6.21 * 7 / 3)
    assert day.soc_after_charging_pct == pytest.approx(80)


def test_overcast_day_has_no_window(cfg):
    p = day_plan(cfg, SUMMER_FRI, 150, soc=40)
    assert p.windows == ()
    assert p.soc_after_charging_pct == 40


def test_surplus_below_minimum_power_is_not_chargeable(cfg):
    # peak surplus ~1.0 kW (< 1.38 kW of 6 A)
    peak = (1.0 / 0.85 + cfg.house.baseload_kw) / (cfg.panels[0].kwp * (1 - cfg.panels[0].system_losses)) * 1000
    p = day_plan(cfg, SUMMER_FRI, peak, soc=40)
    assert p.windows == ()


def test_amps_are_floored_and_capped(cfg):
    step = ForecastStep(at(SUMMER_FRI, 12), at(SUMMER_FRI, 13), 3.7)
    assert amps_for(usable_kw(step, cfg), cfg) == int(usable_kw(step, cfg) * 1000 / 230)
    assert amps_for(50, cfg) == 32


def test_thursday_recurring_away_removes_the_day(cfg):
    p = day_plan(cfg, SUMMER_THU, 900, soc=40)
    assert p.windows == ()


def test_non_thursday_is_unaffected_by_thursday_rule(cfg):
    assert day_plan(cfg, SUMMER_FRI, 900, soc=40).windows


def test_away_until_afternoon_leaves_only_later_window(cfg):
    away = [AwayPeriod(at(SUMMER_FRI, 0), at(SUMMER_FRI, 14))]
    p = day_plan(cfg, SUMMER_FRI, 900, soc=40, away=away)
    assert p.windows
    assert all(w.start >= at(SUMMER_FRI, 14) for w in p.windows)


def test_window_is_trimmed_to_energy_needed(cfg):
    p = day_plan(cfg, SUMMER_FRI, 900, soc=75)
    need = energy_needed_kwh(75, cfg)
    delivered = sum(w.solar_kwh for w in p.windows)
    assert need <= delivered < need + 1.5
    full = day_plan(cfg, SUMMER_FRI, 900, soc=40).windows[0]
    assert p.windows[0].end - p.windows[0].start < full.end - full.start


def test_short_need_still_gets_minimum_window_length(cfg):
    p = day_plan(cfg, SUMMER_FRI, 900, soc=79.8)
    w = p.windows[0]
    assert w.end - w.start >= timedelta(minutes=cfg.planning.min_window_minutes)


def test_no_window_when_battery_at_or_above_target(cfg):
    p = day_plan(cfg, SUMMER_FRI, 900, soc=85)
    assert p.windows == ()
    assert p.soc_after_charging_pct == 85


def test_higher_margin_never_lowers_power_below_lower_margin(cfg):
    cautious = day_plan(cfg, SUMMER_FRI, 900, soc=40).windows[0]
    bold = day_plan(replace(cfg, planning=replace(cfg.planning, margin=1.0)), SUMMER_FRI, 900, soc=40).windows[0]
    assert bold.power_kw > cautious.power_kw


def test_expected_import_stays_within_tolerance_on_every_peak(cfg):
    for peak in (300, 500, 700, 900, 1000):
        for w in day_plan(cfg, SUMMER_FRI, peak, soc=30).windows:
            assert w.import_kwh <= cfg.planning.import_tolerance_kwh + 1e-6


SPRING_FORWARD = date(2026, 3, 29)  # 23 hours: 02:00 winter time becomes 03:00 summer time
FALL_BACK = date(2026, 10, 25)  # 25 hours: 03:00 summer time becomes 02:00 winter time


def test_spring_forward_window_across_the_switch_counts_real_hours():
    # sun at night is artificial: it puts the window across the switch. Keys 1-3 are 01:00 winter time, 03:00, 04:00
    fc = hourly(SPRING_FORWARD, {1: 4.4, 2: 4.4, 3: 4.4})
    # need 10.35% of 70 kWh / 0.9 = 8.05 kWh: 2.5 real hours at 14 A (3.22 kW), 3.5 hours on the clock
    day = plan(fc, golden_config(), 69.65, at(SPRING_FORWARD, 0))[0]
    assert [(w.start, w.end, w.amps, w.import_kwh) for w in day.windows] == [
        (at(SPRING_FORWARD, 1), at(SPRING_FORWARD, 4, 30), 14, 0.0)
    ]
    assert day.windows[0].solar_kwh == pytest.approx(8.05)
    assert day.soc_after_charging_pct == pytest.approx(80)


def test_spring_forward_day_planned_from_midnight_takes_a_whole_days_driving():
    day = plan(hourly(SPRING_FORWARD, {}), golden_config(), 60, at(SPRING_FORWARD, 0))[0]
    assert (day.driving_pct, day.soc_end_of_day_pct) == pytest.approx((18, 42))


def test_fall_back_day_plans_the_afternoon_by_local_time_and_one_days_driving():
    # keys count real hours, so after the repeated 02:00 hour key 11 is 10:00 local time
    fc = hourly(FALL_BACK, {10: 2.6, 11: 4.4, 12: 4.4, 13: 4.4, 14: 4.4, 15: 4.4, 16: 2.6})
    day = plan(fc, golden_config(), 62, at(FALL_BACK, 0))[0]
    assert [(w.start, w.end, w.amps) for w in day.windows] == [(at(FALL_BACK, 10), at(FALL_BACK, 14, 25), 14)]
    assert day.windows[0].solar_kwh == pytest.approx(3.22 * 53 / 12)
    assert (day.soc_after_charging_pct, day.driving_pct, day.soc_end_of_day_pct) == pytest.approx((80, 18, 62))


def test_fall_back_window_across_the_repeated_hour_counts_real_hours():
    # keys 1-3 are 01:00 summer time, 02:00 summer time and 02:00 winter time
    fc = hourly(FALL_BACK, {1: 4.4, 2: 4.4, 3: 4.4})
    # need 8.05 kWh: 2.5 real hours at 3.22 kW, so from 01:00 summer time (23:00 UTC) to 02:30 winter time (01:30 UTC)
    day = plan(fc, golden_config(), 69.65, at(FALL_BACK, 0))[0]
    assert [(w.start.astimezone(UTC), w.end.astimezone(UTC), w.amps) for w in day.windows] == [
        (datetime(2026, 10, 24, 23, 0, tzinfo=UTC), datetime(2026, 10, 25, 1, 30, tzinfo=UTC), 14)
    ]
    assert day.windows[0].solar_kwh == pytest.approx(8.05)
    assert day.soc_after_charging_pct == pytest.approx(80)


def test_run_across_midnight_is_one_window_assigned_to_start_day(cfg):
    start = at(date(2026, 7, 3), 22)
    fc = [ForecastStep(start + timedelta(hours=h), start + timedelta(hours=h + 1), 3.3) for h in range(6)]
    runs = build_runs(fc, cfg, [], start)
    assert len(runs) == 1
    assert list(group_runs_by_day(runs, TZ)) == [date(2026, 7, 3)]
    w = select_windows(runs, 100, cfg)[0]
    assert w.start == start and w.end == start + timedelta(hours=6)


def test_now_clips_the_first_step(cfg):
    p = day_plan(cfg, SUMMER_FRI, 900, soc=40, now_hour=12)
    w = p.windows[0]
    assert w.start >= at(SUMMER_FRI, 12)


def test_projection_subtracts_driving_between_days(cfg):
    fc = forecast_from([(date(2026, 7, 3), 150), (date(2026, 7, 4), 150)])
    days = plan(fc, cfg, 60, at(date(2026, 7, 3), 0))
    drive = 600 / 7 * 18 / 100 / 70 * 100
    assert abs(days[0].soc_end_of_day_pct - (60 - drive)) < 1e-6
    assert abs(days[1].soc_start_pct - days[0].soc_end_of_day_pct) < 1e-9


def test_sunny_day_after_dull_day_charges_from_lower_start(cfg):
    fc = forecast_from([(date(2026, 7, 3), 150), (date(2026, 7, 4), 900)])
    days = plan(fc, cfg, 60, at(date(2026, 7, 3), 0))
    assert days[0].windows == ()
    assert days[1].windows and days[1].soc_start_pct < 60


def test_load_config_defaults_and_overrides(tmp_path):
    assert load_config(None) == Config()
    f = tmp_path / "c.toml"
    f.write_text('[panels]\nkwp = 5.2\n[away]\nrecurring = [{weekday = 5, start = "08:00", end = "20:30"}]\n')
    cfg = load_config(f)
    assert cfg.panels[0].kwp == 5.2 and cfg.panels[0].tilt_deg == 35
    assert cfg.away_rules == (AwayRule(5, time(8, 0), time(20, 30)),)


def test_unknown_config_key_is_rejected(tmp_path):
    f = tmp_path / "c.toml"
    f.write_text("[panels]\nkwhp = 5\n")
    try:
        load_config(f)
    except ValueError as err:
        assert "kwhp" in str(err)
    else:
        raise AssertionError("expected ValueError")


def test_plan_has_one_entry_per_forecast_day(cfg):
    fc = forecast_from([(date(2026, 7, 3), 900), (date(2026, 7, 4), 900)])
    days = plan(fc, cfg, 40, at(date(2026, 7, 3), 0))
    assert [d.date for d in days] == [date(2026, 7, 3), date(2026, 7, 4)]


def _announced(cfg):
    return plan(forecast_from([(SUMMER_FRI, 900)]), cfg, 40, at(SUMMER_FRI, 7))[0].windows[0]


def test_without_pinning_the_running_window_drifts(cfg):
    # documents why pinning exists: replanning at 12:00 gives a different power and end
    announced = _announced(cfg)
    later = plan(forecast_from([(SUMMER_FRI, 900)]), cfg, 40, at(SUMMER_FRI, 12))[0].windows[0]
    assert (later.amps, later.end) != (announced.amps, announced.end)


def test_pinned_window_keeps_its_power_and_end_while_it_runs(cfg):
    announced = _announced(cfg)
    for hour in (9, 10, 12, 14):
        w = plan(forecast_from([(SUMMER_FRI, 900)]), cfg, 40, at(SUMMER_FRI, hour), pinned=[announced])[0].windows
        assert len(w) == 1
        assert (w[0].amps, w[0].end, w[0].start) == (announced.amps, announced.end, announced.start)


def test_pinned_window_counts_only_its_remaining_energy(cfg):
    announced = _announced(cfg)
    day = plan(forecast_from([(SUMMER_FRI, 900)]), cfg, 40, at(SUMMER_FRI, 12), pinned=[announced])[0]
    w = day.windows[0]
    share = (announced.end - at(SUMMER_FRI, 12)) / (announced.end - announced.start)
    assert abs(w.solar_kwh - announced.solar_kwh * share) < 1e-9
    assert day.soc_after_charging_pct > 40


def test_no_second_window_is_planned_on_top_of_a_pinned_one(cfg):
    announced = _announced(cfg)
    day = plan(forecast_from([(SUMMER_FRI, 900)]), cfg, 10, at(SUMMER_FRI, 11), pinned=[announced])[0]
    spans = sorted((w.start, w.end) for w in day.windows)
    assert all(a_end <= b_start for (_, a_end), (b_start, _) in zip(spans, spans[1:], strict=False))


def test_pinned_window_that_is_over_or_not_started_is_ignored(cfg):
    announced = _announced(cfg)
    fc = forecast_from([(SUMMER_FRI, 900)])
    before = plan(fc, cfg, 40, at(SUMMER_FRI, 7), pinned=[announced])[0].windows
    assert before == plan(fc, cfg, 40, at(SUMMER_FRI, 7))[0].windows
    after = plan(fc, cfg, 40, at(SUMMER_FRI, 16), pinned=[announced])[0].windows
    assert all(w != announced for w in after)


from car_charge_planner.planner.model import ConfigError


@pytest.mark.parametrize(
    "toml, fragment",
    [
        ("[charger]\nvolts = 0", "volts"),
        ("[planning]\nmargin = 0", "margin"),
        ("[planning]\nmargin = 1.5", "margin"),
        ("[charger]\nmin_amps = 40", "min_amps"),
        ("[charger]\nefficiency = 0", "efficiency"),
        ("[panels]\nkwp = -3", "kwp"),
        ("[panels]\nsystem_losses = 1", "system_losses"),
        ("[panels]\nkwp = \"big\"", "wrong type"),
        ("[car]\nminimum_pct = 90", "minimum_pct"),
        ("[car]\nusable_battery_kwh = 0", "usable_battery_kwh"),
        ('timezone = "Mars/Base"', "timezone"),
        ("latitude = 120", "latitude"),
        ('[away]\nrecurring = [{weekday = 9, start = "06:00", end = "19:00"}]', "weekday"),
        ('[away]\nrecurring = [{weekday = 1, start = "22:00", end = "06:00"}]', "overnight"),
        ('[away]\nrecurring = [{weekday = 1, start = "6am", end = "19:00"}]', "6am"),
        ("[panels\nkwp = 1", "not valid TOML"),
        ("[panels]\nkwhp = 1", "kwhp"),
    ],
)
def test_bad_config_is_rejected_with_a_readable_message(tmp_path, toml, fragment):
    f = tmp_path / "c.toml"
    f.write_text(toml + "\n")
    with pytest.raises(ConfigError) as err:
        load_config(f)
    assert fragment in str(err.value)


def test_all_problems_are_reported_together(tmp_path):
    f = tmp_path / "c.toml"
    f.write_text("[charger]\nvolts = 0\n[planning]\nmargin = 0\n")
    with pytest.raises(ConfigError) as err:
        load_config(f)
    assert "volts" in str(err.value) and "margin" in str(err.value)


def test_built_in_defaults_are_valid():
    from car_charge_planner.planner.model import validate

    assert validate(Config()) == Config()
