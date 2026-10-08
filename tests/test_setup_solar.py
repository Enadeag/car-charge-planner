"""The Solar panels page of the set-up wizard and its helpers (ticket S7)."""

import os

os.environ.setdefault("QT_QPA_PLATFORM", "offscreen")

from dataclasses import replace

import pytest
from PySide6.QtWidgets import QApplication, QLabel

from app.setup.panels import COMPASS, TILT_PRESETS, azimuth_for, compass_key_for, panels_to_kwp, tilt_preset_for
from app.setup.state import SetupState
from app.setup.typical import TYPICAL_VALUES, with_typical, with_values
from app.setup.wizard import SetupWizard
from forecast.check import Attempt
from planner.model import TYPICAL_KEYS, Config, Setup, load_config, validate

from conftest import example_config

NL = {"LANG": "nl_NL.UTF-8"}


@pytest.fixture
def qapp():
    return QApplication.instance() or QApplication([])


# ---- the helpers --------------------------------------------------------------------------------------------


@pytest.mark.parametrize("count, watts, kwp", [(12, 400, 4.8), (10, 410, 4.1), (20, 375, 7.5), (1, 50, 0.05), (500, 1000, 500.0), (14, 435, 6.09)])
def test_panels_times_watts_is_the_size_in_kwp(count, watts, kwp):
    assert panels_to_kwp(count, watts) == pytest.approx(kwp)


def test_the_eight_directions_have_the_azimuths_the_planner_uses():
    assert {key: azimuth for key, _s, _l, azimuth in COMPASS} == {
        "s": 0, "se": -45, "e": -90, "ne": -135, "n": 180, "nw": 135, "w": 90, "sw": 45,
    }
    assert len(COMPASS) == 8 and len({a for *_, a in COMPASS}) == 8


@pytest.mark.parametrize("key", [k for k, *_ in COMPASS])
def test_a_direction_and_its_degrees_find_each_other(key):
    assert compass_key_for(azimuth_for(key)) == key


@pytest.mark.parametrize("degrees, key", [(180, "n"), (-180, "n"), (0, "s"), (-45.0, "se"), (90, "w")])
def test_north_is_either_way_round(degrees, key):
    assert compass_key_for(degrees) == key


@pytest.mark.parametrize("degrees", [20, -33, 100, 179, -179, 0.5, 44.9])
def test_between_two_buttons_none_matches(degrees):
    assert compass_key_for(degrees) is None


@pytest.mark.parametrize("tilt, preset", [(10, "flat"), (35, "pitched"), (45, "steep"), (0, None), (30, None), (35.5, None)])
def test_a_tilt_matches_a_preset_only_when_exactly_equal(tilt, preset):
    assert tilt_preset_for(tilt) == preset


def test_the_presets_are_the_three_roofs_people_know():
    assert [(k, d) for k, _n, d in TILT_PRESETS] == [("flat", 10), ("pitched", 35), ("steep", 45)]


# ---- typical values -----------------------------------------------------------------------------------------


def test_there_is_a_typical_value_for_every_setting_that_can_be_one_and_they_are_valid():
    assert set(TYPICAL_VALUES) == set(TYPICAL_KEYS)
    validate(with_values(Config(latitude=1.0, longitude=1.0), TYPICAL_VALUES))  # all together make a config the planner accepts


def test_the_typical_panels_are_five_kwp_facing_south_at_35_degrees():
    config = with_typical(Config(), "panels.kwp", "panels.azimuth_deg", "panels.tilt_deg")
    assert (config.panels.kwp, config.panels.azimuth_deg, config.panels.tilt_deg) == (5.0, 0, 35)


def test_a_new_install_starts_with_the_typical_values_not_the_built_in_defaults():
    state = SetupState.starting_from(Config(), NL)
    assert state.config.panels.kwp == 5.0 and state.config.panels.azimuth_deg == 0 and state.config.house.baseload_kw == 0.5
    assert state.config.charger.max_amps == 16 and state.config.setup.typical == TYPICAL_KEYS


def test_a_config_that_is_set_up_is_not_touched():
    assert SetupState.starting_from(example_config(), NL).config.panels.kwp == 4.8


# ---- the page ----------------------------------------------------------------------------------------------


class Rig:
    def __init__(self, tmp_path, cfg=None):
        self.file = tmp_path / "config.toml"
        self.wizard = SetupWizard(cfg if cfg is not None else Config(), self.file, lambda c: None, check=lambda c: Attempt(True, 2, "OK"), environ=NL, qt_locale_name="")
        self.wizard.confirm_leave = lambda: True
        self.wizard.show()
        self.wizard.navigate("solar")
        self.page = self.wizard.pages["solar"]

    @property
    def panels(self):
        return self.wizard.state.config.panels

    @property
    def typical(self):
        return self.wizard.state.config.setup.typical


