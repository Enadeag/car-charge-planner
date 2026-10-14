"""Expected surplus from the forecast."""

from __future__ import annotations

from datetime import date, datetime, timedelta
from zoneinfo import ZoneInfo

from .model import AwayPeriod, AwayRule, ForecastStep, HomeSystem, PlanningPreferences


def usable_kw(step: ForecastStep, system: HomeSystem, preferences: PlanningPreferences) -> float:
    """Surplus after the house, scaled down by the planning margin. The provider already accounted for the panels."""
    surplus = step.power_kw - system.house.baseload_kw
    return max(0.0, surplus) * preferences.margin


def charge_power_kw(amps: int, system: HomeSystem) -> float:
    """Power the charger delivers at this current: every phase carries it. (230 V, 6 A: 1.4 kW on one phase, 4.1 kW on three.)"""
    return amps * system.charger.volts * system.charger.phases / 1000


def amps_for(power_kw: float, system: HomeSystem) -> int:
    """Whole amps (floored), per phase, that fit under power_kw on the configured charger."""
    charger = system.charger
    return min(charger.max_amps, int(power_kw * 1000 / (charger.volts * charger.phases)))


def expand_away(
    rules: tuple[AwayRule, ...],
    manual: list[AwayPeriod],
    first_day: date,
    days: int,
    tz: ZoneInfo,
) -> list[AwayPeriod]:
    """Recurring rules over a date range, plus manual entries."""
    periods = list(manual)
    for offset in range(days):
        day = first_day + timedelta(days=offset)
        for rule in rules:
            if day.weekday() == rule.weekday:
                periods.append(
                    AwayPeriod(
                        datetime.combine(day, rule.start, tz),
                        datetime.combine(day, rule.end, tz),
                    )
                )
    return periods
