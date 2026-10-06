"""Open-Meteo forecast client with an on-disk cache (spec 4.1, 4.7).

Open-Meteo is free for non-commercial use under CC BY 4.0
(https://open-meteo.com). Hourly irradiance is the mean of the *preceding*
hour, so the value stamped T covers [T-1h, T).
"""

from __future__ import annotations

import json
import os
import urllib.error
import urllib.parse
import urllib.request
from dataclasses import dataclass
from datetime import datetime, timedelta, timezone
from pathlib import Path
from typing import Callable
from zoneinfo import ZoneInfo

from planner.model import Config, ForecastStep

URL = "https://api.open-meteo.com/v1/forecast"
USER_AGENT = "car-charge-planner/0.1 (personal use)"
MIN_REFRESH = timedelta(minutes=60)
FORECAST_DAYS = 4


class ForecastUnavailable(Exception):
    """No fresh forecast and nothing usable in the cache."""


@dataclass(frozen=True)
class ForecastResult:
    steps: list[ForecastStep]
    fetched_at: datetime
    from_cache: bool  # True when the network was not used for this result
    stale: bool  # True when a refresh was due but failed

    def age(self, now: datetime) -> timedelta:
        return now - self.fetched_at


def default_cache_path() -> Path:
    base = Path(os.environ.get("XDG_CACHE_HOME", Path.home() / ".cache"))
    return base / "car-charge-planner" / "forecast.json"


def request_url(cfg: Config, days: int = FORECAST_DAYS) -> str:
    query = {
        "latitude": cfg.latitude,
        "longitude": cfg.longitude,
        "hourly": "global_tilted_irradiance,cloud_cover",
        "tilt": cfg.panels.tilt_deg,
        "azimuth": cfg.panels.azimuth_deg,
        "timezone": cfg.timezone,
        "forecast_days": days,
        "timeformat": "unixtime",
    }
    return f"{URL}?{urllib.parse.urlencode(query)}"


def http_get(url: str) -> dict:
    req = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    with urllib.request.urlopen(req, timeout=15) as resp:
        return json.load(resp)


def parse(raw: dict, cfg: Config) -> list[ForecastStep]:
    if raw.get("error"):
        raise ValueError(f"Open-Meteo error: {raw.get('reason')}")
    hourly = raw["hourly"]
    tz = ZoneInfo(cfg.timezone)
    steps = []
    for stamp, gti in zip(hourly["time"], hourly["global_tilted_irradiance"]):
        end = datetime.fromtimestamp(stamp, timezone.utc).astimezone(tz)
        start = (end.astimezone(timezone.utc) - timedelta(hours=1)).astimezone(tz)
        steps.append(ForecastStep(start, end, float(gti or 0.0)))  # null at the horizon edge
    return steps


def _cache_key(cfg: Config) -> dict:
    return {
        "latitude": cfg.latitude,
        "longitude": cfg.longitude,
        "tilt": cfg.panels.tilt_deg,
        "azimuth": cfg.panels.azimuth_deg,
        "timezone": cfg.timezone,
    }


def _read_cache(path: Path, cfg: Config) -> tuple[dict, datetime] | None:
    try:
        blob = json.loads(path.read_text())
        if blob["key"] != _cache_key(cfg):
            return None  # panel or location settings changed
        return blob["response"], datetime.fromtimestamp(blob["fetched_at"], timezone.utc)
    except (OSError, ValueError, KeyError):
        return None


def _write_cache(path: Path, cfg: Config, raw: dict, fetched_at: datetime) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    tmp = path.with_suffix(".tmp")
    tmp.write_text(json.dumps({"key": _cache_key(cfg), "fetched_at": fetched_at.timestamp(), "response": raw}))
    tmp.replace(path)  # never leave a half-written cache


def get_forecast(
    cfg: Config,
    now: datetime,
    cache_path: Path | None = None,
    fetch: Callable[[str], dict] = http_get,
    force: bool = False,
) -> ForecastResult:
    """Cached forecast; refetches when older than MIN_REFRESH; falls back to the cache on failure."""
    path = cache_path or default_cache_path()
    cached = _read_cache(path, cfg)
    if cached and not force and now - cached[1] < MIN_REFRESH:
        return ForecastResult(parse(cached[0], cfg), cached[1], from_cache=True, stale=False)
    try:
        raw = fetch(request_url(cfg))
        steps = parse(raw, cfg)
    except (urllib.error.URLError, OSError, ValueError, KeyError) as err:
        if cached:
            return ForecastResult(parse(cached[0], cfg), cached[1], from_cache=True, stale=True)
        raise ForecastUnavailable(str(err)) from err
    fetched_at = now.astimezone(timezone.utc)
    try:
        _write_cache(path, cfg, raw, fetched_at)
    except OSError:
        pass  # a read-only cache dir must not break planning
    return ForecastResult(steps, fetched_at, from_cache=False, stale=False)
