"""The "What next?" hub, the House page and the Away page of the set-up wizard (ticket S9)."""

import os

os.environ.setdefault("QT_QPA_PLATFORM", "offscreen")

from dataclasses import replace
from datetime import time

import pytest
from PySide6.QtCore import QTime
from PySide6.QtWidgets import QApplication, QLabel

from car_charge_planner.forecast.check import Attempt
from car_charge_planner.planner.model import AwayRule, Config, Setup, load_config
from car_charge_planner.setup.home import (
    HOURS_PER_YEAR,
    MAX_BASELOAD_KW,
    away_problems,
    baseload_from_yearly,
    car_kwh_per_year,
    yearly_from_baseload,
)
from car_charge_planner.setup.recap import build_recap
from car_charge_planner.setup.wizard import SetupWizard
from conftest import example_config

NL = {"LANG": "nl_NL.UTF-8"}


@pytest.fixture
def qapp():
    return QApplication.instance() or QApplication([])


# ---- the helpers --------------------------------------------------------------------------------------------


def test_a_year_has_8760_hours():
    assert HOURS_PER_YEAR == 8760


@pytest.mark.parametrize("yearly, kw", [(4380, 0.5), (8760, 1.0), (3000, 0.3425), (0, 0.0)])
def test_a_years_use_is_an_average_power(yearly, kw):
    assert baseload_from_yearly(yearly) == pytest.approx(kw, abs=1e-4)
    assert yearly_from_baseload(kw) == pytest.approx(yearly, abs=1)


def test_the_cars_share_is_taken_off_when_the_bill_includes_it():
    car = car_kwh_per_year(40, 18)  # 40 km a day at 18 kWh per 100 km
    assert car == pytest.approx(2628)
    assert baseload_from_yearly(5000, car) == pytest.approx((5000 - 2628) / 8760)


def test_a_bill_smaller_than_the_cars_share_gives_nothing_not_less_than_nothing():
    assert baseload_from_yearly(1000, 2628) == 0.0


def test_the_away_rules_are_checked_in_settings_words(cfg):
    assert away_problems(cfg) == []
    bad = replace(cfg, away_rules=(AwayRule(0, time(17, 0), time(8, 0)),))
    assert away_problems(bad) == ["[away] each rule must end after it starts on the same day (no overnight rules)"]
    assert away_problems(replace(bad, car=replace(bad.car, minimum_pct=90, target_pct=50))) == away_problems(bad)  # only the away ones


# ---- the pages --------------------------------------------------------------------------------------------


class Rig:
    def __init__(self, tmp_path, cfg=None):
        self.file = tmp_path / "config.toml"
        self.wizard = SetupWizard(cfg if cfg is not None else Config(), self.file, lambda c: None, check=lambda c: Attempt(True, 2, "OK"), environ=NL, qt_locale_name="")
        self.wizard.confirm_leave = lambda: True
        self.wizard.show()
        self.wizard.navigate("next")
        self.hub = self.wizard.pages["next"]

    @property
    def state(self):
        return self.wizard.state

    @property
    def cfg(self):
        return self.wizard.state.config


@pytest.fixture
def rig(tmp_path, qapp):
    return Rig(tmp_path)


def texts(widget):
    return [l.text() for l in widget.findChildren(QLabel)]


# the hub

def test_the_hub_has_two_cards_each_saying_what_it_does_and_what_skipping_does(rig):
    shown = texts(rig.hub)
    assert rig.wizard.heading.text() == "What next?" and rig.wizard.step.text() == "Step 5 of 6"
    assert "House electricity" in shown and "When is the car away?" in shown
    assert "Tells the plan how much power the house itself uses, so it knows what is left for the car." in shown
    assert "If skipped: a typical value is used." in shown
    assert "Stops the plan recommending charging while the car is out, for example at work." in shown
    assert "If skipped: the car is treated as home all day." in shown


def test_the_away_card_has_the_line_for_commuters_and_the_house_card_does_not(rig):
    assert "If you drive to work, set this." in texts(rig.hub.away)
    assert "If you drive to work, set this." not in texts(rig.hub.house)


def test_nothing_is_done_at_first_and_finish_is_always_there(rig):
    assert rig.hub.house.tick.text() == "" and rig.hub.away.tick.text() == ""
    assert rig.wizard.next_button.isEnabled() and rig.wizard.next_button.text() == "Review and finish"
    rig.wizard.next()
    assert rig.wizard.current == "finish"  # skipping both is fine


def test_each_card_opens_its_page_and_done_returns_to_the_hub(rig):
    rig.hub.house.button.click()
    assert rig.wizard.current == "house" and rig.wizard.step.text() == "" and rig.wizard.next_button.text() == "Done"
    rig.wizard.next()
    assert rig.wizard.current == "next"
    rig.hub.away.button.click()
    assert rig.wizard.current == "away" and rig.wizard.heading.text() == "When is the car away?"
    rig.wizard.back()
    assert rig.wizard.current == "next"


