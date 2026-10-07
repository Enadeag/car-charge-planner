from dataclasses import replace
from datetime import date, timedelta

from app.config_file import ensure_config
from app.view_model import ForecastSource, build, footer_text, footer_tooltip, needs_setup, no_forecast
from planner.model import Config, load_config
from planner.projection import plan
from storage.state import BatteryReading, load_battery

from conftest import TZ, at, forecast_from

DAY = date(2026, 7, 3)  # a Friday
BATTERY = BatteryReading(45, at(DAY, 7))


def view(cfg, now, days=((DAY, 900),), battery=BATTERY, age=timedelta(minutes=5), stale=False):
    fc = forecast_from(list(days))
    return build(plan(fc, cfg, battery.pct, now), now, cfg, battery, age, stale)


def test_upcoming_window_headline_and_tooltip(cfg):
    vm = view(cfg, at(DAY, 7))
    assert vm.state == "upcoming"
    assert vm.headline.startswith("Next: Today 09:00-")
    assert "A" in vm.tooltip and "kW" in vm.detail


def test_active_window_says_charge_now(cfg):
    vm = view(cfg, at(DAY, 11))
    assert vm.state == "active"
    assert vm.headline == "Charge now"
    assert vm.detail.startswith("Until ")


def test_no_window_state(cfg):
    vm = view(cfg, at(DAY, 7), days=((DAY, 150),))
    assert vm.state == "none"
    assert vm.outlook == () and vm.quiet_days == ""  # the headline already says there is nothing


def test_next_window_can_be_tomorrow(cfg):
    vm = view(cfg, at(DAY, 20), days=((DAY, 900), (date(2026, 7, 4), 900)))
    assert vm.headline.startswith("Next: Tomorrow")


def test_outlook_lists_the_later_days_and_not_the_headline_window_again(cfg):
    vm = view(cfg, at(DAY, 7), days=[(DAY + timedelta(days=i), 900) for i in range(4)])
    assert vm.headline.startswith("Next: Today")
    assert [r.day for r in vm.outlook] == ["Tomorrow", "Sun 5 Jul", "Mon 6 Jul"]  # today is the headline
    first = vm.outlook[0]
    assert first.span == "10:00-15:00" or first.span[2] == ":"  # a clock span
    assert first.power.endswith(" A)") and first.energy.endswith(" kWh")
    assert vm.quiet_days == ""


def test_days_without_a_window_are_merged_into_one_quiet_line(cfg):
    days = [(DAY, 900), (DAY + timedelta(days=1), 120), (DAY + timedelta(days=2), 120), (DAY + timedelta(days=3), 900)]
    vm = view(cfg, at(DAY, 7), days=days)
    assert vm.quiet_days == "Tomorrow, Sun: no good window"
    assert [r.day for r in vm.outlook] == ["Mon 6 Jul"]


def test_a_second_window_the_same_day_gets_its_own_row_without_a_repeated_day(cfg):
    from planner.model import DayPlan, Window

    w1 = Window(at(DAY, 9), at(DAY, 11), 8, 1.8, 3.0, 0.0)
    w2 = Window(at(DAY, 13), at(DAY, 15), 7, 1.6, 3.0, 0.0)
    nxt = Window(at(date(2026, 7, 4), 10), at(date(2026, 7, 4), 14), 9, 2.1, 7.0, 0.0)
    days = [DayPlan(DAY, 40, (w1, w2), 60, 45), DayPlan(date(2026, 7, 4), 45, (nxt,), 60, 45)]
    vm = build(days, at(DAY, 7), cfg, BatteryReading(40, at(DAY, 7)), None, False)
    assert [r.day for r in vm.outlook] == ["Today", "Tomorrow"] and vm.outlook[0].span == "13:00-15:00"
    two = DayPlan(DAY, 40, (w1, w2, replace_window(w2)), 60, 45)
    vm2 = build([two], at(DAY, 7), cfg, BatteryReading(40, at(DAY, 7)), None, False)
    assert [r.day for r in vm2.outlook] == ["Today", ""]


def replace_window(w):
    from dataclasses import replace

    return replace(w, start=at(DAY, 16), end=at(DAY, 17))


def test_a_finished_window_is_not_listed(cfg):
    from planner.model import DayPlan, Window

    done = Window(at(DAY, 9), at(DAY, 11), 8, 1.8, 3.0, 0.0)
    later = Window(at(DAY, 14), at(DAY, 16), 7, 1.6, 3.0, 0.0)
    vm = build([DayPlan(DAY, 40, (done, later), 60, 45)], at(DAY, 12), cfg, BatteryReading(40, at(DAY, 7)), None, False)
    assert vm.outlook == () and vm.headline == "Next: Today 14:00-16:00"


def test_assumed_battery_is_flagged_and_prompts(cfg):
    vm = view(cfg, at(DAY, 7), battery=BatteryReading(50, None))
    assert "assumed" in vm.battery_line and vm.battery_stale


def test_fresh_battery_is_not_flagged(cfg):
    vm = view(cfg, at(DAY, 7))
    assert not vm.battery_stale and vm.battery_line == "Battery 45%"


