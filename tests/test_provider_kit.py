"""The conformance kit and the fake provider, tested (ticket P2).

A small provider that really uses the transport passes the kit; broken variants of it each fail the check
that should catch them. That is what makes a green kit mean something for the real providers.
"""

import json
import threading
from datetime import datetime, timedelta, timezone

import pytest

import provider_kit as kit
from fake_provider import FakeProvider, Held, steps_for
from car_charge_planner.forecast.provider import (
    BadResponse,
    NotConfigured,
    RateLimited,
    SettingSpec,
    Unavailable,
    check_steps,
)
from car_charge_planner.forecast.transport import Reply, Request
from car_charge_planner.planner.model import ForecastStep
from provider_kit import ProviderCase, ProviderConformance, ReplayTransport, UnexpectedRequest

from conftest import at
from datetime import date

UTC = timezone.utc
URL = "https://example.test/forecast"
GOOD = json.dumps(
    {"steps": [{"start": "2026-07-03T08:00:00+00:00", "end": "2026-07-03T09:00:00+00:00", "kw": 1.5},
               {"start": "2026-07-03T09:00:00+00:00", "end": "2026-07-03T10:00:00+00:00", "kw": 2.5}]}
).encode()


class Tiny:
    """A well-behaved provider written the way a contributor would write one."""

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


# ---- broken providers: each one must be caught --------------------------------------------------------------------


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


# ---- the replay transport ----------------------------------------------------------------------------------------


def test_the_replay_transport_answers_what_was_recorded_and_fails_on_anything_else():
    replay = ReplayTransport([("example.test/a", Reply(200, b"a")), (lambda r: r.url.endswith("/b"), Reply(404))])
    assert replay(Request("https://example.test/a?x=1")).body == b"a"
    assert replay(Request("https://other.test/b")).status == 404
    with pytest.raises(UnexpectedRequest):
        replay(Request("https://example.test/c"))
    assert [r.url for r in replay.unexpected] == ["https://example.test/c"] and len(replay.requests) == 3


def test_a_recorded_exception_is_raised():
    with pytest.raises(OSError, match="down"):
        ReplayTransport([("", OSError("down"))])(Request("https://example.test/"))


def test_reply_headers_are_case_insensitive_and_json_decodes():
    reply = Reply(200, b'{"a": 1}', {"Retry-After": "5"})
    assert reply.header("retry-after") == "5" and reply.header("missing") is None
    assert reply.json() == {"a": 1} and reply.ok and not Reply(429).ok


# ---- the fake provider -------------------------------------------------------------------------------------------

DAY = date(2026, 7, 3)


def ask(fake):
    return fake.fetch(None, {}, at(DAY, 7))


def test_the_script_runs_in_order_and_the_last_entry_repeats():
    fake = FakeProvider(script=[steps_for(DAY), Unavailable("down"), RateLimited("busy", timedelta(minutes=5))])
    assert check_steps(ask(fake)) == []
    with pytest.raises(Unavailable):
        ask(fake)
    for _ in range(2):
        with pytest.raises(RateLimited) as caught:
            ask(fake)
        assert caught.value.retry_after == timedelta(minutes=5)
    assert fake.call_count == 4


def test_a_script_that_must_not_repeat_says_so():
    fake = FakeProvider(script=[steps_for(DAY)], repeat_last=False)
    ask(fake)
    with pytest.raises(IndexError, match="asked 2 times"):
        ask(fake)


def test_the_fake_records_what_it_was_asked():
    fake = FakeProvider(script=[steps_for(DAY)])
    fake.fetch("system", {"api_key": "k"}, at(DAY, 7))
    assert fake.calls == [("system", {"api_key": "k"}, at(DAY, 7))]


def test_a_callable_entry_is_called_each_time():
    counter = iter(range(10))
    fake = FakeProvider(script=[lambda: steps_for(DAY, 100 * (next(counter) + 1))], repeat_last=True)
    first, second = max(s.power_kw for s in ask(fake)), max(s.power_kw for s in ask(fake))
    assert second > first


def test_a_held_fetch_waits_until_released():
    release = threading.Event()
    fake = FakeProvider(script=[Held(release, steps_for(DAY))])
    result = []
    worker = threading.Thread(target=lambda: result.append(ask(fake)))
    worker.start()
    worker.join(0.2)
    assert worker.is_alive() and not result  # in flight
    release.set()
    worker.join(2)
    assert result and check_steps(result[0]) == []


def test_a_held_fetch_can_end_in_an_error():
    release = threading.Event()
    release.set()
    fake = FakeProvider(script=[Held(release, Unavailable("late failure"))])
    with pytest.raises(Unavailable, match="late"):
        ask(fake)


def test_the_fake_meets_the_metadata_rules_of_the_kit():
    case = ProviderCase(make=lambda t: FakeProvider(), ok=[], server_error=[], bad_body=[], empty_body=[])
    kit.check_metadata(case)