def test_finish_from_the_hub_is_the_next_page_of_the_flow_not_a_page_opened_from_it(rig):
    rig.hub.house.button.click()
    rig.wizard.next()  # Done
    rig.wizard.next()  # Review and finish
    assert rig.wizard.current == "finish" and rig.wizard.sequence.index("finish") == rig.wizard.sequence.index("next") + 1


# the house

def open_house(rig):
    rig.hub.house.button.click()
    return rig.wizard.pages["house"]


def test_the_yearly_use_becomes_an_average_power_and_a_value_of_the_owners_own(rig):
    page = open_house(rig)
    page.yearly.setValue(4380)
    assert rig.cfg.house.baseload_kw == pytest.approx(0.5) and "house.baseload_kw" not in rig.cfg.setup.typical
    assert page.result.text() == "= an average of 0.50 kW for the house"
    page.yearly.setValue(5256)
    assert rig.cfg.house.baseload_kw == pytest.approx(0.6)


def test_a_bill_that_includes_the_car_has_the_cars_share_taken_off(rig):
    page = open_house(rig)
    page.yearly.setValue(5000)
    page.includes_car.setChecked(True)
    car = car_kwh_per_year(rig.cfg.car.km_per_day, rig.cfg.car.consumption_kwh_per_100km)
    assert rig.cfg.house.baseload_kw == pytest.approx((5000 - car) / 8760, abs=1e-4)
    assert "of the car taken off" in page.result.text()
    page.includes_car.setChecked(False)
    assert rig.cfg.house.baseload_kw == pytest.approx(5000 / 8760, abs=1e-4)


def test_an_impossible_number_is_said_and_stops_done_and_is_not_stored_above_the_limit(rig):
    page = open_house(rig)
    page.yearly.setValue(50_000)  # 5.7 kW: a big house, but possible
    assert page.warning.text() == "" and rig.wizard.next_button.isEnabled()
    page.yearly.setValue(190_000)  # 21.7 kW on average: not something a house does
    assert "more than the plan can use (20 kW)" in page.warning.text() and not rig.wizard.next_button.isEnabled()
    assert rig.cfg.house.baseload_kw <= MAX_BASELOAD_KW
    page.yearly.setValue(10_000)
    assert page.warning.text() == "" and rig.wizard.next_button.isEnabled()


def test_i_dont_know_keeps_the_typical_value_and_counts_as_an_answer(rig):
    page = open_house(rig)
    page.dont_know.click()
    assert rig.cfg.house.baseload_kw == 0.5 and "house.baseload_kw" in rig.cfg.setup.typical and "house" in rig.state.done
    assert "a typical value" in page.current.text()


def test_i_dont_know_after_giving_a_value_goes_back_to_the_typical_one(tmp_path, qapp):
    rig = Rig(tmp_path, cfg=example_config())
    page = open_house(rig)
    page.yearly.setValue(7000)
    assert "house.baseload_kw" not in rig.cfg.setup.typical
    page.dont_know.click()
    assert rig.cfg.house.baseload_kw == 0.5 and "house.baseload_kw" in rig.cfg.setup.typical


def test_just_looking_at_the_house_page_is_not_an_answer(rig):
    before = rig.cfg
    open_house(rig)
    rig.wizard.back()
    assert rig.cfg == before and "house" not in rig.state.done and rig.hub.house.tick.text() == ""


def test_the_house_card_ticks_and_says_what_was_set(rig):
    page = open_house(rig)
    page.yearly.setValue(4380)
    rig.wizard.next()
    assert rig.hub.house.tick.text() == "✓ Done" and rig.hub.house.status.text() == "0.50 kW"
    page = open_house(rig)
    page.dont_know.click()
    rig.wizard.next()
    assert rig.hub.house.status.text() == "0.50 kW, a typical value"


# the away page

def open_away(rig):
    rig.hub.away.button.click()
    return rig.wizard.pages["away"]


def test_the_car_is_usually_home_is_an_answer_and_means_no_rules(tmp_path, qapp):
    rig = Rig(tmp_path, cfg=example_config())  # it has a Thursday rule
    page = open_away(rig)
    assert page.some_days.isChecked() and len(page.rows) == 1
    page.at_home.click()
    assert rig.cfg.away_rules == () and page.rows == [] and "away" in rig.state.done and page.rules_box.isHidden()


def test_a_fresh_install_has_no_choice_made_yet(rig):
    page = open_away(rig)
    assert not page.at_home.isChecked() and not page.some_days.isChecked() and page.rules_box.isHidden()
    assert rig.cfg.away_rules == () and "away" not in rig.state.done


