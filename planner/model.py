"""Data types and configuration for the planner. No I/O except loading TOML."""

from __future__ import annotations

import os
import tomllib
from functools import lru_cache
from dataclasses import dataclass, field, fields, replace
from datetime import datetime, time, timedelta, timezone
from pathlib import Path
from zoneinfo import ZoneInfo, ZoneInfoNotFoundError


def elapsed_hours(start: datetime, end: datetime) -> float:
    """Real elapsed hours. Same-timezone datetimes subtract by wall clock, which is wrong across DST."""
    return (end.astimezone(timezone.utc) - start.astimezone(timezone.utc)) / timedelta(hours=1)


def add_hours(moment: datetime, hours: float) -> datetime:
    """moment + elapsed hours. Plain addition on a zoned datetime moves the wall clock, which is wrong across DST."""
    return (moment.astimezone(timezone.utc) + timedelta(hours=hours)).astimezone(moment.tzinfo)


@dataclass(frozen=True)
class ForecastStep:
    """Solar irradiance on the panel plane (W/m2) for [start, end)."""

    start: datetime
    end: datetime
    gti_wm2: float

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
    kwp: float = 4.8
    tilt_deg: float = 35
    azimuth_deg: float = -45
    system_losses: float = 0.14


@dataclass(frozen=True)
class House:
    baseload_kw: float = 0.6


@dataclass(frozen=True)
class Car:
    usable_battery_kwh: float = 70
    consumption_kwh_per_100km: float = 18
    km_per_day: float = 600 / 7
    minimum_pct: float = 30
    target_pct: float = 80


@dataclass(frozen=True)
class Charger:
    min_amps: int = 6
    max_amps: int = 32
    volts: float = 230
    efficiency: float = 0.90


@dataclass(frozen=True)
class Planning:
    margin: float = 0.85
    import_tolerance_kwh: float = 0.2
    min_window_minutes: int = 45


@dataclass(frozen=True)
class Notify:
    lead_minutes: int = 15


INTERFACE_MODES = ("auto", "tray", "window")


@dataclass(frozen=True)
class Interface:
    """How the app shows itself: a tray icon with a popup, an ordinary window, or whichever the desktop supports."""

    mode: str = "auto"


def find_timezone(environ: dict[str, str], etc_timezone: str, localtime_target: str) -> str:
    """The system's IANA time zone name from what the system offers, or UTC.

    Looks at TZ, then /etc/timezone (Debian style), then the target of the /etc/localtime link.
    """
    candidates = [environ.get("TZ", "").lstrip(":"), etc_timezone.strip()]
    if "zoneinfo/" in localtime_target:
        candidates.append(localtime_target.split("zoneinfo/", 1)[1])
    for name in candidates:
        for prefix in ("posix/", "right/"):
            name = name.removeprefix(prefix)
        if not name:
            continue
        try:
            ZoneInfo(name)
        except (ZoneInfoNotFoundError, ValueError):
            continue
        return name
    return "UTC"


@lru_cache(maxsize=1)
def system_timezone() -> str:
    try:
        etc_timezone = Path("/etc/timezone").read_text()
    except OSError:
        etc_timezone = ""
    return find_timezone(dict(os.environ), etc_timezone, os.path.realpath("/etc/localtime"))


@dataclass(frozen=True)
class Config:
    panels: Panels = field(default_factory=Panels)
    house: House = field(default_factory=House)
    car: Car = field(default_factory=Car)
    charger: Charger = field(default_factory=Charger)
    planning: Planning = field(default_factory=Planning)
    notify: Notify = field(default_factory=Notify)
    interface: Interface = field(default_factory=Interface)
    away_rules: tuple[AwayRule, ...] = ()
    timezone: str = field(default_factory=system_timezone)
    # (0, 0) means "not set": a new install has no location until the user gives one
    latitude: float = 0.0
    longitude: float = 0.0

    @property
    def is_set_up(self) -> bool:
        """Has the user given a location? Until then nothing is fetched and there is no plan."""
        return (self.latitude, self.longitude) != (0, 0)


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
    date: object  # datetime.date
    soc_start_pct: float
    windows: tuple[Window, ...]
    soc_after_charging_pct: float
    soc_end_of_day_pct: float
    driving_pct: float = 0.0  # driving taken off this day; end of day is clamped at 0, this keeps the true shortfall


class ConfigError(ValueError):
    """The config file has a value the planner cannot work with."""


