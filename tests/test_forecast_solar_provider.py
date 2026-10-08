"""Forecast.Solar as a provider (ticket P4): the conformance kit with the recorded answer, plus what is specific to it."""

import json
from datetime import UTC, datetime, timedelta
from pathlib import Path

import pytest

from car_charge_planner.forecast.provider import BadResponse, RateLimited, SystemDescription, Unavailable, check_steps
from car_charge_planner.forecast.providers.forecast_solar import ForecastSolar
from car_charge_planner.forecast.registry import registry
from car_charge_planner.forecast.transport import Reply
from provider_kit import EXAMPLE_SYSTEM, ProviderCase, ProviderConformance, ReplayTransport

FIXTURES = Path(__file__).parent / "fixtures"
RECORDED = (FIXTURES / "forecast_solar_2days.json").read_bytes()
RATE_LIMITED = (FIXTURES / "forecast_solar_429.json").read_bytes()
SPRING = (FIXTURES / "forecast_solar_dst_spring.json").read_bytes()
AUTUMN = (FIXTURES / "forecast_solar_dst_autumn.json").read_bytes()
NOW = datetime(2026, 10, 7, 7, 52, 51, tzinfo=UTC)  # when the answer was recorded
RETRY_AT = "2026-10-07T10:19:59+02:00"  # in the 429 fixture: 08:19:59 UTC
SECRET = "FS-SECRET-KEY-5555"


class TestForecastSolarConformance(ProviderConformance):
    @pytest.fixture
    def case(self):
        return ProviderCase(
            make=ForecastSolar,
            ok=[("estimate/watthours/period", Reply(200, RECORDED))],
            server_error=[("", Reply(502, b"Bad Gateway"))],
            bad_body=[("", Reply(200, b"<html>maintenance</html>"))],
            empty_body=[("", Reply(200, b"{}"))],
            rate_limited=[("", Reply(429, RATE_LIMITED, {"X-Ratelimit-Retry-At": RETRY_AT}))],
            retry_after=timedelta(minutes=27, seconds=8),
            settings={},
            secrets={"api_key": SECRET},
            now=NOW,
        )


def fetch(body=RECORDED, system=EXAMPLE_SYSTEM, settings=None, status=200, headers=None, now=NOW):
    transport = ReplayTransport([("", Reply(status, body, headers or {}))])
    steps = ForecastSolar(transport).fetch(system, settings or {}, now)
    return steps, transport


def local(step):
    return step.start.strftime("%H:%M:%S"), step.end.strftime("%H:%M:%S")


# ---- reading the answer ---------------------------------------------------------------------------------------


def test_each_key_after_the_first_of_a_day_is_a_step_of_the_energy_since_the_key_before():
    steps, _ = fetch()
    assert len(steps) == 26  # two days of 14 keys, the sunrise key of each has nothing before it
    first = steps[0]
    assert local(first) == ("07:51:52", "08:00:00")
    assert first.power_kw == pytest.approx(0.010 / (488 / 3600))  # 10 Wh in 8 min 8 s
    nine = next(s for s in steps if s.end.hour == 9 and s.end.day == 7)
    assert local(nine) == ("08:00:00", "09:00:00") and nine.power_kw == pytest.approx(0.332)
    eleven = next(s for s in steps if s.end.hour == 11 and s.end.day == 7)
    assert eleven.power_kw == pytest.approx(1.574)


def test_the_energy_of_the_steps_is_the_energy_of_the_answer():
    steps, _ = fetch()
    raw = json.loads(RECORDED)["result"]
    days = {}
    for key, wh in raw.items():
        days.setdefault(key[:10], []).append(wh)
    expected = sum(sum(values[1:]) for values in days.values())  # the sunrise key's 0 starts each day
    delivered = sum(s.power_kw * s.hours for s in steps) * 1000
    assert delivered == pytest.approx(expected)


def test_night_gives_no_steps_and_the_steps_satisfy_the_contract():
    steps, _ = fetch()
    assert check_steps(steps) == []
    overnight = [b.start - a.end for a, b in zip(steps, steps[1:], strict=False) if b.start != a.end]
    assert len(overnight) == 1 and overnight[0] > timedelta(hours=10)  # the gap between the two days


