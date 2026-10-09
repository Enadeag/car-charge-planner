"""The Location page of the set-up wizard: search, pick, coordinates, time zone."""

import os

os.environ.setdefault("QT_QPA_PLATFORM", "offscreen")

import threading
from dataclasses import replace
from pathlib import Path

import pytest
from PySide6.QtCore import Qt
from PySide6.QtTest import QTest
from PySide6.QtWidgets import QLabel

from car_charge_planner.forecast.check import Attempt
from car_charge_planner.forecast.provider import Place, PlaceResults, Problem
from car_charge_planner.forecast.providers.open_meteo import OpenMeteo
from car_charge_planner.forecast.registry import ProviderRegistry
from car_charge_planner.forecast.transport import Reply
from car_charge_planner.planner.model import Config, Forecast, load_config
from car_charge_planner.setup.wizard import SetupWizard
from conftest import example_config
from fake_provider import FakeProvider
from provider_kit import ReplayTransport

FIXTURES = Path(__file__).parent / "fixtures"
AMSTERDAM = (FIXTURES / "open_meteo_geocoding_amsterdam.json").read_bytes()
UTRECHT = Place("Utrecht", 52.09, 5.12, "Europe/Amsterdam", "Utrecht", "The Netherlands", "Utrecht")
PARIS = Place("Paris", 48.85, 2.35, "Europe/Paris", "Ile-de-France", "France", "Paris")
OHIO_A = Place("Amsterdam", 40.47, -80.92, "America/New_York", "Ohio", "United States", "Jefferson")
OHIO_B = Place("Amsterdam", 39.96, -82.38, "America/New_York", "Ohio", "United States", "Licking")


def sub(tmp_path, name):
    folder = tmp_path / name
    folder.mkdir()
    return folder


class Finder:
    """A scripted place search: what it was asked, and what it answers."""

    def __init__(self, answer=None):
        self.answer = answer or PlaceResults(places=(UTRECHT, PARIS))
        self.calls = []

    def __call__(self, text, language="en"):
        self.calls.append((text, language))
        return self.answer


class Rig:
    def __init__(self, tmp_path, cfg=None, finder=None, provider_id="finder", has_search=True, registry=None):
        self.finder = finder or Finder()
        if registry is None:
            registry = ProviderRegistry()
            registry.register(FakeProvider(id="finder", name="Finder", search_places=self.finder if has_search else None))
            registry.register(FakeProvider(id="plain", name="Plain"))
        base = cfg if cfg is not None else Config()
        base = replace(base, forecast=Forecast(provider_id, "", {}))
        self.file = tmp_path / "config.toml"
        self.wizard = SetupWizard(base, self.file, lambda c: None, registry=registry, check=lambda c: Attempt(True, 2, "OK"), environ={"LANG": "nl_NL"}, qt_locale_name="")
        self.wizard.confirm_leave = lambda: True
        self.wizard.show()
        self.wizard.navigate("location")
        self.page = self.wizard.pages["location"]
        self.page.timer.setInterval(20)

    def type(self, text, wait=True):
        self.page.search.setText(text)
        if wait:
            self.search_done()

    def search_done(self):
        QTest.qWait(80)  # the pause, then the worker
        for thread in list(self.page._threads):
            thread.join(5)
        QTest.qWait(40)

    def labels(self):
        return [self.page.results.item(i).text() for i in range(self.page.results.count())]

    def pick(self, row):
        self.page.results.setCurrentRow(row)
        self.page.results.itemClicked.emit(self.page.results.item(row))


@pytest.fixture
def rig(tmp_path, qapp):
    return Rig(tmp_path)


def texts(widget):
    return [l.text() for l in widget.findChildren(QLabel)]


def test_the_page_says_the_typed_text_goes_to_the_provider_by_name(rig):
    assert rig.page.privacy.text() == "What you type here is sent to Finder to find the place."
    assert rig.wizard.heading.text() == "Where are your solar panels?"
    assert rig.page.search.placeholderText() == "Town, or postcode"


