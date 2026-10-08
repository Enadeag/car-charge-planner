"""The set-up wizard's answers and recap, without Qt (ticket S5)."""

from dataclasses import replace

import pytest

from car_charge_planner.setup.recap import build_recap, compass_label
from car_charge_planner.setup.state import SetupState
from car_charge_planner.planner.model import TYPICAL_KEYS, AwayRule, Config, ConfigError, Setup, first_run_config

from conftest import example_config

US = {"LANG": "en_US.UTF-8"}
NL = {"LANG": "nl_NL.UTF-8"}


def typical(*keys):
    return replace(example_config(), setup=Setup(keys))


# ---- starting ------------------------------------------------------------------------------------------------


def test_a_new_install_starts_with_every_value_typical_and_the_regions_units():
    state = SetupState.starting_from(Config(), US)
    assert state.config.setup.typical == TYPICAL_KEYS and state.config.interface.units == "imperial"
    assert SetupState.starting_from(first_run_config(), NL).config.interface.units == "metric"
    assert SetupState.starting_from(Config(), {}, "en_GB").config.interface.units == "imperial"


def test_a_set_up_config_is_taken_as_it_is_so_running_set_up_again_is_pre_filled():
    cfg = replace(typical("panels.kwp"), interface=replace(example_config().interface, units="metric"))
    state = SetupState.starting_from(cfg, US)  # the region says imperial; the owner's own choice stands
    assert state.config == cfg and state.initial == cfg and not state.touched


def test_nothing_has_been_done_until_something_is():
    state = SetupState.starting_from(example_config(), NL)
    assert not state.touched
    state.place_label = "Utrecht"
    assert state.touched
    other = SetupState.starting_from(example_config(), NL)
    other.config = replace(other.config, latitude=1.0)
    assert other.touched
    hub = SetupState.starting_from(example_config(), NL)
    hub.done.add("house")
    assert hub.touched


# ---- typical values --------------------------------------------------------------------------------------------


def test_giving_a_value_and_saying_i_dont_know_move_keys_in_and_out_in_a_fixed_order():
    state = SetupState.starting_from(example_config(), NL)
    state.use_typical("charger.max_amps", "panels.kwp")
    assert state.config.setup.typical == ("panels.kwp", "charger.max_amps")  # the order of TYPICAL_KEYS, not of asking
    state.use_typical("panels.kwp")  # twice changes nothing
    assert state.config.setup.typical == ("panels.kwp", "charger.max_amps")
    state.give_value("panels.kwp", "house.baseload_kw")  # one that was not typical is fine
    assert state.config.setup.typical == ("charger.max_amps",)


def test_only_known_keys_can_be_typical():
    state = SetupState.starting_from(example_config(), NL)
    state.use_typical("latitude")
    assert state.config.setup.typical == ()  # not a setting a person can leave to a typical value


# ---- the result ---------------------------------------------------------------------------------------------


def test_the_result_is_checked_like_settings_checks_it():
    assert SetupState.starting_from(example_config(), NL).result().latitude == 52.37
    fresh = SetupState.starting_from(Config(), NL)
    assert not fresh.has_location
    with pytest.raises(ConfigError, match="location"):
        fresh.result()
    broken = SetupState.starting_from(example_config(), NL)
    broken.config = replace(broken.config, charger=replace(broken.config.charger, phases=2))
    with pytest.raises(ConfigError, match="phases"):
        broken.result()


# ---- the recap ---------------------------------------------------------------------------------------------


def lines(state):
    return {g.key: {line.label: line for line in g.lines} for g in build_recap(state)}


def test_the_recap_has_the_six_groups_each_with_the_page_that_changes_it():
    groups = build_recap(SetupState.starting_from(example_config(), NL))
    assert [(g.key, g.page) for g in groups] == [
        ("place", "location"), ("panels", "solar"), ("car", "car"), ("charger", "car"), ("house", "house"), ("away", "away"),
    ]
    assert [g.title for g in groups] == ["Place", "Solar panels", "Car", "Charger", "House", "Car away"]


