"""More than one panel array: the config, its migration, the forecast as the sum of the arrays."""

import json
from dataclasses import replace
from datetime import UTC, date, datetime, timedelta

import pytest

from car_charge_planner.forecast.provider import PanelArray, SystemDescription, add_up, check_steps, each_array
from car_charge_planner.forecast.registry import ProviderRegistry
from car_charge_planner.forecast.service import ForecastService, forecast_key
from car_charge_planner.planner.config_io import dump_config, write_config
from car_charge_planner.planner.model import (
    TYPICAL_KEYS,
    ConfigError,
    Forecast,
    ForecastStep,
    Panels,
    Setup,
    base_key,
    load_config,
    migrate_1_to_2,
    panel_key,
    split_panel_key,
    typical_keys,
    validate,
    with_array,
    with_setting,
    without_array,
)
from conftest import TZ, at
from fake_provider import FakeProvider

DAY = date(2026, 7, 3)
EAST = Panels(kwp=3.0, tilt_deg=30, azimuth_deg=-90, system_losses=0.14, name="East roof")
WEST = Panels(kwp=2.5, tilt_deg=30, azimuth_deg=90, system_losses=0.10, name="West roof")


def two_arrays(cfg):
    return replace(cfg, panels=(EAST, WEST))


def test_the_file_has_a_panels_table_for_each_array_and_reads_back(tmp_path, cfg):
    cfg = two_arrays(cfg)
    text = dump_config(cfg)
    assert text.count("[[panels]]") == 2 and "[panels]\n" not in text
    assert '[[panels]]\nname = "East roof"\nkwp = 3.0\ntilt_deg = 30\nazimuth_deg = -90\nsystem_losses = 0.14\n' in text
    path = tmp_path / "config.toml"
    write_config(cfg, path)
    assert load_config(path) == cfg


def test_an_array_without_a_name_is_written_without_one(cfg):
    assert "name =" not in dump_config(cfg)


def test_an_old_file_with_one_panels_table_is_one_array(tmp_path):
    path = tmp_path / "config.toml"
    path.write_text('latitude = 52.37\nlongitude = 4.9\n[panels]\nkwp = 4.2\ntilt_deg = 20\n[setup]\ntypical = ["panels.azimuth_deg"]\n')
    cfg = load_config(path)
    assert cfg.panels == (Panels(kwp=4.2, tilt_deg=20),)
    assert cfg.setup.typical == ("panels.azimuth_deg",)
    write_config(cfg, path)
    assert "schema = 2" in path.read_text() and "[[panels]]" in path.read_text()
    assert load_config(path) == cfg


def test_the_migration_turns_the_table_into_a_list_of_one_and_leaves_the_rest():
    assert migrate_1_to_2({"panels": {"kwp": 4.0}, "car": {"km_per_day": 10}}) == {"panels": [{"kwp": 4.0}], "car": {"km_per_day": 10}}
    assert migrate_1_to_2({}) == {}
    assert migrate_1_to_2({"panels": [{"kwp": 4.0}]}) == {"panels": [{"kwp": 4.0}]}


def test_a_file_without_panels_has_the_one_default_array(tmp_path):
    path = tmp_path / "config.toml"
    path.write_text("schema = 2\n")
    assert load_config(path).panels == (Panels(),)


@pytest.mark.parametrize(
    "text, message",
    [
        ("schema = 2\n[panels]\nkwp = 4.0\n", r"\[\[panels\]\] must be one or more tables"),
        ("schema = 2\npanels = []\n", r"\[\[panels\]\] must be one or more tables"),
        ("schema = 2\n[[panels]]\nkwp = 4.0\n[[panels]]\nkwhp = 1\n", r"unknown keys in \[panels\] array 2: \['kwhp'\]"),
        ("schema = 2\n[[panels]]\nkwp = 4.0\n[[panels]]\nkwp = 0\n", r"\[panels\] array 2 kwp must be above 0"),
        ("schema = 2\n[[panels]]\nname = 5\n", "name must be a text"),
    ],
)
def test_a_file_with_arrays_that_make_no_sense_is_refused(tmp_path, text, message):
    path = tmp_path / "config.toml"
    path.write_text(text)
    with pytest.raises(ConfigError, match=message):
        load_config(path)