def test_the_place_data_credit_is_shown_for_open_meteo_and_not_for_others(tmp_path, qapp):
    open_meteo = ProviderRegistry()
    open_meteo.register(OpenMeteo(ReplayTransport([("", Reply(200, AMSTERDAM))])))
    shown = Rig(tmp_path, registry=open_meteo, provider_id="open-meteo").page
    assert shown.credit.text() == "Place data: GeoNames, via Open-Meteo.com" and not shown.credit.isHidden()
    other = Rig(sub(tmp_path, "other")).page
    assert other.credit.isHidden()


def test_without_a_place_search_the_page_says_why_and_asks_for_coordinates(tmp_path, qapp):
    rig = Rig(tmp_path, provider_id="plain")
    page = rig.page
    assert page.privacy.text() == "Plain has no place search, so enter the coordinates of your panels instead."
    assert page.search_box.isHidden() and not page.coordinates_box.isHidden() and page.coordinates_toggle.isHidden()


def test_a_provider_that_is_not_known_asks_for_coordinates_too(tmp_path, qapp):
    page = Rig(tmp_path, provider_id="solcast").page
    assert page.search_box.isHidden() and "forecast service has no place search" in page.privacy.text()


def test_a_search_is_sent_after_a_pause_and_only_for_the_final_text(rig):
    for text in ("U", "Ut", "Utr", "Utre"):
        rig.page.search.setText(text)
    assert rig.finder.calls == []  # still typing
    rig.search_done()
    assert rig.finder.calls == [("Utre", "en")]


def test_the_search_runs_off_the_window_thread(tmp_path, qapp):
    seen = []

    def finder(text, language="en"):
        seen.append(threading.current_thread())
        return PlaceResults(places=(UTRECHT,))

    rig = Rig(tmp_path, finder=finder)
    rig.type("Utrecht")
    assert seen and seen[0] is not threading.main_thread()


def test_pressing_enter_searches_at_once(rig):
    rig.page.timer.setInterval(10_000)
    rig.page.search.setText("Utrecht")
    QTest.keyClick(rig.page.search, Qt.Key.Key_Return)
    rig.search_done()
    assert rig.finder.calls == [("Utrecht", "en")]


def test_the_search_is_in_the_apps_language(tmp_path, qapp):
    from car_charge_planner import i18n
    from test_i18n import make_catalogue

    make_catalogue(tmp_path, "nl", {})
    i18n.init(["nl"], tmp_path)
    try:
        rig = Rig(sub(tmp_path, "w"))
        rig.type("Utrecht")
        assert rig.finder.calls == [("Utrecht", "nl")]
    finally:
        i18n.init(languages=[])


@pytest.mark.parametrize("text", ["", "a", " a "])
def test_too_little_to_search_sends_nothing_and_says_what_is_needed(rig, text):
    rig.type(text)
    assert rig.finder.calls == []
    assert rig.page.status.text() == ("Type at least two characters." if text.strip() else "")


def test_results_are_listed_as_name_region_country(rig):
    rig.type("Utrecht")
    assert rig.labels() == ["Utrecht, The Netherlands", "Paris, Ile-de-France, France"]
    assert rig.page.status.text() == "2 places found. Pick yours."


def test_one_result_is_said_in_the_singular(tmp_path, qapp):
    rig = Rig(tmp_path, finder=Finder(PlaceResults(places=(UTRECHT,))))
    rig.type("Utrecht")
    assert rig.page.status.text() == "1 place found. Pick yours."


def test_two_results_that_read_alike_are_told_apart_by_their_finer_area(tmp_path, qapp):
    rig = Rig(tmp_path, finder=Finder(PlaceResults(places=(OHIO_A, UTRECHT, OHIO_B))))
    rig.type("Amsterdam")
    assert rig.labels() == ["Amsterdam, Jefferson, Ohio, United States", "Utrecht, The Netherlands", "Amsterdam, Licking, Ohio, United States"]


