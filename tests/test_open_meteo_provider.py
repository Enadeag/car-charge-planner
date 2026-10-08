"""Open-Meteo as a provider (ticket P3): the conformance kit with the recorded answer, plus what is specific to it."""

import json
from datetime import UTC, datetime, timedelta
from pathlib import Path
from urllib.parse import parse_qs, urlparse

import pytest

from car_charge_planner.forecast.provider import BadResponse, NotConfigured, RateLimited, SystemDescription, Unavailable
from car_charge_planner.forecast.providers.open_meteo import OpenMeteo, power_kw
from car_charge_planner.forecast.registry import registry
from car_charge_planner.forecast.transport import Reply
from provider_kit import EXAMPLE_SYSTEM, ProviderCase, ProviderConformance, ReplayTransport

RECORDED = (Path(__file__).parent / "fixtures" / "open_meteo_3days.json").read_bytes()
NOW = datetime(2026, 10, 6, 7, 0, tzinfo=UTC)
SECRET = "OM-SECRET-KEY-9876"


class TestOpenMeteoConformance(ProviderConformance):
    @pytest.fixture
    def case(self):
        return ProviderCase(
            make=OpenMeteo,
            ok=[("/v1/forecast", Reply(200, RECORDED))],
            server_error=[("", Reply(503, b"Service Unavailable"))],
            bad_body=[("", Reply(200, b"<html>maintenance</html>"))],
            empty_body=[("", Reply(200, b"{}"))],
            rate_limited=[("", Reply(429, json.dumps({"error": True, "reason": "limit"}).encode(), {"Retry-After": "90"}))],
            retry_after=timedelta(seconds=90),
            settings={},
            secrets={"api_key": SECRET},
            now=NOW,
        )


def fetch(routes=None, settings=None, system=EXAMPLE_SYSTEM):
    transport = ReplayTransport(routes or [("", Reply(200, RECORDED))])
    provider = OpenMeteo(transport)
    return provider.fetch(system, settings or {}, NOW), transport


# ---- the answer ----------------------------------------------------------------------------------------------


def test_the_value_stamped_at_nine_covers_the_hour_before_it_and_becomes_power():
    steps, _ = fetch()
    assert len(steps) == 72 and all(s.end - s.start == timedelta(hours=1) for s in steps)
    nine = next(s for s in steps if s.end.hour == 9 and s.end.day == 6)
    assert nine.start.hour == 8 and nine.power_kw == pytest.approx(4.8 * 0.1566 * 0.86)  # 156.6 W/m2
    assert steps[0].end.utcoffset() == timedelta(hours=2)  # CEST: timezone-aware, in the system's zone


def test_losses_and_size_are_applied_by_the_provider():
    small, _ = fetch(system=SystemDescription(52.37, 4.9, 2.4, 35, -45, 0.14, "Europe/Amsterdam"))
    lossless, _ = fetch(system=SystemDescription(52.37, 4.9, 4.8, 35, -45, 0.0, "Europe/Amsterdam"))
    base, _ = fetch()
    assert max(s.power_kw for s in small) == pytest.approx(max(s.power_kw for s in base) / 2)
    assert max(s.power_kw for s in lossless) == pytest.approx(max(s.power_kw for s in base) / 0.86)
    assert power_kw(1000, 4.8, 0.14) == pytest.approx(4.128)


def test_a_null_at_the_edge_of_the_horizon_is_no_power():
    raw = {"hourly": {"time": [1791237600], "global_tilted_irradiance": [None]}}
    steps, _ = fetch([("", Reply(200, json.dumps(raw).encode()))])
    assert steps[0].power_kw == 0.0


@pytest.mark.parametrize(
    "raw",
    [
        {"hourly": {"time": [1791237600, 1791241200], "global_tilted_irradiance": [1.0]}},  # lengths differ
        {"hourly": {"time": ["soon"], "global_tilted_irradiance": [1.0]}},
        {"hourly": {"time": [1791237600], "global_tilted_irradiance": ["lots"]}},
        {"hourly": {"time": [1791241200, 1791237600], "global_tilted_irradiance": [1.0, 1.0]}},  # out of order
        {"hourly": {"time": [1791237600], "global_tilted_irradiance": [-50.0]}},  # negative power
        {"hourly": {"time": [10**30], "global_tilted_irradiance": [1.0]}},
        [1, 2, 3],
        {"error": True, "reason": "Latitude must be in range of -90 to 90°."},
    ],
)
def test_an_answer_that_is_not_as_documented_is_a_bad_response(raw):
    with pytest.raises(BadResponse):
        fetch([("", Reply(200, json.dumps(raw).encode()))])