def test_one_array_keeps_the_messages_as_they_were(cfg):
    with pytest.raises(ConfigError, match=r"\[panels\] kwp must be above 0"):
        validate(replace(cfg, panels=(Panels(kwp=0),)))


def test_there_is_always_at_least_one_array(cfg):
    with pytest.raises(ConfigError, match="at least one panel array"):
        validate(replace(cfg, panels=()))


def test_the_total_size_is_the_sum_of_the_arrays(cfg):
    assert two_arrays(cfg).total_kwp == pytest.approx(5.5) and cfg.total_kwp == 4.8


def test_the_first_array_keeps_its_old_keys_and_the_others_are_numbered():
    assert [panel_key(0, "kwp"), panel_key(1, "kwp"), panel_key(9, "tilt_deg")] == ["panels.kwp", "panels.2.kwp", "panels.10.tilt_deg"]
    assert split_panel_key("panels.kwp") == (0, "kwp") and split_panel_key("panels.2.name") == (1, "name")
    for key in ("panels.1.kwp", "panels.02.kwp", "panels.2.kwpp", "panels.x.kwp", "car.km_per_day", "latitude"):
        assert split_panel_key(key) is None
    assert base_key("panels.3.tilt_deg") == "panels.tilt_deg" and base_key("car.km_per_day") == "car.km_per_day"


def test_each_array_has_its_own_typical_keys(cfg):
    keys = typical_keys(two_arrays(cfg))
    assert keys[:8] == (
        "panels.kwp", "panels.tilt_deg", "panels.azimuth_deg", "panels.system_losses",
        "panels.2.kwp", "panels.2.tilt_deg", "panels.2.azimuth_deg", "panels.2.system_losses",
    )
    assert keys[8:] == tuple(k for k in TYPICAL_KEYS if not k.startswith("panels."))
    assert typical_keys(cfg) == TYPICAL_KEYS


def test_a_typical_key_of_an_array_that_does_not_exist_is_refused(cfg):
    validate(replace(two_arrays(cfg), setup=Setup(("panels.2.kwp",))))
    with pytest.raises(ConfigError, match="do not exist"):
        validate(replace(cfg, setup=Setup(("panels.2.kwp",))))


def test_settings_are_set_by_key_on_the_right_array(cfg):
    changed = with_setting(two_arrays(cfg), "panels.2.tilt_deg", 15)
    assert changed.panels == (EAST, replace(WEST, tilt_deg=15))
    assert with_setting(changed, "panels.kwp", 3.3).panels[0].kwp == 3.3


def test_an_added_array_is_typical_until_the_owner_says_otherwise(cfg):
    added = with_array(replace(cfg, setup=Setup(("car.km_per_day",))))
    assert added.panels == (cfg.panels[0], Panels())
    assert added.setup.typical == ("panels.2.kwp", "panels.2.tilt_deg", "panels.2.azimuth_deg", "panels.2.system_losses", "car.km_per_day")
    validate(added)


def test_removing_an_array_moves_the_typical_flags_of_the_later_ones_up(cfg):
    three = replace(cfg, panels=(EAST, WEST, Panels()), setup=Setup(("panels.kwp", "panels.2.tilt_deg", "panels.3.kwp", "car.km_per_day")))
    gone = without_array(three, 1)
    assert gone.panels == (EAST, Panels()) and gone.setup.typical == ("panels.kwp", "panels.2.kwp", "car.km_per_day")
    validate(gone)
    with pytest.raises(ValueError):
        without_array(cfg, 0)


def hour(h, minute=0):
    return datetime(2026, 7, 3, h, minute, tzinfo=TZ)


def steps(*rows):
    return [ForecastStep(start, end, power) for start, end, power in rows]


