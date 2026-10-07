"""Metric and imperial units (ticket S2)."""

import os

os.environ.setdefault("QT_QPA_PLATFORM", "offscreen")

from dataclasses import replace

import pytest
from PySide6.QtWidgets import QApplication

from app import units
from app.settings.window import SettingsWindow
from planner.model import Config, ConfigError, load_config, validate
from planner.config_io import dump_config, write_config



@pytest.fixture
def qapp():
    return QApplication.instance() or QApplication([])


# ---- the conversions ---------------------------------------------------------------------------------------


def test_a_mile_is_1609_metres():
    assert units.miles_to_km(1) == pytest.approx(1.609344) and units.km_to_miles(1.609344) == pytest.approx(1)
    assert units.km_to_miles(40) == pytest.approx(24.8548, rel=1e-5)


def test_consumption_converts_by_the_reciprocal():
    assert units.kwh_per_100km_to_miles_per_kwh(18) == pytest.approx(62.1371 / 18, rel=1e-5)  # 3.45 mi/kWh
    assert units.miles_per_kwh_to_kwh_per_100km(3.452) == pytest.approx(18, rel=1e-3)
    assert units.kwh_per_100km_to_miles_per_kwh(10) > units.kwh_per_100km_to_miles_per_kwh(20)  # more thirsty, fewer miles


@pytest.mark.parametrize("value", [0.1, 1, 5, 14, 18, 22, 60, 123.456])
def test_the_round_trips_agree(value):
    assert units.miles_to_km(units.km_to_miles(value)) == pytest.approx(value, rel=1e-12)
    assert units.miles_per_kwh_to_kwh_per_100km(units.kwh_per_100km_to_miles_per_kwh(value)) == pytest.approx(value, rel=1e-12)
    for quantity in (units.DISTANCE, units.CONSUMPTION):
        shown = units.to_display(quantity, value, units.IMPERIAL)
        assert units.from_display(quantity, shown, units.IMPERIAL) == pytest.approx(value, rel=1e-12)
        assert units.to_display(quantity, value, units.METRIC) == value  # metric is the stored value itself


def test_the_reciprocal_has_no_answer_for_no_consumption():
    for bad in (0, -1):
        with pytest.raises(ValueError):
            units.kwh_per_100km_to_miles_per_kwh(bad)
        with pytest.raises(ValueError):
            units.miles_per_kwh_to_kwh_per_100km(bad)
    assert units.km_to_miles(0) == 0  # no distance is fine


def test_the_limits_of_a_box_are_converted_and_turned_round_for_consumption():
    assert units.display_range(units.DISTANCE, 0, 1000, units.METRIC) == (0, 1000)
    assert units.display_range(units.DISTANCE, 0, 1000, units.IMPERIAL) == (0, 622)
    low, high = units.display_range(units.CONSUMPTION, 5, 60, units.IMPERIAL)
    assert (low, high) == (1.0, 12.5)  # 60 kWh/100 km is the smallest number of miles, 5 the largest
    assert units.display_range(units.CONSUMPTION, 5, 60, units.METRIC) == (5, 60)


def test_labels_decimals_and_steps_follow_the_system():
    assert [units.unit_label(q, s) for q in (units.DISTANCE, units.CONSUMPTION) for s in units.SYSTEMS] == ["km", "mi", "kWh/100 km", "mi/kWh"]
    assert units.suffix(units.DISTANCE, units.IMPERIAL) == " mi"
    assert units.display_decimals(units.CONSUMPTION, 1, units.IMPERIAL) == 2 and units.display_decimals(units.CONSUMPTION, 1, units.METRIC) == 1
    assert units.display_step(units.CONSUMPTION, 0.5, units.IMPERIAL) == 0.05 and units.display_step(units.DISTANCE, 5, units.IMPERIAL) == 5
    with pytest.raises(ValueError):
        units.unit_label("weight", units.METRIC)


# ---- which system to suggest ---------------------------------------------------------------------------------


@pytest.mark.parametrize("region", ["US", "GB", "LR", "MM"])
def test_these_regions_are_suggested_imperial(region):
    assert units.suggest_system({"LANG": f"en_{region}.UTF-8"}) == units.IMPERIAL


