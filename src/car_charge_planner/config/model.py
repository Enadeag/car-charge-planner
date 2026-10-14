"""The engine's config (`Config`), its checks, and reading it from config.toml (writing is in `config.io`)."""

from __future__ import annotations

import re
import tomllib
from dataclasses import dataclass, field, fields, replace
from datetime import time
from pathlib import Path
from typing import Any, cast
from zoneinfo import ZoneInfo, ZoneInfoNotFoundError

from car_charge_planner.i18n import _  # the messages reach the user in Settings, so they are translated
from car_charge_planner.planner.model import AwayRule, HomeSystem, Panels, PlanningPreferences
from car_charge_planner.planner.schema import FIRST_VERSION, KEY, Migration, SchemaError, upgrade

CONFIG_SCHEMA = 3  # the config.toml format this version writes; raise it with a migrate_<n>_to_<n+1> below


def migrate_1_to_2(data: dict[str, Any]) -> dict[str, Any]:
    """Version 2 allows several panel arrays: the one `[panels]` table becomes a list of one (`[[panels]]`)."""
    panels = data.get("panels")
    if isinstance(panels, dict):
        data["panels"] = [panels]
    return data


def migrate_2_to_3(data: dict[str, Any]) -> dict[str, Any]:
    """Version 3 keeps only the engine's settings. The planning preferences come together in [planning]: the charge
    levels from [car] and the notification lead time from [notify]. [interface] goes: it is the desktop app's, which
    takes it into app.toml before this runs (app_settings.adopt_interface)."""
    data.pop("interface", None)
    car = data.get("car")
    planning: dict[str, Any] = dict(data.get("planning", {}))
    if isinstance(car, dict):
        car = cast("dict[str, Any]", car)
        for key in ("minimum_pct", "target_pct"):
            if key in car:
                planning[key] = car.pop(key)
    notify = data.pop("notify", None)
    if isinstance(notify, dict) and "lead_minutes" in notify:
        planning["notify_lead_minutes"] = cast("dict[str, Any]", notify)["lead_minutes"]
    if planning:
        data["planning"] = planning
    return data


CONFIG_MIGRATIONS: dict[int, Migration] = {1: migrate_1_to_2, 2: migrate_2_to_3}  # source version -> the step to the next one


# The settings a person can give no value for ("I don't know"): the app then uses a typical value and remembers
# that it did, in [setup] typical. These are all facts about the system; the planning preferences are choices and
# never typical. The "panels." keys are those of the first panel array; the second array's are "panels.2.kwp" and
# so on (see panel_key and typical_keys).
TYPICAL_KEYS = (
    "panels.kwp",
    "panels.tilt_deg",
    "panels.azimuth_deg",
    "panels.system_losses",
    "house.baseload_kw",
    "car.usable_battery_kwh",
    "car.consumption_kwh_per_100km",
    "car.km_per_day",
    "charger.phases",
    "charger.min_amps",
    "charger.max_amps",
    "charger.volts",
    "charger.efficiency",
)


@dataclass(frozen=True)
class Setup:
    """Where values of the system came from, when not from the owner: what set-up filled in with typical values and
    the owner has not yet replaced. A value not listed was entered by the owner. Another origin (a value read from a
    device) would be one more list here."""

    typical: tuple[str, ...] = ()


ID_PATTERN = re.compile(r"^[a-z0-9][a-z0-9-]*$")  # a forecast provider's id, as written in the config file
SETTING_KEY = re.compile(r"^[a-z0-9_]+$")


@dataclass(frozen=True)
class Forecast:
    """Which forecast providers to use, and each one's settings that are not secret (those live in secrets.toml)."""

    primary: str = "open-meteo"
    fallback: str = ""  # empty means none
    settings: dict[str, dict[str, str]] = field(default_factory=dict[str, dict[str, str]])  # provider id -> {setting key: value}


@dataclass(frozen=True)
class Config:
    """What the engine keeps for the owner. The planner sees only `system` and `planning`."""

    system: HomeSystem = field(default_factory=HomeSystem)
    planning: PlanningPreferences = field(default_factory=PlanningPreferences)
    forecast: Forecast = field(default_factory=Forecast)
    setup: Setup = field(default_factory=Setup)

    @property
    def is_set_up(self) -> bool:
        return self.system.is_set_up

    def with_system(self, **changes: Any) -> Config:
        return replace(self, system=replace(self.system, **changes))

    def with_planning(self, **changes: Any) -> Config:
        return replace(self, planning=replace(self.planning, **changes))


SYSTEM_SECTIONS = ("house", "car", "charger")  # [section] key -> system.<section>.<key>
SYSTEM_VALUES = ("timezone", "latitude", "longitude")  # written at the top of the file, without a section
PANEL_TYPICAL_FIELDS = ("kwp", "tilt_deg", "azimuth_deg", "system_losses")  # what each array can have typical
_PANEL_FIELDS = tuple(f.name for f in fields(Panels))


