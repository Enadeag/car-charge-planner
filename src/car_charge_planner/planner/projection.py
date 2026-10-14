"""Energy need and battery projection over the forecast days."""

from __future__ import annotations

from dataclasses import replace
from datetime import datetime, timedelta
from zoneinfo import ZoneInfo

from .model import AwayPeriod, DayPlan, ForecastStep, HomeSystem, PlanningPreferences, Window, elapsed_hours, utc
from .surplus import expand_away
from .windows import build_runs, group_runs_by_day, select_windows


def daily_driving_pct(system: HomeSystem) -> float:
    kwh = system.car.km_per_day * system.car.consumption_kwh_per_100km / 100
    return kwh / system.car.usable_battery_kwh * 100


def energy_needed_kwh(soc_pct: float, system: HomeSystem, preferences: PlanningPreferences) -> float:
    """Grid-side energy to reach the target level from soc_pct."""
    battery_kwh = max(0.0, preferences.target_pct - soc_pct) / 100 * system.car.usable_battery_kwh
    return battery_kwh / system.charger.efficiency


def away_periods(forecast: list[ForecastStep], system: HomeSystem, manual: list[AwayPeriod] | None) -> list[AwayPeriod]:
    """Recurring rules plus manual entries, covering every forecast day."""
    tz = ZoneInfo(system.timezone)
    first_day = min((s.start for s in forecast), key=utc).astimezone(tz).date()
    last_day = _last_day(forecast, tz)
    return expand_away(system.away_rules, manual or [], first_day, (last_day - first_day).days + 2, tz)


def _last_day(forecast: list[ForecastStep], tz: ZoneInfo):
    # a step ending exactly at midnight belongs to the day before
    return (max(utc(s.end) for s in forecast) - timedelta(microseconds=1)).astimezone(tz).date()


def _share_of_day_left(now: datetime, tz: ZoneInfo) -> float:
    """Fraction of today still to come: driving already done is in the battery reading."""
    today = now.astimezone(tz).date()
    start = datetime.combine(today, datetime.min.time(), tz)
    end = datetime.combine(today + timedelta(days=1), datetime.min.time(), tz)
    return min(1.0, max(0.0, elapsed_hours(now, end) / elapsed_hours(start, end)))


def _remaining_parts(windows: list[Window], now: datetime) -> list[Window]:
    """The same windows with only the energy still to come, start and power unchanged."""
    parts: list[Window] = []
    for w in windows:
        share = elapsed_hours(now, w.end) / elapsed_hours(w.start, w.end)
        parts.append(replace(w, solar_kwh=w.solar_kwh * share, import_kwh=w.import_kwh * share))
    return parts


def plan(
    forecast: list[ForecastStep],
    system: HomeSystem,
    preferences: PlanningPreferences,
    soc_pct: float,
    now: datetime,
    manual_away: list[AwayPeriod] | None = None,
    pinned: list[Window] | None = None,
) -> list[DayPlan]:
    """Day-by-day plan assuming the owner follows it.

    Driving is applied at the end of each day, so day N's charging starts
    from the level left by the driving of day N-1. Today only gets the share
    of a day's driving that is still ahead of us.

    A pinned window (one already announced) that is running now is kept as it
    was: same power and end, so the advice does not drift while the owner is
    charging at the power they set. Only its remaining energy is counted.
    """
    if not forecast:
        return []
    tz = ZoneInfo(system.timezone)
    last_day = _last_day(forecast, tz)
    away = away_periods(forecast, system, manual_away)
    running = _remaining_parts([w for w in pinned or [] if utc(w.start) <= utc(now) < utc(w.end)], now)
    blocked = away + [AwayPeriod(w.start, w.end) for w in running]
    runs = build_runs(forecast, system, preferences, blocked, now)
    # sun left over right before or after a running window belongs to it: no second window there
    runs = [r for r in runs if not any(utc(r[0].start) == utc(w.end) or utc(r[-1].end) == utc(w.start) for w in running)]
    by_day = group_runs_by_day(runs, tz)

    plans: list[DayPlan] = []
    today = now.astimezone(tz).date()
    day = today
    soc = soc_pct
    while day <= last_day:
        kept = [w for w in running if w.start.astimezone(tz).date() == day]
        need = max(0.0, energy_needed_kwh(soc, system, preferences) - sum(w.solar_kwh for w in kept))
        windows = tuple(sorted([*kept, *select_windows(by_day.get(day, []), need, system, preferences)], key=lambda w: utc(w.start)))
        gained = sum(w.solar_kwh for w in windows) * system.charger.efficiency
        after = max(soc, min(preferences.target_pct, soc + gained / system.car.usable_battery_kwh * 100))
        driving = daily_driving_pct(system) * (_share_of_day_left(now, tz) if day == today else 1.0)
        end_of_day = max(0.0, after - driving)
        plans.append(DayPlan(day, soc, windows, after, end_of_day, driving))
        soc = end_of_day
        day = day.fromordinal(day.toordinal() + 1)
    return plans