@pytest.mark.parametrize("name", ["nl_NL.UTF-8", "de_DE", "fr_CA", "en_AU", "en_IE", "en_IN", "pt_BR", "ja_JP", "C", "POSIX", "en", ""])
def test_everywhere_else_is_suggested_metric(name):
    assert units.suggest_system({"LANG": name}) == units.METRIC


def test_the_measurement_category_beats_the_language_and_lc_all_beats_both():
    assert units.suggest_system({"LANG": "en_US.UTF-8", "LC_MEASUREMENT": "nl_NL.UTF-8"}) == units.METRIC
    assert units.suggest_system({"LANG": "nl_NL.UTF-8", "LC_MEASUREMENT": "en_GB.UTF-8"}) == units.IMPERIAL
    assert units.suggest_system({"LANG": "nl_NL.UTF-8", "LC_MEASUREMENT": "en_GB.UTF-8", "LC_ALL": "de_DE.UTF-8"}) == units.METRIC


def test_with_nothing_set_qts_system_locale_decides():
    assert units.suggest_system({}, "en_US") == units.IMPERIAL
    assert units.suggest_system({}, "nl_NL") == units.METRIC
    assert units.suggest_system({}, "") == units.METRIC
    assert units.suggest_system({"LANG": "nl_NL"}, "en_US") == units.METRIC  # the environment speaks first


def test_a_dashed_or_scripted_name_still_has_its_region():
    assert units.suggest_system({"LANG": "en-US"}) == units.IMPERIAL
    assert units.suggest_system({"LANG": "zh_Hant_TW"}) == units.METRIC
    assert isinstance(units.system_qt_locale_name(), str)


# ---- the setting ---------------------------------------------------------------------------------------------


def test_metric_is_the_default_and_the_choice_survives_a_round_trip(tmp_path, cfg):
    assert Config().interface.units == "metric"
    path = tmp_path / "config.toml"
    write_config(replace(cfg, interface=replace(cfg.interface, units="imperial")), path)
    assert 'units = "imperial"' in path.read_text() and load_config(path).interface.units == "imperial"
    assert 'units = "metric"' in dump_config(cfg)


def test_an_old_config_means_metric(tmp_path):
    path = tmp_path / "config.toml"
    path.write_text('[interface]\nmode = "window"\n')
    assert load_config(path).interface.units == "metric"


@pytest.mark.parametrize("value", ["", "imperial ", "US", "miles", "METRIC"])
def test_anything_else_is_refused(cfg, value):
    with pytest.raises(ConfigError, match="units"):
        validate(replace(cfg, interface=replace(cfg.interface, units=value)))


# ---- Settings ------------------------------------------------------------------------------------------------


def car_tab(window):
    return next(t for t in window.field_tabs if t.key == "house_car")


def with_units(cfg, system):
    return replace(cfg, interface=replace(cfg.interface, units=system))


def boxes(window):
    tab = car_tab(window)
    return tab.inputs["car.km_per_day"], tab.inputs["car.consumption_kwh_per_100km"]


def make_window(cfg, tmp_path):
    return SettingsWindow(cfg, tmp_path / "config.toml", lambda c: None)


def test_metric_shows_km_and_kwh_per_100_km(tmp_path, qapp, cfg):
    distance, consumption = boxes(make_window(cfg, tmp_path))
    assert distance.suffix() == " km" and consumption.suffix() == " kWh/100 km"
    assert distance.value() == pytest.approx(85.7) and consumption.value() == 18


def test_imperial_shows_miles_and_miles_per_kwh_with_converted_limits(tmp_path, qapp, cfg):
    distance, consumption = boxes(make_window(with_units(cfg, "imperial"), tmp_path))
    assert distance.suffix() == " mi" and consumption.suffix() == " mi/kWh"
    assert distance.value() == pytest.approx(53.3, abs=0.05) and consumption.value() == pytest.approx(3.45, abs=0.005)
    assert (distance.minimum(), distance.maximum()) == (0, 622)
    assert (consumption.minimum(), consumption.maximum()) == (1.0, 12.5) and consumption.decimals() == 2


