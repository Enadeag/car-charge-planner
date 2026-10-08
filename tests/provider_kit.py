"""The conformance kit: what every forecast provider must do, run against recorded answers (ticket P2).

A provider's test module describes the provider in a `ProviderCase` and subclasses `ProviderConformance`:

    class TestMyProvider(ProviderConformance):
        @pytest.fixture
        def case(self):
            return ProviderCase(
                make=lambda transport: MyProvider(transport),
                ok=[("api.example/forecast", Reply(200, RECORDED))],
                server_error=[("", Reply(503))],
                bad_body=[("", Reply(200, b"<html>"))],
                empty_body=[("", Reply(200, b"{}"))],
            )

Nothing here touches the network: the provider gets a `ReplayTransport`, which answers only what the case
recorded and fails on any other request. The checks are plain functions (`check_*`) so the kit can itself be
tested against deliberately broken providers.
"""

from __future__ import annotations

import itertools
import logging
import re
from collections.abc import Callable
from dataclasses import dataclass, field, replace
from datetime import UTC, datetime, timedelta

import pytest

from car_charge_planner.forecast.provider import (
    BadResponse,
    ForecastProvider,
    NotConfigured,
    PanelArray,
    PlaceSearch,
    Problem,
    ProviderError,
    RateLimited,
    SystemDescription,
    Unavailable,
    add_up,
    check_steps,
)
from car_charge_planner.forecast.transport import Reply, Request, Transport

Match = str | Callable[[Request], bool]
Routes = list[tuple[Match, Reply | Exception]]

EXAMPLE_SYSTEM = SystemDescription(52.37, 4.90, (PanelArray(4.8, 35, -45, 0.14),), "Europe/Amsterdam")
SECOND_ARRAY = PanelArray(2.4, 15, 90, 0.10)  # added to the case's system for the several-arrays check
EXAMPLE_NOW = datetime(2026, 7, 3, 7, 0, tzinfo=UTC)
HORIZON_SLACK = timedelta(days=1)


class UnexpectedRequest(Exception):
    """The provider asked for something the case did not record."""


class ReplayTransport:
    """Answers from recorded routes; any other request fails the test. A route matches when its text is part
    of the URL (an empty text matches everything) or its function returns true. A route that holds an
    exception raises it, which is how "no network" is played."""

    def __init__(self, routes: Routes) -> None:
        self.routes = routes
        self.requests: list[Request] = []
        self.unexpected: list[Request] = []  # also kept, in case the provider swallowed the exception

    def __call__(self, request: Request) -> Reply:
        self.requests.append(request)
        for match, outcome in self.routes:
            hit = match(request) if callable(match) else match in request.url
            if hit:
                if isinstance(outcome, Exception):
                    raise outcome
                return outcome
        self.unexpected.append(request)
        raise UnexpectedRequest(f"the provider asked for {request.url!r}, which the case did not record")


@dataclass
class ProviderCase:
    make: Callable[[Transport], ForecastProvider]
    ok: Routes  # a good answer
    server_error: Routes  # a 5xx
    bad_body: Routes  # a 200 that is not what the provider documents
    empty_body: Routes  # a 200 with nothing in it
    rate_limited: Routes | None = None  # skip the check when the provider has no such answer
    retry_after: timedelta | None = None  # what the rate-limited answer says, when it says
    system: SystemDescription = EXAMPLE_SYSTEM
    settings: dict[str, str] = field(default_factory=dict)  # a working set of settings (required ones filled in)
    now: datetime = EXAMPLE_NOW  # when the recorded answer was "fetched"
    secrets: dict[str, str] = field(default_factory=dict)  # secret settings with recognisable values

    def provider(self, routes: Routes) -> tuple[ForecastProvider, ReplayTransport]:
        transport = ReplayTransport(routes)
        return self.make(transport), transport

    def all_settings(self) -> dict[str, str]:
        return {**self.settings, **self.secrets}


# ---- the checks ------------------------------------------------------------------------------------------------


def check_metadata(case: ProviderCase) -> None:
    provider, _ = case.provider(case.ok)
    assert re.fullmatch(r"[a-z0-9][a-z0-9-]*", provider.id), f"id {provider.id!r}: lower case letters, digits and hyphens"
    for name in ("name", "description", "attribution", "sends"):
        assert str(getattr(provider, name)).strip(), f"{name} is empty"
    assert provider.terms_url.startswith("https://"), "terms_url must be an https link"
    assert provider.min_refresh > timedelta(0), "min_refresh must be positive"
    assert provider.horizon > timedelta(0), "horizon must be positive"
    assert provider.max_useful_age >= provider.min_refresh, "a forecast cannot be useful for less than the refresh interval"
    keys = [s.key for s in provider.settings]
    assert len(keys) == len(set(keys)), "setting keys must be unique"
    for spec in provider.settings:
        assert spec.key and spec.label.strip() and spec.hint.strip(), f"setting {spec.key!r} needs a key, a label and a hint"
    assert provider.search_places is None or callable(provider.search_places)