@pytest.fixture
def rig(tmp_path, qapp):
    return Rig(tmp_path)


def texts(widget):
    return [l.text() for l in widget.findChildren(QLabel)]


def test_a_new_install_shows_the_typical_values_and_says_they_are(rig):
    page = rig.page
    assert (page.kwp.value(), page.degrees.value(), page.tilt.value()) == (5.0, 0, 35)
    assert page.compass_buttons["s"].isChecked() and page.tilt_buttons["pitched"].isChecked()
    assert page.typical_note.text() == "Typical values in use: 5.0 kWp, facing South (0°), tilt 35°. Enter your own, or leave them."
    assert rig.wizard.heading.text() == "Your solar panels" and rig.wizard.next_button.isEnabled()


def test_a_set_up_config_shows_its_own_values_without_the_typical_note(tmp_path, qapp):
    rig = Rig(tmp_path, cfg=example_config())
    assert (rig.page.kwp.value(), rig.page.degrees.value(), rig.page.tilt.value()) == (4.8, -45, 35)
    assert rig.page.compass_buttons["se"].isChecked() and rig.page.typical_note.text() == ""


# size

def test_typing_the_size_gives_that_value_and_it_stops_being_typical(rig):
    rig.page.kwp.setValue(6.4)
    assert rig.panels.kwp == 6.4 and "panels.kwp" not in rig.typical
    assert "panels.tilt_deg" in rig.typical and "panels.azimuth_deg" in rig.typical  # the others are still typical
    assert "panels.system_losses" in rig.typical  # never asked here


def test_panels_times_watts_gives_the_size_and_shows_the_sum(rig):
    rig.page.by_count.setChecked(True)
    rig.page.count.setValue(12)
    rig.page.watts.setValue(400)
    assert rig.panels.kwp == pytest.approx(4.8) and rig.page.kwp.value() == pytest.approx(4.8)
    assert rig.page.product.text() == "= 4.80 kWp" and "panels.kwp" not in rig.typical
    rig.page.watts.setValue(450)
    assert rig.panels.kwp == pytest.approx(5.4)


def test_the_note_lists_only_the_values_that_are_still_typical(rig):
    rig.page.compass_buttons["se"].click()
    assert rig.page.typical_note.text() == "Typical values in use: 5.0 kWp, tilt 35\u00b0. Enter your own, or leave them."
    rig.page.kwp.setValue(6.0)
    rig.page.tilt.setValue(30)
    assert rig.page.typical_note.text() == ""


def test_the_sum_is_shown_even_before_that_way_is_chosen_but_does_not_change_the_answer(rig):
    rig.page.count.setValue(10)
    rig.page.watts.setValue(500)
    assert rig.page.product.text() == "= 5.00 kWp" and rig.panels.kwp == 5.0 and "panels.kwp" in rig.typical


def test_the_size_has_the_limits_settings_has(rig):
    rig.page.kwp.setValue(1000)
    assert rig.page.kwp.value() == 100
    rig.page.kwp.setValue(0)
    assert rig.page.kwp.value() == 0.1


# direction

def test_a_compass_button_sets_the_degrees_and_the_answer(rig):
    rig.page.compass_buttons["e"].click()
    assert rig.page.degrees.value() == -90 and rig.panels.azimuth_deg == -90 and "panels.azimuth_deg" not in rig.typical
    assert rig.page.direction_name.text() == "East (-90°)"
    rig.page.compass_buttons["n"].click()
    assert rig.page.degrees.value() == 180 and rig.panels.azimuth_deg == 180


def test_typing_degrees_presses_the_matching_button_or_none(rig):
    rig.page.degrees.setValue(45)
    assert rig.page.compass_buttons["sw"].isChecked() and not rig.page.compass_buttons["s"].isChecked()
    rig.page.degrees.setValue(20)
    assert not any(b.isChecked() for b in rig.page.compass_buttons.values())
    assert rig.panels.azimuth_deg == 20 and rig.page.direction_name.text() == "20°"
    rig.page.degrees.setValue(-180)
    assert rig.page.compass_buttons["n"].isChecked()


def test_clicking_the_direction_that_is_already_shown_is_still_an_answer(rig):
    assert "panels.azimuth_deg" in rig.typical
    rig.page.compass_buttons["s"].click()  # the typical value, but now the person's own choice
    assert rig.panels.azimuth_deg == 0 and "panels.azimuth_deg" not in rig.typical


# tilt

def test_a_tilt_preset_sets_the_angle_and_the_answer(rig):
    rig.page.tilt_buttons["flat"].click()
    assert rig.page.tilt.value() == 10 and rig.panels.tilt_deg == 10 and "panels.tilt_deg" not in rig.typical
    rig.page.tilt_buttons["steep"].click()
    assert rig.panels.tilt_deg == 45


