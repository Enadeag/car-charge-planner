"""A small provider passes the conformance kit and each broken variant fails the check that should catch it."""

import json
from dataclasses import replace
from datetime import UTC, datetime, timedelta

import pytest

import provider_kit as kit
from car_charge_planner.forecast.provider import (
    BadResponse,
    NotConfigured,
    RateLimited,
    SettingSpec,
    Unavailable,
    add_up,
)
from car_charge_planner.forecast.transport import Reply, Request
from car_charge_planner.planner.model import ForecastStep
from provider_kit import ProviderCase, ProviderConformance, ReplayTransport, UnexpectedRequest

UTC = UTC
URL = "https://example.test/forecast"
GOOD = json.dumps(
    {"steps": [{"start": "2026-07-03T08:00:00+00:00", "end": "2026-07-03T09:00:00+00:00", "kw": 1.5},
               {"start": "2026-07-03T09:00:00+00:00", "end": "2026-07-03T10:00:00+00:00", "kw": 2.5}]}
).encode()


class Tiny:
    id = "tiny"
    name = "Tiny"
    description = "A tiny forecast service."
    terms_url = "https://example.test/terms"
    attribution = "Forecast by Tiny"
    sends = "Your location and panel settings."
    min_refresh = timedelta(hours=1)
    horizon = timedelta(days=2)
    max_useful_age = timedelta(hours=6)
    settings = (SettingSpec("api_key", "API key", "Your key.", optional=False, secret=True),)
    search_places = None

    def __init__(self, transport):
        self.transport = transport

    def fetch(self, system, settings, now):
        key = settings.get("api_key")
        if not key:
            raise NotConfigured("an API key is needed")
        try:
            reply = self.transport(Request(f"{URL}?lat={system.latitude}&key={key}"))
        except OSError as err:
            raise Unavailable(f"could not reach the service: {err}") from err
        if reply.status == 429:
            raise RateLimited("slow down", self._retry(reply))
        if reply.status >= 500:
            raise Unavailable(f"server error {reply.status}")
        try:
            rows = reply.json()["steps"]
            return [
                ForecastStep(datetime.fromisoformat(r["start"]), datetime.fromisoformat(r["end"]), float(r["kw"]))
                for r in rows
            ]
        except (ValueError, KeyError, TypeError) as err:
            raise BadResponse("the answer is not what the service documents") from err

    @staticmethod
    def _retry(reply):
        value = reply.header("retry-after")
        return timedelta(seconds=int(value)) if value and value.isdigit() else None


def tiny_case(make=Tiny, ok_body=GOOD):
    return ProviderCase(
        make=make,
        ok=[("example.test", Reply(200, ok_body))],
        server_error=[("", Reply(503))],
        bad_body=[("", Reply(200, b"<html>not json</html>"))],
        empty_body=[("", Reply(200, b"{}"))],
        rate_limited=[("", Reply(429, b"", {"Retry-After": "120"}))],
        retry_after=timedelta(seconds=120),
        settings={},
        secrets={"api_key": "SECRET-KEY-123"},
        now=datetime(2026, 7, 3, 7, 0, tzinfo=UTC),
    )


class TestTinyPassesTheKit(ProviderConformance):
    @pytest.fixture
    def case(self):
        return tiny_case()


class NegativePower(Tiny):
    def fetch(self, system, settings, now):
        return [ForecastStep(s.start, s.end, -s.power_kw) for s in super().fetch(system, settings, now)]


class ForgetsRateLimits(Tiny):
    def fetch(self, system, settings, now):
        try:
            return super().fetch(system, settings, now)
        except RateLimited as err:
            raise Unavailable("something went wrong") from err  # the wrong kind: no cool-down would start


class CrashesOnBadJson(Tiny):
    def fetch(self, system, settings, now):
        reply = self.transport(Request(f"{URL}?key={settings['api_key']}"))
        if reply.status >= 500:
            raise Unavailable("server")
        return [ForecastStep(datetime.fromisoformat(r["start"]), datetime.fromisoformat(r["end"]), r["kw"])
                for r in json.loads(reply.body)["steps"]]  # a KeyError or JSONDecodeError escapes


class LeaksTheKey(Tiny):
    def fetch(self, system, settings, now):
        try:
            return super().fetch(system, settings, now)
        except Unavailable as err:
            raise Unavailable(f"{err} (key {settings['api_key']})") from err


class SendsBeforeCheckingSettings(Tiny):
    def fetch(self, system, settings, now):
        self.transport(Request(URL))
        return super().fetch(system, settings, now)


