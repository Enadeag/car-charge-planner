"""Turns a plan into the text the tray and popup show. No Qt, no I/O."""

from __future__ import annotations

from dataclasses import dataclass
from datetime import date, datetime, timedelta
from zoneinfo import ZoneInfo

from planner.advice import GridAdvice
from planner.model import Config, DayPlan, Window
from storage.state import BatteryReading

ATTRIBUTION = "Weather data by Open-Meteo.com (CC BY 4.0)"

# Fixed names: strftime("%a") follows the process locale, which Qt changes.
WEEKDAYS = ("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
MONTHS = ("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")


@dataclass(frozen=True)
class OutlookRow:
    day: str  # "Tomorrow"; empty for a second window on the same day
    span: str  # "10:00-15:00"
    power: str  # "1.6 kW (7 A)"
    energy: str  # "7.9 kWh"


@dataclass(frozen=True)
class AdviceView:
    """Grid-charge advice in three parts, so the popup can give the facts first and the reason second."""

    title: str  # "Grid charge needed · Thu 8 Oct 00:00-05:55"
    detail: str  # "3.0 kW (13 A) · about 18 kWh"
    reason: str  # "Battery would drop to 7% (minimum 30%) by the end of Thu 8 Oct."


@dataclass(frozen=True)
class ViewModel:
    state: str  # "active" | "upcoming" | "none" | "grid" | "no-forecast"
    tooltip: str
    headline: str
    detail: str  # one compact line: "Until 15:00 · 1.6 kW (7 A) · 5.8 kWh · 99% solar"
    battery_line: str
    battery_stale: bool  # not entered yet, or a day or more old: prompt for an update
    battery_pct: float
    projection: str  # "Battery 45% → 55% by 15:00", empty when there is no window
    outlook: tuple[OutlookRow, ...]  # later windows; the headline window is not repeated
    quiet_days: str  # "Thu, Fri: no good window", empty when nothing is worth saying
    footer: str
    advice: AdviceView | None = None


def _clock(moment: datetime, tz: ZoneInfo) -> str:
    return moment.astimezone(tz).strftime("%H:%M")


def _span(w: Window, tz: ZoneInfo) -> str:
    return f"{_clock(w.start, tz)}-{_clock(w.end, tz)}"


def _power(w: Window) -> str:
    return f"{w.power_kw:.1f} kW ({w.amps} A)"


def day_label(day: date, today: date) -> str:
    if day == today:
        return "Today"
    if day == today + timedelta(days=1):
        return "Tomorrow"
    return f"{WEEKDAYS[day.weekday()]} {day.day} {MONTHS[day.month - 1]}"


def battery_status(battery: BatteryReading, now: datetime) -> tuple[str, bool]:
    """Line to show and whether the reading needs updating (spec 4.7)."""
    if battery.assumed:
        return f"Battery {battery.pct:.0f}% (assumed, not entered yet). Please enter it.", True
    days = (now - battery.entered_at).days
    if days < 1:
        return f"Battery {battery.pct:.0f}%", False
    return f"Battery {battery.pct:.0f}%, entered {days} day{'s' if days != 1 else ''} ago. Please update.", True


def _projection(day: DayPlan, window: Window, cfg: Config, tz: ZoneInfo) -> str:
    """Battery before and after this window, counting earlier windows the same day."""
    level = day.soc_start_pct
    for w in day.windows:
        gained = w.solar_kwh * cfg.charger.efficiency / cfg.car.usable_battery_kwh * 100
        after = max(level, min(cfg.car.target_pct, level + gained))
        if w is window:
            return f"Battery {level:.0f}% \u2192 {after:.0f}% by {_clock(window.end, tz)}"
        level = after
    return ""


SOURCE = "Weather data: Open-Meteo.com"


def footer_text(age: timedelta | None, stale: bool) -> str:
    if age is None:
        return SOURCE
    minutes = int(age.total_seconds() // 60)
    when = f"{minutes} min" if minutes < 120 else f"{minutes // 60} h"
    flag = ", refresh failed" if stale else ""
    return f"Forecast {when} old{flag} \u00b7 {SOURCE}"


def advice_view(advice: GridAdvice, now: datetime, cfg: Config, tz: ZoneInfo) -> AdviceView:
    today = now.astimezone(tz).date()
    label = day_label(advice.day, today)
    label = label.lower() if label in ("Today", "Tomorrow") else label
    reason = (
        f"Battery would drop to {max(0.0, advice.soc_end_pct):.0f}% "
        f"(minimum {cfg.car.minimum_pct:.0f}%) by the end of {label}."
    )
    if advice.window is None:
        return AdviceView(
            "Grid charge needed",
            f"About {advice.energy_kwh:.0f} kWh, but there is no free time at home to charge it.",
            reason,
        )
    w = advice.window
    when = f"{day_label(w.start.astimezone(tz).date(), today)} {_span(w, tz)}"
    return AdviceView(
        f"Grid charge needed \u00b7 {when}",
        f"{_power(w)} \u00b7 about {advice.energy_kwh:.0f} kWh",
        reason,
    )


def _quiet_label(day: date, today: date) -> str:
    label = day_label(day, today)
    return label if label in ("Today", "Tomorrow") else WEEKDAYS[day.weekday()]


def _outlook(days: list[DayPlan], headline_window: Window | None, now: datetime, tz: ZoneInfo, today: date):
    rows: list[OutlookRow] = []
    quiet: list[str] = []
    for d in days:
        shown_day = False
        for w in d.windows:
            if w is headline_window or w.end <= now:
                continue  # the headline already says it
            rows.append(
                OutlookRow("" if shown_day else day_label(d.date, today), _span(w, tz), _power(w), f"{w.solar_kwh:.1f} kWh")
            )
            shown_day = True
        if not d.windows:
            quiet.append(_quiet_label(d.date, today))
    # when every day is quiet the headline already says so
    quiet_text = f"{', '.join(quiet)}: no good window" if quiet and len(quiet) < len(days) else ""
    return tuple(rows), quiet_text


def no_forecast(reason: str, battery: BatteryReading, now: datetime) -> ViewModel:
    line, stale = battery_status(battery, now)
    return ViewModel(
        state="no-forecast",
        tooltip="No forecast available",
        headline="No forecast available",
        detail=reason,
        battery_line=line,
        battery_stale=stale,
        battery_pct=battery.pct,
        projection="",
        outlook=(),
        quiet_days="",
        footer=SOURCE,
    )


def _facts(window: Window) -> str:
    return f"{_power(window)} \u00b7 {window.solar_kwh:.1f} kWh \u00b7 {window.solar_share:.0%} solar"


def build(
    days: list[DayPlan],
    now: datetime,
    cfg: Config,
    battery: BatteryReading,
    forecast_age: timedelta | None,
    stale: bool,
    advice: GridAdvice | None = None,
) -> ViewModel:
    tz = ZoneInfo(cfg.timezone)
    today = now.astimezone(tz).date()
    upcoming = [(d, w) for d in days for w in d.windows if w.end > now]
    headline_window = upcoming[0][1] if upcoming else None
    outlook, quiet_days = _outlook(days, headline_window, now, tz, today)
    battery_line, battery_stale = battery_status(battery, now)
    common = dict(
        battery_line=battery_line,
        battery_stale=battery_stale,
        battery_pct=battery.pct,
        outlook=outlook,
        quiet_days=quiet_days,
        footer=footer_text(forecast_age, stale),
        advice=advice_view(advice, now, cfg, tz) if advice else None,
    )

    if not upcoming:
        horizon = len(days)
        no_sun = f"Not enough solar surplus in the next {horizon} day{'s' if horizon != 1 else ''}."
        if advice:
            w = advice.window
            tip = f"Grid charge needed: {day_label(w.start.astimezone(tz).date(), today)} {_span(w, tz)}" if w else "Grid charge needed"
            return ViewModel(state="grid", tooltip=tip, headline="Grid charge needed", detail=no_sun, projection="", **common)
        return ViewModel(
            state="none", tooltip="No good window coming up", headline="No good window", detail=no_sun, projection="", **common
        )

    day_plan, window = upcoming[0]
    projection = _projection(day_plan, window, cfg, tz)
    if window.start <= now + timedelta(seconds=1):
        return ViewModel(
            state="active",
            tooltip=f"Charge now until {_clock(window.end, tz)} at {window.amps} A",
            headline="Charge now",
            detail=f"Until {_clock(window.end, tz)} \u00b7 {_facts(window)}",
            projection=projection,
            **common,
        )
    label = day_label(day_plan.date, today)
    return ViewModel(
        state="upcoming",
        tooltip=f"Next: {label} {_span(window, tz)}, {window.amps} A",
        headline=f"Next: {label} {_span(window, tz)}",
        detail=_facts(window),
        projection=projection,
        **common,
    )
