"""Turns a plan into one view per day for the dashboard's day tabs: texts, the solar curve, the windows and the battery. No Qt, no I/O."""

from __future__ import annotations

from collections.abc import Sequence
from dataclasses import dataclass
from datetime import date, datetime, time, timedelta
from typing import Literal
from zoneinfo import ZoneInfo

from car_charge_planner.config.model import Config
from car_charge_planner.planner.advice import GridAdvice
from car_charge_planner.planner.model import AwayPeriod, AwayRule, DayPlan, ForecastStep, Window, elapsed_hours, utc
from car_charge_planner.planner.surplus import expand_away

from .i18n import _, number, pgettext
from .names import weekday_short
from .view_model import day_label, power_text

DayKind = Literal["sun", "grid", "away", "none"]  # the source colour of the day's tab
AwayOrigin = Literal["routine", "manual"]  # a recurring away time from Settings, or one entered for this day


@dataclass(frozen=True)
class Span:
    """Part of one local day, in real hours since its midnight: a day with a clock change has 23 or 25."""

    start_h: float
    end_h: float
    text: str  # "11:30-14:30"


@dataclass(frozen=True)
class AwayView:
    span: Span
    origin: AwayOrigin


@dataclass(frozen=True)
class DayView:
    date: date
    tab: str  # "Today", "Thu"
    title: str  # "Today", "Tomorrow", "Thu 8 Oct"
    kind: DayKind
    heading: str  # the main card's label: "Charge window", "No good window", "Away"
    hero: str  # the main card's large text: the window, the advised grid charge or the away times; may be empty
    facts: tuple[str, ...]  # "1.6 kW (7 A)", "about 7.9 kWh", "99% sun"
    note: str  # why there is no window, or what the grid charge is for; may be empty
    hours: float  # length of the day: the chart's x axis runs from 0 to this
    solar: tuple[tuple[float, float], ...]  # (hours since midnight, expected kW), in time order
    now_h: float | None  # where "now" is, on today only
    charges: tuple[Span, ...]  # the solar charge windows
    grid: Span | None  # the grid charge advised on this day
    away: tuple[AwayView, ...]
    battery_before_pct: float
    battery_after_pct: float  # after this day's charging, before its driving
    minimum_pct: float
    target_pct: float
    battery_text: str  # "46% → 74%"
    summary: str  # the day in one sentence, for a screen reader


def _midnight(day: date, tz: ZoneInfo) -> datetime:
    return datetime.combine(day, time(), tz)


def _clip(start: datetime, end: datetime, day: date, tz: ZoneInfo) -> tuple[float, float] | None:
    """[start, end) as hours since the day's midnight, cut to the day; None when they do not meet."""
    first, last = _midnight(day, tz), _midnight(day + timedelta(days=1), tz)
    begin, finish = max(utc(start), utc(first)), min(utc(end), utc(last))
    if begin >= finish:
        return None
    return elapsed_hours(first, begin), elapsed_hours(first, finish)


def _span(start: datetime, end: datetime, day: date, tz: ZoneInfo) -> Span | None:
    hours = _clip(start, end, day, tz)
    if hours is None:
        return None
    first, last = _midnight(day, tz), _midnight(day + timedelta(days=1), tz)
    start_text = "00:00" if utc(start) <= utc(first) else start.astimezone(tz).strftime("%H:%M")
    end_text = "24:00" if utc(end) >= utc(last) else end.astimezone(tz).strftime("%H:%M")  # 00:00 would read as the start of the day
    return Span(round(hours[0], 3), round(hours[1], 3), f"{start_text}-{end_text}")


def _solar(steps: Sequence[ForecastStep], day: date, tz: ZoneInfo) -> tuple[tuple[float, float], ...]:
    points: list[tuple[float, float]] = []
    for step in sorted(steps, key=lambda s: utc(s.start)):
        hours = _clip(step.start, step.end, day, tz)
        if hours is not None:
            points.append((round((hours[0] + hours[1]) / 2, 3), round(step.power_kw, 3)))
    return tuple(points)


