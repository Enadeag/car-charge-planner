"""The forecast service: which provider to ask, when, and what to do when it cannot answer.

Per refresh it asks the primary provider first and keeps using the primary's last good forecast for as long as
that is still useful; only when the primary has nothing usable is the fallback asked. A provider that says it
is rate limited is left alone until it says it may be asked again. See `.scratch/forecast-providers/spec.md`
section 5. The service is blocking: the controller runs it off the UI thread.
"""

from __future__ import annotations

import json
import logging
import os
import threading
from dataclasses import asdict, dataclass
from datetime import datetime, timedelta, timezone
from pathlib import Path
from typing import Callable
from zoneinfo import ZoneInfo

from app.i18n import _
from planner.model import Config, ForecastStep

from .provider import (
    BadResponse,
    ForecastProvider,
    ProviderError,
    RateLimited,
    SystemDescription,
    Unavailable,
    check_steps,
)
from .registry import ProviderRegistry, registry as builtin
from .secret_store import Secrets, SecretsError, load_secrets
from .selection import provider_settings

log = logging.getLogger(__name__)

RETRY_AFTER_UNAVAILABLE = timedelta(minutes=5)  # a call that never got an answer did not use the provider's allowance


class ForecastUnavailable(Exception):
    """No provider gave a forecast and nothing usable is cached. The message says why, per provider."""


@dataclass(frozen=True)
class ForecastResult:
    steps: list[ForecastStep]
    fetched_at: datetime
    from_cache: bool  # True when the network was not used for this result
    stale: bool  # True when a refresh was due but could not be done
    provider_id: str = ""
    provider_name: str = ""
    attribution: str = ""
    from_fallback: bool = False  # the primary had nothing usable
    primary_name: str = ""  # who was asked first, for "from <fallback>, <primary> unavailable"
    cooldown_until: datetime | None = None  # the provider that gave this is rate limited until then

    def age(self, now: datetime) -> timedelta:
        return now - self.fetched_at


def default_cache_dir() -> Path:
    base = Path(os.environ.get("XDG_CACHE_HOME", Path.home() / ".cache"))
    return base / "car-charge-planner"


def _default_secrets() -> Secrets:
    from app.config_file import secrets_path

    return load_secrets(secrets_path())


def forecast_key(cfg: Config) -> dict:
    """What a forecast depends on. When it changes, a forecast shown or cached for the old one no longer applies."""
    return {
        "primary": cfg.forecast.primary,
        "fallback": cfg.forecast.fallback,
        "system": asdict(SystemDescription.from_config(cfg)),
        "settings": cfg.forecast.settings,
    }


@dataclass
class _Usable:
    steps: list[ForecastStep]
    fetched_at: datetime
    from_cache: bool
    stale: bool
    provider: ForecastProvider
    cooldown_until: datetime | None = None


@dataclass
class _Failure:
    reason: str