def test_steps_that_line_up_are_added_step_by_step():
    a = steps((hour(8), hour(9), 1.0), (hour(9), hour(10), 2.0))
    b = steps((hour(8), hour(9), 0.5), (hour(9), hour(10), 0.25))
    assert add_up([a, b]) == steps((hour(8), hour(9), 1.5), (hour(9), hour(10), 2.25))


def test_steps_that_do_not_line_up_are_cut_and_no_energy_is_lost():
    a = steps((hour(8), hour(10), 1.0))
    b = steps((hour(8), hour(8, 30), 2.0), (hour(8, 30), hour(10), 0.5))
    total = add_up([a, b])
    assert total == steps((hour(8), hour(8, 30), 3.0), (hour(8, 30), hour(10), 1.5))
    assert sum(s.power_kw * s.hours for s in total) == pytest.approx(2.0 + 1.0 + 0.75)


def test_a_gap_in_one_array_adds_nothing_and_a_gap_in_all_stays_a_gap():
    a = steps((hour(8), hour(9), 1.0), (hour(9), hour(10), 1.0), (hour(12), hour(13), 1.0))
    b = steps((hour(8), hour(9), 2.0), (hour(12), hour(13), 2.0))
    assert add_up([a, b]) == steps((hour(8), hour(9), 3.0), (hour(9), hour(10), 1.0), (hour(12), hour(13), 3.0))


def test_the_total_covers_only_the_time_every_array_covers():
    a = steps((hour(8), hour(9), 1.0), (hour(9), hour(10), 1.0), (hour(10), hour(11), 1.0))
    b = steps((hour(9), hour(10), 2.0))
    assert add_up([a, b]) == steps((hour(9), hour(10), 3.0))
    assert add_up([a, []]) == [] and add_up([]) == []


def test_one_forecast_is_returned_as_it_is_and_the_total_keeps_the_contract():
    a = steps((hour(8), hour(9), 1.0))
    assert add_up([a]) == a
    utc = [ForecastStep(s.start.astimezone(UTC), s.end.astimezone(UTC), 1.0) for s in a]
    total = add_up([a, utc])
    assert total == steps((hour(8), hour(9), 2.0)) and total[0].start.tzinfo == TZ and check_steps(total) == []


def test_each_array_is_asked_on_its_own_and_in_order():
    system = SystemDescription(52.37, 4.9, (PanelArray(3.0, 30, -90, 0.14), PanelArray(2.5, 30, 90, 0.1)), "Europe/Amsterdam")
    asked = []

    def one(array):
        asked.append(array)
        return steps((hour(8), hour(9), array.kwp))

    assert each_array(system, one) == steps((hour(8), hour(9), 5.5))
    assert asked == list(system.arrays) and system.kwp == pytest.approx(5.5)


def bell_for(array: PanelArray) -> list[ForecastStep]:
    """A made-up forecast that depends on everything about the array: east peaks early, west late."""
    peak_hour = 13 - array.azimuth_deg / 30  # -90 (east) peaks at 16:00 UTC+2 minus 3 hours
    result = []
    for h in range(5, 22):
        start = at(DAY, h)
        shape = max(0.0, 1 - abs(h + 0.5 - peak_hour) / 6)
        result.append(ForecastStep(start, start + timedelta(hours=1), array.kwp * shape * (1 - array.system_losses) * (1 - array.tilt_deg / 200)))
    return result


def service_for(tmp_path, fake):
    registry = ProviderRegistry()
    registry.register(fake)
    return ForecastService(registry, tmp_path / "cache", lambda: {})


def test_two_arrays_give_the_sum_of_the_two_single_array_forecasts(tmp_path, cfg):
    cfg = replace(cfg, forecast=Forecast(primary="fake", fallback="", settings={}))
    fake = FakeProvider(per_array=bell_for)
    now = at(DAY, 7)

    def forecast(panels):
        return service_for(tmp_path / str(len(fake.calls)), fake).get(replace(cfg, panels=panels), now).steps

    east, west, both = forecast((EAST,)), forecast((WEST,)), forecast((EAST, WEST))
    assert east != west
    assert [s.start for s in both] == [s.start for s in east] and [s.end for s in both] == [s.end for s in east]
    assert [s.power_kw for s in both] == pytest.approx([e.power_kw + w.power_kw for e, w in zip(east, west, strict=True)])
    assert len(fake.calls[-1][0].arrays) == 2