def test_an_untouched_field_keeps_its_exact_stored_value_in_both_systems(tmp_path, qapp, cfg):
    exact = replace(cfg, car=replace(cfg.car, km_per_day=600 / 7, consumption_kwh_per_100km=17.77))
    for system in ("metric", "imperial"):
        window = make_window(with_units(exact, system), tmp_path)
        assert window.current_config().car == exact.car and not window.is_dirty()  # nothing rounded, nothing changed


def test_typing_miles_stores_kilometres(tmp_path, qapp, cfg):
    file = tmp_path / "config.toml"
    window = SettingsWindow(with_units(cfg, "imperial"), file, lambda c: None)
    distance, consumption = boxes(window)
    distance.setValue(25)
    consumption.setValue(4.0)
    assert window.save()
    saved = load_config(file)
    assert saved.car.km_per_day == pytest.approx(25 * 1.609344) and saved.car.consumption_kwh_per_100km == pytest.approx(100 / (4.0 * 1.609344), rel=1e-6)
    assert saved.interface.units == "imperial"


def test_choosing_the_system_changes_the_boxes_at_once_and_keeps_the_stored_values(tmp_path, qapp, cfg):
    window = make_window(cfg, tmp_path)
    units_box = window.interface_tab.inputs["interface.units"]
    distance, consumption = boxes(window)
    stored = window.current_config().car
    units_box.setCurrentIndex(units_box.findData("imperial"))
    assert distance.suffix() == " mi" and consumption.suffix() == " mi/kWh"
    assert distance.value() == pytest.approx(53.3, abs=0.05)
    assert window.current_config().car == stored  # only the way it is shown changed
    units_box.setCurrentIndex(units_box.findData("metric"))
    assert distance.suffix() == " km" and window.current_config().car == stored and not window.is_dirty()


def test_an_edit_made_in_one_system_survives_switching_to_the_other(tmp_path, qapp, cfg):
    window = make_window(cfg, tmp_path)
    distance, consumption = boxes(window)
    distance.setValue(100)  # km
    units_box = window.interface_tab.inputs["interface.units"]
    units_box.setCurrentIndex(units_box.findData("imperial"))
    assert distance.value() == pytest.approx(62.1, abs=0.05)
    assert window.current_config().car.km_per_day == pytest.approx(100, rel=1e-3)  # the value typed, not a fresh one
    units_box.setCurrentIndex(units_box.findData("metric"))
    assert distance.value() == 100 and window.current_config().car.km_per_day == 100  # exactly, not 99.9 from a rounded 62.1 mi


def test_reset_tab_shows_the_defaults_in_the_chosen_system(tmp_path, qapp, cfg):
    window = make_window(with_units(cfg, "imperial"), tmp_path)
    window.tabs.setCurrentWidget(car_tab(window))
    distance, consumption = boxes(window)
    distance.setValue(10)
    window.reset_tab()
    default = Config().car
    assert distance.suffix() == " mi" and distance.value() == pytest.approx(units.km_to_miles(default.km_per_day), abs=0.05)
    assert window.current_config().car.km_per_day == pytest.approx(default.km_per_day) or window.current_config().car.km_per_day == default.km_per_day


def test_the_units_choice_is_on_the_interface_tab_with_its_hint(tmp_path, qapp, cfg):
    window = make_window(cfg, tmp_path)
    box = window.interface_tab.inputs["interface.units"]
    assert [box.itemText(i) for i in range(box.count())] == ["Metric (km, kWh/100 km)", "Imperial (miles, mi/kWh)"]
    from PySide6.QtWidgets import QLabel

    assert any("Energy and power are the same" in l.text() for l in window.interface_tab.findChildren(QLabel))


def test_a_value_only_one_system_can_hold_is_not_lost(tmp_path, qapp, cfg):
    """A stored 0 km a day is a real value (a car that stays home); imperial shows 0 and saves 0."""
    window = make_window(with_units(replace(cfg, car=replace(cfg.car, km_per_day=0.0)), "imperial"), tmp_path)
    assert boxes(window)[0].value() == 0 and window.current_config().car.km_per_day == 0.0
