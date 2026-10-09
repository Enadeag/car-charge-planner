"""Open-Meteo (https://open-meteo.com) as a forecast provider: it forecasts irradiance on a tilted plane, which is turned into power.

Hourly irradiance is the mean of the *preceding* hour, so the value stamped T covers [T-1h, T). The irradiance is for one
plane, so each panel array is one request and the arrays' power is added up hour by hour.
"""

from __future__ import annotations

import urllib.parse
from datetime import UTC, datetime, timedelta
from typing import Any, cast
from zoneinfo import ZoneInfo, ZoneInfoNotFoundError

from car_charge_planner.i18n import N_
from car_charge_planner.planner.model import ForecastStep

from ..provider import (
    BadResponse,
    NotConfigured,
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
from .open_meteo_geocoding import OpenMeteoGeocoder

PUBLIC_SERVER = "https://api.open-meteo.com"
PATH = "/v1/forecast"
FORECAST_DAYS = 4


def power_kw(irradiance_wm2: float, kwp: float, system_losses: float) -> float:
    """AC power of a system from the irradiance on its panels."""
    return kwp * (irradiance_wm2 / 1000) * (1 - system_losses)


class OpenMeteo:
    id = "open-meteo"
    name = "Open-Meteo"
    description = N_(
        "A free weather service run by Open-Meteo.com. It forecasts the sunlight on your panels and the app "
        "turns that into the power of your system. For non-commercial use."
    )
    terms_url = "https://open-meteo.com/en/terms"
    attribution = "Weather data: Open-Meteo.com (CC BY 4.0)"
    sends = N_(
        "Your location (latitude and longitude), your time zone and the tilt and direction of your panels. "
        "With an API key, the key too."
    )
    min_refresh = timedelta(minutes=60)
    horizon = timedelta(days=FORECAST_DAYS)
    max_useful_age = timedelta(hours=12)
    settings = (
        SettingSpec(
            "base_url",
            N_("Server address"),
            N_("Leave empty for the free public service. A paid plan, or a server of your own, has its own address."),
        ),
        SettingSpec(
            "api_key",
            N_("API key"),
            N_("Only for a paid plan (commercial use). Free use needs none."),
            secret=True,
        ),
    )
    place_attribution = "Place data: GeoNames, via Open-Meteo.com"  # shown where its place search is used

    def __init__(self, transport: Transport = urllib_transport) -> None:
        self.transport = transport
        self.search_places: PlaceSearch | None = OpenMeteoGeocoder(transport)  # its place search: the same service family and terms

    @staticmethod
    def request_url(system: SystemDescription, array: PanelArray, settings: dict[str, str]) -> str:
        """The request for one panel array of the system."""
        server = (settings.get("base_url") or "").strip().rstrip("/") or PUBLIC_SERVER
        if not (server.startswith("https://") or server.startswith("http://localhost") or server.startswith("http://127.0.0.1")):
            raise NotConfigured("the server address must start with https://")
        query = {
            "latitude": system.latitude,
            "longitude": system.longitude,
            "hourly": "global_tilted_irradiance",
            "tilt": array.tilt_deg,
            "azimuth": array.azimuth_deg,
            "timezone": system.timezone,
            "forecast_days": FORECAST_DAYS,
            "timeformat": "unixtime",
        }
        key = (settings.get("api_key") or "").strip()
        if key:
            query["apikey"] = key  # the way Open-Meteo documents it for its customer servers
        return f"{server}{PATH}?{urllib.parse.urlencode(query)}"

    def fetch(self, system: SystemDescription, settings: dict[str, str], now: datetime) -> list[ForecastStep]:
        for array in system.arrays:
            self.request_url(system, array, settings)  # may raise NotConfigured: before anything is sent
        return require_valid_steps(each_array(system, lambda array: self._fetch_array(system, array, settings)))

    def _fetch_array(self, system: SystemDescription, array: PanelArray, settings: dict[str, str]) -> list[ForecastStep]:
        url = self.request_url(system, array, settings)
        try:
            reply = self.transport(Request(url, {"User-Agent": USER_AGENT}))
        except OSError as err:  # includes timeouts and URLError; the text never carries the URL (it may hold the key)
            raise Unavailable(f"could not reach Open-Meteo ({type(err).__name__})") from None
        self._check_status(reply)
        return require_valid_steps(self._steps(reply, system, array))

    @staticmethod
    def _check_status(reply: Reply) -> None:
        if reply.ok:
            return
        if reply.status == 429:
            raise RateLimited("Open-Meteo says too many requests", _retry_after(reply))
        if reply.status >= 500:
            raise Unavailable(f"Open-Meteo has a problem (status {reply.status})")
        if reply.status in (401, 403):
            raise BadResponse(f"Open-Meteo refused the request (status {reply.status}): check the API key and server address")
        raise BadResponse(f"Open-Meteo did not accept the request (status {reply.status}){_reason(reply)}")

    @staticmethod
    def _steps(reply: Reply, system: SystemDescription, array: PanelArray) -> list[ForecastStep]:
        try:
            raw = reply.json()
        except ValueError:
            raise BadResponse("Open-Meteo gave an answer that is not JSON") from None
        if not isinstance(raw, dict):
            raise BadResponse("Open-Meteo gave an unexpected answer")
        raw = cast("dict[str, Any]", raw)
        if raw.get("error"):
            raise BadResponse(f"Open-Meteo reports an error: {raw.get('reason', 'no reason given')}")
        try:
            hourly = raw["hourly"]
            stamps, irradiance = hourly["time"], hourly["global_tilted_irradiance"]
            if len(stamps) != len(irradiance):
                raise ValueError("the lists differ in length")
            tz = ZoneInfo(system.timezone)
            steps: list[ForecastStep] = []
            for stamp, value in zip(stamps, irradiance, strict=False):
                end = datetime.fromtimestamp(stamp, UTC).astimezone(tz)
                start = (end.astimezone(UTC) - timedelta(hours=1)).astimezone(tz)
                watts = float(value or 0.0)  # null at the edge of the horizon
                steps.append(ForecastStep(start, end, power_kw(watts, array.kwp, array.system_losses)))
        except (KeyError, TypeError, ValueError, OverflowError, OSError, ZoneInfoNotFoundError):
            raise BadResponse("Open-Meteo gave an answer that is not what it documents") from None
        return steps


def _retry_after(reply: Reply) -> timedelta | None:
    value = (reply.header("Retry-After") or "").strip()
    return timedelta(seconds=int(value)) if value.isdigit() else None


def _reason(reply: Reply) -> str:
    try:
        reason = reply.json().get("reason")
    except (ValueError, AttributeError):
        return ""
    return f": {reason}" if reason else ""
