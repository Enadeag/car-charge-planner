"""Turns a plan into the text the tray and popup show. No Qt, no I/O."""

from __future__ import annotations

from dataclasses import dataclass
from datetime import date, datetime, timedelta
from typing import TypedDict
from zoneinfo import ZoneInfo

from car_charge_planner.planner.advice import GridAdvice
from car_charge_planner.planner.model import Config, DayPlan, Window
from car_charge_planner.storage.state import BatteryReading

from .i18n import _, ngettext, number, pgettext
from .names import short_date, weekday_short


@dataclass(frozen=True)
class ForecastSource:
    """Who made the forecast on screen, for the footer. The credit is the provider's own text."""

    attribution: str
    provider_name: str = ""
    from_fallback: bool = False  # the primary had nothing usable
    primary_name: str = ""
    cooling_down: bool = False  # the provider asked not to be asked again yet


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
    typical_line: str = ""  # "Based on typical values for: ..."; empty when the owner has given every value
    typical_tab: str = ""  # the Settings tab to open from it
    footer_tooltip: str = ""  # the full credit and, when it matters, why this provider answered


def _clock(moment: datetime, tz: ZoneInfo) -> str:
    return moment.astimezone(tz).strftime("%H:%M")


def _span(w: Window, tz: ZoneInfo) -> str:
    return f"{_clock(w.start, tz)}-{_clock(w.end, tz)}"


def _power(w: Window, phases: int = 1) -> str:
    if phases == 3:  # the current is on each of the three phases: say so, or it reads as a third of the power
        return _("{power} kW ({amps} A per phase)").format(power=number(w.power_kw, 1), amps=w.amps)
    return _("{power} kW ({amps} A)").format(power=number(w.power_kw, 1), amps=w.amps)


def day_label(day: date, today: date) -> str:
    if day == today:
        return _("Today")
    if day == today + timedelta(days=1):
        return _("Tomorrow")
    return short_date(day)


def battery_status(battery: BatteryReading, now: datetime) -> tuple[str, bool]:
    """Line to show and whether the reading needs updating (spec 4.7)."""
    if battery.entered_at is None:  # assumed: never entered
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


SHOWN_TYPICAL = 3  # names listed in the line; the rest are counted


def typical_line(typical: tuple[str, ...]) -> tuple[str, str]:
    """("Based on typical values for: Panel size, Tilt, Direction and 5 more.", the Settings tab of the first one)."""
    from .settings.fields import TABS  # data only: no Qt

    names = {spec.key: (spec.label, tab.key) for tab in TABS for spec in tab.fields}
    known = [key for key in typical if key in names]
    if not known:
        return "", ""
    shown = pgettext("list separator", ", ").join(_(names[key][0]) for key in known[:SHOWN_TYPICAL])
    if len(known) > SHOWN_TYPICAL:
        text = _("Based on typical values for: {items} and {n} more.").format(items=shown, n=len(known) - SHOWN_TYPICAL)
    else:
        text = ngettext(
            "Based on a typical value for: {items}.", "Based on typical values for: {items}.", len(known)
        ).format(items=shown)
    return text, names[known[0]][1]


