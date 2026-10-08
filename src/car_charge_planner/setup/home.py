"""Helpers for the House and Away pages. No Qt."""

from __future__ import annotations

from car_charge_planner.planner.model import Config, ConfigError, validate

HOURS_PER_YEAR = 365 * 24
DAYS_PER_YEAR = 365
MAX_BASELOAD_KW = 20  # what Settings accepts


def car_kwh_per_year(km_per_day: float, kwh_per_100km: float) -> float:
    """Roughly what the car takes from the mains in a year: the distance it drives times what it uses per kilometre."""
    return km_per_day * DAYS_PER_YEAR * kwh_per_100km / 100


def baseload_from_yearly(yearly_kwh: float, car_yearly_kwh: float = 0.0) -> float:
    """The house's average power from a year's bill, without the car if the bill includes it. Never below nothing."""
    return max(0.0, yearly_kwh - car_yearly_kwh) / HOURS_PER_YEAR


def yearly_from_baseload(baseload_kw: float) -> float:
    return baseload_kw * HOURS_PER_YEAR


def away_problems(config: Config) -> list[str]:
    """What is wrong with the away rules, in the words Settings uses (the same check)."""
    try:
        validate(config)
    except ConfigError as err:
        lines = [line.removeprefix("- ").strip() for line in str(err).split("\n")[1:]]
        return [line for line in lines if line.startswith("[away]")]
    return []
