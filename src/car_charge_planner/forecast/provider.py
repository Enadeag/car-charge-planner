"""What a forecast provider is: the contract every provider fulfils and the planner relies on.

A provider gets the user's solar system (one or more panel arrays) and returns the expected solar power of all of them
together per time step. Caching, the fallback and the settings screens live outside it. See `docs/providers.md`.
"""

from __future__ import annotations

import math
from collections.abc import Callable, Sequence
from dataclasses import dataclass
from datetime import UTC, datetime, timedelta
from enum import Enum
from typing import Protocol

from car_charge_planner.planner.model import Config, ForecastStep


@dataclass(frozen=True)
class PanelArray:
    """Panels with one tilt and one direction. Azimuth: 0 = south, -90 = east, 90 = west, 180 = north."""

    kwp: float
    tilt_deg: float
    azimuth_deg: float
    system_losses: float  # a fraction, 0.14 = 14%


@dataclass(frozen=True)
class SystemDescription:
    """What a provider needs to know to forecast this system: where it is and its panel arrays (at least one)."""

    latitude: float
    longitude: float
    arrays: tuple[PanelArray, ...]
    timezone: str

    @property
    def kwp(self) -> float:
        return sum(a.kwp for a in self.arrays)

    @classmethod
    def from_config(cls, cfg: Config) -> SystemDescription:
        return cls(
            latitude=cfg.latitude,
            longitude=cfg.longitude,
            arrays=tuple(PanelArray(p.kwp, p.tilt_deg, p.azimuth_deg, p.system_losses) for p in cfg.panels),
            timezone=cfg.timezone,
        )


@dataclass(frozen=True)
class SettingSpec:
    """One setting a provider asks for (a server address, an API key). Shown in Settings, stored by the app."""

    key: str
    label: str
    hint: str
    optional: bool = True
    secret: bool = False  # kept in secrets.toml, shown masked


@dataclass(frozen=True)
class Place:
    """A place a person can pick as the location of their panels."""

    name: str
    latitude: float
    longitude: float
    timezone: str  # an IANA name
    region: str = ""  # the first administrative area: a state, a province
    country: str = ""
    area: str = ""  # a finer one (county, municipality): tells apart two places with the same name

    @property
    def label(self) -> str:
        """"Name, region, country": what the list shows."""
        return ", ".join(part for part in (self.name, self.region if self.region != self.name else "", self.country) if part)

    @property
    def detailed_label(self) -> str:
        """The same with the finer area, for when two results would read alike."""
        parts = (self.name, self.area if self.area not in (self.name, self.region) else "", self.region if self.region != self.name else "", self.country)
        return ", ".join(part for part in parts if part)


class Problem(Enum):
    TOO_SHORT = "too short"  # fewer than two characters: nothing was asked
    UNAVAILABLE = "unavailable"  # no network, a timeout, a server error
    RATE_LIMITED = "rate limited"
    BAD_RESPONSE = "bad response"


@dataclass(frozen=True)
class PlaceResults:
    """What a place search gives back: places, or why not. No places and no problem means nothing matched."""

    places: tuple[Place, ...] = ()
    problem: Problem | None = None
    retry_after: timedelta | None = None

    @property
    def ok(self) -> bool:
        return self.problem is None

    @property
    def no_results(self) -> bool:
        return self.ok and not self.places


class PlaceSearch(Protocol):
    """An optional ability of a provider: turn what a person typed into places. Blocking; never raises."""

    def __call__(self, text: str, language: str = "en") -> PlaceResults: ...


class ProviderError(Exception):
    """A provider could not give a forecast. The subclasses say why, so the service can react."""


class Unavailable(ProviderError):
    """No network, a timeout, or a server error: try again later."""


class RateLimited(ProviderError):
    """The provider refuses more requests for now. `retry_after` is what it said, when it said anything."""

    def __init__(self, message: str = "", retry_after: timedelta | None = None) -> None:
        super().__init__(message or "rate limited")
        self.retry_after = retry_after


class BadResponse(ProviderError):
    """An answer that is not what the provider documents (including steps that break the contract)."""


class NotConfigured(ProviderError):
    """A required setting is missing."""


