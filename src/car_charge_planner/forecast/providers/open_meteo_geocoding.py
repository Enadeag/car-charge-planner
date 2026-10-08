"""Open-Meteo's geocoding service as a provider's place search.

https://geocoding-api.open-meteo.com/v1/search?name=<text>&count=<n>&language=<xx>. Two characters match a name
exactly, three or more by the start of a name; the answer lists places ranked by size, each with coordinates
and a time zone; no match is an answer without a `results` list. Location data is from GeoNames. The same
terms as the forecast service apply (the free service is for non-commercial use). Checked 2026-10-07.
"""

from __future__ import annotations

import urllib.parse
from datetime import timedelta
from zoneinfo import ZoneInfo, ZoneInfoNotFoundError

from ..provider import Place, PlaceResults, Problem
from ..transport import Reply, Request, Transport, urllib_transport

SERVER = "https://geocoding-api.open-meteo.com"
PATH = "/v1/search"
MIN_CHARACTERS = 2
RESULTS = 8
TIMEOUT_S = 6  # a person is waiting at a search box


class OpenMeteoGeocoder:
    def __init__(self, transport: Transport = urllib_transport) -> None:
        self.transport = transport

    def __call__(self, text: str, language: str = "en") -> PlaceResults:
        return self.search(text, language)

    def search(self, text: str, language: str = "en") -> PlaceResults:
        text = " ".join(text.split())
        if len(text) < MIN_CHARACTERS:
            return PlaceResults(problem=Problem.TOO_SHORT)  # nothing is sent for it
        query = {"name": text, "count": RESULTS, "language": (language or "en").split("_")[0].lower(), "format": "json"}
        url = f"{SERVER}{PATH}?{urllib.parse.urlencode(query)}"
        try:
            reply = self.transport(Request(url, timeout_s=TIMEOUT_S))
        except OSError:
            return PlaceResults(problem=Problem.UNAVAILABLE)
        if reply.status == 429:
            return PlaceResults(problem=Problem.RATE_LIMITED, retry_after=_retry_after(reply))
        if reply.status >= 500:
            return PlaceResults(problem=Problem.UNAVAILABLE)
        if not reply.ok:
            return PlaceResults(problem=Problem.BAD_RESPONSE)
        try:
            raw = reply.json()
            if not isinstance(raw, dict) or raw.get("error"):
                return PlaceResults(problem=Problem.BAD_RESPONSE)
            return PlaceResults(places=tuple(p for p in (_place(r) for r in raw.get("results") or []) if p is not None))
        except (ValueError, TypeError, AttributeError):
            return PlaceResults(problem=Problem.BAD_RESPONSE)


def _place(row) -> Place | None:
    """One result, or None when it lacks what a location needs (coordinates, a time zone we know)."""
    try:
        latitude, longitude = float(row["latitude"]), float(row["longitude"])
        timezone = row["timezone"]
        ZoneInfo(timezone)
        if not (-90 <= latitude <= 90 and -180 <= longitude <= 180) or not row.get("name"):
            return None
    except (KeyError, TypeError, ValueError, ZoneInfoNotFoundError):
        return None
    return Place(
        name=str(row["name"]),
        latitude=latitude,
        longitude=longitude,
        timezone=timezone,
        region=str(row.get("admin1") or ""),
        country=str(row.get("country") or row.get("country_code") or ""),
        area=str(row.get("admin2") or ""),
    )


def _retry_after(reply: Reply) -> timedelta | None:
    value = (reply.header("Retry-After") or "").strip()
    return timedelta(seconds=int(value)) if value.isdigit() else None