def panel_key(index: int, name: str) -> str:
    """The key of a value of panel array `index` (counted from 0): "panels.kwp" for the first array and
    "panels.2.kwp" for the second."""
    return f"panels.{name}" if index == 0 else f"panels.{index + 1}.{name}"


def split_panel_key(key: str) -> tuple[int, str] | None:
    """(array index from 0, field name) for a panel key, None for any other key."""
    parts = key.split(".")
    if parts[0] != "panels" or parts[-1] not in _PANEL_FIELDS:
        return None
    if len(parts) == 2:
        return 0, parts[1]
    if len(parts) == 3 and parts[1].isdigit() and parts[1] == str(int(parts[1])) and int(parts[1]) >= 2:
        return int(parts[1]) - 1, parts[2]
    return None


def base_key(key: str) -> str:
    """The key with the array number taken out: "panels.2.kwp" -> "panels.kwp". Other keys are returned as they are."""
    split = split_panel_key(key)
    return f"panels.{split[1]}" if split is not None else key


def typical_keys(cfg: Config) -> tuple[str, ...]:
    """Every setting of this config that can be a typical value, in the order the file lists them."""
    arrays = tuple(panel_key(i, name) for i in range(len(cfg.system.panels)) for name in PANEL_TYPICAL_FIELDS)
    return arrays + tuple(key for key in TYPICAL_KEYS if split_panel_key(key) is None)


def setting(cfg: Config, key: str) -> Any:
    """A value by its key, which is where the file keeps it: "car.km_per_day", "planning.margin", "latitude"."""
    split = split_panel_key(key)
    if split is not None:
        return getattr(cfg.system.panels[split[0]], split[1])
    if "." in key:
        section, name = key.split(".")
        return getattr(getattr(cfg.system, section), name) if section in SYSTEM_SECTIONS else getattr(getattr(cfg, section), name)
    return getattr(cfg.system, key)


def with_setting(cfg: Config, key: str, value: Any) -> Config:
    """The config with one value changed, by its key. A panel key needs its array to exist."""
    split = split_panel_key(key)
    if split is not None:
        index, name = split
        arrays = list(cfg.system.panels)
        arrays[index] = replace(arrays[index], **{name: value})
        return cfg.with_system(panels=tuple(arrays))
    if "." in key:
        section, name = key.split(".")
        if section in SYSTEM_SECTIONS:
            return cfg.with_system(**{section: replace(getattr(cfg.system, section), **{name: value})})
        return replace(cfg, **{section: replace(getattr(cfg, section), **{name: value})})
    return cfg.with_system(**{key: value})


def with_array(cfg: Config, array: Panels | None = None, typical: bool = True) -> Config:
    """The config with one more panel array at the end: typical values unless one is given, and flagged as typical
    (`typical`) until the owner enters their own."""
    cfg = cfg.with_system(panels=(*cfg.system.panels, array or Panels()))
    if not typical:
        return cfg
    index = len(cfg.system.panels) - 1
    added = {panel_key(index, name) for name in PANEL_TYPICAL_FIELDS}
    kept = set(cfg.setup.typical) | added
    return replace(cfg, setup=Setup(tuple(key for key in typical_keys(cfg) if key in kept)))


def without_array(cfg: Config, index: int) -> Config:
    """The config without panel array `index`; the arrays after it move up, and so do their typical flags."""
    if len(cfg.system.panels) <= 1:
        raise ValueError("the last panel array cannot be removed")
    typical: list[str] = []
    for key in cfg.setup.typical:
        split = split_panel_key(key)
        if split is None:
            typical.append(key)
        elif split[0] < index:
            typical.append(key)
        elif split[0] > index:
            typical.append(panel_key(split[0] - 1, split[1]))
    arrays = cfg.system.panels[:index] + cfg.system.panels[index + 1 :]
    return replace(cfg.with_system(panels=arrays), setup=Setup(tuple(typical)))


class ConfigError(ValueError):
    """A config value the planner cannot work with."""


