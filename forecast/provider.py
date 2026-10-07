"""What a forecast provider is: the contract every provider fulfils and the planner relies on.

A provider is given a description of the user's solar system and returns the expected solar power per time
step. Everything else (caching, the fallback, the settings screens) lives outside it and works with any
provider. See `.scratch/forecast-providers/spec.md` and, for contributors, `docs/providers.md`.
"""

from __future__ import annotations

import math
from dataclasses import dataclass
from datetime import datetime, timedelta
from typing import Callable, Protocol, Sequence

from planner.model import Config, ForecastStep


@dataclass(frozen=True)
class SystemDescription:
    """What a provider needs to know to forecast this system. Azimuth: 0 = south, -90 = east, 90 = west."""

    latitude: float
    longitude: float
    kwp: float
    tilt_deg: float
    azimuth_deg: float
    system_losses: float  # a fraction, 0.14 = 14%
    timezone: str

    @classmethod
    def from_config(cls, cfg: Config) -> "SystemDescription":
        return cls(
            latitude=cfg.latitude,
            longitude=cfg.longitude,
            kwp=cfg.panels.kwp,
            tilt_deg=cfg.panels.tilt_deg,
            azimuth_deg=cfg.panels.azimuth_deg,
            system_losses=cfg.panels.system_losses,
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


PlaceSearch = Callable[[str], Sequence[object]]  # an optional ability; its result type comes with ticket P9


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
        if not (isinstance(step.power_kw, (int, float)) and math.isfinite(step.power_kw)):
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