def test_the_recap_shows_what_was_answered_in_the_chosen_units():
    state = SetupState.starting_from(example_config(), NL)
    shown = lines(state)
    assert shown["panels"]["Size"].value == "4.80 kWp" and shown["panels"]["Direction"].value == "South-east (-45°)"
    assert shown["car"]["Driving per day"].value == "86 km" and shown["car"]["Consumption"].value == "18.0 kWh/100 km"
    assert shown["charger"]["Phases"].value == "1" and shown["charger"]["Highest current"].value == "32 A" and shown["charger"]["Lowest current"].value == "6 A"
    assert shown["house"]["Own use"].value == "0.60 kW" and shown["place"]["Time zone"].value == "Europe/Amsterdam"
    state.config = replace(state.config, interface=replace(state.config.interface, units="imperial"))
    shown = lines(state)
    assert shown["car"]["Driving per day"].value == "53 mi" and shown["car"]["Consumption"].value == "3.45 mi/kWh"


def test_typical_values_are_marked_as_such_and_the_owners_own_are_not():
    state = SetupState.starting_from(typical("panels.kwp", "car.km_per_day", "charger.max_amps"), NL)
    shown = lines(state)
    assert shown["panels"]["Size"].typical and not shown["panels"]["Tilt"].typical
    assert shown["car"]["Driving per day"].typical and not shown["car"]["Usable battery"].typical
    assert shown["charger"]["Highest current"].typical and not shown["charger"]["Lowest current"].typical  # each on its own line
    assert not any(line.typical for line in lines(SetupState.starting_from(example_config(), NL))["panels"].values())


def test_the_place_is_the_name_chosen_or_else_the_coordinates_or_else_not_chosen():
    state = SetupState.starting_from(example_config(), NL)
    assert lines(state)["place"]["Location"].value == "52.3700, 4.9000"
    state.place_label = "Amsterdam, North Holland, The Netherlands"
    assert lines(state)["place"]["Location"].value == "Amsterdam, North Holland, The Netherlands"
    assert lines(SetupState.starting_from(Config(), NL))["place"]["Location"].value == "not chosen yet"


def test_away_times_are_listed_or_the_car_is_home_all_day():
    from datetime import time

    state = SetupState.starting_from(example_config(), NL)
    assert lines(state)["away"]["Away times"].value == "Thu 06:00-19:00"
    state.config = replace(state.config, away_rules=(AwayRule(0, time(8, 0), time(17, 0)), AwayRule(4, time(9, 0), time(12, 30))))
    assert lines(state)["away"]["Away times"].value == "Mon 08:00-17:00, Fri 09:00-12:30"
    state.config = replace(state.config, away_rules=())
    assert lines(state)["away"]["Away times"].value == "the car is treated as home all day"


@pytest.mark.parametrize(
    "azimuth, expected",
    [(0, "South (0°)"), (-45, "South-east (-45°)"), (-90, "East (-90°)"), (-135, "North-east (-135°)"), (180, "North (180°)"),
     (-180, "North (180°)"), (135, "North-west (135°)"), (90, "West (90°)"), (45, "South-west (45°)"),
     (20, "20°"), (-33.5, "-33.5°")],
)
def test_the_eight_compass_directions_are_named_and_anything_else_is_degrees(azimuth, expected):
    assert compass_label(azimuth) == expected


def test_the_wording_of_the_recap_is_translated(tmp_path):
    from car_charge_planner import i18n
    from test_i18n import make_catalogue

    make_catalogue(tmp_path, "xx", {"Solar panels": "Zonnepanelen", ("compass direction", "South-east"): "Zuidoost"})
    i18n.init(["xx"], tmp_path)
    try:
        groups = build_recap(SetupState.starting_from(example_config(), NL))
        assert groups[1].title == "Zonnepanelen" and next(l for l in groups[1].lines if l.label == "Direction").value.startswith("Zuidoost")
    finally:
        i18n.init(languages=[])