def _away(rules: tuple[AwayRule, ...], manual: Sequence[AwayPeriod], day: date, tz: ZoneInfo) -> tuple[AwayView, ...]:
    found: list[tuple[AwayView, datetime]] = []
    sources: tuple[tuple[AwayOrigin, list[AwayPeriod]], ...] = (("routine", expand_away(rules, [], day, 1, tz)), ("manual", list(manual)))
    for origin, periods in sources:
        for period in periods:
            span = _span(period.start, period.end, day, tz)
            if span is not None:
                found.append((AwayView(span, origin), period.start))
    return tuple(view for view, _start in sorted(found, key=lambda item: utc(item[1])))


def _facts(window: Window, phases: int) -> tuple[str, ...]:
    return (
        power_text(window, phases),
        _("about {energy} kWh").format(energy=number(window.solar_kwh + window.import_kwh, 1)),
    )


def _summary(title: str, kind: DayKind, hero: str, peak_kw: float) -> str:
    if kind == "sun":
        plan = _("Charge window {span}.").format(span=hero)
    elif kind == "grid":
        plan = _("No good window; grid charge {span}.").format(span=hero) if hero else _("No good window.")
    elif kind == "away":
        plan = _("The car is away {spans}.").format(spans=hero)
    else:
        plan = _("No good window.")
    return _("{day}: expected solar power up to {peak} kW. {plan}").format(day=title, peak=number(peak_kw, 1), plan=plan)


def build_days(
    days: Sequence[DayPlan],
    steps: Sequence[ForecastStep],
    advice: GridAdvice | None,
    cfg: Config,
    manual_away: Sequence[AwayPeriod],
    now: datetime,
) -> tuple[DayView, ...]:
    tz = ZoneInfo(cfg.system.timezone)
    today = now.astimezone(tz).date()
    phases = cfg.system.charger.phases
    grid_day = None
    if advice is not None:
        grid_day = advice.window.start.astimezone(tz).date() if advice.window else advice.day
    views: list[DayView] = []
    for plan in days:
        day = plan.date
        charges = tuple(s for w in plan.windows if (s := _span(w.start, w.end, day, tz)) is not None)
        grid = None
        if advice is not None and advice.window is not None:
            grid = _span(advice.window.start, advice.window.end, day, tz)
        away = _away(cfg.system.away_rules, manual_away, day, tz)
        solar = _solar(steps, day, tz)
        title = day_label(day, today)

        kind: DayKind
        if plan.windows:
            kind, first = "sun", plan.windows[0]
            heading, hero, note = _("Charge window"), charges[0].text if charges else "", ""
            facts = (*_facts(first, phases), _("{share}% sun").format(share=number(first.solar_share * 100)))
        elif day == grid_day and advice is not None:
            kind, heading, hero, note = "grid", _("No good window"), grid.text if grid else "", _("Charge from the grid to stay above the minimum.")
            facts = _facts(advice.window, phases) if advice.window else ()
        elif away:
            kind, heading, hero, note, facts = "away", _("Away"), pgettext("list separator", ", ").join(a.span.text for a in away), _("No charging while the car is away."), ()
        else:
            kind, heading, hero, note, facts = "none", _("No good window"), "", _("Not enough solar surplus on this day."), ()

        views.append(
            DayView(
                date=day,
                tab=_("Today") if day == today else weekday_short(day.weekday()),
                title=title,
                kind=kind,
                heading=heading,
                hero=hero,
                facts=facts,
                note=note,
                hours=round(elapsed_hours(_midnight(day, tz), _midnight(day + timedelta(days=1), tz)), 3),
                solar=solar,
                now_h=round(elapsed_hours(_midnight(day, tz), now), 3) if day == today else None,
                charges=charges,
                grid=grid,
                away=away,
                battery_before_pct=plan.soc_start_pct,
                battery_after_pct=plan.soc_after_charging_pct,
                minimum_pct=cfg.planning.minimum_pct,
                target_pct=cfg.planning.target_pct,
                battery_text=_("{before}% → {after}%").format(before=number(plan.soc_start_pct), after=number(plan.soc_after_charging_pct)),
                summary=_summary(title, kind, hero, max((kw for _h, kw in solar), default=0.0)),
            )
        )
    return tuple(views)