class ForecastService:
    def __init__(
        self,
        registry: ProviderRegistry | None = None,
        cache_dir: Path | None = None,
        secrets: Callable[[], Secrets] | None = None,
    ) -> None:
        self.registry = registry or builtin
        self.cache_dir = Path(cache_dir) if cache_dir else default_cache_dir()
        self._secrets = secrets or _default_secrets
        self._lock = threading.Lock()  # one fetch at a time: two at once would both spend the provider's allowance
        self._retry_at: dict[str, datetime] = {}  # provider id -> not before (after a failed or unanswered attempt)

    # -- asking ----------------------------------------------------------------------------------------------

    def get(self, cfg: Config, now: datetime, force: bool = False) -> ForecastResult:
        """A forecast for the config, or `ForecastUnavailable`. `force` skips the minimum refresh interval (the
        owner pressed refresh) but never a provider's cool-down."""
        with self._lock:
            try:
                secrets, problem = self._secrets(), ""
            except SecretsError as err:  # the providers that need no key still work
                secrets, problem = {}, str(err)
            system = SystemDescription.from_config(cfg)
            order = [cfg.forecast.primary] + ([cfg.forecast.fallback] if cfg.forecast.fallback else [])
            reasons: list[str] = []
            primary_name = ""
            for index, provider_id in enumerate(order):
                outcome = self._from(provider_id, cfg, system, secrets, now, force)
                if index == 0 and provider_id in self.registry:
                    primary_name = self.registry.get(provider_id).name
                if isinstance(outcome, _Usable):
                    provider = outcome.provider
                    return ForecastResult(
                        outcome.steps, outcome.fetched_at, outcome.from_cache, outcome.stale,
                        provider.id, provider.name, provider.attribution,
                        from_fallback=index > 0, primary_name=primary_name if index > 0 else "",
                        cooldown_until=outcome.cooldown_until,
                    )
                reasons.append(outcome.reason)
            if problem:
                reasons.append(problem)
            raise ForecastUnavailable("; ".join(reasons))

    def _from(self, provider_id: str, cfg: Config, system: SystemDescription, secrets: Secrets, now: datetime, force: bool):
        if provider_id not in self.registry:
            return _Failure(_("The forecast provider {id!r} is not known to this version of the app").format(id=provider_id))
        provider = self.registry.get(provider_id)
        settings = provider_settings(cfg, secrets, provider)
        missing = [_(spec.label) for spec in provider.settings if not spec.optional and not settings.get(spec.key)]
        if missing:  # nothing is sent without what the provider requires
            return _Failure(_("{provider}: {settings} is required").format(provider=provider.name, settings=", ".join(missing)))
        key = _cache_key(system, provider, cfg)
        path = self.cache_dir / f"forecast-{provider.id}.json"
        blob = _read_blob(path)
        cached = _cached_forecast(blob, key, ZoneInfo(cfg.timezone))
        cooldown_until = _cooldown(blob)
        cooling = cooldown_until is not None and now < cooldown_until
        usable = cached is not None and now - cached[1] <= provider.max_useful_age

        if cached and not force and now - cached[1] < provider.min_refresh:
            return _Usable(cached[0], cached[1], True, False, provider, cooldown_until if cooling else None)
        waiting = self._retry_at.get(provider.id)
        if cooling or (waiting is not None and now < waiting and not force):
            until = cooldown_until if cooling else waiting
            if usable:
                return _Usable(cached[0], cached[1], True, True, provider, cooldown_until if cooling else None)
            return _Failure(_("{provider}: not asked again until {time}").format(provider=provider.name, time=until.astimezone(now.tzinfo).strftime("%H:%M")))

        try:
            steps = provider.fetch(system, settings, now)
            problems = check_steps(steps)
            if problems:
                raise BadResponse("; ".join(problems[:3]))
        except RateLimited as err:
            until = now + (err.retry_after if err.retry_after is not None else provider.min_refresh)
            _write_blob(path, {"cooldown_until": until.timestamp()})
            return self._after_failure(provider, err, cached, usable, until)
        except Unavailable as err:
            self._retry_at[provider.id] = now + RETRY_AFTER_UNAVAILABLE
            return self._after_failure(provider, err, cached, usable, None)
        except ProviderError as err:
            self._retry_at[provider.id] = now + provider.min_refresh  # it answered: that call counted
            return self._after_failure(provider, err, cached, usable, None)
        except Exception as err:  # a bug in a provider must not take the app down; the text may hold a secret
            log.error("forecast provider %s failed unexpectedly (%s)", provider.id, type(err).__name__)
            self._retry_at[provider.id] = now + provider.min_refresh
            return self._after_failure(provider, BadResponse(_("an unexpected error ({kind})").format(kind=type(err).__name__)), cached, usable, None)
        self._retry_at.pop(provider.id, None)
        fetched_at = now.astimezone(timezone.utc)
        _write_blob(path, {"key": key, "fetched_at": fetched_at.timestamp(), "steps": _dump_steps(steps), "cooldown_until": None})
        return _Usable(list(steps), fetched_at, False, False, provider)

    @staticmethod
    def _after_failure(provider, err: ProviderError, cached, usable: bool, cooldown_until):
        if usable:
            return _Usable(cached[0], cached[1], True, True, provider, cooldown_until)
        return _Failure(f"{provider.name}: {err}")


# ---- the cache -------------------------------------------------------------------------------------------------


def _cache_key(system: SystemDescription, provider: ForecastProvider, cfg: Config) -> dict:
    plain = {k: v for k, v in cfg.forecast.settings.get(provider.id, {}).items() if k in {s.key for s in provider.settings if not s.secret}}
    return {"system": asdict(system), "settings": plain}


def _read_blob(path: Path) -> dict:
    try:
        blob = json.loads(path.read_text())
        return blob if isinstance(blob, dict) else {}
    except (OSError, ValueError):
        return {}


def _write_blob(path: Path, updates: dict) -> None:
    blob = _read_blob(path)
    blob.update(updates)
    try:
        path.parent.mkdir(parents=True, exist_ok=True)
        tmp = path.with_suffix(".tmp")
        tmp.write_text(json.dumps(blob))
        tmp.replace(path)  # never leave a half-written cache
    except OSError:
        pass  # a read-only cache directory must not break planning


def _dump_steps(steps: list[ForecastStep]) -> list[list[float]]:
    return [[s.start.timestamp(), s.end.timestamp(), s.power_kw] for s in steps]


def _cached_forecast(blob: dict, key: dict, tz: ZoneInfo) -> tuple[list[ForecastStep], datetime] | None:
    """The cached steps and when they were fetched, if they are for this system and settings and intact."""
    try:
        if blob["key"] != key:
            return None
        fetched_at = datetime.fromtimestamp(blob["fetched_at"], timezone.utc)
        steps = [
            ForecastStep(
                datetime.fromtimestamp(a, timezone.utc).astimezone(tz), datetime.fromtimestamp(b, timezone.utc).astimezone(tz), float(p)
            )
            for a, b, p in blob["steps"]
        ]
        return (steps, fetched_at) if steps and not check_steps(steps) else None
    except (KeyError, TypeError, ValueError, OverflowError, OSError):
        return None


def _cooldown(blob: dict) -> datetime | None:
    try:
        stamp = blob.get("cooldown_until")
        return datetime.fromtimestamp(stamp, timezone.utc) if stamp else None
    except (TypeError, ValueError, OverflowError, OSError):
        return None