def test_the_reason_the_server_gives_for_a_refusal_is_kept():
    body = json.dumps({"error": True, "reason": "Latitude must be in range of -90 to 90°."}).encode()
    with pytest.raises(BadResponse, match="Latitude must be"):
        fetch([("", Reply(400, body))])


@pytest.mark.parametrize("status", [401, 403])
def test_a_refused_key_points_at_the_settings(status):
    with pytest.raises(BadResponse, match="API key"):
        fetch([("", Reply(status))])


def test_the_retry_time_is_none_when_the_server_gives_none():
    with pytest.raises(RateLimited) as caught:
        fetch([("", Reply(429))])
    assert caught.value.retry_after is None


def test_a_timezone_the_system_does_not_know_is_a_bad_response():
    odd = SystemDescription(52.37, 4.9, 4.8, 35, -45, 0.14, "Mars/Olympus")
    with pytest.raises(BadResponse):
        fetch(system=odd)


# ---- the request ---------------------------------------------------------------------------------------------


def query(url):
    return {k: v[0] for k, v in parse_qs(urlparse(url).query).items()}


def test_the_request_describes_the_system_and_asks_only_for_irradiance():
    _, transport = fetch()
    request = transport.requests[0]
    parsed = urlparse(request.url)
    assert f"{parsed.scheme}://{parsed.netloc}{parsed.path}" == "https://api.open-meteo.com/v1/forecast"
    assert query(request.url) == {
        "latitude": "52.37", "longitude": "4.9", "hourly": "global_tilted_irradiance", "tilt": "35",
        "azimuth": "-45", "timezone": "Europe/Amsterdam", "forecast_days": "4", "timeformat": "unixtime",
    }  # no cloud cover, no key


def test_the_user_agent_names_the_project_and_its_address():
    _, transport = fetch()
    agent = transport.requests[0].headers["User-Agent"]
    assert "car-charge-planner" in agent and "https://github.com/" in agent


def test_a_server_address_replaces_the_public_one_and_a_key_is_sent_as_apikey():
    settings = {"base_url": "https://customer-api.open-meteo.com/", "api_key": SECRET}
    _, transport = fetch(settings=settings)
    url = transport.requests[0].url
    assert url.startswith("https://customer-api.open-meteo.com/v1/forecast?")
    assert query(url)["apikey"] == SECRET


def test_blank_settings_mean_the_public_service_without_a_key():
    _, transport = fetch(settings={"base_url": "  ", "api_key": ""})
    assert transport.requests[0].url.startswith("https://api.open-meteo.com/")
    assert "apikey" not in query(transport.requests[0].url)


@pytest.mark.parametrize("address", ["http://api.open-meteo.com", "ftp://x", "api.open-meteo.com", "javascript:alert(1)"])
def test_an_address_that_is_not_https_is_refused_before_anything_is_sent(address):
    transport = ReplayTransport([("", Reply(200, RECORDED))])
    with pytest.raises(NotConfigured, match="https"):
        OpenMeteo(transport).fetch(EXAMPLE_SYSTEM, {"base_url": address}, NOW)
    assert transport.requests == []


@pytest.mark.parametrize("address", ["http://localhost:8080", "http://127.0.0.1:8080"])
def test_a_server_of_your_own_on_this_computer_may_use_plain_http(address):
    _, transport = fetch(settings={"base_url": address})
    assert transport.requests[0].url.startswith(address)


def test_the_key_never_appears_in_an_error_message():
    for outcome in (OSError(f"https://x/?apikey={SECRET} refused"), Reply(503), Reply(200, b"nope")):
        route = [("", outcome)]
        with pytest.raises((Unavailable, BadResponse)) as caught:
            fetch(route, settings={"api_key": SECRET})
        assert SECRET not in str(caught.value)


# ---- who it is -----------------------------------------------------------------------------------------------


def test_it_declares_what_the_spec_says():
    provider = OpenMeteo()
    assert provider.id == "open-meteo" and provider.horizon == timedelta(days=4)
    assert provider.min_refresh == timedelta(minutes=60) and provider.max_useful_age == timedelta(hours=12)
    assert provider.attribution == "Weather data: Open-Meteo.com (CC BY 4.0)"
    assert [(s.key, s.secret, s.optional) for s in provider.settings] == [("base_url", False, True), ("api_key", True, True)]
    assert callable(provider.search_places)  # its place search, from ticket S4 and P9


def test_it_is_a_built_in_provider():
    assert registry.get("open-meteo").name == "Open-Meteo"
