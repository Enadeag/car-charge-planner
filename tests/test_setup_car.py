"""The Car and charger page of the set-up wizard and its helpers."""

import os

os.environ.setdefault("QT_QPA_PLATFORM", "offscreen")

from dataclasses import replace

import pytest

from car_charge_planner.forecast.check import Attempt
from car_charge_planner.planner.model import TYPICAL_KEYS, Config, Setup, load_config
from car_charge_planner.setup.vehicle import CAR_KEYS, CHARGER_KEYS, problems
from car_charge_planner.setup.wizard import SetupWizard
from conftest import example_config

NL = {"LANG": "nl_NL.UTF-8"}


def test_the_planners_own_messages_are_what_the_page_shows(cfg):
    assert problems(cfg) == []
    low = replace(cfg, car=replace(cfg.car, minimum_pct=90, target_pct=50))
    assert problems(low) == ["[car] needs 0 <= minimum_pct < target_pct <= 100"]
    amps = replace(cfg, charger=replace(cfg.charger, min_amps=20, max_amps=10))
    assert problems(amps) == ["[charger] needs 0 < min_amps <= max_amps"]


def test_only_the_car_and_the_charger_are_this_pages_business(cfg):
    odd = replace(cfg, latitude=999.0, car=replace(cfg.car, minimum_pct=90, target_pct=50))
    assert problems(odd) == ["[car] needs 0 <= minimum_pct < target_pct <= 100"]


class Rig:
    def __init__(self, tmp_path, cfg=None, units="metric"):
        base = cfg if cfg is not None else Config()
        base = replace(base, interface=replace(base.interface, units=units))
        self.file = tmp_path / "config.toml"
        region = {"LANG": "en_US.UTF-8"} if units == "imperial" else NL
        self.wizard = SetupWizard(base, self.file, lambda c: None, check=lambda c: Attempt(True, 2, "OK"), environ=region, qt_locale_name="")
        self.wizard.confirm_leave = lambda: True
        self.wizard.show()
        self.wizard.navigate("car")
        self.page = self.wizard.pages["car"]

    @property
    def cfg(self):
        return self.wizard.state.config

    @property
    def typical(self):
        return self.cfg.setup.typical


@pytest.fixture
def rig(tmp_path, qapp):
    return Rig(tmp_path)


def test_a_new_install_shows_the_typical_values_and_says_so(rig):
    page = rig.page
    assert (page.battery.value(), page.consumption.value(), page.weekly.value()) == (60, 18, 280)
    assert (page.minimum.value(), page.target.value()) == (30, 80)
    assert page.one_phase.isChecked() and (page.max_amps.value(), page.min_amps.value()) == (16, 6)
    assert page.car_note.text() and page.charger_note.text()  # it says the values are typical
    assert rig.wizard.next_button.isEnabled()


@pytest.mark.parametrize("units", ["metric", "imperial"])
def test_just_looking_at_the_page_changes_no_answer(tmp_path, qapp, units):
    """A box clamps its value when its range is set and reports a change, which recorded a consumption of 5 as an answer."""
    for cfg in (Config(), example_config()):
        folder = tmp_path / f"{units}-{cfg.is_set_up}"
        folder.mkdir()
        rig = Rig(folder, cfg=cfg, units=units)
        before = rig.wizard.pages["welcome"].state.initial
        assert rig.cfg.car == before.car and rig.cfg.charger == before.charger and rig.cfg.panels == before.panels
        assert rig.typical == before.setup.typical
        rig.wizard.next()
        rig.wizard.back()
        assert rig.cfg.car == before.car and rig.typical == before.setup.typical


def test_a_set_up_config_shows_its_own_values_and_no_typical_note(tmp_path, qapp):
    rig = Rig(tmp_path, cfg=example_config())
    assert rig.page.battery.value() == 70 and rig.page.max_amps.value() == 32
    assert rig.page.weekly.value() == 600 and rig.page.car_note.text() == "" and rig.page.charger_note.text() == ""


# the car

def test_each_car_value_given_is_remembered_and_stops_being_typical(rig):
    rig.page.battery.setValue(75)
    rig.page.consumption.setValue(16.5)
    rig.page.weekly.setValue(350)
    assert (rig.cfg.car.usable_battery_kwh, rig.cfg.car.consumption_kwh_per_100km) == (75, 16.5)
    assert rig.cfg.car.km_per_day == pytest.approx(50) and not set(CAR_KEYS) & set(rig.typical)
    assert set(CHARGER_KEYS) <= set(rig.typical)
    assert rig.page.car_note.text() == ""


def test_a_week_is_what_is_asked_and_a_day_is_what_is_stored(rig):
    rig.page.weekly.setValue(420)
    assert rig.cfg.car.km_per_day == pytest.approx(60)


def test_an_untouched_distance_is_not_rewritten_by_showing_it_rounded(tmp_path, qapp):
    odd = replace(example_config(), car=replace(example_config().car, km_per_day=600 / 7 + 0.123))
    rig = Rig(tmp_path, cfg=odd)
    assert rig.page.weekly.value() == 601
    rig.wizard.next()
    assert rig.cfg.car.km_per_day == odd.car.km_per_day


def test_the_charge_levels_are_preferences_not_typical_values(rig):
    before = rig.typical
    rig.page.minimum.setValue(20)
    rig.page.target.setValue(90)
    assert (rig.cfg.car.minimum_pct, rig.cfg.car.target_pct) == (20, 90) and rig.typical == before