def validate(cfg: Config) -> Config:
    """Reject values that would crash the planner or silently give a wrong plan."""
    problems: list[str] = []

    def need(ok: bool, message: str) -> None:
        if not ok:
            problems.append(message)

    system, planning = cfg.system, cfg.planning
    need(len(system.panels) >= 1, _("[panels] there must be at least one panel array"))
    for index, panels in enumerate(system.panels):
        where = array_section(index, len(system.panels))
        need(panels.kwp > 0, _("{panels} kwp must be above 0").format(panels=where))
        need(0 <= panels.tilt_deg <= 90, _("{panels} tilt_deg must be between 0 and 90").format(panels=where))
        need(-180 <= panels.azimuth_deg <= 180, _("{panels} azimuth_deg must be between -180 and 180").format(panels=where))
        need(0 <= panels.system_losses < 1, _("{panels} system_losses must be at least 0 and below 1").format(panels=where))
        need(isinstance(panels.name, str), _("{panels} name must be a text").format(panels=where))  # pyright: ignore[reportUnnecessaryIsInstance]  # the values come from a TOML file
    need(system.house.baseload_kw >= 0, _("[house] baseload_kw cannot be negative"))
    need(system.car.usable_battery_kwh > 0, _("[car] usable_battery_kwh must be above 0"))
    need(system.car.consumption_kwh_per_100km > 0, _("[car] consumption_kwh_per_100km must be above 0"))
    need(system.car.km_per_day >= 0, _("[car] km_per_day cannot be negative"))
    need(0 <= planning.minimum_pct < planning.target_pct <= 100, _("[planning] needs 0 <= minimum_pct < target_pct <= 100"))
    need(0 < system.charger.min_amps <= system.charger.max_amps, _("[charger] needs 0 < min_amps <= max_amps"))
    need(
        system.charger.phases in (1, 3) and not isinstance(system.charger.phases, bool),  # pyright: ignore[reportUnnecessaryIsInstance]  # TOML `true` equals 1
        _("[charger] phases must be 1 or 3"),
    )
    need(system.charger.volts > 0, _("[charger] volts must be above 0"))
    need(0 < system.charger.efficiency <= 1, _("[charger] efficiency must be above 0 and at most 1"))
    need(0 < planning.margin <= 1, _("[planning] margin must be above 0 and at most 1"))
    need(planning.import_tolerance_kwh >= 0, _("[planning] import_tolerance_kwh cannot be negative"))
    need(planning.min_window_minutes > 0, _("[planning] min_window_minutes must be above 0"))
    need(planning.notify_lead_minutes >= 0, _("[planning] notify_lead_minutes cannot be negative"))
    need(bool(ID_PATTERN.match(str(cfg.forecast.primary))), _("[forecast] primary must be a provider id such as \"open-meteo\""))
    need(
        cfg.forecast.fallback == "" or bool(ID_PATTERN.match(str(cfg.forecast.fallback))),
        _("[forecast] fallback must be empty or a provider id"),
    )
    need(
        not cfg.forecast.fallback or cfg.forecast.fallback != cfg.forecast.primary,
        _("[forecast] the fallback must be a different provider than the primary"),
    )
    for provider_id, values in cfg.forecast.settings.items():
        need(bool(ID_PATTERN.match(str(provider_id))), _("[forecast.{id}] is not a provider id").format(id=provider_id))
        for key, value in values.items():
            need(
                bool(SETTING_KEY.match(str(key))) and isinstance(value, str),  # pyright: ignore[reportUnnecessaryIsInstance]  # the values come from a TOML file
                _("[forecast.{id}] {key} must be a text").format(id=provider_id, key=key),
            )
    possible = typical_keys(cfg)
    unknown = [k for k in cfg.setup.typical if k not in possible]
    need(not unknown, _("[setup] typical lists settings that do not exist: {keys}").format(keys=", ".join(map(str, unknown))))
    need(len(set(cfg.setup.typical)) == len(cfg.setup.typical), _("[setup] typical lists a setting twice"))
    need(-90 <= system.latitude <= 90, _("latitude must be between -90 and 90"))
    need(-180 <= system.longitude <= 180, _("longitude must be between -180 and 180"))
    try:
        ZoneInfo(system.timezone)
    except (ZoneInfoNotFoundError, ValueError):
        problems.append(_("timezone {zone!r} is not a known time zone").format(zone=system.timezone))
    for rule in system.away_rules:
        need(0 <= rule.weekday <= 6, _("[away] weekday {weekday} must be 0 (Monday) to 6 (Sunday)").format(weekday=rule.weekday))
        need(rule.start < rule.end, _("[away] each rule must end after it starts on the same day (no overnight rules)"))
    if problems:
        raise ConfigError(_("Invalid config:") + "\n- " + "\n- ".join(problems))
    return cfg


def array_section(index: int, count: int) -> str:
    """How a message names a panel array: "[panels]" when there is one, "[panels] array 2" when there are several."""
    return "[panels]" if count == 1 else _("[panels] array {number}").format(number=index + 1)


def require_location(cfg: Config) -> Config:
    """For saving settings: a plan needs a real location, so (0, 0) is refused with a plain message."""
    if not cfg.is_set_up:
        raise ConfigError(_("Enter your location: latitude and longitude cannot both be 0"))
    return cfg