def test_steps_are_in_the_system_time_zone_whatever_the_place_says():
    in_utc = SystemDescription(52.37, 4.9, 4.8, 35, -45, 0.14, "UTC")
    steps, _ = fetch(system=in_utc)
    assert steps[0].start.tzinfo.key == "UTC" and local(steps[0]) == ("05:51:52", "06:00:00")  # same instants


def test_the_time_zone_of_the_place_in_the_answer_is_what_the_keys_are_read_in():
    raw = json.loads(RECORDED)
    raw["message"]["info"]["timezone"] = "America/New_York"
    steps, _ = fetch(json.dumps(raw).encode())
    assert steps[0].start.astimezone(UTC).hour == 11  # 07:51 in New York (EDT) is 11:51 UTC


def test_the_system_losses_setting_is_not_applied():
    lossy = SystemDescription(52.37, 4.9, 4.8, 35, -45, 0.5, "Europe/Amsterdam")
    assert fetch(system=lossy)[0] == fetch()[0]


# ---- daylight saving ------------------------------------------------------------------------------------------


def test_across_the_missing_hour_a_period_is_the_real_elapsed_time():
    steps, _ = fetch(SPRING)
    assert check_steps(steps) == []
    across = steps[0]  # 01:30 (CET) to 03:30 (CEST) on 2026-03-29: one hour has gone, not two
    assert across.hours == pytest.approx(1.0) and across.power_kw == pytest.approx(0.120)  # 120 Wh in that hour
    assert across.start.astimezone(UTC).hour == 0 and across.end.astimezone(UTC).hour == 1


def test_across_the_repeated_hour_a_period_is_the_real_elapsed_time():
    steps, _ = fetch(AUTUMN)
    assert check_steps(steps) == []
    first, second = steps[0], steps[1]  # 01:30 CEST, 02:30 (the first 02:30, CEST), then 03:30 CET
    assert first.hours == pytest.approx(1.0) and second.hours == pytest.approx(2.0)  # the repeated hour is counted
    assert first.power_kw == pytest.approx(0.060) and second.power_kw == pytest.approx(0.045)  # 60 Wh in 1 h, 90 Wh in 2 h
    assert first.end == second.start


# ---- the request ----------------------------------------------------------------------------------------------


def test_the_public_request_has_the_documented_shape():
    _, transport = fetch()
    request = transport.requests[0]
    assert request.url == "https://api.forecast.solar/estimate/watthours/period/52.37/4.9/35/-45/4.8"
    assert request.headers["Accept"] == "application/json" and "car-charge-planner" in request.headers["User-Agent"]


def test_a_key_goes_in_front_of_the_route_as_documented():
    _, transport = fetch(settings={"api_key": f" {SECRET} "})
    assert transport.requests[0].url == f"https://api.forecast.solar/{SECRET}/estimate/watthours/period/52.37/4.9/35/-45/4.8"


def test_blank_key_means_the_public_plan():
    _, transport = fetch(settings={"api_key": "  "})
    assert transport.requests[0].url.startswith("https://api.forecast.solar/estimate/")


@pytest.mark.parametrize(
    "direction, azimuth, sent",
    [("south", 0, "0"), ("east", -90, "-90"), ("west", 90, "90"), ("north", 180, "180"), ("north", -180, "-180"), ("south-east", -45, "-45")],
)
def test_our_azimuth_is_theirs(direction, azimuth, sent):
    """Both use 0 = south, -90 = east, 90 = west and 180 / -180 = north, so it passes through unchanged."""
    system = SystemDescription(52.37, 4.9, 4.8, 35, azimuth, 0.14, "Europe/Amsterdam")
    assert ForecastSolar.request_url(system, {}).split("/")[-2] == sent


def test_numbers_are_whole_degrees_and_short_decimals():
    system = SystemDescription(52.37123456, -4.123456, 4.123456, 34.6, 12.4, 0.14, "Europe/Amsterdam")
    assert ForecastSolar.request_url(system, {}).endswith("/52.3712/-4.1235/35/12/4.123")
    flat = SystemDescription(52.0, 4.0, 5.0, 120, 250, 0.14, "Europe/Amsterdam")
    assert ForecastSolar.request_url(flat, {}).endswith("/52/4/90/180/5")  # limited to what the API accepts