def test_a_minimum_not_below_the_target_is_said_in_settings_words_and_stops_next(rig):
    rig.page.minimum.setValue(85)  # the target is 80
    assert "[car] needs 0 <= minimum_pct < target_pct <= 100" in rig.page.errors.text()
    assert not rig.wizard.next_button.isEnabled()
    rig.page.target.setValue(95)
    assert rig.page.errors.text() == "" and rig.wizard.next_button.isEnabled()


def test_i_dont_know_the_car_puts_back_its_typical_values_and_remembers_them(tmp_path, qapp):
    rig = Rig(tmp_path, cfg=example_config())
    rig.page.battery.setValue(77)
    rig.page.weekly.setValue(100)
    rig.page.minimum.setValue(10)
    rig.page.car_unknown.click()
    assert (rig.cfg.car.usable_battery_kwh, rig.cfg.car.consumption_kwh_per_100km, rig.cfg.car.km_per_day) == (60, 18, 40)
    assert set(CAR_KEYS) <= set(rig.typical) and not set(CHARGER_KEYS) & set(rig.typical)
    assert rig.cfg.car.minimum_pct == 10
    assert (rig.page.battery.value(), rig.page.weekly.value()) == (60, 280)


# the charger

def test_choosing_three_phases_is_an_answer(rig):
    rig.page.three_phases.setChecked(True)
    assert rig.cfg.charger.phases == 3 and "charger.phases" not in rig.typical
    rig.page.one_phase.setChecked(True)
    assert rig.cfg.charger.phases == 1


def test_the_currents_given_are_remembered_and_the_voltage_stays_typical_until_given(rig):
    rig.page.max_amps.setValue(32)
    rig.page.min_amps.setValue(8)
    assert (rig.cfg.charger.max_amps, rig.cfg.charger.min_amps) == (32, 8)
    assert "charger.max_amps" not in rig.typical and "charger.min_amps" not in rig.typical and "charger.volts" in rig.typical
    rig.page.volts.setValue(240)
    assert rig.cfg.charger.volts == 240 and "charger.volts" not in rig.typical


def test_a_lowest_current_above_the_highest_is_said_and_stops_next(rig):
    rig.page.min_amps.setValue(20)  # the highest is 16
    assert "[charger] needs 0 < min_amps <= max_amps" in rig.page.errors.text() and not rig.wizard.next_button.isEnabled()
    rig.page.max_amps.setValue(25)
    assert rig.wizard.next_button.isEnabled()


def test_i_dont_know_the_charger_puts_back_its_typical_values_and_remembers_them(tmp_path, qapp):
    rig = Rig(tmp_path, cfg=example_config())
    rig.page.three_phases.setChecked(True)
    rig.page.max_amps.setValue(40)
    rig.page.charger_unknown.click()
    assert (rig.cfg.charger.phases, rig.cfg.charger.max_amps, rig.cfg.charger.min_amps, rig.cfg.charger.volts) == (1, 16, 6, 230)
    assert set(CHARGER_KEYS) <= set(rig.typical) and rig.page.one_phase.isChecked() and rig.page.max_amps.value() == 16


def test_imperial_asks_for_miles_and_miles_per_kwh_with_converted_ranges(tmp_path, qapp):
    rig = Rig(tmp_path, units="imperial")
    page = rig.page
    assert page.weekly.suffix() == " mi" and page.consumption.suffix() == " mi/kWh"
    assert page.weekly.value() == pytest.approx(174, abs=1)
    assert page.consumption.value() == pytest.approx(3.45, abs=0.005)
    assert "Typical: 2.82 to 4.44 mi/kWh." in page.consumption_note.text()
    assert (page.consumption.minimum(), page.consumption.maximum()) == (1.0, 12.5)


def test_what_is_typed_in_miles_is_stored_in_kilometres(tmp_path, qapp):
    rig = Rig(tmp_path, units="imperial")
    rig.page.weekly.setValue(200)
    rig.page.consumption.setValue(4.0)
    assert rig.cfg.car.km_per_day == pytest.approx(200 * 1.609344 / 7, rel=1e-5)
    assert rig.cfg.car.consumption_kwh_per_100km == pytest.approx(100 / (4.0 * 1.609344), rel=1e-5)


def test_going_back_to_welcome_and_changing_the_units_changes_this_page(tmp_path, qapp):
    rig = Rig(tmp_path)
    rig.wizard.navigate("welcome")
    welcome = rig.wizard.pages["welcome"]
    welcome.units.setCurrentIndex(welcome.units.findData("imperial"))
    rig.wizard.navigate("car")
    assert rig.page.weekly.suffix() == " mi" and rig.page.consumption.suffix() == " mi/kWh"
    assert rig.cfg.car.km_per_day == 40


# writing

def test_finishing_writes_what_was_answered_here(tmp_path, qapp):
    rig = Rig(tmp_path, cfg=replace(example_config(), setup=Setup(TYPICAL_KEYS)))
    rig.page.battery.setValue(64)
    rig.page.three_phases.setChecked(True)
    rig.page.max_amps.setValue(16)
    rig.wizard.navigate("finish")
    for thread in list(rig.wizard.pages["finish"]._threads):
        thread.join(5)
    assert rig.wizard.finish()
    written = load_config(rig.file)
    assert (written.car.usable_battery_kwh, written.charger.phases) == (64, 3)
    assert "car.usable_battery_kwh" not in written.setup.typical and "charger.phases" not in written.setup.typical
    assert "car.km_per_day" in written.setup.typical and "charger.efficiency" in written.setup.typical
