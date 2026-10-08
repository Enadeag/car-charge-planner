"""Helpers for the Car and charger page. No Qt."""

from __future__ import annotations

from planner.model import Config, ConfigError, validate

DAYS_PER_WEEK = 7
CAR_KEYS = ("car.usable_battery_kwh", "car.consumption_kwh_per_100km", "car.km_per_day")  # the car's numbers a person may not know
CHARGER_KEYS = ("charger.phases", "charger.max_amps", "charger.min_amps", "charger.volts")  # (the efficiency is never asked)


def weekly_to_daily(weekly: float) -> float:
    """People know a week ("about 300 km"); the planner takes a day."""
    return weekly / DAYS_PER_WEEK


def daily_to_weekly(daily: float) -> float:
    return daily * DAYS_PER_WEEK


def problems(config: Config) -> list[str]:
    """What is wrong with the car and charger settings, in the words Settings uses (they come from the same check)."""
    try:
        validate(config)
    except ConfigError as err:
        lines = [line.removeprefix("- ").strip() for line in str(err).split("\n")[1:]]
        return [line for line in lines if line.startswith(("[car]", "[charger]"))]
    return []
