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
class ViewModel:
    state: str  # "active" | "upcoming" | "none" | "no-forecast"
    tooltip: str
    headline: str
    detail: str
    battery_line: str
    battery_stale: bool  # not entered yet, or a day or more old: prompt for an update
    projection: str  # "Battery 45% -> 55% by 15:00", empty when there is no window
    outlook: tuple[str, ...]
    footer: str
    advice: str = ""  # grid-charge advice, shown in its own block


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
            return f"Battery {level:.0f}% -> {after:.0f}% by {_clock(window.end, tz)}"
        level = after
    return ""


def footer_text(age: timedelta | None, stale: bool) -> str:
    if age is None:
        return ATTRIBUTION
    minutes = int(age.total_seconds() // 60)
    when = f"{minutes} min" if minutes < 120 else f"{minutes // 60} h"
    flag = ", refresh failed" if stale else ""
    return f"Forecast {when} old{flag}. {ATTRIBUTION}"


def advice_text(advice: GridAdvice, now: datetime, cfg: Config, tz: ZoneInfo) -> str:
    today = now.astimezone(tz).date()
    label = day_label(advice.day, today)
    label = label.lower() if label in ("Today", "Tomorrow") else label
    dip = (
        f"battery would drop to {max(0.0, advice.soc_end_pct):.0f}% "
        f"(minimum {cfg.car.minimum_pct:.0f}%) by the end of {label}"
    )
    if advice.window is None:
        return f"Grid charge needed: {dip}, and there is no free time at home to charge about {advice.energy_kwh:.0f} kWh."
    w = advice.window
    when = f"{day_label(w.start.astimezone(tz).date(), today)} {_span(w, tz)}"
    return (
        f"Grid charge needed: {dip}. Charge about {advice.energy_kwh:.0f} kWh, best {when} at {_power(w)}. "
        f"Expect {w.import_kwh:.1f} kWh from the grid and {w.solar_kwh:.1f} kWh from the sun."
    )


def no_forecast(reason: str, battery: BatteryReading, now: datetime) -> ViewModel:
    return ViewModel(
        state="no-forecast",
        tooltip="No forecast available",
        headline="No forecast available",
        detail=reason,
        battery_line=battery_status(battery, now)[0],
        battery_stale=battery_status(battery, now)[1],
        projection="",
        outlook=(),
        footer=ATTRIBUTION,
    )


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
    outlook = []
    for d in days:
        label = day_label(d.date, today)
        if d.windows:
            parts = [f"{_span(w, tz)}  {_power(w)}  {w.solar_kwh:.1f} kWh" for w in d.windows]
            outlook.append(f"{label}: " + "; ".join(parts))
        else:
            outlook.append(f"{label}: no good window")

    footer = footer_text(forecast_age, stale)
    advice_line = advice_text(advice, now, cfg, tz) if advice else ""
    battery_line, battery_stale = battery_status(battery, now)

    if not upcoming:
        horizon = len(days)
        no_sun = f"Not enough solar surplus in the next {horizon} day{'s' if horizon != 1 else ''}."
        if advice:
            w = advice.window
            tip = f"Grid charge needed: {day_label(w.start.astimezone(tz).date(), today)} {_span(w, tz)}" if w else "Grid charge needed"
            return ViewModel(
                "grid", tip, "Grid charge needed", no_sun, battery_line, battery_stale, "", tuple(outlook), footer, advice_line
            )
        return ViewModel(
            "none",
            "No good window coming up",
            "No good window",
            no_sun,
            battery_line,
            battery_stale,
            "",
            tuple(outlook),
            footer,
        )

    day_plan, window = upcoming[0]
    day = day_plan.date
    projection = _projection(day_plan, window, cfg, tz)
    if window.start <= now + timedelta(seconds=1):
        return ViewModel(
            "active",
            f"Charge now until {_clock(window.end, tz)} at {window.amps} A",
            "Charge now",
            f"Until {_clock(window.end, tz)} at {_power(window)}. "
            f"About {window.solar_kwh:.1f} kWh, {window.solar_share:.0%} solar.",
            battery_line,
            battery_stale,
            projection,
            tuple(outlook),
            footer,
            advice_line,
        )

    label = day_label(day, today)
    return ViewModel(
        "upcoming",
        f"Next: {label} {_span(window, tz)}, {window.amps} A",
        f"Next: {label} {_span(window, tz)}",
        f"{_power(window)}. About {window.solar_kwh:.1f} kWh, {window.solar_share:.0%} solar.",
        battery_line,
        battery_stale,
        projection,
        tuple(outlook),
        footer,
        advice_line,
    )
