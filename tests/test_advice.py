from datetime import date, time, timedelta

from car_charge_planner.away_input import make_away
from car_charge_planner.planner.advice import grid_advice
from car_charge_planner.planner.model import AwayPeriod, elapsed_hours
from car_charge_planner.planner.projection import plan

from conftest import TZ, at, forecast_from

FRI = date(2026, 7, 3)


def advise(cfg, days, soc, now=None, away=None):
    fc = forecast_from(days)
    now = now or at(days[0][0], 7)
    plans = plan(fc, cfg, soc, now, away)
    return grid_advice(plans, fc, cfg, away, now), plans


def dull(n, start=FRI):
    return [(start + timedelta(days=i), 120) for i in range(n)]


def test_no_advice_when_the_sun_keeps_the_car_above_minimum(cfg):
    advice, _ = advise(cfg, [(FRI, 900), (FRI + timedelta(days=1), 900)], soc=60)
    assert advice is None


def test_no_advice_on_a_dull_day_with_a_comfortable_battery(cfg):
    advice, _ = advise(cfg, dull(1), soc=70)
    assert advice is None


def test_dip_before_the_next_sunny_day_triggers_advice(cfg):
    days = dull(2) + [(FRI + timedelta(days=2), 900)]
    advice, plans = advise(cfg, days, soc=55)  # ends today ~39%, tomorrow ~17%, then the sun comes
    assert advice is not None
    assert advice.day == FRI + timedelta(days=1)
    assert plans[0].soc_end_of_day_pct > cfg.car.minimum_pct
    assert advice.deficit_pct > 0


def test_advice_energy_matches_the_deficit(cfg):
    advice, _ = advise(cfg, dull(3), soc=35)
    expected = advice.deficit_pct / 100 * cfg.car.usable_battery_kwh / cfg.charger.efficiency
    assert abs(advice.energy_kwh - expected) < 1e-9
    w = advice.window
    assert abs(w.solar_kwh + w.import_kwh - advice.energy_kwh) < 1e-6


def test_true_shortfall_is_kept_when_driving_exceeds_the_battery(cfg):
    advice, _ = advise(cfg, dull(1), soc=5, now=at(FRI, 0))
    assert advice.soc_end_pct < 0 and advice.deficit_pct > cfg.car.minimum_pct


def test_no_solar_means_lowest_power_and_earliest_start(cfg):
    night_only = [(FRI, 0), (FRI + timedelta(days=1), 0)]
    advice, _ = advise(cfg, night_only, soc=35, now=at(FRI, 0))
    w = advice.window
    assert w.amps == cfg.charger.min_amps
    assert w.import_kwh > 0 and w.solar_kwh == 0
    assert w.start == at(advice.window.start.date(), 0) or w.start.hour == 0


def test_placement_prefers_the_hours_with_leftover_sun(cfg):
    # peak surplus ~1.2 kW is below the 6 A minimum, so no solar window, but it still offsets grid draw
    advice, _ = advise(cfg, [(FRI, 650), (FRI + timedelta(days=1), 650)], soc=22, now=at(FRI, 0))
    w = advice.window
    assert advice is not None and w.solar_kwh > 0
    assert 8 <= w.start.hour <= 14


def test_window_energy_equals_power_times_duration(cfg):
    advice, _ = advise(cfg, dull(3), soc=35)
    w = advice.window
    hours = (w.end - w.start) / timedelta(hours=1)
    assert abs(w.power_kw * hours - advice.energy_kwh) < w.power_kw * 5 / 60 + 1e-6  # end rounds up to 5 min


def test_window_stays_clear_of_planned_solar_windows(cfg):
    # sunny day that cannot fully fix a very low battery
    advice, plans = advise(cfg, [(FRI, 900), (FRI + timedelta(days=1), 120)], soc=8, now=at(FRI, 0))
    assert advice is not None and plans[0].windows
    for day in plans:
        for solar in day.windows:
            w = advice.window
            assert w.end <= solar.start or w.start >= solar.end


def test_window_never_overlaps_away_periods(cfg):
    away = [make_away(FRI, False, time(0), time(20), TZ)]
    advice, _ = advise(cfg, dull(2), soc=30, now=at(FRI, 0), away=away)
    w = advice.window
    assert all(w.end <= a.start or w.start >= a.end for a in away)


def test_falls_back_to_an_earlier_day_when_the_dip_day_has_no_time_at_home(cfg):
    sat = FRI + timedelta(days=1)
    away = [make_away(sat, True, time(), time(), TZ)]
    advice, _ = advise(cfg, dull(2), soc=20, now=at(FRI, 0), away=away)
    assert advice.day == FRI  # day 0 already dips with this battery
    advice2, _ = advise(cfg, dull(3), soc=45, now=at(FRI, 0), away=away)
    assert advice2.window is None or advice2.window.start.date() != sat


def test_no_window_when_every_day_is_away(cfg):
    away = [AwayPeriod(at(FRI, 0), at(date(2026, 7, 10), 0))]
    advice, _ = advise(cfg, dull(2), soc=20, now=at(FRI, 0), away=away)
    assert advice is not None and advice.window is None


def test_late_evening_does_not_count_a_whole_days_driving_again(cfg):
    advice, plans = advise(cfg, dull(1), soc=33, now=at(FRI, 22))
    assert advice is None  # 2 h of the day left: ~1.1% of battery, still above 30%
    assert plans[0].driving_pct < 2


def test_advice_on_dst_day_does_not_crash(cfg):
    day = date(2026, 3, 29)
    advice, _ = advise(cfg, [(day, 100), (day + timedelta(days=1), 100)], soc=32, now=at(day, 0))
    assert advice is None or advice.window is None or advice.window.end > advice.window.start


def test_advice_is_for_the_first_dip_only(cfg):
    advice, plans = advise(cfg, dull(4), soc=40)
    first = next(d for d in plans if d.soc_after_charging_pct - d.driving_pct < cfg.car.minimum_pct - 1)
    assert advice.day == first.date


def test_a_tiny_shortfall_is_not_worth_a_warning(cfg):
    # land the first day's end level about 0.5 points under the minimum
    from car_charge_planner.planner.projection import daily_driving_pct

    soc = cfg.car.minimum_pct - 0.5 + daily_driving_pct(cfg)
    advice, plans = advise(cfg, dull(1), soc=soc, now=at(FRI, 0))
    assert 0 < cfg.car.minimum_pct - plans[0].soc_end_of_day_pct < 1
    assert advice is None


def test_advice_on_the_25_hour_fall_back_day(cfg):
    day = date(2026, 10, 25)
    advice, plans = advise(cfg, [(day, 100), (day + timedelta(days=1), 100)], soc=12, now=at(day, 0))
    assert advice is not None and advice.window is not None
    w = advice.window
    assert w.end > w.start
    hours = elapsed_hours(w.start, w.end)
    assert abs(w.power_kw * hours - advice.energy_kwh) < w.power_kw * 5 / 60 + 1e-6
