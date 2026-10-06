"""Energy need and battery projection over the forecast days (spec 4.5)."""

from __future__ import annotations

from dataclasses import replace
from datetime import datetime, timedelta, timezone
from zoneinfo import ZoneInfo

from .model import AwayPeriod, Config, DayPlan, ForecastStep, Window, elapsed_hours
from .surplus import expand_away
from .windows import build_runs, group_runs_by_day, select_windows


def daily_driving_pct(cfg: Config) -> float:
    kwh = cfg.car.km_per_day * cfg.car.consumption_kwh_per_100km / 100
    return kwh / cfg.car.usable_battery_kwh * 100


def energy_needed_kwh(soc_pct: float, cfg: Config) -> float:
    """Grid-side energy to reach the target level from soc_pct."""
    battery_kwh = max(0.0, cfg.car.target_pct - soc_pct) / 100 * cfg.car.usable_battery_kwh
    return battery_kwh / cfg.charger.efficiency


def away_periods(forecast: list[ForecastStep], cfg: Config, manual: list[AwayPeriod] | None) -> list[AwayPeriod]:
    """Recurring rules plus manual entries, covering every forecast day."""
    tz = ZoneInfo(cfg.timezone)
    first_day = min(s.start for s in forecast).astimezone(tz).date()
    last_day = _last_day(forecast, tz)
    return expand_away(cfg.away_rules, manual or [], first_day, (last_day - first_day).days + 2, tz)


def _last_day(forecast: list[ForecastStep], tz: ZoneInfo):
    # a step ending exactly at midnight belongs to the day before
    return (max(s.end for s in forecast) - timedelta(microseconds=1)).astimezone(tz).date()


def _share_of_day_left(now: datetime, tz: ZoneInfo) -> float:
    """Fraction of today still to come: driving already done is in the battery reading."""
    local = now.astimezone(tz)
    midnight = datetime.combine(local.date() + timedelta(days=1), datetime.min.time(), tz)
    left = (midnight.astimezone(timezone.utc) - now.astimezone(timezone.utc)) / timedelta(hours=24)
    return min(1.0, max(0.0, left))


def _remaining_parts(windows: list[Window], now: datetime) -> list[Window]:
    """The same windows with only the energy still to come, start and power unchanged."""
    parts = []
    for w in windows:
        share = elapsed_hours(now, w.end) / elapsed_hours(w.start, w.end)
        parts.append(replace(w, solar_kwh=w.solar_kwh * share, import_kwh=w.import_kwh * share))
    return parts


def plan(
    forecast: list[ForecastStep],
    cfg: Config,
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
    tz = ZoneInfo(cfg.timezone)
    last_day = _last_day(forecast, tz)
    away = away_periods(forecast, cfg, manual_away)
    running = _remaining_parts([w for w in pinned or [] if w.start <= now < w.end], now)
    blocked = away + [AwayPeriod(w.start, w.end) for w in running]
    runs = build_runs(forecast, cfg, blocked, now)
    # sun left over right before or after a running window belongs to it: no second window there
    runs = [r for r in runs if not any(r[0].start == w.end or r[-1].end == w.start for w in running)]
    by_day = group_runs_by_day(runs, tz)

    plans: list[DayPlan] = []
    today = now.astimezone(tz).date()
    day = today
    soc = soc_pct
    while day <= last_day:
        kept = [w for w in running if w.start.astimezone(tz).date() == day]
        need = max(0.0, energy_needed_kwh(soc, cfg) - sum(w.solar_kwh for w in kept))
        windows = tuple(sorted([*kept, *select_windows(by_day.get(day, []), need, cfg)], key=lambda w: w.start))
        gained = sum(w.solar_kwh for w in windows) * cfg.charger.efficiency
        after = max(soc, min(cfg.car.target_pct, soc + gained / cfg.car.usable_battery_kwh * 100))
        driving = daily_driving_pct(cfg) * (_share_of_day_left(now, tz) if day == today else 1.0)
        end_of_day = max(0.0, after - driving)
        plans.append(DayPlan(day, soc, windows, after, end_of_day, driving))
        soc = end_of_day
        day = day.fromordinal(day.toordinal() + 1)
    return plans