class AsksForSomethingElse(Tiny):
    def fetch(self, system, settings, now):
        self.transport(Request("https://elsewhere.test/other"))
        return super().fetch(system, settings, now)


class SwallowsTheUnexpected(Tiny):
    def fetch(self, system, settings, now):
        try:
            self.transport(Request("https://elsewhere.test/other"))
        except Exception:
            pass
        return super().fetch(system, settings, now)


class NaiveTimes(Tiny):
    def fetch(self, system, settings, now):
        return [ForecastStep(s.start.replace(tzinfo=None), s.end.replace(tzinfo=None), s.power_kw)
                for s in super().fetch(system, settings, now)]


class KeepsOnlyTheLastArray(Tiny):
    def fetch(self, system, settings, now):
        answers = [super(KeepsOnlyTheLastArray, self).fetch(replace(system, arrays=(a,)), settings, now) for a in system.arrays]
        return answers[-1]  # asked for each, but did not add them up


class LeavesOutAnArrayThatFailed(Tiny):
    def fetch(self, system, settings, now):
        answers = []
        for array in system.arrays:
            try:
                answers.append(super().fetch(replace(system, arrays=(array,)), settings, now))
            except Unavailable:
                pass  # a total without this array looks fine and is wrong
        return add_up(answers)


# (the broken provider, the check that must catch it, how the check fails)
BROKEN = [
    (NegativePower, kit.check_good_answer, AssertionError),
    (NaiveTimes, kit.check_good_answer, AssertionError),
    (ForgetsRateLimits, kit.check_rate_limit, Unavailable),  # the wrong error kind comes out instead of RateLimited
    (CrashesOnBadJson, kit.check_bad_body, ValueError),  # the provider's own crash is not a BadResponse
    (CrashesOnBadJson, kit.check_empty_body, KeyError),
    (LeaksTheKey, kit.check_secrets_stay_secret, AssertionError),
    (SendsBeforeCheckingSettings, kit.check_missing_required_settings, AssertionError),
    (AsksForSomethingElse, kit.check_good_answer, UnexpectedRequest),
    (SwallowsTheUnexpected, kit.check_good_answer, AssertionError),
    (KeepsOnlyTheLastArray, kit.check_several_arrays, AssertionError),
    (LeavesOutAnArrayThatFailed, kit.check_several_arrays, pytest.fail.Exception),  # Unavailable was expected
]


@pytest.mark.parametrize("provider, check, failure", BROKEN, ids=[f"{p.__name__}-{c.__name__}" for p, c, _ in BROKEN])
def test_the_kit_catches_a_broken_provider(provider, check, failure):
    with pytest.raises(failure):
        check(tiny_case(make=provider))


def test_the_kit_catches_a_provider_that_never_uses_its_transport():
    class Offline(Tiny):
        def fetch(self, system, settings, now):
            return [ForecastStep(datetime(2026, 7, 3, 8, tzinfo=UTC), datetime(2026, 7, 3, 9, tzinfo=UTC), 1.0)]

    with pytest.raises(AssertionError, match="transport"):
        kit.check_good_answer(tiny_case(make=Offline))


def test_the_kit_catches_missing_metadata():
    class NoTerms(Tiny):
        terms_url = "http://example.test/terms"

    with pytest.raises(AssertionError, match="https"):
        kit.check_metadata(tiny_case(make=NoTerms))


def test_the_kit_catches_steps_beyond_the_declared_horizon():
    far = json.dumps({"steps": [{"start": "2026-07-20T08:00:00+00:00", "end": "2026-07-20T09:00:00+00:00", "kw": 1}]}).encode()
    with pytest.raises(AssertionError, match="horizon"):
        kit.check_good_answer(tiny_case(ok_body=far))


def test_the_replay_transport_answers_what_was_recorded_and_fails_on_anything_else():
    replay = ReplayTransport([("example.test/a", Reply(200, b"a")), (lambda r: r.url.endswith("/b"), Reply(404))])
    assert replay(Request("https://example.test/a?x=1")).body == b"a"
    assert replay(Request("https://other.test/b")).status == 404
    with pytest.raises(UnexpectedRequest):
        replay(Request("https://example.test/c"))
    assert [r.url for r in replay.unexpected] == ["https://example.test/c"] and len(replay.requests) == 3
    with pytest.raises(OSError, match="down"):  # a recorded exception is raised
        ReplayTransport([("", OSError("down"))])(Request("https://example.test/"))
