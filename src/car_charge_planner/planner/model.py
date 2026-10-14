"""What the planner works with: the user's system, their planning preferences, the forecast and the plan it makes."""

from __future__ import annotations

import math
from dataclasses import dataclass, field
from datetime import UTC, datetime, time, timedelta
from datetime import date as DateType
from zoneinfo import ZoneInfo

import tzlocal


def utc(moment: datetime) -> datetime:
    """The same instant in UTC. Compare in UTC: datetimes in one zone compare by wall clock, so the repeated hour would vanish."""
    return moment.astimezone(UTC)


_FIVE_MINUTES = timedelta(minutes=5)


def ceil_to_five_minutes(moment: datetime) -> datetime:
    """moment rounded up to whole five minutes of real time since its local midnight."""
    midnight = utc(moment.replace(hour=0, minute=0, second=0, microsecond=0, fold=0))
    steps = math.ceil((utc(moment) - midnight) / _FIVE_MINUTES - 1e-9)
    return (midnight + steps * _FIVE_MINUTES).astimezone(moment.tzinfo)


def elapsed_hours(start: datetime, end: datetime) -> float:
    """Real elapsed hours. Same-timezone datetimes subtract by wall clock, which is wrong across DST."""
    return (end.astimezone(UTC) - start.astimezone(UTC)) / timedelta(hours=1)


def add_hours(moment: datetime, hours: float) -> datetime:
    """moment + elapsed hours. Plain addition on a zoned datetime moves the wall clock, which is wrong across DST."""
    return (moment.astimezone(UTC) + timedelta(hours=hours)).astimezone(moment.tzinfo)


@dataclass(frozen=True)
class ForecastStep:
    """Expected AC power of the solar system (kW) for [start, end), as a forecast provider gives it."""

    start: datetime
    end: datetime
    power_kw: float

    @property
    def hours(self) -> float:
        return elapsed_hours(self.start, self.end)


@dataclass(frozen=True)
class AwayPeriod:
    """A span when the car is not at home and cannot be charged."""

    start: datetime
    end: datetime


@dataclass(frozen=True)
class AwayRule:
    """Recurring away period: weekday 0=Monday .. 6=Sunday, local times."""

    weekday: int
    start: time
    end: time


@dataclass(frozen=True)
class Panels:
    """One panel array: panels with the same tilt and direction. A home may have several (east and west roofs)."""

    kwp: float = 5.0
    tilt_deg: float = 35
    azimuth_deg: float = 0  # south
    system_losses: float = 0.14
    name: str = ""  # optional, for the owner: "Garage roof"


@dataclass(frozen=True)
class House:
    baseload_kw: float = 0.5


@dataclass(frozen=True)
class Car:
    usable_battery_kwh: float = 60
    consumption_kwh_per_100km: float = 18
    km_per_day: float = 40


@dataclass(frozen=True)
class Charger:
    phases: int = 1  # 1 or 3: each phase carries the current, so three phases deliver three times the power
    min_amps: int = 6
    max_amps: int = 16
    volts: float = 230
    efficiency: float = 0.90


def system_timezone() -> str:
    """The system's IANA time zone name, or UTC when tzlocal cannot tell or names a zone we do not know."""
    try:
        name = tzlocal.get_localzone_name()
        ZoneInfo(name)
    except Exception:  # tzlocal raises whatever its platform backend does (missing keys, unreadable links)
        return "UTC"
    return name


@dataclass(frozen=True)
class HomeSystem:
    """Facts about the owner's home and equipment, and when the car is away: what the plan is made for.

    Not named SystemDescription: that is the smaller part a forecast provider is given (forecast/provider.py)."""

    panels: tuple[Panels, ...] = (Panels(),)  # one or more panel arrays: [[panels]] in the file
    house: House = field(default_factory=House)
    car: Car = field(default_factory=Car)
    charger: Charger = field(default_factory=Charger)
    away_rules: tuple[AwayRule, ...] = ()
    timezone: str = field(default_factory=system_timezone)
    # (0, 0) means "not set": a new install has no location until the user gives one
    latitude: float = 0.0
    longitude: float = 0.0

    @property
    def is_set_up(self) -> bool:
        """Has the user given a location? Until then nothing is fetched and there is no plan."""
        return (self.latitude, self.longitude) != (0, 0)

    @property
    def total_kwp(self) -> float:
        return sum(p.kwp for p in self.panels)


@dataclass(frozen=True)
class PlanningPreferences:
    """How the owner wants the plan made: choices, not facts about the equipment."""

    minimum_pct: float = 30
    target_pct: float = 80
    margin: float = 0.85
    import_tolerance_kwh: float = 0.2
    min_window_minutes: int = 45
    notify_lead_minutes: int = 15


@dataclass(frozen=True)
class Window:
    """A recommended charge window at one constant charge power."""

    start: datetime
    end: datetime
    amps: int
    power_kw: float
    solar_kwh: float
    import_kwh: float

    @property
    def solar_share(self) -> float:
        total = self.solar_kwh + self.import_kwh
        return self.solar_kwh / total if total > 0 else 0.0


@dataclass(frozen=True)
class DayPlan:
    date: DateType  # not `date`: the field name would hide the class
    soc_start_pct: float
    windows: tuple[Window, ...]
    soc_after_charging_pct: float
    soc_end_of_day_pct: float
    driving_pct: float = 0.0  # driving taken off this day; end of day is clamped at 0, this keeps the true shortfall
