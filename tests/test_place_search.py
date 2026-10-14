"""Place search as a provider ability: Open-Meteo's geocoder, the interface and who has it."""

import json
from dataclasses import replace
from datetime import timedelta
from pathlib import Path
from urllib.parse import parse_qs, urlparse

import pytest

from car_charge_planner.config.model import Forecast
from car_charge_planner.forecast.provider import Place, PlaceResults, Problem
from car_charge_planner.forecast.providers.forecast_solar import ForecastSolar
from car_charge_planner.forecast.providers.open_meteo import OpenMeteo
from car_charge_planner.forecast.providers.open_meteo_geocoding import OpenMeteoGeocoder
from car_charge_planner.forecast.registry import ProviderRegistry
from car_charge_planner.forecast.selection import place_search
from car_charge_planner.forecast.transport import Reply
from fake_provider import FakeProvider
from provider_kit import PlaceCase, PlaceSearchConformance, ReplayTransport

FIXTURES = Path(__file__).parent / "fixtures"
AMSTERDAM = (FIXTURES / "open_meteo_geocoding_amsterdam.json").read_bytes()
NOTHING = (FIXTURES / "open_meteo_geocoding_none.json").read_bytes()


class TestOpenMeteoGeocoderConformance(PlaceSearchConformance):
    @pytest.fixture
    def place_case(self):
        return PlaceCase(
            make=OpenMeteoGeocoder,
            ok=[("/v1/search", Reply(200, AMSTERDAM))],
            empty=[("/v1/search", Reply(200, NOTHING))],
            server_error=[("", Reply(503))],
            bad_body=[("", Reply(200, b"<html>maintenance</html>"))],
            rate_limited=[("", Reply(429, b"{}", {"Retry-After": "60"}))],
            retry_after=timedelta(seconds=60),
        )


def search(text="Amsterdam", language="en", body=AMSTERDAM, status=200):
    transport = ReplayTransport([("", Reply(status, body))])
    return OpenMeteoGeocoder(transport)(text, language), transport


def test_the_recorded_answer_gives_places_in_the_order_the_service_ranks_them():
    results, _ = search()
    labels = [p.label for p in results.places]
    assert labels[0] == "Amsterdam, North Holland, The Netherlands" and len(labels) == 8
    first = results.places[0]
    assert (first.latitude, first.longitude, first.timezone) == (52.37403, 4.88969, "Europe/Amsterdam")
    assert labels[4] == "eMvelo, Mpumalanga, South Africa"


def test_two_places_that_read_alike_can_be_told_apart_by_their_finer_area():
    places = search()[0].places
    ohio = [p for p in places if p.region == "Ohio"]
    assert len(ohio) == 2 and ohio[0].label == ohio[1].label
    assert {p.detailed_label for p in ohio} == {"Amsterdam, Jefferson, Ohio, United States", "Amsterdam, Licking, Ohio, United States"}


def test_a_search_that_matches_nothing_has_no_places_and_no_problem():
    results, _ = search("Zzzzzzzzqx", body=NOTHING)
    assert results.places == () and results.ok and results.no_results


def test_the_request_names_the_text_the_count_the_language_and_a_short_timeout():
    _, transport = search("  New   York ", "nl_NL")
    request = transport.requests[0]
    parsed = urlparse(request.url)
    assert f"{parsed.scheme}://{parsed.netloc}{parsed.path}" == "https://geocoding-api.open-meteo.com/v1/search"
    assert {k: v[0] for k, v in parse_qs(parsed.query).items()} == {"name": "New York", "count": "8", "language": "nl", "format": "json"}
    assert request.timeout_s == 6


def test_a_missing_language_means_english():
    assert parse_qs(urlparse(search(language="")[1].requests[0].url).query)["language"] == ["en"]


@pytest.mark.parametrize("text", ["", " ", "a", "  x  "])
def test_fewer_than_two_characters_is_not_searched(text):
    results, transport = search(text)
    assert results.problem is Problem.TOO_SHORT and transport.requests == []


def test_two_characters_are_searched():
    assert search("Ur")[1].requests