def test_a_forecast_for_several_arrays_is_served_from_the_cache(tmp_path, cfg):
    cfg = replace(two_arrays(cfg), forecast=Forecast(primary="fake", fallback="", settings={}))
    fake = FakeProvider(per_array=bell_for)
    now = at(DAY, 7)
    assert not service_for(tmp_path, fake).get(cfg, now).from_cache
    again = service_for(tmp_path, fake).get(cfg, now + timedelta(minutes=10))
    assert again.from_cache and len(fake.calls) == 1


def test_a_cache_written_for_one_array_in_the_old_shape_is_not_used_and_does_not_crash(tmp_path, cfg):
    cfg = replace(cfg, forecast=Forecast(primary="fake", fallback="", settings={}))
    fake = FakeProvider(per_array=bell_for)
    now = at(DAY, 7)
    old_key = {"system": {"latitude": cfg.latitude, "longitude": cfg.longitude, "kwp": 4.8, "tilt_deg": 35, "azimuth_deg": -45, "system_losses": 0.14, "timezone": cfg.timezone}, "settings": {}}
    cache = tmp_path / "cache"
    cache.mkdir()
    (cache / "forecast-fake.json").write_text(json.dumps({"key": old_key, "fetched_at": now.timestamp(), "steps": [[now.timestamp(), now.timestamp() + 3600, 9.9]]}))
    result = service_for(tmp_path, fake).get(cfg, now)
    assert not result.from_cache and len(fake.calls) == 1 and all(s.power_kw != 9.9 for s in result.steps)


def test_adding_or_changing_an_array_changes_what_the_forecast_depends_on(cfg):
    assert forecast_key(two_arrays(cfg)) != forecast_key(cfg)
    assert forecast_key(two_arrays(cfg)) != forecast_key(replace(cfg, panels=(EAST, replace(WEST, tilt_deg=10))))
    assert forecast_key(replace(cfg, panels=(EAST, WEST))) == forecast_key(replace(cfg, panels=(EAST, replace(WEST, name="Garage"))))


def test_the_finish_page_lists_the_total_and_each_array(cfg):
    from car_charge_planner.setup.recap import build_recap
    from car_charge_planner.setup.state import SetupState

    state = SetupState(config=replace(two_arrays(cfg), setup=Setup(("panels.2.tilt_deg",))), initial=cfg)
    group = next(g for g in build_recap(state) if g.key == "panels")
    assert [(line.label, line.value, line.typical) for line in group.lines] == [
        ("Total size", "5.50 kWp", False),
        ("East roof", "3.00 kWp, facing East (-90°), tilt 30°", False),
        ("West roof", "2.50 kWp, facing West (90°), tilt 30°", True),
        ("System losses", "14%, 10%", False),
    ]
    one = next(g for g in build_recap(SetupState(config=cfg, initial=cfg)) if g.key == "panels")
    assert [line.label for line in one.lines] == ["Size", "Direction", "Tilt", "System losses"]


def test_the_plan_page_names_a_typical_setting_once_whichever_array_it_is_of():
    from car_charge_planner.view_model import typical_line

    assert typical_line(("panels.2.kwp",)) == ("Based on a typical value for: Panel size.", "solar")
    assert typical_line(("panels.kwp", "panels.2.kwp", "panels.2.tilt_deg"))[0] == "Based on typical values for: Panel size, Tilt."


def test_the_demo_forecast_is_the_power_of_all_arrays(cfg):
    from car_charge_planner.planner.cli import demo_forecast

    one = demo_forecast("summer", DAY, TZ, replace(cfg, panels=(EAST,)))
    both = demo_forecast("summer", DAY, TZ, two_arrays(cfg))
    other = demo_forecast("summer", DAY, TZ, replace(cfg, panels=(WEST,)))
    assert [b.power_kw for b in both] == pytest.approx([o.power_kw + w.power_kw for o, w in zip(one, other, strict=True)])