def test_old_battery_entry_shows_age(cfg):
    vm = view(cfg, at(DAY, 7), battery=BatteryReading(45, at(date(2026, 6, 30), 7)))
    assert "3 days ago" in vm.battery_line and vm.battery_stale and "update" in vm.battery_line


def test_projection_shows_battery_before_and_after_window(cfg):
    vm = view(cfg, at(DAY, 7))
    assert vm.projection.startswith("Battery 45% \u2192 ")
    assert vm.projection.endswith("by 15:00")
    after = int(vm.projection.split("\u2192 ")[1].split("%")[0])
    assert 45 < after <= 80


def test_projection_is_capped_at_target(cfg):
    vm = view(cfg, at(DAY, 7), battery=BatteryReading(78, at(DAY, 7)))
    assert int(vm.projection.split("\u2192 ")[1].split("%")[0]) == 80


def test_no_projection_without_a_window(cfg):
    assert view(cfg, at(DAY, 7), days=((DAY, 150),)).projection == ""


OPEN_METEO = ForecastSource("Weather data: Open-Meteo.com (CC BY 4.0)", "Open-Meteo")
BACKUP = ForecastSource("Solar forecast: Forecast.Solar", "Forecast.Solar", from_fallback=True, primary_name="Open-Meteo")


def test_footer_reports_age_stale_and_the_providers_own_credit():
    assert footer_text(timedelta(minutes=5), False, OPEN_METEO) == "Forecast 5 min old \u00b7 Weather data: Open-Meteo.com (CC BY 4.0)"
    assert footer_text(timedelta(minutes=119), False, OPEN_METEO).startswith("Forecast 119 min old")
    assert footer_text(timedelta(hours=5), True, OPEN_METEO) == "Forecast 5 h old, refresh failed \u00b7 Weather data: Open-Meteo.com (CC BY 4.0)"
    assert footer_text(timedelta(minutes=5), True, OPEN_METEO).startswith("Forecast 5 min old, refresh failed")
    assert footer_text(None, False, OPEN_METEO) == "Weather data: Open-Meteo.com (CC BY 4.0)"


def test_the_credit_is_whatever_the_provider_says():
    assert footer_text(timedelta(minutes=5), False, ForecastSource("Solar forecast: Forecast.Solar")).endswith("Solar forecast: Forecast.Solar")


def test_a_fallback_forecast_says_where_it_is_from_and_credits_that_provider():
    assert footer_text(timedelta(minutes=5), False, BACKUP) == (
        "Forecast 5 min old \u00b7 from Forecast.Solar, primary unavailable \u00b7 Solar forecast: Forecast.Solar"
    )


def test_a_cool_down_says_the_provider_is_busy():
    busy = ForecastSource("Weather data: Open-Meteo.com (CC BY 4.0)", "Open-Meteo", cooling_down=True)
    assert footer_text(timedelta(hours=2), True, busy) == (
        "Forecast 2 h old, refresh failed \u00b7 provider busy, retrying later \u00b7 Weather data: Open-Meteo.com (CC BY 4.0)"
    )
    both = replace(BACKUP, cooling_down=True)
    assert footer_text(timedelta(minutes=5), False, both).count("\u00b7") == 3


def test_without_a_source_there_is_only_the_age_and_without_either_nothing():
    assert footer_text(timedelta(minutes=5), False) == "Forecast 5 min old"
    assert footer_text(None, False) == ""


def test_the_tooltip_carries_the_full_credit_and_the_reason(cfg):
    assert footer_tooltip(None) == "" and footer_tooltip(OPEN_METEO) == "Weather data: Open-Meteo.com (CC BY 4.0)"
    lines = footer_tooltip(BACKUP).split("\n")
    assert lines[0] == "Solar forecast: Forecast.Solar"
    assert lines[1] == "Forecast.Solar is used because Open-Meteo has no forecast that is still usable."
    unnamed = footer_tooltip(replace(BACKUP, primary_name="")).split("\n")[1]
    assert unnamed == "Forecast.Solar is used because the first provider has no forecast that is still usable."
    busy = footer_tooltip(replace(OPEN_METEO, cooling_down=True)).split("\n")
    assert busy[1] == "Open-Meteo asked not to be asked again yet. The app waits and tries again later."


def test_the_build_puts_the_footer_and_its_tooltip_in_the_view(cfg):
    fc = forecast_from([(DAY, 900)])
    now = at(DAY, 7)
    vm = build(plan(fc, cfg, 45, now), now, cfg, BATTERY, timedelta(minutes=5), False, None, BACKUP)
    assert "from Forecast.Solar, primary unavailable" in vm.footer and "Open-Meteo has no forecast" in vm.footer_tooltip


def test_before_there_is_a_forecast_the_footer_is_empty(cfg):
    assert needs_setup(BATTERY, at(DAY, 7)).footer == "" and no_forecast("offline", BATTERY, at(DAY, 7)).footer == ""


# ---- short horizons ------------------------------------------------------------------------------------------


