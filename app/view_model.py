"""Turns a plan into the text the tray and popup show. No Qt, no I/O."""

from __future__ import annotations

from dataclasses import dataclass
from datetime import date, datetime, timedelta
from zoneinfo import ZoneInfo

from .i18n import N_, _, ngettext, number, pgettext
from .names import short_date, weekday_short
from planner.advice import GridAdvice
from planner.model import Config, DayPlan, Window
from storage.state import BatteryReading

ATTRIBUTION = N_("Weather data by Open-Meteo.com (CC BY 4.0)")
SOURCE = N_("Weather data: Open-Meteo.com")


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
    state: str  # "active" | "upcoming" | "none" | "grid" | "no-forecast" | "needs-setup"
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
    return _("{power} kW ({amps} A)").format(power=number(w.power_kw, 1), amps=w.amps)


def day_label(day: date, today: date) -> str:
    if day == today:
        return _("Today")
    if day == today + timedelta(days=1):
        return _("Tomorrow")
    return short_date(day)


def battery_status(battery: BatteryReading, now: datetime) -> tuple[str, bool]:
    """Line to show and whether the reading needs updating (spec 4.7)."""
    if battery.assumed:
        return _("Battery {pct}% (assumed, not entered yet). Please enter it.").format(pct=number(battery.pct)), True
    days = (now - battery.entered_at).days
    if days < 1:
        return _("Battery {pct}%").format(pct=number(battery.pct)), False
    text = ngettext(
        "Battery {pct}%, entered {n} day ago. Please update.",
        "Battery {pct}%, entered {n} days ago. Please update.",
        days,
    )
    return text.format(pct=number(battery.pct), n=days), True


def _projection(day: DayPlan, window: Window, cfg: Config, tz: ZoneInfo) -> str:
    """Battery before and after this window, counting earlier windows the same day."""
    level = day.soc_start_pct
    for w in day.windows:
        gained = w.solar_kwh * cfg.charger.efficiency / cfg.car.usable_battery_kwh * 100
        after = max(level, min(cfg.car.target_pct, level + gained))
        if w is window:
            return _("Battery {level}% \u2192 {after}% by {time}").format(
                level=number(level), after=number(after), time=_clock(window.end, tz)
            )
        level = after
    return ""


