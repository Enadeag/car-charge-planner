from datetime import date, timedelta

from app.config_file import DEFAULT_CONFIG_TOML, ensure_config
from app.view_model import build, footer_text, no_forecast
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
    assert vm.outlook == ("Today: no good window",)


def test_next_window_can_be_tomorrow(cfg):
    vm = view(cfg, at(DAY, 20), days=((DAY, 900), (date(2026, 7, 4), 900)))
    assert vm.headline.startswith("Next: Tomorrow")


def test_outlook_labels_days(cfg):
    vm = view(cfg, at(DAY, 7), days=[(DAY + timedelta(days=i), 900) for i in range(4)])
    assert [line.split(":")[0] for line in vm.outlook] == ["Today", "Tomorrow", "Sun 5 Jul", "Mon 6 Jul"]


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
    assert vm.projection.startswith("Battery 45% -> ")
    assert vm.projection.endswith("by 15:00")
    after = int(vm.projection.split("-> ")[1].split("%")[0])
    assert 45 < after <= 80


def test_projection_is_capped_at_target(cfg):
    vm = view(cfg, at(DAY, 7), battery=BatteryReading(78, at(DAY, 7)))
    assert int(vm.projection.split("-> ")[1].split("%")[0]) == 80


def test_no_projection_without_a_window(cfg):
    assert view(cfg, at(DAY, 7), days=((DAY, 150),)).projection == ""


def test_footer_reports_age_stale_and_attribution():
    assert "5 min" in footer_text(timedelta(minutes=5), False)
    assert "refresh failed" in footer_text(timedelta(hours=5), True)
    assert "5 h old" in footer_text(timedelta(hours=5), True)
    assert "Open-Meteo.com" in footer_text(None, False)


def test_no_forecast_view(cfg):
    vm = no_forecast("offline", BATTERY, at(DAY, 7))
    assert vm.state == "no-forecast" and vm.detail == "offline"


def test_default_config_file_matches_built_in_defaults(tmp_path):
    f = tmp_path / "config.toml"
    f.write_text(DEFAULT_CONFIG_TOML)
    assert load_config(f) == Config()


def test_ensure_config_creates_but_never_overwrites(tmp_path):
    f = tmp_path / "sub" / "config.toml"
    assert ensure_config(f) == f and f.read_text() == DEFAULT_CONFIG_TOML
    f.write_text("[panels]\nkwp = 6\n")
    ensure_config(f)
    assert f.read_text() == "[panels]\nkwp = 6\n"


def test_missing_state_gives_assumed_battery(tmp_path):
    reading = load_battery(tmp_path / "nope.json")
    assert reading.assumed and reading.pct == 50


def test_active_window_also_shows_projection(cfg):
    vm = view(cfg, at(DAY, 11))
    assert vm.state == "active" and vm.projection.startswith("Battery 45% -> ")


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
    assert vm.advice.startswith("Grid charge needed: battery would drop to")
    assert "kWh from the grid" in vm.advice and "minimum 30%" in vm.advice
    assert "Grid charge needed" in vm.tooltip


def test_comfortable_battery_on_dull_day_shows_no_advice(cfg):
    vm = _advice_view(cfg, [(DAY, 120)], 70, at(DAY, 7))
    assert vm.state == "none" and vm.advice == "" and vm.headline == "No good window"


def test_advice_block_also_shows_alongside_a_solar_window(cfg):
    days = [(DAY, 900), (date(2026, 7, 4), 120), (date(2026, 7, 5), 120), (date(2026, 7, 6), 120)]
    vm = _advice_view(cfg, days, 40, at(DAY, 7))
    assert vm.state in ("active", "upcoming")
    assert vm.advice.startswith("Grid charge needed")


def test_advice_text_when_there_is_no_room_at_home(cfg):
    from planner.advice import GridAdvice
    from app.view_model import advice_text

    text = advice_text(GridAdvice(DAY, 12.0, 18.0, 14.0, None), at(DAY, 7), cfg, TZ)
    assert "no free time at home" in text and "14 kWh" in text


def test_advice_day_label_keeps_weekday_capitalised(cfg):
    from planner.advice import GridAdvice
    from app.view_model import advice_text

    later = advice_text(GridAdvice(date(2026, 7, 8), 8.0, 22.0, 17.0, None), at(DAY, 7), cfg, TZ)
    assert "by the end of Wed 8 Jul" in later
    today = advice_text(GridAdvice(DAY, 8.0, 22.0, 17.0, None), at(DAY, 7), cfg, TZ)
    assert "by the end of today" in today
