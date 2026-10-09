"""Expected surplus from the forecast."""

from __future__ import annotations

from datetime import date, datetime, timedelta
from zoneinfo import ZoneInfo

from .model import AwayPeriod, AwayRule, Config, ForecastStep


def usable_kw(step: ForecastStep, cfg: Config) -> float:
    """Surplus after the house, scaled down by the planning margin. The provider already accounted for the panels."""
    surplus = step.power_kw - cfg.house.baseload_kw
    return max(0.0, surplus) * cfg.planning.margin


def charge_power_kw(amps: int, cfg: Config) -> float:
    """Power the charger delivers at this current: every phase carries it. (230 V, 6 A: 1.4 kW on one phase, 4.1 kW on three.)"""
    return amps * cfg.charger.volts * cfg.charger.phases / 1000


def amps_for(power_kw: float, cfg: Config) -> int:
    """Whole amps (floored), per phase, that fit under power_kw on the configured charger."""
    return min(cfg.charger.max_amps, int(power_kw * 1000 / (cfg.charger.volts * cfg.charger.phases)))


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
