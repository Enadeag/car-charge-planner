"""Synthetic forecasts for trying the planner without network access."""

from __future__ import annotations

import math
from datetime import UTC, date, datetime, timedelta
from zoneinfo import ZoneInfo


def bell_day(day: date, peak_wm2: float, tz: ZoneInfo, sunrise: float = 6, sunset: float = 18) -> list[tuple[datetime, float]]:
    """Hourly irradiance for one local day, shaped like a half sine between sunrise and sunset.

    Steps are built on the UTC timeline so DST days have 23 or 25 steps.
    """
    midnight = datetime.combine(day, datetime.min.time(), tz).astimezone(UTC)
    nxt = datetime.combine(day + timedelta(days=1), datetime.min.time(), tz).astimezone(UTC)
    out: list[tuple[datetime, float]] = []
    moment = midnight
    while moment < nxt:
        local = moment.astimezone(tz)
        hour = local.hour + local.minute / 60 + 0.5
        value = 0.0
        if sunrise <= hour <= sunset:
            value = peak_wm2 * math.sin(math.pi * (hour - sunrise) / (sunset - sunrise))
        out.append((local, value))
        moment += timedelta(hours=1)
    return out