def validate(cfg: Config) -> Config:
    """Reject values that would crash the planner or silently give a wrong plan."""
    problems: list[str] = []

    def need(ok: bool, message: str) -> None:
        if not ok:
            problems.append(message)

    need(cfg.panels.kwp > 0, "[panels] kwp must be above 0")
    need(0 <= cfg.panels.tilt_deg <= 90, "[panels] tilt_deg must be between 0 and 90")
    need(-180 <= cfg.panels.azimuth_deg <= 180, "[panels] azimuth_deg must be between -180 and 180")
    need(0 <= cfg.panels.system_losses < 1, "[panels] system_losses must be at least 0 and below 1")
    need(cfg.house.baseload_kw >= 0, "[house] baseload_kw cannot be negative")
    need(cfg.car.usable_battery_kwh > 0, "[car] usable_battery_kwh must be above 0")
    need(cfg.car.consumption_kwh_per_100km > 0, "[car] consumption_kwh_per_100km must be above 0")
    need(cfg.car.km_per_day >= 0, "[car] km_per_day cannot be negative")
    need(0 <= cfg.car.minimum_pct < cfg.car.target_pct <= 100, "[car] needs 0 <= minimum_pct < target_pct <= 100")
    need(0 < cfg.charger.min_amps <= cfg.charger.max_amps, "[charger] needs 0 < min_amps <= max_amps")
    need(cfg.charger.volts > 0, "[charger] volts must be above 0")
    need(0 < cfg.charger.efficiency <= 1, "[charger] efficiency must be above 0 and at most 1")
    need(0 < cfg.planning.margin <= 1, "[planning] margin must be above 0 and at most 1")
    need(cfg.planning.import_tolerance_kwh >= 0, "[planning] import_tolerance_kwh cannot be negative")
    need(cfg.planning.min_window_minutes > 0, "[planning] min_window_minutes must be above 0")
    need(cfg.notify.lead_minutes >= 0, "[notify] lead_minutes cannot be negative")
    need(cfg.interface.mode in INTERFACE_MODES, f"[interface] mode must be one of {', '.join(INTERFACE_MODES)}, not {cfg.interface.mode!r}")
    need(-90 <= cfg.latitude <= 90, "latitude must be between -90 and 90")
    need(-180 <= cfg.longitude <= 180, "longitude must be between -180 and 180")
    try:
        ZoneInfo(cfg.timezone)
    except (ZoneInfoNotFoundError, ValueError):
        problems.append(f"timezone {cfg.timezone!r} is not a known time zone")
    for rule in cfg.away_rules:
        need(0 <= rule.weekday <= 6, f"[away] weekday {rule.weekday} must be 0 (Monday) to 6 (Sunday)")
        need(rule.start < rule.end, "[away] each rule must end after it starts on the same day (no overnight rules)")
    if problems:
        raise ConfigError("Invalid config:\n- " + "\n- ".join(problems))
    return cfg


def require_location(cfg: Config) -> Config:
    """For saving settings: a plan needs a real location, so (0, 0) is refused with a plain message."""
    if not cfg.is_set_up:
        raise ConfigError("Enter your location: latitude and longitude cannot both be 0")
    return cfg


def _parse_time(value: str) -> time:
    try:
        hour, minute = value.split(":")
        return time(int(hour), int(minute))
    except (ValueError, AttributeError) as err:
        raise ConfigError(f"[away] time {value!r} must look like \"06:00\"") from err


def load_config(path: Path | None) -> Config:
    """Load config from TOML, falling back to defaults for anything missing."""
    if path is None:
        return Config()
    try:
        data = tomllib.loads(Path(path).read_text())
    except tomllib.TOMLDecodeError as err:
        raise ConfigError(f"Config file is not valid TOML: {err}") from err
    cfg = Config()
    for section in ("panels", "house", "car", "charger", "planning", "notify", "interface"):
        if section in data:
            current = getattr(cfg, section)
            known = {f.name for f in fields(current)}
            unknown = set(data[section]) - known
            if unknown:
                raise ConfigError(f"unknown keys in [{section}]: {sorted(unknown)}")
            cfg = replace(cfg, **{section: replace(current, **data[section])})
    for key in ("timezone", "latitude", "longitude"):
        if key in data:
            cfg = replace(cfg, **{key: data[key]})
    rules = data.get("away", {}).get("recurring")
    if rules is not None:
        cfg = replace(
            cfg,
            away_rules=tuple(
                AwayRule(r["weekday"], _parse_time(r["start"]), _parse_time(r["end"]))
                for r in rules
            ),
        )
    try:
        return validate(cfg)
    except TypeError as err:
        raise ConfigError(f"A config value has the wrong type: {err}") from err
