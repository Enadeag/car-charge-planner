"""Building and describing manual away periods. No Qt."""

from __future__ import annotations

from datetime import date, datetime, time, timedelta
from zoneinfo import ZoneInfo

from car_charge_planner.planner.model import AwayPeriod, AwayRule

from .i18n import _, pgettext
from .names import short_date, weekday_long, weekday_short


def make_away(day: date, all_day: bool, start: time, end: time, tz: ZoneInfo) -> AwayPeriod:
    """An all-day period spans local midnight to midnight; otherwise start-end on that day."""
    if all_day:
        return AwayPeriod(
            datetime.combine(day, time(0, 0), tz),
            datetime.combine(day + timedelta(days=1), time(0, 0), tz),
        )
    if end <= start:
        raise ValueError(_("The end time must be after the start time"))
    return AwayPeriod(datetime.combine(day, start, tz), datetime.combine(day, end, tz))


def _is_all_day(period: AwayPeriod, tz: ZoneInfo) -> bool:
    start, end = period.start.astimezone(tz), period.end.astimezone(tz)
    midnight = time(0, 0)
    return start.time() == midnight and end.time() == midnight and end.date() == start.date() + timedelta(days=1)


def describe(period: AwayPeriod, tz: ZoneInfo) -> str:
    start, end = period.start.astimezone(tz), period.end.astimezone(tz)
    if _is_all_day(period, tz):
        return _("{day}, all day").format(day=short_date(start.date()))
    if start.date() == end.date():
        return _("{day}, {start}-{end}").format(day=short_date(start.date()), start=f"{start:%H:%M}", end=f"{end:%H:%M}")
    return _("{start_day} {start} - {end_day} {end}").format(
        start_day=short_date(start.date()),
        start=f"{start:%H:%M}",
        end_day=short_date(end.date()),
        end=f"{end:%H:%M}",
    )


def describe_rule(rule: AwayRule) -> str:
    return _("Every {weekday}, {start}-{end}").format(
        weekday=weekday_long(rule.weekday), start=f"{rule.start:%H:%M}", end=f"{rule.end:%H:%M}"
    )


def away_summary(rules: tuple[AwayRule, ...], periods: tuple[AwayPeriod, ...], now: datetime, tz: ZoneInfo, limit: int = 3) -> str:
    """One compact line for the plan page: weekly rules by weekday, then upcoming one-off days."""
    items: list[str] = []
    for rule in rules:
        label = _("{weekday} weekly").format(weekday=weekday_short(rule.weekday))
        if label not in items:
            items.append(label)
    for period in periods:
        if period.end > now:
            start = period.start.astimezone(tz)
            items.append(short_date(start.date()) if _is_all_day(period, tz) else describe(period, tz))
    if not items:
        return _("No absences planned")
    shown = pgettext("list separator", ", ").join(items[:limit])
    if len(items) > limit:
        return _("Away: {items} +{n} more").format(items=shown, n=len(items) - limit)
    return _("Away: {items}").format(items=shown)