def check_good_answer(case: ProviderCase) -> None:
    provider, transport = case.provider(case.ok)
    steps = provider.fetch(case.system, case.all_settings(), case.now)
    assert steps, "a good answer must give at least one step"
    assert check_steps(steps) == []
    assert transport.requests, "the provider answered without asking: it must go through the transport it was given"
    assert not transport.unexpected, f"unexpected requests: {[r.url for r in transport.unexpected]}"
    assert max(s.end for s in steps) <= case.now + provider.horizon + HORIZON_SLACK, "steps reach past the declared horizon"


def check_several_arrays(case: ProviderCase) -> None:
    """A system with two panel arrays gives one valid forecast. A provider that asks once per array (the recorded
    answer is replayed for each request) gives the sum of the two single-array forecasts, and a failure for the
    second array is a failure of the whole forecast, not a total without it."""
    system = replace(case.system, arrays=(*case.system.arrays, SECOND_ARRAY))
    provider, transport = case.provider(case.ok)
    total = provider.fetch(system, case.all_settings(), case.now)
    assert total and check_steps(total) == []
    assert not transport.unexpected, f"unexpected requests: {[r.url for r in transport.unexpected]}"
    if len(transport.requests) != len(system.arrays):
        return  # one request for all arrays: the recorded answer cannot tell what the sum should be
    singles = [case.provider(case.ok)[0].fetch(replace(system, arrays=(array,)), case.all_settings(), case.now) for array in system.arrays]
    expected = add_up(singles)
    assert [(s.start, s.end) for s in total] == [(s.start, s.end) for s in expected]
    assert [s.power_kw for s in total] == pytest.approx([s.power_kw for s in expected])
    counter = itertools.count()
    second_fails = [(lambda _request: next(counter) >= 1, Reply(503, b"Service Unavailable")), *case.ok]
    _raises(replace(case, system=system), second_fails, Unavailable)


def check_deterministic(case: ProviderCase) -> None:
    provider, _ = case.provider(case.ok)
    first = provider.fetch(case.system, case.all_settings(), case.now)
    second = provider.fetch(case.system, case.all_settings(), case.now)
    assert first == second


def check_inputs_untouched(case: ProviderCase) -> None:
    provider, _ = case.provider(case.ok)
    settings = case.all_settings()
    before = dict(settings)
    provider.fetch(case.system, settings, case.now)
    assert settings == before, "fetch changed the settings it was given"


def _raises(case: ProviderCase, routes: Routes, kind: type[ProviderError]) -> ProviderError:
    provider, transport = case.provider(routes)
    with pytest.raises(kind) as caught:
        provider.fetch(case.system, case.all_settings(), case.now)
    assert not transport.unexpected, f"unexpected requests: {[r.url for r in transport.unexpected]}"
    return caught.value


def check_rate_limit(case: ProviderCase) -> None:
    if case.rate_limited is None:
        pytest.skip("this provider has no rate-limit answer in the case")
    error = _raises(case, case.rate_limited, RateLimited)
    assert error.retry_after == case.retry_after


def check_server_error(case: ProviderCase) -> None:
    _raises(case, case.server_error, Unavailable)


def check_no_network(case: ProviderCase, failure: Exception) -> None:
    _raises(case, [("", failure)], Unavailable)


def check_bad_body(case: ProviderCase) -> None:
    _raises(case, case.bad_body, BadResponse)


def check_empty_body(case: ProviderCase) -> None:
    """Either no steps that are valid, or a `ProviderError`; never some other exception."""
    provider, transport = case.provider(case.empty_body)
    try:
        steps = provider.fetch(case.system, case.all_settings(), case.now)
    except ProviderError:
        return
    assert check_steps(steps) == []
    assert not transport.unexpected


def check_missing_required_settings(case: ProviderCase) -> None:
    provider, transport = case.provider(case.ok)
    required = [s.key for s in provider.settings if not s.optional]
    if not required:
        pytest.skip("this provider has no required settings")
    for key in required:
        settings = {k: v for k, v in case.all_settings().items() if k != key}
        with pytest.raises(NotConfigured):
            provider.fetch(case.system, settings, case.now)
    assert not transport.requests, "a missing setting must be reported before anything is sent"


class _Capture(logging.Handler):
    def __init__(self) -> None:
        super().__init__(logging.DEBUG)
        self.text: list[str] = []

    def emit(self, record: logging.LogRecord) -> None:
        self.text.append(record.getMessage())


def check_secrets_stay_secret(case: ProviderCase) -> None:
    """A secret setting must not appear in a log line or an error message, whatever went wrong."""
    if not case.secrets:
        pytest.skip("this provider has no secret settings")
    capture = _Capture()
    root = logging.getLogger()
    previous = root.level
    root.addHandler(capture)
    root.setLevel(logging.DEBUG)
    messages: list[str] = []
    try:
        scenarios = [case.ok, case.server_error, case.bad_body, case.empty_body, [("", OSError("down"))]]
        if case.rate_limited is not None:
            scenarios.append(case.rate_limited)
        for routes in scenarios:
            provider, _ = case.provider(routes)
            try:
                provider.fetch(case.system, case.all_settings(), case.now)
            except ProviderError as err:
                messages.append(str(err))
    finally:
        root.removeHandler(capture)
        root.setLevel(previous)
    shown = "\n".join(capture.text + messages)
    for key, value in case.secrets.items():
        assert value not in shown, f"the secret {key!r} appears in a log line or an error message"


