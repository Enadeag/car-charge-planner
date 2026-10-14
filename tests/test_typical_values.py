"""Typical values are remembered: which settings are still ones the app filled in."""

import os

os.environ.setdefault("QT_QPA_PLATFORM", "offscreen")

from dataclasses import replace
from datetime import date, timedelta

import pytest
from PySide6.QtWidgets import QApplication

from car_charge_planner.app_settings import DesktopSettings
from car_charge_planner.config.io import dump_config, write_config
from car_charge_planner.config.model import TYPICAL_KEYS, Config, ConfigError, Setup, first_run_config, load_config, validate
from car_charge_planner.planner.projection import plan
from car_charge_planner.popup import Popup
from car_charge_planner.settings.fields import TABS
from car_charge_planner.settings.window import SettingsWindow
from car_charge_planner.storage.state import BatteryReading, State
from car_charge_planner.view_model import build, needs_setup, no_forecast, typical_line
from conftest import at, engine_for, forecast_from

DAY = date(2026, 7, 3)
NOW = at(DAY, 7)


def with_typical(cfg, *keys):
    return replace(cfg, setup=Setup(tuple(keys)))


def test_nothing_is_typical_by_default_and_an_old_config_means_the_values_are_the_owners(tmp_path):
    assert Config().setup.typical == ()
    path = tmp_path / "config.toml"
    path.write_text("[panels]\nkwp = 6.0\n", encoding="utf-8")  # the file of an earlier version
    assert load_config(path).setup.typical == ()


def test_the_list_is_written_and_read_back(tmp_path, cfg):
    chosen = with_typical(cfg, "panels.kwp", "car.km_per_day")
    path = tmp_path / "config.toml"
    write_config(chosen, path)
    assert '[setup]\ntypical = ["panels.kwp", "car.km_per_day"]' in path.read_text(encoding="utf-8")
    assert load_config(path).setup.typical == ("panels.kwp", "car.km_per_day")
    assert "typical = []" in dump_config(cfg)


def test_a_new_install_starts_with_every_value_typical(tmp_path):
    assert first_run_config().setup.typical == TYPICAL_KEYS
    path = tmp_path / "config.toml"
    write_config(first_run_config(), path)
    assert load_config(path) == first_run_config()


@pytest.mark.parametrize("keys, message", [(("panels.kwp", "panels.kwpp"), "do not exist"), (("latitude",), "do not exist"), (("panels.kwp", "panels.kwp"), "twice")])
def test_a_list_that_makes_no_sense_is_refused(cfg, keys, message):
    with pytest.raises(ConfigError, match=message):
        validate(with_typical(cfg, *keys))


@pytest.mark.parametrize("text, message", [("typical = 5", "list of setting names"), ("typical = [1, 2]", "list of setting names"), ("typicl = []", "unknown keys in .setup.")])
def test_a_file_that_makes_no_sense_is_refused(tmp_path, text, message):
    path = tmp_path / "config.toml"
    path.write_text(f"[setup]\n{text}\n", encoding="utf-8")
    with pytest.raises(ConfigError, match=message):
        load_config(path)


def test_every_listed_key_is_a_field_in_settings():
    keys = {spec.key for tab in TABS for spec in tab.fields}
    assert set(TYPICAL_KEYS) <= keys
    assert not any(spec.flag for tab in TABS for spec in tab.fields)


def tags(window, tab_key):
    tab = next(t for t in window.field_tabs if t.key == tab_key)
    return {key: "Typical value." in label.text() for key, label in tab.hints.items()}


def make(cfg, tmp_path):
    file = tmp_path / "config.toml"
    return SettingsWindow(engine_for(cfg, file), DesktopSettings()), file


def test_tags_show_only_on_the_listed_keys(tmp_path, qapp, cfg):
    window, _ = make(with_typical(cfg, "panels.kwp", "panels.tilt_deg"), tmp_path)
    shown = tags(window, "solar")
    assert shown["panels.kwp"] and shown["panels.tilt_deg"] and not shown["panels.azimuth_deg"] and not shown["latitude"]


def test_a_user_who_gave_every_value_sees_no_tags(tmp_path, qapp, cfg):
    window, _ = make(cfg, tmp_path)
    assert not any(any(tags(window, tab.key).values()) for tab in window.field_tabs)


def test_a_new_install_shows_tags_on_every_value_that_can_be_typical(tmp_path, qapp):
    window, _ = make(first_run_config(), tmp_path)
    shown = {k for tab in window.field_tabs for k, v in tags(window, tab.key).items() if v}
    assert shown == set(TYPICAL_KEYS)


def test_editing_a_value_clears_its_tag_at_once_and_saving_removes_the_key(tmp_path, qapp, cfg):
    window, file = make(with_typical(cfg, "panels.kwp", "panels.tilt_deg", "car.km_per_day"), tmp_path)
    box = window.field_tabs[0].inputs["panels.kwp"]
    box.setValue(5.5)
    assert not tags(window, "solar")["panels.kwp"] and tags(window, "solar")["panels.tilt_deg"]
    assert window.current_config().setup.typical == ("panels.tilt_deg", "car.km_per_day")
    assert window.save()
    assert load_config(file).setup.typical == ("panels.tilt_deg", "car.km_per_day")
    assert not tags(window, "solar")["panels.kwp"]


def test_saving_without_touching_a_field_leaves_it_typical(tmp_path, qapp, cfg):
    window, file = make(with_typical(cfg, "panels.kwp", "car.km_per_day"), tmp_path)
    window.field_tabs[0].inputs["latitude"].setValue(51.5)  # something else changes
    assert window.save()
    assert load_config(file).setup.typical == ("panels.kwp", "car.km_per_day")