def test_typing_an_angle_presses_the_matching_preset_or_none(rig):
    rig.page.tilt.setValue(45)
    assert rig.page.tilt_buttons["steep"].isChecked()
    rig.page.tilt.setValue(30)
    assert not any(b.isChecked() for b in rig.page.tilt_buttons.values()) and rig.panels.tilt_deg == 30


def test_clicking_the_preset_that_is_already_shown_is_still_an_answer(rig):
    assert "panels.tilt_deg" in rig.typical
    rig.page.tilt_buttons["pitched"].click()
    assert "panels.tilt_deg" not in rig.typical


# "I don't know"

def test_i_dont_know_puts_the_typical_values_back_and_remembers_them_as_typical(tmp_path, qapp):
    rig = Rig(tmp_path, cfg=example_config())  # a set-up config: values of its own
    rig.page.kwp.setValue(7.2)
    rig.page.compass_buttons["w"].click()
    rig.page.tilt_buttons["steep"].click()
    assert rig.typical == ()
    rig.page.dont_know.click()
    assert (rig.panels.kwp, rig.panels.azimuth_deg, rig.panels.tilt_deg) == (5.0, 0, 35)
    assert rig.typical == ("panels.kwp", "panels.tilt_deg", "panels.azimuth_deg")  # not the losses: never asked of this owner
    assert (rig.page.kwp.value(), rig.page.degrees.value(), rig.page.tilt.value()) == (5.0, 0, 35)
    assert rig.page.compass_buttons["s"].isChecked() and "Typical values in use" in rig.page.typical_note.text()


def test_changing_one_value_after_i_dont_know_gives_only_that_one(rig):
    rig.page.dont_know.click()
    rig.page.tilt.setValue(25)
    assert [k for k in rig.typical if k.startswith("panels.")] == ["panels.kwp", "panels.azimuth_deg", "panels.system_losses"]
    assert rig.panels.tilt_deg == 25


def test_i_dont_know_leaves_the_rest_of_the_answers_alone(tmp_path, qapp):
    rig = Rig(tmp_path, cfg=example_config())
    rig.page.dont_know.click()
    assert rig.wizard.state.config.latitude == 52.37 and rig.wizard.state.config.car == example_config().car


# going on and back

def test_the_answers_are_kept_when_leaving_and_coming_back(rig):
    rig.page.kwp.setValue(6.6)
    rig.page.compass_buttons["se"].click()
    rig.wizard.next()
    rig.wizard.back()
    assert rig.page.kwp.value() == 6.6 and rig.page.degrees.value() == -45 and rig.page.compass_buttons["se"].isChecked()


def test_finishing_writes_the_panels_and_what_is_still_typical(tmp_path, qapp):
    rig = Rig(tmp_path, cfg=replace(example_config(), setup=Setup(TYPICAL_KEYS)))
    rig.page.kwp.setValue(6.0)
    rig.page.compass_buttons["s"].click()
    rig.wizard.navigate("finish")
    for thread in list(rig.wizard.pages["finish"]._threads):
        thread.join(5)
    assert rig.wizard.finish()
    written = load_config(rig.file)
    assert (written.panels.kwp, written.panels.azimuth_deg) == (6.0, 0)
    assert "panels.kwp" not in written.setup.typical and "panels.azimuth_deg" not in written.setup.typical
    assert "panels.tilt_deg" in written.setup.typical and "car.km_per_day" in written.setup.typical


# the explanations

def test_each_value_has_its_where_to_find_line_and_the_east_west_note(rig):
    shown = " ".join(texts(rig.page))
    assert "installer's datasheet or invoice, and in the inverter's app" in shown
    assert "A satellite view in a map app shows it" in shown and "0 is south, -90 east, 90 west, 180 north" in shown
    assert "Most pitched roofs are 30 to 40 degrees" in shown
    assert "both an east and a west roof" in shown and "face them south" in shown and "flat tilt (about 10 degrees)" in shown


def test_the_buttons_are_named_in_the_language(tmp_path, qapp):
    from app import i18n
    from test_i18n import make_catalogue

    make_catalogue(tmp_path, "xx", {("compass, short", "SE"): "ZO", ("compass direction", "South-east"): "Zuidoost", ("roof tilt", "Flat roof"): "Plat dak"})
    i18n.init(["xx"], tmp_path)
    try:
        (tmp_path / "w").mkdir()
        rig = Rig(tmp_path / "w")
        assert rig.page.compass_buttons["se"].text() == "ZO" and rig.page.compass_buttons["se"].toolTip() == "Zuidoost"
        assert rig.page.tilt_buttons["flat"].text() == "Plat dak (10°)"
    finally:
        i18n.init(languages=[])
