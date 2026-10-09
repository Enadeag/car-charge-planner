"""Forecast.Solar (https://forecast.solar) as a forecast provider; the public plan needs no key.

Asks for `estimate/watthours/period`: the Wh of the period "from the last timestamp to the timestamp in the key", in the
place's local time. A day's first key (sunrise, value 0) gives no step. The public plan forecasts one plane per request,
and paid plans' multi-plane limits are not visible from the key, so each array is asked for separately and the answers
added up; a rate limit on any request is one for the whole forecast.
"""

from __future__ import annotations

import math
from datetime import UTC, date, datetime, timedelta
from typing import Any, cast
from zoneinfo import ZoneInfo, ZoneInfoNotFoundError

from car_charge_planner.i18n import N_
from car_charge_planner.planner.model import ForecastStep

from ..provider import (
    BadResponse,
    PanelArray,
    PlaceSearch,
    RateLimited,
    SettingSpec,
    SystemDescription,
    Unavailable,
    each_array,
    require_valid_steps,
)
from ..transport import USER_AGENT, Reply, Request, Transport, urllib_transport

SERVER = "https://api.forecast.solar"
ROUTE = "estimate/watthours/period"


def _number(value: float, decimals: int) -> str:
    return f"{value:.{decimals}f}".rstrip("0").rstrip(".") or "0"


class ForecastSolar:
    id = "forecast-solar"
    name = "Forecast.Solar"
    description = N_(
        "A solar forecast service (Forecast.Solar) that estimates the output of your system itself, with its "
        "own allowance for losses, so the system losses setting is not used. The free public plan gives today "
        "and tomorrow, hour by hour, and allows 12 requests an hour for everyone behind your internet address. "
        "Each panel array takes one request."
    )
    terms_url = "https://doc.forecast.solar/account_models"
    attribution = "Solar forecast: Forecast.Solar"
    sends = N_(
        "Your location (latitude and longitude), the tilt and direction of your panels and their size in kWp. "
        "With an API key, the key too."
    )
    min_refresh = timedelta(minutes=60)  # 12 calls an hour are shared by everything behind your address (one per array)
    horizon = timedelta(days=2)  # the public plan: this day and the next
    max_useful_age = timedelta(hours=6)
    settings = (
        SettingSpec(
            "api_key",
            N_("API key"),
            N_("Only for a paid plan, which adds more days and finer steps. The free public plan needs none."),
            secret=True,
        ),
    )
    search_places: PlaceSearch | None = None

    def __init__(self, transport: Transport = urllib_transport) -> None:
        self.transport = transport

    @staticmethod
    def request_url(system: SystemDescription, array: PanelArray, settings: dict[str, str]) -> str:
        """The request for one panel array of the system. Tilt and direction are whole degrees in this API."""
        key = (settings.get("api_key") or "").strip()
        tilt = min(90, max(0, round(array.tilt_deg)))
        azimuth = min(180, max(-180, round(array.azimuth_deg)))
        parts = [
            SERVER,
            *([key] if key else []),
            ROUTE,
            _number(system.latitude, 4),
            _number(system.longitude, 4),
            str(tilt),
            str(azimuth),
            _number(array.kwp, 3),
        ]
        return "/".join(parts)

    def fetch(self, system: SystemDescription, settings: dict[str, str], now: datetime) -> list[ForecastStep]:
        return require_valid_steps(each_array(system, lambda array: self._fetch_array(system, array, settings, now)))

    def _fetch_array(self, system: SystemDescription, array: PanelArray, settings: dict[str, str], now: datetime) -> list[ForecastStep]:
        url = self.request_url(system, array, settings)
        try:
            reply = self.transport(Request(url, {"User-Agent": USER_AGENT, "Accept": "application/json"}))
        except OSError as err:  # the text never carries the URL: it holds the key
            raise Unavailable(f"could not reach Forecast.Solar ({type(err).__name__})") from None
        self._check_status(reply, now)
        return require_valid_steps(self._steps(reply, system))

    @staticmethod
    def _check_status(reply: Reply, now: datetime) -> None:
        if reply.ok:
            return
        if reply.status == 429:
            raise RateLimited("Forecast.Solar says too many requests", _retry_after(reply, now))
        if reply.status >= 500:
            raise Unavailable(f"Forecast.Solar has a problem (status {reply.status})")
        if reply.status in (401, 403):
            raise BadResponse(f"Forecast.Solar refused the request (status {reply.status}): check the API key")
        raise BadResponse(f"Forecast.Solar did not accept the request (status {reply.status}){_text(reply)}")

    @staticmethod
    def _steps(reply: Reply, system: SystemDescription) -> list[ForecastStep]:
        try:
            raw = reply.json()
        except ValueError:
            raise BadResponse("Forecast.Solar gave an answer that is not JSON") from None
        try:
            message = raw["message"]
            if message.get("code", 0) != 0:
                raise BadResponse(f"Forecast.Solar reports a problem: {message.get('text') or message.get('type')}")
            result = raw["result"]
            if not isinstance(result, dict) or not result:
                raise BadResponse("Forecast.Solar gave no forecast")
            series = cast("dict[str, Any]", result)
            place_zone = ZoneInfo(message.get("info", {}).get("timezone") or system.timezone)
            return _steps_from(series, place_zone, ZoneInfo(system.timezone))
        except (KeyError, TypeError, AttributeError, ValueError, OverflowError, ZoneInfoNotFoundError):
            raise BadResponse("Forecast.Solar gave an answer that is not what it documents") from None


def _steps_from(series: dict[str, Any], place_zone: ZoneInfo, out_zone: ZoneInfo) -> list[ForecastStep]:
    """Each key is a local time at the place; each value the Wh of the period ending at it."""
    points: list[tuple[datetime, date, float]] = []
    for key, watt_hours in series.items():
        local = datetime.strptime(key, "%Y-%m-%d %H:%M:%S").replace(tzinfo=place_zone)
        energy = float(watt_hours or 0.0)
        if not math.isfinite(energy):
            raise ValueError("energy is not a finite number")
        points.append((local.astimezone(UTC), local.date(), energy))
    points.sort(key=lambda p: p[0])
    steps: list[ForecastStep] = []
    previous = None
    for instant, day, energy in points:
        if previous is not None and previous[1] == day and instant > previous[0]:
            hours = (instant - previous[0]) / timedelta(hours=1)
            steps.append(ForecastStep(previous[0].astimezone(out_zone), instant.astimezone(out_zone), energy / 1000 / hours))
        previous = (instant, day)
    return steps


def _retry_after(reply: Reply, now: datetime) -> timedelta | None:
    """When it may be asked again: the header, else the body, as a time; None when neither says."""
    stamp = reply.header("X-Ratelimit-Retry-At")
    if not stamp:
        try:
            stamp = reply.json()["message"]["ratelimit"]["retry-at"]
        except (ValueError, KeyError, TypeError):
            return None
    try:
        retry_at = datetime.fromisoformat(stamp)
    except (ValueError, TypeError):
        return None
    if retry_at.tzinfo is None:
        return None
    return max(timedelta(0), retry_at - now)


def _text(reply: Reply) -> str:
    try:
        text = reply.json()["message"]["text"]
    except (ValueError, KeyError, TypeError):
        return ""
    return f": {text}" if text else ""