# ---- failures -------------------------------------------------------------------------------------------------


def rate_limit(headers=None, body=RATE_LIMITED, now=NOW):
    with pytest.raises(RateLimited) as caught:
        fetch(body, status=429, headers=headers, now=now)
    return caught.value.retry_after


def test_the_retry_time_comes_from_the_header_first():
    assert rate_limit({"X-Ratelimit-Retry-At": "2026-10-07T10:30:00+02:00"}) == timedelta(minutes=37, seconds=9)


def test_else_from_the_body():
    assert rate_limit() == timedelta(minutes=27, seconds=8)  # 08:19:59 UTC minus 07:52:51


def test_a_retry_time_already_past_is_zero():
    assert rate_limit(now=datetime(2026, 10, 7, 12, 0, tzinfo=UTC)) == timedelta(0)


@pytest.mark.parametrize("headers, body", [({}, b"{}"), ({}, b"not json"), ({"X-Ratelimit-Retry-At": "soon"}, b"{}"), ({"X-Ratelimit-Retry-At": "2026-10-07T10:00:00"}, b"{}")])
def test_without_a_usable_retry_time_there_is_none(headers, body):
    assert rate_limit(headers, body) is None


@pytest.mark.parametrize("status", [500, 502, 503])
def test_server_errors_are_unavailable(status):
    with pytest.raises(Unavailable):
        fetch(b"", status=status)


def test_a_rejected_request_keeps_the_reason_the_server_gives():
    body = json.dumps({"message": {"code": 422, "type": "validation error", "text": "Latitude must be between -90 and 90"}}).encode()
    with pytest.raises(BadResponse, match="Latitude must be"):
        fetch(body, status=422)


@pytest.mark.parametrize("status", [401, 403])
def test_a_refused_key_points_at_the_settings(status):
    with pytest.raises(BadResponse, match="API key"):
        fetch(b"", status=status)


@pytest.mark.parametrize(
    "raw",
    [
        {"result": {}, "message": {"code": 0}},  # nothing in it
        {"result": [], "message": {"code": 0}},
        {"message": {"code": 0}},  # no result
        {"result": {"2026-10-07 08:00:00": 1}},  # no message
        {"result": {"yesterday": 1}, "message": {"code": 0}},  # a key that is not a time
        {"result": {"2026-10-07 08:00:00": "lots", "2026-10-07 09:00:00": 1}, "message": {"code": 0}},
        {"result": {"2026-10-07 08:00:00": 0, "2026-10-07 09:00:00": -5}, "message": {"code": 0}},  # negative energy
        {"result": {"2026-10-07 08:00:00": 0, "2026-10-07 09:00:00": 1}, "message": {"code": 0, "info": {"timezone": "Mars/Olympus"}}},
        {"result": {"2026-10-07 08:00:00": 0}, "message": {"code": 2, "type": "error", "text": "no data for this place"}},
        [1, 2],
    ],
)
def test_an_answer_that_is_not_as_documented_is_a_bad_response(raw):
    with pytest.raises(BadResponse):
        fetch(json.dumps(raw).encode())


def test_the_key_never_appears_in_an_error_message():
    for outcome in (OSError(f"https://x/{SECRET}/estimate refused"), Reply(503), Reply(200, b"nope")):
        with pytest.raises((Unavailable, BadResponse)) as caught:
            ForecastSolar(ReplayTransport([("", outcome)])).fetch(EXAMPLE_SYSTEM, {"api_key": SECRET}, NOW)
        assert SECRET not in str(caught.value)


# ---- who it is ------------------------------------------------------------------------------------------------


def test_it_declares_the_public_plan():
    provider = ForecastSolar()
    assert provider.id == "forecast-solar" and provider.horizon == timedelta(days=2)
    assert provider.min_refresh == timedelta(minutes=60) and provider.search_places is None
    assert [(s.key, s.secret, s.optional) for s in provider.settings] == [("api_key", True, True)]
    assert "losses" in provider.description  # the user is told the setting is not used
    assert provider.attribution == "Solar forecast: Forecast.Solar"


def test_both_providers_are_built_in():
    assert set(registry.ids()) >= {"open-meteo", "forecast-solar"}