def test_choosing_away_times_starts_with_a_row_to_edit(rig):
    page = open_away(rig)
    page.some_days.click()
    assert len(page.rows) == 1 and rig.cfg.away_rules == (AwayRule(0, time(8, 0), time(17, 0)),) and "away" in rig.state.done


def test_rows_can_be_added_edited_and_removed(rig):
    page = open_away(rig)
    page.some_days.click()
    page.add_button.click()
    assert len(page.rows) == 2
    page.rows[1].weekday.setCurrentIndex(3)
    page.rows[1].start.setTime(QTime(6, 0))
    page.rows[1].end.setTime(QTime(19, 0))
    assert rig.cfg.away_rules[1] == AwayRule(3, time(6, 0), time(19, 0))
    page.rows[0].remove_button.click()
    assert rig.cfg.away_rules == (AwayRule(3, time(6, 0), time(19, 0)),)


def test_a_time_that_ends_before_it_starts_is_said_in_settings_words_and_stops_done(rig):
    page = open_away(rig)
    page.some_days.click()
    page.rows[0].end.setTime(QTime(7, 0))  # the start is 08:00
    assert "[away] each rule must end after it starts on the same day (no overnight rules)" in page.errors.text()
    assert not rig.wizard.next_button.isEnabled()
    page.rows[0].end.setTime(QTime(18, 0))
    assert page.errors.text() == "" and rig.wizard.next_button.isEnabled()


def test_the_away_card_ticks_and_lists_the_times(rig):
    page = open_away(rig)
    page.some_days.click()
    page.rows[0].weekday.setCurrentIndex(3)
    rig.wizard.next()
    assert rig.hub.away.tick.text() == "✓ Done" and rig.hub.away.status.text() == "Thu 08:00-17:00"
    page = open_away(rig)
    page.at_home.click()
    rig.wizard.next()
    assert rig.hub.away.status.text() == "usually home"


def test_going_back_to_the_page_shows_the_rules_again(rig):
    page = open_away(rig)
    page.some_days.click()
    rig.wizard.next()
    page = open_away(rig)
    assert page.some_days.isChecked() and len(page.rows) == 1


# skipping, and the recap

def test_skipped_options_leave_the_typical_house_and_no_away_rules_and_the_recap_says_so(rig):
    groups = {g.key: {line.label: line for line in g.lines} for g in build_recap(rig.state)}
    assert groups["house"]["Own use"].typical and groups["house"]["Own use"].value == "0.50 kW"
    assert groups["away"]["Away times"].value == "the car is treated as home all day"
    assert rig.cfg.away_rules == ()


def test_the_finish_page_links_back_to_house_and_away_now_that_they_exist(rig):
    rig.wizard.navigate("finish")
    links = [l.text().split("href='")[1].split("'")[0] for l in rig.wizard.pages["finish"].findChildren(QLabel) if "href=" in l.text()]
    assert links == ["location", "solar", "car", "car", "house", "away"]
    link = next(l for l in rig.wizard.pages["finish"].findChildren(QLabel) if "href='away'" in l.text())
    link.linkActivated.emit("away")
    assert rig.wizard.current == "away"
    rig.wizard.back()
    assert rig.wizard.current == "finish"


def test_finishing_writes_what_was_answered_on_all_three_pages(tmp_path, qapp):
    rig = Rig(tmp_path, cfg=replace(example_config(), away_rules=(), setup=Setup(("house.baseload_kw",))))
    open_house(rig).yearly.setValue(4380)
    rig.wizard.next()
    page = open_away(rig)
    page.some_days.click()
    page.rows[0].weekday.setCurrentIndex(3)
    rig.wizard.next()
    rig.wizard.navigate("finish")
    for thread in list(rig.wizard.pages["finish"]._threads):
        thread.join(5)
    assert rig.wizard.finish()
    written = load_config(rig.file)
    assert written.house.baseload_kw == pytest.approx(0.5) and written.away_rules == (AwayRule(3, time(8, 0), time(17, 0)),)
    assert "house.baseload_kw" not in written.setup.typical


def test_the_hub_and_its_pages_are_in_the_language(tmp_path, qapp):
    from car_charge_planner import i18n
    from test_i18n import make_catalogue

    make_catalogue(tmp_path, "xx", {"What next?": "Wat nu?", "House electricity": "Huisstroom", "If you drive to work, set this.": "Rijd je naar je werk? Vul dit in."})
    i18n.init(["xx"], tmp_path)
    try:
        (tmp_path / "w").mkdir()
        rig = Rig(tmp_path / "w")
        assert rig.wizard.heading.text() == "Wat nu?" and "Huisstroom" in texts(rig.hub) and "Rijd je naar je werk? Vul dit in." in texts(rig.hub)
    finally:
        i18n.init(languages=[])