def horizon_view(cfg, peaks, now=None):
    now = now or at(DAY, 7)
    fc = forecast_from([(DAY + timedelta(days=i), peak) for i, peak in enumerate(peaks)])
    return build(plan(fc, cfg, 60, now), now, cfg, BATTERY, timedelta(minutes=5), False, None, OPEN_METEO)


def test_a_one_day_forecast_has_no_outlook_and_says_one_day(cfg):
    vm = horizon_view(cfg, [900])
    assert vm.state == "upcoming" and vm.outlook == () and vm.quiet_days == ""
    dull = horizon_view(cfg, [120])
    assert dull.state == "none" and dull.detail == "Not enough solar surplus in the next 1 day."


def test_a_two_day_forecast_shows_the_second_day_only(cfg):
    vm = horizon_view(cfg, [900, 900])
    assert [row.day for row in vm.outlook] == ["Tomorrow"] and vm.quiet_days == ""
    assert horizon_view(cfg, [120, 120]).detail == "Not enough solar surplus in the next 2 days."


def test_a_quiet_second_day_is_named_and_a_quiet_only_day_is_not_repeated(cfg):
    assert horizon_view(cfg, [900, 120]).quiet_days == "Tomorrow: no good window"
    assert horizon_view(cfg, [120]).quiet_days == ""  # the headline already says it


def test_late_in_the_day_a_short_forecast_still_plans(cfg):
    vm = horizon_view(cfg, [900, 900], now=at(DAY, 23))
    assert vm.state == "upcoming" and vm.headline.startswith("Next: Tomorrow")


def test_no_forecast_view(cfg):
    vm = no_forecast("offline", BATTERY, at(DAY, 7))
    assert vm.state == "no-forecast" and vm.detail == "offline"


def test_first_config_file_matches_built_in_defaults(tmp_path):
    f = ensure_config(tmp_path / "config.toml")
    assert load_config(f) == Config()


def test_ensure_config_creates_but_never_overwrites(tmp_path):
    f = tmp_path / "sub" / "config.toml"
    assert ensure_config(f) == f and f.exists()
    f.write_text("[panels]\nkwp = 6\n")
    ensure_config(f)
    assert f.read_text() == "[panels]\nkwp = 6\n"


def test_missing_state_gives_assumed_battery(tmp_path):
    reading = load_battery(tmp_path / "nope.json")
    assert reading.assumed and reading.pct == 50


def test_active_window_also_shows_projection(cfg):
    vm = view(cfg, at(DAY, 11))
    assert vm.state == "active" and vm.projection.startswith("Battery 45% \u2192 ")


def _advice_view(cfg, days, soc, now):
    from planner.advice import grid_advice

    fc = forecast_from(list(days))
    plans = plan(fc, cfg, soc, now)
    battery = BatteryReading(soc, now)
    return build(plans, now, cfg, battery, None, False, grid_advice(plans, fc, cfg, None, now))


def test_dull_days_and_low_battery_give_grid_state(cfg):
    days = [(DAY + timedelta(days=i), 120) for i in range(3)]
    vm = _advice_view(cfg, days, 35, at(DAY, 7))
    assert vm.state == "grid" and vm.headline == "Grid charge needed"
    assert vm.advice.title.startswith("Grid charge needed \u00b7 ")  # when, as a fact
    assert "kW (" in vm.advice.detail and "about" in vm.advice.detail and "kWh" in vm.advice.detail
    assert vm.advice.reason.startswith("Battery would drop to") and "minimum 30%" in vm.advice.reason
    assert "Grid charge needed" in vm.tooltip


def test_comfortable_battery_on_dull_day_shows_no_advice(cfg):
    vm = _advice_view(cfg, [(DAY, 120)], 70, at(DAY, 7))
    assert vm.state == "none" and vm.advice is None and vm.headline == "No good window"


def test_advice_block_also_shows_alongside_a_solar_window(cfg):
    days = [(DAY, 900), (date(2026, 7, 4), 120), (date(2026, 7, 5), 120), (date(2026, 7, 6), 120)]
    vm = _advice_view(cfg, days, 40, at(DAY, 7))
    assert vm.state in ("active", "upcoming")
    assert vm.advice is not None and vm.advice.title.startswith("Grid charge needed")


def test_advice_when_there_is_no_room_at_home(cfg):
    from planner.advice import GridAdvice
    from app.view_model import advice_view

    view_ = advice_view(GridAdvice(DAY, 12.0, 18.0, 14.0, None), at(DAY, 7), cfg, TZ)
    assert view_.title == "Grid charge needed"
    assert "no free time at home" in view_.detail and "14 kWh" in view_.detail


def test_advice_day_label_keeps_weekday_capitalised(cfg):
    from planner.advice import GridAdvice
    from app.view_model import advice_view

    later = advice_view(GridAdvice(date(2026, 7, 8), 8.0, 22.0, 17.0, None), at(DAY, 7), cfg, TZ)
    assert later.reason.endswith("by the end of Wed 8 Jul.")
    today = advice_view(GridAdvice(DAY, 8.0, 22.0, 17.0, None), at(DAY, 7), cfg, TZ)
    assert today.reason.endswith("by the end of today.")