def test_nothing_found_says_so_and_offers_the_way_out(tmp_path, qapp):
    rig = Rig(tmp_path, finder=Finder(PlaceResults()))
    rig.type("Zzzzzz")
    assert rig.labels() == [] and "No place found" in rig.page.status.text() and "coordinates" in rig.page.status.text()


@pytest.mark.parametrize(
    "problem, wanted",
    [
        (Problem.UNAVAILABLE, "Could not reach the search service"),
        (Problem.RATE_LIMITED, "The search service is busy"),
        (Problem.BAD_RESPONSE, "gave an answer the app does not understand"),
    ],
)
def test_each_problem_is_said_in_plain_words_and_the_coordinates_are_put_in_sight(tmp_path, qapp, problem, wanted):
    rig = Rig(tmp_path, finder=Finder(PlaceResults(problem=problem)))
    rig.type("Utrecht")
    assert wanted in rig.page.status.text() and "coordinates" in rig.page.status.text()
    assert not rig.page.coordinates_box.isHidden() and rig.labels() == []


def test_a_late_answer_to_an_older_search_is_ignored(rig):
    rig.type("Utrecht")
    old = rig.page._serial
    rig.type("Paris")  # a newer search
    rig.page._show_results(old, PlaceResults(places=(OHIO_A,)))  # the older answer arrives late
    assert "Amsterdam" not in " ".join(rig.labels())


def test_the_real_open_meteo_search_works_through_the_page(tmp_path, qapp):
    registry = ProviderRegistry()
    transport = ReplayTransport([("", Reply(200, AMSTERDAM))])
    registry.register(OpenMeteo(transport))
    rig = Rig(tmp_path, registry=registry, provider_id="open-meteo")
    rig.type("Amsterdam")
    assert len(rig.labels()) == 8 and rig.labels()[0] == "Amsterdam, North Holland, The Netherlands"
    assert rig.labels().count("Amsterdam, Ohio, United States") == 0  # the two Ohio ones are told apart
    rig.pick(0)
    assert rig.wizard.state.config.timezone == "Europe/Amsterdam" and rig.wizard.state.config.latitude == 52.37403


def test_nothing_is_chosen_at_first_and_next_waits(rig):
    assert rig.page.chosen.text() == "No place chosen yet." and not rig.wizard.next_button.isEnabled()


def test_picking_a_result_sets_the_place_the_coordinates_and_the_zone(rig):
    rig.type("Utrecht")
    rig.pick(1)
    cfg = rig.wizard.state.config
    assert (cfg.latitude, cfg.longitude, cfg.timezone) == (48.85, 2.35, "Europe/Paris")
    assert rig.wizard.state.place_label == "Paris, Ile-de-France, France"
    assert rig.page.chosen.text() == "Chosen: Paris, Ile-de-France, France (Europe/Paris)"
    assert rig.page.latitude.value() == 48.85 and rig.page.timezone.currentText() == "Europe/Paris"
    assert rig.wizard.next_button.isEnabled()


def test_the_place_picked_is_what_the_recap_and_the_written_config_say(rig):
    rig.type("Utrecht")
    rig.pick(0)
    rig.wizard.navigate("finish")
    assert "Utrecht, The Netherlands" in texts(rig.wizard.pages["finish"])
    for thread in list(rig.wizard.pages["finish"]._threads):
        thread.join(5)
    assert rig.wizard.finish()
    written = load_config(rig.file)
    assert (written.latitude, written.longitude, written.timezone) == (52.09, 5.12, "Europe/Amsterdam")


def test_the_coordinates_are_behind_a_button_with_a_hint_on_reading_them(rig):
    assert rig.page.coordinates_box.isHidden()
    rig.page.coordinates_toggle.click()
    assert not rig.page.coordinates_box.isHidden()
    assert any("In a map app, press and hold" in t and "0 and 0 together mean not set" in t for t in texts(rig.page))