def test_results_without_what_a_location_needs_are_left_out():
    rows = [
        {"name": "Good", "latitude": 1.0, "longitude": 2.0, "timezone": "Europe/Amsterdam", "admin1": "Region", "country": "Land"},
        {"name": "No zone", "latitude": 1.0, "longitude": 2.0},
        {"name": "Odd zone", "latitude": 1.0, "longitude": 2.0, "timezone": "Mars/Olympus"},
        {"name": "No latitude", "longitude": 2.0, "timezone": "Europe/Amsterdam"},
        {"name": "Off the map", "latitude": 95.0, "longitude": 2.0, "timezone": "Europe/Amsterdam"},
        {"name": "", "latitude": 1.0, "longitude": 2.0, "timezone": "Europe/Amsterdam"},
        {"name": "Text coordinates", "latitude": "north", "longitude": 2.0, "timezone": "Europe/Amsterdam"},
        "not even a row",
    ]
    results, _ = search(body=json.dumps({"results": rows}).encode())
    assert [p.name for p in results.places] == ["Good"] and results.ok


def test_the_country_code_stands_in_for_a_missing_country_name():
    row = {"name": "Place", "latitude": 1.0, "longitude": 2.0, "timezone": "UTC", "country_code": "NL"}
    assert search(body=json.dumps({"results": [row]}).encode())[0].places[0].label == "Place, NL"


@pytest.mark.parametrize(
    "status, body, problem",
    [
        (400, json.dumps({"error": True, "reason": "Parameter count must be between 1 and 100."}).encode(), Problem.BAD_RESPONSE),
        (200, json.dumps({"error": True, "reason": "odd"}).encode(), Problem.BAD_RESPONSE),
        (200, b"[1, 2]", Problem.BAD_RESPONSE),
        (200, b'{"results": 5}', Problem.BAD_RESPONSE),
        (200, b"nope", Problem.BAD_RESPONSE),
        (500, b"", Problem.UNAVAILABLE),
        (429, b"", Problem.RATE_LIMITED),
    ],
)
def test_each_failure_is_a_typed_result_and_never_an_exception(status, body, problem):
    assert search(body=body, status=status)[0].problem is problem


def test_the_retry_time_is_only_given_when_the_service_gives_one():
    assert search(status=429)[0].retry_after is None


@pytest.mark.parametrize(
    "place, label, detailed",
    [
        (Place("Berlin", 52.5, 13.4, "Europe/Berlin", "Berlin", "Deutschland", "Berlin, Stadt"), "Berlin, Deutschland", "Berlin, Berlin, Stadt, Deutschland"),
        (Place("Paris", 48.9, 2.3, "Europe/Paris", "Ile-de-France", "France", "Paris"), "Paris, Ile-de-France, France", "Paris, Ile-de-France, France"),
        (Place("Nowhere", 0.0, 0.0, "UTC"), "Nowhere", "Nowhere"),
    ],
)
def test_the_label_is_name_region_country_without_repeating_the_name(place, label, detailed):
    assert place.label == label and place.detailed_label == detailed


def test_open_meteo_has_a_place_search_and_forecast_solar_says_not_available():
    assert isinstance(OpenMeteo().search_places, OpenMeteoGeocoder)
    assert ForecastSolar().search_places is None


def test_the_open_meteo_provider_searches_through_its_own_transport():
    transport = ReplayTransport([("", Reply(200, AMSTERDAM))])
    results = OpenMeteo(transport).search_places("Amsterdam")
    assert results.places and len(transport.requests) == 1


def result(*names):
    return PlaceResults(places=tuple(Place(n, 1.0, 2.0, "UTC") for n in names))


def selection(cfg, primary, fallback="", **abilities):
    reg = ProviderRegistry()
    with_search = FakeProvider(id="finder", name="Finder", search_places=lambda text, language="en": result("Found"))
    without = FakeProvider(id="plain", name="Plain")
    reg.register(with_search)
    reg.register(without)
    return place_search(replace(cfg, forecast=Forecast(primary, fallback, {})), reg)


def test_the_primary_providers_search_is_the_one_used(cfg):
    found = selection(cfg, "finder")
    assert found is not None and found[0].id == "finder" and found[1]("anything").places[0].name == "Found"


def test_a_primary_without_a_search_means_coordinates_only(cfg):
    assert selection(cfg, "plain") is None


def test_the_fallback_is_not_asked_for_places(cfg):
    assert selection(cfg, "plain", fallback="finder") is None


def test_an_unknown_primary_means_coordinates_only(cfg):
    assert selection(cfg, "solcast") is None


def test_the_built_in_choices(cfg):
    open_meteo = place_search(replace(cfg, forecast=Forecast("open-meteo", "", {})))
    assert open_meteo is not None and open_meteo[0].name == "Open-Meteo"
    assert place_search(replace(cfg, forecast=Forecast("forecast-solar", "open-meteo", {}))) is None