def footer_text(age: timedelta | None, stale: bool, source: ForecastSource | None = None) -> str:
    """"Forecast 12 min old · [from Forecast.Solar, primary unavailable ·] Weather data: Open-Meteo.com (CC BY 4.0)"."""
    items: list[str] = []
    if age is not None:
        minutes = int(age.total_seconds() // 60)
        if minutes < 120:
            text = _("Forecast {n} min old") if not stale else _("Forecast {n} min old, refresh failed")
        else:
            text = _("Forecast {n} h old") if not stale else _("Forecast {n} h old, refresh failed")
        items.append(text.format(n=minutes if minutes < 120 else minutes // 60))
    if source is not None:
        if source.from_fallback:
            items.append(_("from {provider}, primary unavailable").format(provider=source.provider_name))
        if source.cooling_down:
            items.append(_("provider busy, retrying later"))
        items.append(source.attribution)
    return pgettext("footer separator", " \u00b7 ").join(items)


def footer_tooltip(source: ForecastSource | None) -> str:
    """The full credit, and what the footer's short notes mean."""
    if source is None:
        return ""
    lines = [source.attribution]
    if source.from_fallback:
        if source.primary_name:
            lines.append(_("{provider} is used because {primary} has no forecast that is still usable.").format(provider=source.provider_name, primary=source.primary_name))
        else:
            lines.append(_("{provider} is used because the first provider has no forecast that is still usable.").format(provider=source.provider_name))
    if source.cooling_down:
        lines.append(_("{provider} asked not to be asked again yet. The app waits and tries again later.").format(provider=source.provider_name))
    return "\n".join(lines)


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
        _("{power} \u00b7 about {energy} kWh").format(power=_power(w, cfg.charger.phases), energy=number(advice.energy_kwh)),
        reason,
    )


def _quiet_label(day: date, today: date) -> str:
    if day in (today, today + timedelta(days=1)):
        return day_label(day, today)
    return weekday_short(day.weekday())


def _outlook(days: list[DayPlan], headline_window: Window | None, now: datetime, tz: ZoneInfo, today: date, phases: int = 1):
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
                    _power(w, phases),
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
        detail=_("Set up the app to get a plan. It takes a few minutes."),
        battery_line=line,
        battery_stale=False,  # no prompt to update the battery before there is anything to plan
        battery_pct=battery.pct,
        projection="",
        outlook=(),
        quiet_days="",
        footer="",
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
        footer="",
    )


def _facts(window: Window, phases: int = 1) -> str:
    return _("{power} \u00b7 {energy} kWh \u00b7 {share}% solar").format(
        power=_power(window, phases), energy=number(window.solar_kwh, 1), share=number(window.solar_share * 100)
    )


class _Common(TypedDict):
    """The fields every ViewModel of a plan shares, apart from its state and texts."""

    battery_line: str
    battery_stale: bool
    battery_pct: float
    outlook: tuple[OutlookRow, ...]
    quiet_days: str
    footer: str
    footer_tooltip: str
    typical_line: str
    typical_tab: str
    advice: AdviceView | None


def build(
    days: list[DayPlan],
    now: datetime,
    cfg: Config,
    battery: BatteryReading,
    forecast_age: timedelta | None,
    stale: bool,
    advice: GridAdvice | None = None,
    source: ForecastSource | None = None,
) -> ViewModel:
    tz = ZoneInfo(cfg.timezone)
    today = now.astimezone(tz).date()
    upcoming = [(d, w) for d in days for w in d.windows if w.end > now]
    headline_window = upcoming[0][1] if upcoming else None
    outlook, quiet_days = _outlook(days, headline_window, now, tz, today, cfg.charger.phases)
    battery_line, battery_stale = battery_status(battery, now)
    typical = typical_line(cfg.setup.typical)
    common = _Common(
        battery_line=battery_line,
        battery_stale=battery_stale,
        battery_pct=battery.pct,
        outlook=outlook,
        quiet_days=quiet_days,
        footer=footer_text(forecast_age, stale, source),
        footer_tooltip=footer_tooltip(source),
        typical_line=typical[0],
        typical_tab=typical[1],
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

    three = cfg.charger.phases == 3
    day_plan, window = upcoming[0]
    projection = _projection(day_plan, window, cfg, tz)
    if window.start <= now + timedelta(seconds=1):
        return ViewModel(
            state="active",
            tooltip=(
                _("Charge now until {end} at {amps} A per phase") if three else _("Charge now until {end} at {amps} A")
            ).format(end=_clock(window.end, tz), amps=window.amps),
            headline=_("Charge now"),
            detail=_("Until {end} \u00b7 {facts}").format(end=_clock(window.end, tz), facts=_facts(window, cfg.charger.phases)),
            projection=projection,
            **common,
        )
    label = day_label(day_plan.date, today)
    span = _span(window, tz)
    return ViewModel(
        state="upcoming",
        tooltip=(_("Next: {day} {span}, {amps} A per phase") if three else _("Next: {day} {span}, {amps} A")).format(
            day=label, span=span, amps=window.amps
        ),
        headline=_("Next: {day} {span}").format(day=label, span=span),
        detail=_facts(window, cfg.charger.phases),
        projection=projection,
        **common,
    )