def test_typed_coordinates_make_a_location_with_the_computers_time_zone(rig, monkeypatch):
    monkeypatch.setattr("car_charge_planner.setup.location.system_timezone", lambda: "Europe/Lisbon")
    rig.page.latitude.setValue(38.72)
    rig.page.longitude.setValue(-9.14)
    cfg = rig.wizard.state.config
    assert (cfg.latitude, cfg.longitude, cfg.timezone) == (38.72, -9.14, "Europe/Lisbon")
    assert rig.wizard.state.place_label == "" and rig.page.chosen.text() == "Chosen: 38.7200, -9.1400 (Europe/Lisbon)"
    assert rig.wizard.next_button.isEnabled()


def test_zero_and_zero_is_not_a_location(rig):
    rig.page.latitude.setValue(10.0)
    assert rig.wizard.next_button.isEnabled()
    rig.page.latitude.setValue(0.0)
    assert not rig.wizard.next_button.isEnabled() and rig.page.chosen.text() == "No place chosen yet."
    rig.page.longitude.setValue(0.5)
    assert rig.wizard.next_button.isEnabled()  # 0 and 0.5 is a place on the map


def test_the_coordinates_cannot_leave_the_map(rig):
    rig.page.latitude.setValue(120)
    rig.page.longitude.setValue(-400)
    assert rig.page.latitude.value() == 90 and rig.page.longitude.value() == -180


def test_typing_coordinates_after_a_search_result_forgets_its_name(rig):
    rig.type("Utrecht")
    rig.pick(0)
    rig.page.latitude.setValue(52.1)
    assert rig.wizard.state.place_label == "" and "52.1000" in rig.page.chosen.text()


def test_the_time_zone_is_under_advanced_and_can_be_changed(rig):
    rig.type("Utrecht")
    rig.pick(0)
    assert rig.page.advanced_box.isHidden()
    rig.page.advanced_toggle.click()
    assert not rig.page.advanced_box.isHidden() and rig.page.timezone.currentText() == "Europe/Amsterdam"
    rig.page.timezone.setCurrentText("Europe/London")
    assert rig.wizard.state.config.timezone == "Europe/London"
    assert rig.page.chosen.text() == "Chosen: Utrecht, The Netherlands (Europe/London)"


def test_a_time_zone_the_app_does_not_know_is_refused_and_put_in_sight(rig):
    rig.page.latitude.setValue(10.0)
    rig.page.timezone.setCurrentText("Mars/Olympus")
    assert "not a time zone the app knows" in rig.page.zone_error.text()
    assert not rig.wizard.next_button.isEnabled() and not rig.page.advanced_box.isHidden()
    rig.page.timezone.setCurrentText("Europe/Paris")
    assert rig.page.zone_error.text() == "" and rig.wizard.next_button.isEnabled()


def test_a_time_zone_chosen_by_hand_survives_typing_coordinates(rig, monkeypatch):
    monkeypatch.setattr("car_charge_planner.setup.location.system_timezone", lambda: "Europe/Lisbon")
    rig.page.timezone.setCurrentText("Asia/Tokyo")
    rig.page.latitude.setValue(35.68)
    rig.page.longitude.setValue(139.69)
    assert rig.wizard.state.config.timezone == "Asia/Tokyo"


def test_running_set_up_again_shows_the_place_it_has_and_lets_you_go_on(tmp_path, qapp):
    rig = Rig(tmp_path, cfg=example_config())
    assert rig.page.chosen.text() == "Chosen: 52.3700, 4.9000 (Europe/Amsterdam)"
    assert not rig.page.coordinates_box.isHidden() and rig.wizard.next_button.isEnabled()
    assert rig.page.latitude.value() == 52.37


def test_going_back_to_the_page_shows_what_was_chosen(rig):
    rig.type("Utrecht")
    rig.pick(0)
    rig.wizard.next()
    rig.wizard.back()
    assert rig.page.chosen.text().startswith("Chosen: Utrecht, The Netherlands") and rig.wizard.next_button.isEnabled()
