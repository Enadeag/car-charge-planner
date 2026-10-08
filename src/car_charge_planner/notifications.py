"""When to announce a charge window, and what to say. No Qt, no I/O."""

from __future__ import annotations

from datetime import datetime, timedelta
from zoneinfo import ZoneInfo

from car_charge_planner.planner.advice import GridAdvice
from car_charge_planner.planner.model import Config, DayPlan, Window

from .i18n import _, number

MAX_FORECAST_AGE = timedelta(hours=6)
SOLAR, GRID = "solar", "grid"


def candidate_windows(days: list[DayPlan], advice: GridAdvice | None) -> list[tuple[str, Window]]:
    found = [(SOLAR, w) for d in days for w in d.windows]
    if advice and advice.window:
        found.append((GRID, advice.window))
    return sorted(found, key=lambda kw: kw[1].start)


def due(
    windows: list[tuple[str, Window]],
    already: tuple[Window, ...],
    now: datetime,
    lead_minutes: int,
    forecast_age: timedelta,
    refresh_failed: bool,
) -> list[tuple[str, Window]]:
    """Windows to announce right now.

    Only windows that have not started: the live plan clips a running window's start to
    'now', so those are skipped by construction. A window overlapping one already announced
    counts as announced (a refreshed forecast can nudge a start by a few minutes).
    """
    if refresh_failed or forecast_age > MAX_FORECAST_AGE:
        return []
    lead = timedelta(minutes=lead_minutes)
    return [
        (kind, w)
        for kind, w in windows
        if now < w.start <= now + lead
        and not any(n.start < w.end and w.start < n.end for n in already)
    ]


def message(kind: str, window: Window, cfg: Config, tz: ZoneInfo) -> tuple[str, str]:
    span = f"{window.start.astimezone(tz):%H:%M}-{window.end.astimezone(tz):%H:%M}"
    template = (
        _("Set the current to {amps} A per phase ({power} kW) in your charger's app.")
        if cfg.charger.phases == 3
        else _("Set the current to {amps} A ({power} kW) in your charger's app.")
    )
    setting = template.format(amps=window.amps, power=number(window.power_kw, 1))
    if kind == GRID:
        return (
            _("Grid charge window {span}").format(span=span),
            _("{setting} About {energy} kWh, needed to stay above {minimum}%.").format(
                setting=setting, energy=number(window.solar_kwh + window.import_kwh), minimum=number(cfg.car.minimum_pct)
            ),
        )
    return (
        _("Charge window {span}").format(span=span),
        _("{setting} About {energy} kWh, {share}% solar.").format(
            setting=setting, energy=number(window.solar_kwh, 1), share=number(window.solar_share * 100)
        ),
    )