def test_typing_the_same_value_back_is_not_giving_a_value_of_your_own(tmp_path, qapp, cfg):
    window, _ = make(with_typical(cfg, "panels.kwp"), tmp_path)
    box = window.field_tabs[0].inputs["panels.kwp"]
    box.setValue(6.0)
    assert not tags(window, "solar")["panels.kwp"]
    box.setValue(cfg.system.panels[0].kwp)
    assert tags(window, "solar")["panels.kwp"] and window.current_config().setup.typical == ("panels.kwp",)


def test_an_edit_on_one_tab_leaves_the_other_tabs_typical_keys_alone(tmp_path, qapp, cfg):
    window, _ = make(with_typical(cfg, "panels.kwp", "charger.max_amps"), tmp_path)
    window.field_tabs[0].inputs["panels.kwp"].setValue(7.0)
    assert window.current_config().setup.typical == ("charger.max_amps",)
    charger = next(t for t in window.field_tabs if t.key == "charger")
    charger.inputs["charger.max_amps"].setValue(20)
    assert window.current_config().setup.typical == ()


def test_unit_switching_does_not_count_as_giving_a_value(tmp_path, qapp, cfg):
    window, _ = make(with_typical(cfg, "car.km_per_day", "car.consumption_kwh_per_100km"), tmp_path)
    units_box = window.interface_tab.inputs["interface.units"]
    units_box.setCurrentIndex(units_box.findData("imperial"))
    assert window.current_config().setup.typical == ("car.consumption_kwh_per_100km", "car.km_per_day")
    car = next(t for t in window.field_tabs if t.key == "house_car")
    assert all(v for k, v in tags(window, "house_car").items() if k in ("car.km_per_day", "car.consumption_kwh_per_100km"))
    assert car.units == "imperial"


def test_reset_tab_makes_that_tabs_built_in_values_typical_again(tmp_path, qapp, cfg):
    window, file = make(cfg, tmp_path)
    window.tabs.setCurrentWidget(next(t for t in window.field_tabs if t.key == "charger"))
    window.reset_tab()
    charger_keys = tuple(k for k in TYPICAL_KEYS if k.startswith("charger."))
    assert window.current_config().setup.typical == charger_keys
    assert all(tags(window, "charger")[k] for k in charger_keys)
    assert window.save()
    assert load_config(file).setup.typical == charger_keys


def test_no_typical_values_means_no_line():
    assert typical_line(()) == ("", "")
    assert typical_line(("not.a.key",)) == ("", "")  # nothing the owner could check


def test_one_typical_value_is_said_in_the_singular():
    assert typical_line(("panels.kwp",)) == ("Based on a typical value for: Panel size.", "solar")


def test_a_few_are_listed_and_a_long_list_is_shortened():
    text, tab = typical_line(("panels.kwp", "panels.tilt_deg", "panels.azimuth_deg"))
    assert text == "Based on typical values for: Panel size, Tilt, Direction." and tab == "solar"
    text, _ = typical_line(TYPICAL_KEYS)
    assert text == f"Based on typical values for: Panel size, Tilt, Direction and {len(TYPICAL_KEYS) - 3} more."
    assert len(text) < 90


def test_the_link_goes_to_the_tab_of_the_first_value():
    assert typical_line(("car.km_per_day", "panels.kwp"))[1] == "house_car"
    assert typical_line(("charger.max_amps",))[1] == "charger"


def view(cfg, state=None):
    forecast = forecast_from([(DAY, 900), (DAY + timedelta(days=1), 900)])
    plans = plan(forecast, cfg.system, cfg.planning, 45, NOW)
    vm = build(plans, NOW, cfg, BatteryReading(45, NOW), timedelta(minutes=5), False)
    popup = Popup()
    popup.update_view(vm, State(BatteryReading(45, NOW)), NOW.date(), cfg.system.away_rules)
    return popup, vm


def test_the_line_appears_while_values_are_typical_and_disappears_when_they_are_not(qapp, cfg):
    popup, vm = view(with_typical(cfg, "panels.kwp", "car.km_per_day"))
    assert vm.typical_line == "Based on typical values for: Panel size, Driving per day." and vm.typical_tab == "solar"
    assert not popup.typical.isHidden() and "Check them in Settings" in popup.typical.text() and "Driving per day" in popup.typical.text()
    popup, vm = view(cfg)
    assert vm.typical_line == "" and popup.typical.isHidden()


def test_the_link_opens_settings_on_the_right_tab(qapp, cfg):
    popup, _ = view(with_typical(cfg, "car.km_per_day"))
    seen = []
    popup.settings_requested.connect(seen.append)
    popup.typical.linkActivated.emit("settings")
    assert seen == ["house_car"]


def test_there_is_no_line_before_there_is_a_plan(qapp, cfg):
    popup = Popup()
    for vm in (needs_setup(BatteryReading(45, NOW), NOW), no_forecast("offline", BatteryReading(45, NOW), NOW)):
        popup.update_view(vm, State(BatteryReading(45, NOW)), NOW.date(), cfg.system.away_rules)
        assert vm.typical_line == "" and popup.typical.isHidden()


def test_the_line_is_readable_on_a_dark_window(qapp, cfg):
    from PySide6.QtGui import QColor, QPalette

    from car_charge_planner import colours

    original = qapp.palette()
    palette = QPalette(original)
    palette.setColor(QPalette.ColorRole.Window, QColor("#31363b"))
    palette.setColor(QPalette.ColorRole.WindowText, QColor("#eff0f1"))
    qapp.setPalette(palette)
    try:
        popup, _ = view(first_run_config())
        popup.show()
        QApplication.processEvents()
        assert colours.LINK["dark"] in popup.typical.text()
        popup.close()
    finally:
        qapp.setPalette(original)