class ForecastProvider(Protocol):
    id: str  # short and stable: used in the config file and the cache file name
    name: str  # shown to the user
    description: str
    terms_url: str
    attribution: str  # the credit the provider's terms ask for, shown with the forecast
    sends: str  # in plain words, what leaves the computer (shown in Settings and the README)
    min_refresh: timedelta  # never ask more often than this
    horizon: timedelta  # how far ahead it forecasts
    max_useful_age: timedelta  # a forecast older than this is not worth keeping when it cannot be refreshed
    settings: tuple[SettingSpec, ...]
    search_places: PlaceSearch | None

    def fetch(self, system: SystemDescription, settings: dict[str, str], now: datetime) -> list[ForecastStep]:
        """Blocking. Returns steps that satisfy `check_steps`; raises a `ProviderError` otherwise."""
        ...


def _aware(moment: datetime) -> bool:
    return moment.tzinfo is not None and moment.utcoffset() is not None


def check_steps(steps: Sequence[ForecastStep]) -> list[str]:
    """What is wrong with a provider's answer, in words. Empty when it satisfies the contract.

    Steps are timezone-aware, each lasts a positive time, they follow each other in order without overlap,
    and each power is a finite number that is not negative.
    """
    problems: list[str] = []
    previous_end: datetime | None = None
    for index, step in enumerate(steps):
        if not (_aware(step.start) and _aware(step.end)):
            problems.append(f"step {index}: start and end must carry a time zone")
            continue
        if step.end <= step.start:
            problems.append(f"step {index}: must end after it starts")
        if previous_end is not None and step.start < previous_end:
            problems.append(f"step {index}: overlaps or precedes the step before it")
        if not (isinstance(step.power_kw, (int, float)) and math.isfinite(step.power_kw)):  # pyright: ignore[reportUnnecessaryIsInstance]  # a provider may break its type
            problems.append(f"step {index}: power must be a finite number")
        elif step.power_kw < 0:
            problems.append(f"step {index}: power cannot be negative")
        previous_end = step.end
    return problems


def require_valid_steps(steps: Sequence[ForecastStep]) -> list[ForecastStep]:
    """The steps themselves when they satisfy the contract; otherwise `BadResponse` naming the first problems."""
    problems = check_steps(steps)
    if problems:
        raise BadResponse("; ".join(problems[:3]))
    return list(steps)


def add_up(forecasts: Sequence[Sequence[ForecastStep]]) -> list[ForecastStep]:
    """The forecasts of several panel arrays as one: the power of all of them per step.

    When the arrays' steps line up, each step's power is the sum of theirs. Otherwise the steps are cut wherever any array's
    step starts or ends and each piece gets the sum of the powers that cover it, so no energy is lost or counted twice. An
    array without a step at some time (night) adds nothing then; a time no array covers stays a gap. The result covers only
    the time every array covers, so a shorter answer for one array never shows as a drop at the end. The steps are in the
    time zone of the first forecast's.
    """
    lists = [list(steps) for steps in forecasts]
    if len(lists) == 1:
        return lists[0]
    if not lists or any(not steps for steps in lists):
        return []
    zone = lists[0][0].start.tzinfo
    first = max(steps[0].start for steps in lists)
    last = min(steps[-1].end for steps in lists)
    cuts = sorted({moment.astimezone(UTC) for steps in lists for step in steps for moment in (step.start, step.end) if first <= moment <= last})
    positions = [0] * len(lists)
    total: list[ForecastStep] = []
    for start, end in zip(cuts, cuts[1:], strict=False):
        power, covered = 0.0, False
        for index, steps in enumerate(lists):
            while positions[index] < len(steps) and steps[positions[index]].end <= start:
                positions[index] += 1
            if positions[index] < len(steps) and steps[positions[index]].start <= start:
                power += steps[positions[index]].power_kw  # the step reaches past `start`, so it covers [start, end)
                covered = True
        if covered:
            total.append(ForecastStep(start.astimezone(zone), end.astimezone(zone), power))
    return total


def each_array(system: SystemDescription, fetch_one: Callable[[PanelArray], list[ForecastStep]]) -> list[ForecastStep]:
    """For a service that forecasts one array per request: asks `fetch_one` for each array and adds the answers up (`add_up`).
    Each answer must satisfy `check_steps`. A `ProviderError` for any array fails the whole forecast: a total without one of
    the arrays would be wrong.
    """
    return add_up([fetch_one(array) for array in system.arrays])
