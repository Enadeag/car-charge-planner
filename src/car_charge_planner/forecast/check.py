"""Asking a provider for one forecast, to see whether it works: the Settings tab's Test button and the set-up
screen's connection check. Nothing is cached or saved, and the answer is in plain words."""

from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime
from zoneinfo import ZoneInfo

from car_charge_planner.i18n import _, ngettext
from car_charge_planner.planner.model import Config

from .provider import (
    BadResponse,
    ForecastProvider,
    NotConfigured,
    ProviderError,
    RateLimited,
    SystemDescription,
    Unavailable,
)
from .secret_store import Secrets
from .selection import provider_settings


@dataclass(frozen=True)
class Attempt:
    worked: bool
    days: int = 0  # days of forecast, from today
    message: str = ""  # for the person: "OK: 4 days of forecast", or what went wrong


def forecast_days(steps, now: datetime, tz: ZoneInfo) -> int:
    """How many days, today onwards, the steps cover. A step is placed by its middle: Open-Meteo's first step is the
    hour before midnight, which belongs to yesterday and must not count."""
    today = now.astimezone(tz).date()
    middles = (s.start + (s.end - s.start) / 2 for s in steps)
    return len({m.astimezone(tz).date() for m in middles if m.astimezone(tz).date() >= today})


def describe_failure(provider: ForecastProvider, err: Exception) -> str:
    """What went wrong, in plain words."""
    if isinstance(err, RateLimited):
        if err.retry_after is not None:
            minutes = max(1, round(err.retry_after.total_seconds() / 60))
            return ngettext(
                "{provider} says there were too many requests. Try again in {n} minute.",
                "{provider} says there were too many requests. Try again in {n} minutes.",
                minutes,
            ).format(provider=provider.name, n=minutes)
        return _("{provider} says there were too many requests. Try again later.").format(provider=provider.name)
    if isinstance(err, Unavailable):
        return _("Could not get a forecast from {provider}: {reason}").format(provider=provider.name, reason=err)
    if isinstance(err, NotConfigured):
        return _("{provider} is not set up completely: {reason}").format(provider=provider.name, reason=err)
    if isinstance(err, BadResponse):
        return _("{provider} gave an answer the app does not understand: {reason}").format(provider=provider.name, reason=err)
    return _("{provider}: {reason}").format(provider=provider.name, reason=err)


def missing_settings(provider: ForecastProvider, cfg: Config, secrets: Secrets) -> list[str]:
    """The labels of required settings that have no value."""
    values = provider_settings(cfg, secrets, provider)
    return [_(spec.label) for spec in provider.settings if not spec.optional and not values.get(spec.key)]


def try_provider(provider: ForecastProvider, cfg: Config, secrets: Secrets, now: datetime | None = None) -> Attempt:
    """One blocking fetch with these settings. Never raises. The caller has checked that the location is set."""
    tz = ZoneInfo(cfg.timezone)
    now = now or datetime.now(tz)
    missing = missing_settings(provider, cfg, secrets)
    if missing:
        return Attempt(False, message=_("{setting} is required.").format(setting=", ".join(missing)))
    try:
        steps = provider.fetch(SystemDescription.from_config(cfg), provider_settings(cfg, secrets, provider), now)
    except ProviderError as err:
        return Attempt(False, message=describe_failure(provider, err))
    except Exception as err:  # a provider bug: say what kind, not what it said (it may hold a key)
        return Attempt(False, message=_("{provider}: unexpected error ({kind})").format(provider=provider.name, kind=type(err).__name__))
    days = forecast_days(steps, now, tz)
    return Attempt(True, days, ngettext("OK: {n} day of forecast", "OK: {n} days of forecast", days).format(n=days))
