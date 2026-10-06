"""Building and describing manual away periods. No Qt."""

from __future__ import annotations

from datetime import date, datetime, time, timedelta
from zoneinfo import ZoneInfo

from planner.model import AwayPeriod, AwayRule

from .view_model import MONTHS, WEEKDAYS


def make_away(day: date, all_day: bool, start: time, end: time, tz: ZoneInfo) -> AwayPeriod:
    """An all-day period spans local midnight to midnight; otherwise start-end on that day."""
    if all_day:
        return AwayPeriod(
            datetime.combine(day, time(0, 0), tz),
            datetime.combine(day + timedelta(days=1), time(0, 0), tz),
        )
    if end <= start:
        raise ValueError("The end time must be after the start time")
    return AwayPeriod(datetime.combine(day, start, tz), datetime.combine(day, end, tz))


def _day(d: date) -> str:
    return f"{WEEKDAYS[d.weekday()]} {d.day} {MONTHS[d.month - 1]}"


def describe(period: AwayPeriod, tz: ZoneInfo) -> str:
    start, end = period.start.astimezone(tz), period.end.astimezone(tz)
    midnight = time(0, 0)
    if start.time() == midnight and end.time() == midnight and end.date() == start.date() + timedelta(days=1):
        return f"{_day(start.date())}, all day"
    if start.date() == end.date():
        return f"{_day(start.date())}, {start:%H:%M}-{end:%H:%M}"
    return f"{_day(start.date())} {start:%H:%M} - {_day(end.date())} {end:%H:%M}"


FULL_WEEKDAYS = ("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")


def describe_rule(rule: AwayRule) -> str:
    return f"Every {FULL_WEEKDAYS[rule.weekday]}, {rule.start:%H:%M}-{rule.end:%H:%M}"


def away_summary(rules: tuple[AwayRule, ...], periods: tuple[AwayPeriod, ...], now: datetime, tz: ZoneInfo, limit: int = 3) -> str:
    """One compact line for the plan page: weekly rules by weekday, then upcoming one-off days."""
    items: list[str] = []
    for rule in rules:
        label = f"{WEEKDAYS[rule.weekday]} weekly"
        if label not in items:
            items.append(label)
    for period in periods:
        if period.end > now:
            items.append(describe(period, tz).replace(", all day", ""))
    if not items:
        return "No absences planned"
    shown = ", ".join(items[:limit])
    return f"Away: {shown}" + (f" +{len(items) - limit} more" if len(items) > limit else "")
