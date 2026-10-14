"""Grid-charge advice: when the battery would drop below the minimum."""

from __future__ import annotations

import math
from dataclasses import dataclass
from datetime import UTC, date, datetime, timedelta
from zoneinfo import ZoneInfo

from .model import AwayPeriod, DayPlan, ForecastStep, HomeSystem, PlanningPreferences, Window, add_hours, ceil_to_five_minutes, utc
from .projection import away_periods
from .surplus import charge_power_kw, usable_kw

SLOT = timedelta(minutes=15)
SLOT_H = 0.25
_EPS = 1e-9
MIN_DEFICIT_PCT = 1.0  # a shortfall smaller than this is forecast noise, not worth a warning


@dataclass(frozen=True)
class GridAdvice:
    day: date  # the day the battery would end below the minimum
    soc_end_pct: float  # projected level at the end of that day, may be negative
    deficit_pct: float
    energy_kwh: float  # grid-side energy needed to stay at the minimum
    window: Window | None  # None: no free time at home to fit it


def _day_slots(day: date, tz: ZoneInfo) -> list[tuple[datetime, datetime]]:
    """15-minute slots over one local day, built on the UTC timeline so DST days are right."""
    start = datetime.combine(day, datetime.min.time(), tz).astimezone(UTC)
    end = datetime.combine(day + timedelta(days=1), datetime.min.time(), tz).astimezone(UTC)
    slots: list[tuple[datetime, datetime]] = []
    while start < end:
        slots.append((start.astimezone(tz), (start + SLOT).astimezone(tz)))
        start += SLOT
    return slots


def _best_placement(
    day: date,
    energy_kwh: float,
    forecast: list[ForecastStep],
    system: HomeSystem,
    preferences: PlanningPreferences,
    away: list[AwayPeriod],
    planned: list[Window],
    now: datetime,
) -> Window | None:
    tz = ZoneInfo(system.timezone)
    slots = _day_slots(day, tz)
    usable: list[float | None] = []
    for start, _ in slots:
        step = next((s for s in forecast if utc(s.start) <= utc(start) < utc(s.end)), None)
        usable.append(usable_kw(step, system, preferences) if step else None)
    free = [
        usable[i] is not None
        and utc(slots[i][0]) >= utc(now)
        and not any(utc(a.start) < utc(slots[i][1]) and utc(slots[i][0]) < utc(a.end) for a in away)
        and not any(utc(w.start) < utc(slots[i][1]) and utc(slots[i][0]) < utc(w.end) for w in planned)
        for i in range(len(slots))
    ]
    run = [0] * (len(slots) + 1)  # consecutive free slots starting at i
    for i in range(len(slots) - 1, -1, -1):
        run[i] = run[i + 1] + 1 if free[i] else 0

    best = None
    best_key = None
    for amps in range(system.charger.min_amps, system.charger.max_amps + 1):
        power = charge_power_kw(amps, system)
        hours = energy_kwh / power
        n = math.ceil(hours / SLOT_H - _EPS)
        if n < 1 or n > len(slots):
            continue
        last_h = hours - (n - 1) * SLOT_H
        grid_prefix, sun_prefix = [0.0], [0.0]
        for u in usable:
            u = u or 0.0
            grid_prefix.append(grid_prefix[-1] + max(0.0, power - u) * SLOT_H)
            sun_prefix.append(sun_prefix[-1] + min(power, u) * SLOT_H)
        for i in range(len(slots) - n + 1):
            if run[i] < n:
                continue
            u_last = usable[i + n - 1] or 0.0
            imported = grid_prefix[i + n - 1] - grid_prefix[i] + max(0.0, power - u_last) * last_h
            solar = sun_prefix[i + n - 1] - sun_prefix[i] + min(power, u_last) * last_h
            key = (round(imported, 6), amps, slots[i][0].timestamp())
            if best_key is None or key < best_key:
                best_key = key
                start = slots[i][0]
                end = min(ceil_to_five_minutes(add_hours(start, hours)), slots[i + n - 1][1], key=utc)
                best = Window(start, end, amps, power, solar, imported)
    return best


def grid_advice(
    days: list[DayPlan],
    forecast: list[ForecastStep],
    system: HomeSystem,
    preferences: PlanningPreferences,
    manual_away: list[AwayPeriod] | None,
    now: datetime,
) -> GridAdvice | None:
    """Advice for the first day the battery would end below the minimum charge level, if any.

    Looks at the end-of-day level after planned solar charging and driving, so a dip
    that the sun will fix before it happens does not trigger a warning.
    """
    minimum = preferences.minimum_pct
    dip = next((d for d in days if d.soc_after_charging_pct - d.driving_pct < minimum - MIN_DEFICIT_PCT), None)
    if dip is None or not forecast:
        return None
    end_pct = dip.soc_after_charging_pct - dip.driving_pct
    deficit = minimum - end_pct
    energy = deficit / 100 * system.car.usable_battery_kwh / system.charger.efficiency

    away = away_periods(forecast, system, manual_away)
    by_date = {d.date: d for d in days}
    window = None
    day = dip.date
    while day >= days[0].date and window is None:  # latest feasible day first
        window = _best_placement(day, energy, forecast, system, preferences, away, list(by_date[day].windows), now)
        day -= timedelta(days=1)
    return GridAdvice(dip.date, end_pct, deficit, energy, window)