def footer_text(age: timedelta | None, stale: bool) -> str:
    if age is None:
        return _(SOURCE)
    minutes = int(age.total_seconds() // 60)
    if minutes < 120:
        text = _("Forecast {n} min old \u00b7 {source}") if not stale else _("Forecast {n} min old, refresh failed \u00b7 {source}")
    else:
        text = _("Forecast {n} h old \u00b7 {source}") if not stale else _("Forecast {n} h old, refresh failed \u00b7 {source}")
    return text.format(n=minutes if minutes < 120 else minutes // 60, source=_(SOURCE))


def advice_view(advice: GridAdvice, now: datetime, cfg: Config, tz: ZoneInfo) -> AdviceView:
    today = now.astimezone(tz).date()
    if advice.day == today:
        template = _("Battery would drop to {level}% (minimum {minimum}%) by the end of today.")
    elif advice.day == today + timedelta(days=1):
        template = _("Battery would drop to {level}% (minimum {minimum}%) by the end of tomorrow.")
    else:
        template = _("Battery would drop to {level}% (minimum {minimum}%) by the end of {day}.")
    reason = template.format(
        level=number(max(0.0, advice.soc_end_pct)), minimum=number(cfg.car.minimum_pct), day=short_date(advice.day)
    )
    if advice.window is None:
        return AdviceView(
            _("Grid charge needed"),
            _("About {energy} kWh, but there is no free time at home to charge it.").format(
                energy=number(advice.energy_kwh)
            ),
            reason,
        )
    w = advice.window
    when = _("{day} {span}").format(day=day_label(w.start.astimezone(tz).date(), today), span=_span(w, tz))
    return AdviceView(
        _("Grid charge needed \u00b7 {when}").format(when=when),
        _("{power} \u00b7 about {energy} kWh").format(power=_power(w), energy=number(advice.energy_kwh)),
        reason,
    )


def _quiet_label(day: date, today: date) -> str:
    if day in (today, today + timedelta(days=1)):
        return day_label(day, today)
    return weekday_short(day.weekday())


def _outlook(days: list[DayPlan], headline_window: Window | None, now: datetime, tz: ZoneInfo, today: date):
    rows: list[OutlookRow] = []
    quiet: list[str] = []
    for d in days:
        shown_day = False
        for w in d.windows:
            if w is headline_window or w.end <= now:
                continue  # the headline already says it
            rows.append(
                OutlookRow(
                    "" if shown_day else day_label(d.date, today),
                    _span(w, tz),
                    _power(w),
                    _("{energy} kWh").format(energy=number(w.solar_kwh, 1)),
                )
            )
            shown_day = True
        if not d.windows:
            quiet.append(_quiet_label(d.date, today))
    # when every day is quiet the headline already says so
    quiet_text = ""
    if quiet and len(quiet) < len(days):
        quiet_text = _("{days}: no good window").format(days=pgettext("list separator", ", ").join(quiet))
    return tuple(rows), quiet_text


def needs_setup(battery: BatteryReading, now: datetime) -> ViewModel:
    """Shown until the user has given a location: nothing is fetched and there is no plan yet."""
    line, _stale = battery_status(battery, now)
    return ViewModel(
        state="needs-setup",
        tooltip=_("Not set up yet"),
        headline=_("Not set up yet"),
        detail=_("Enter your location in Settings to get a plan."),
        battery_line=line,
        battery_stale=False,  # no prompt to update the battery before there is anything to plan
        battery_pct=battery.pct,
        projection="",
        outlook=(),
        quiet_days="",
        footer=_(SOURCE),
    )


def no_forecast(reason: str, battery: BatteryReading, now: datetime) -> ViewModel:
    line, stale = battery_status(battery, now)
    return ViewModel(
        state="no-forecast",
        tooltip=_("No forecast available"),
        headline=_("No forecast available"),
        detail=reason,
        battery_line=line,
        battery_stale=stale,
        battery_pct=battery.pct,
        projection="",
        outlook=(),
        quiet_days="",
        footer=_(SOURCE),
    )


def _facts(window: Window) -> str:
    return _("{power} \u00b7 {energy} kWh \u00b7 {share}% solar").format(
        power=_power(window), energy=number(window.solar_kwh, 1), share=number(window.solar_share * 100)
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
        no_sun = ngettext(
            "Not enough solar surplus in the next {n} day.", "Not enough solar surplus in the next {n} days.", horizon
        ).format(n=horizon)
        if advice:
            w = advice.window
            tip = _("Grid charge needed")
            if w:
                when = _("{day} {span}").format(day=day_label(w.start.astimezone(tz).date(), today), span=_span(w, tz))
                tip = _("Grid charge needed: {when}").format(when=when)
            return ViewModel(
                state="grid", tooltip=tip, headline=_("Grid charge needed"), detail=no_sun, projection="", **common
            )
        return ViewModel(
            state="none",
            tooltip=_("No good window coming up"),
            headline=_("No good window"),
            detail=no_sun,
            projection="",
            **common,
        )

    day_plan, window = upcoming[0]
    projection = _projection(day_plan, window, cfg, tz)
    if window.start <= now + timedelta(seconds=1):
        return ViewModel(
            state="active",
            tooltip=_("Charge now until {end} at {amps} A").format(end=_clock(window.end, tz), amps=window.amps),
            headline=_("Charge now"),
            detail=_("Until {end} \u00b7 {facts}").format(end=_clock(window.end, tz), facts=_facts(window)),
            projection=projection,
            **common,
        )
    label = day_label(day_plan.date, today)
    span = _span(window, tz)
    return ViewModel(
        state="upcoming",
        tooltip=_("Next: {day} {span}, {amps} A").format(day=label, span=span, amps=window.amps),
        headline=_("Next: {day} {span}").format(day=label, span=span),
        detail=_facts(window),
        projection=projection,
        **common,
    )