# ---- place search (a provider's optional ability) ---------------------------------------------------------------


@dataclass
class PlaceCase:
    """Describes a place search for `PlaceSearchConformance`, the way `ProviderCase` describes a provider."""

    make: Callable[[Transport], PlaceSearch]
    ok: Routes  # an answer with places
    empty: Routes  # an answer to something that matches nothing
    server_error: Routes
    bad_body: Routes
    rate_limited: Routes | None = None
    retry_after: timedelta | None = None
    query: str = "Amsterdam"

    def search(self, routes: Routes) -> tuple[PlaceSearch, ReplayTransport]:
        transport = ReplayTransport(routes)
        return self.make(transport), transport


def check_places_found(case: PlaceCase) -> None:
    from zoneinfo import ZoneInfo

    search, transport = case.search(case.ok)
    results = search(case.query)
    assert results.ok and results.places, "a good answer must give places"
    assert transport.requests and not transport.unexpected, "it must ask through the transport, and only for what was recorded"
    for place in results.places:
        assert place.name.strip() and place.label.strip()
        assert -90 <= place.latitude <= 90 and -180 <= place.longitude <= 180
        ZoneInfo(place.timezone)  # raises for a time zone nobody knows
    assert all(r.timeout_s <= 10 for r in transport.requests), "a search box needs a short timeout"


def check_places_too_short(case: PlaceCase) -> None:
    search, transport = case.search(case.ok)
    for text in ("", " ", "a", " a "):
        results = search(text)
        assert results.problem is Problem.TOO_SHORT and not results.places
    assert transport.requests == [], "nothing is sent for fewer than two characters"


def check_places_nothing_found(case: PlaceCase) -> None:
    search, _ = case.search(case.empty)
    results = search(case.query)
    assert results.ok and results.no_results


def check_places_failures(case: PlaceCase) -> None:
    for failure in (OSError("unreachable"), TimeoutError("slow")):
        assert case.search([("", failure)])[0](case.query).problem is Problem.UNAVAILABLE
    assert case.search(case.server_error)[0](case.query).problem is Problem.UNAVAILABLE
    assert case.search(case.bad_body)[0](case.query).problem is Problem.BAD_RESPONSE
    if case.rate_limited is not None:
        limited = case.search(case.rate_limited)[0](case.query)
        assert limited.problem is Problem.RATE_LIMITED and limited.retry_after == case.retry_after


class PlaceSearchConformance:
    """Subclass as `Test<Provider>Places` and provide a `place_case` fixture."""

    @pytest.fixture
    def place_case(self) -> PlaceCase:  # pragma: no cover - the subclass provides it
        raise NotImplementedError

    def test_it_finds_places_with_coordinates_and_a_time_zone(self, place_case):
        check_places_found(place_case)

    def test_it_asks_for_nothing_when_there_is_too_little_to_search(self, place_case):
        check_places_too_short(place_case)

    def test_a_search_that_matches_nothing_is_an_answer_not_a_problem(self, place_case):
        check_places_nothing_found(place_case)

    def test_failures_come_back_typed_and_never_raise(self, place_case):
        check_places_failures(place_case)


# ---- the suite a provider's test module inherits ------------------------------------------------------------------


class ProviderConformance:
    """Subclass it as `Test<Provider>` and provide the `case` fixture."""

    @pytest.fixture
    def case(self) -> ProviderCase:  # pragma: no cover - the subclass provides it
        raise NotImplementedError("provide a `case` fixture returning a ProviderCase")

    def test_declares_complete_metadata(self, case):
        check_metadata(case)

    def test_a_good_answer_gives_valid_steps_through_the_transport(self, case):
        check_good_answer(case)

    def test_several_panel_arrays_give_one_forecast_of_their_sum(self, case):
        check_several_arrays(case)

    def test_the_same_answer_gives_the_same_steps(self, case):
        check_deterministic(case)

    def test_it_leaves_its_inputs_alone(self, case):
        check_inputs_untouched(case)

    def test_a_rate_limit_is_rate_limited_with_the_retry_time(self, case):
        check_rate_limit(case)

    def test_a_server_error_is_unavailable(self, case):
        check_server_error(case)

    @pytest.mark.parametrize("failure", [OSError("unreachable"), TimeoutError("slow"), ConnectionResetError("reset")])
    def test_no_network_is_unavailable(self, case, failure):
        check_no_network(case, failure)

    def test_an_answer_that_is_not_as_documented_is_a_bad_response(self, case):
        check_bad_body(case)

    def test_an_empty_answer_never_crashes(self, case):
        check_empty_body(case)

    def test_missing_required_settings_are_not_configured(self, case):
        check_missing_required_settings(case)

    def test_secrets_never_reach_logs_or_errors(self, case):
        check_secrets_stay_secret(case)