def _parse_time(value: str) -> time:
    try:
        hour, minute = value.split(":")
        return time(int(hour), int(minute))
    except (ValueError, AttributeError) as err:
        raise ConfigError(_('[away] time {value!r} must look like "06:00"').format(value=value)) from err


def _read_forecast(table: dict[str, Any]) -> Forecast:
    primary = table.get("primary", Forecast.primary)
    fallback = table.get("fallback", Forecast.fallback)
    settings: dict[str, dict[str, str]] = {}
    for key, value in table.items():
        if key in ("primary", "fallback"):
            continue
        if not isinstance(value, dict):  # a provider's settings are a table of their own: [forecast.<id>]
            raise ConfigError(_("unknown keys in [{section}]: {keys}").format(section="forecast", keys=[key]))
        settings[key] = dict(cast("dict[str, str]", value))
    return Forecast(primary, fallback, settings)


def _read_setup(table: dict[str, Any]) -> Setup:
    unknown = set(table) - {"typical"}
    if unknown:
        raise ConfigError(_("unknown keys in [{section}]: {keys}").format(section="setup", keys=sorted(unknown)))
    typical: Any = table.get("typical", [])
    if not isinstance(typical, list) or not all(isinstance(k, str) for k in cast("list[Any]", typical)):
        raise ConfigError(_("[setup] typical must be a list of setting names"))
    return Setup(tuple(cast("list[str]", typical)))


def first_run_config() -> Config:
    """The config a new install starts with: every value is a typical one, until the owner gives their own."""
    return replace(Config(), setup=Setup(TYPICAL_KEYS))


def _read_panels(value: Any) -> tuple[Panels, ...]:
    if not isinstance(value, list) or not value or not all(isinstance(t, dict) for t in cast("list[Any]", value)):
        raise ConfigError(_("[[panels]] must be one or more tables, one for each panel array"))
    tables = cast("list[dict[str, Any]]", value)
    known = set(_PANEL_FIELDS)
    arrays: list[Panels] = []
    for index, table in enumerate(tables):
        unknown = set(table) - known
        if unknown:
            raise ConfigError(_("unknown keys in {panels}: {keys}").format(panels=array_section(index, len(tables)), keys=sorted(unknown)))
        arrays.append(replace(Panels(), **table))
    return tuple(arrays)


def _read_section(current: Any, section: str, table: dict[str, Any]) -> Any:
    unknown = set(table) - {f.name for f in fields(current)}
    if unknown:
        raise ConfigError(_("unknown keys in [{section}]: {keys}").format(section=section, keys=sorted(unknown)))
    return replace(current, **table)


def read_toml(path: Path) -> dict[str, Any]:
    try:
        return tomllib.loads(Path(path).read_text(encoding="utf-8"))
    except tomllib.TOMLDecodeError as err:
        raise ConfigError(_("Config file is not valid TOML: {error}").format(error=err)) from err


def load_config(path: Path | None) -> Config:
    """Load config from TOML, falling back to defaults for anything missing."""
    if path is None:
        return Config()
    data = read_toml(path)
    try:
        data = upgrade(data, CONFIG_SCHEMA, CONFIG_MIGRATIONS, Path(path).name)  # before any value is read
    except SchemaError as err:
        raise ConfigError(str(err)) from err
    system = HomeSystem()
    if "panels" in data:
        system = replace(system, panels=_read_panels(data["panels"]))
    for section in SYSTEM_SECTIONS:
        if section in data:
            system = replace(system, **{section: _read_section(getattr(system, section), section, data[section])})
    for key in SYSTEM_VALUES:
        if key in data:
            system = replace(system, **{key: data[key]})
    rules = data.get("away", {}).get("recurring")
    if rules is not None:
        system = replace(
            system,
            away_rules=tuple(AwayRule(r["weekday"], _parse_time(r["start"]), _parse_time(r["end"])) for r in rules),
        )
    cfg = Config(system=system)
    if "planning" in data:
        cfg = replace(cfg, planning=_read_section(cfg.planning, "planning", data["planning"]))
    if "forecast" in data:
        cfg = replace(cfg, forecast=_read_forecast(data["forecast"]))
    if "setup" in data:
        cfg = replace(cfg, setup=_read_setup(data["setup"]))
    try:
        return validate(cfg)
    except TypeError as err:
        raise ConfigError(_("A config value has the wrong type: {error}").format(error=err)) from err


def stored_schema(path: Path) -> int | None:
    """The format version a config file says it is in (1 when it says none: the files before versioning), or None when that cannot be read."""
    try:
        version: Any = read_toml(path).get(KEY, FIRST_VERSION)
    except (ConfigError, OSError):
        return None
    return version if isinstance(version, int) and not isinstance(version, bool) else None
