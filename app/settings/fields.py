"""What the Settings window shows: one FieldSpec per config value. Data only, no Qt."""

from __future__ import annotations

from dataclasses import dataclass, replace

from planner.model import Config


@dataclass(frozen=True)
class FieldSpec:
    key: str  # "panels.kwp", or "latitude" for a top-level value
    label: str
    hint: str
    unit: str = ""
    kind: str = "float"  # "float" | "int" | "timezone" | "choice"
    minimum: float = 0  # limits of the input box, in shown units; validate() stays the judge
    maximum: float = 1000
    decimals: int = 1
    step: float = 1.0
    scale: float = 1.0  # shown = stored * scale, so a fraction can be shown as a percent
    flag: str = ""  # "Assumed" or "Estimate": a value that is not measured yet
    choices: tuple[tuple[str, str], ...] = ()  # for kind "choice": (stored value, label shown)


@dataclass(frozen=True)
class TabSpec:
    title: str
    fields: tuple[FieldSpec, ...]


def get_value(cfg: Config, key: str):
    if "." in key:
        section, name = key.split(".")
        return getattr(getattr(cfg, section), name)
    return getattr(cfg, key)


def set_value(cfg: Config, key: str, value) -> Config:
    if "." in key:
        section, name = key.split(".")
        return replace(cfg, **{section: replace(getattr(cfg, section), **{name: value})})
    return replace(cfg, **{key: value})


SOLAR = TabSpec(
    "Solar and location",
    (
        FieldSpec(
            "panels.kwp", "Panel size", "Total peak power of all panels together, as stated on the installer's datasheet or in the inverter app.",
            unit="kWp", minimum=0.1, maximum=100, decimals=2, step=0.1, flag="Assumed",
        ),
        FieldSpec(
            "panels.tilt_deg", "Tilt", "Angle of the panels from horizontal: 0 is flat, 90 is vertical.",
            unit="degrees", minimum=0, maximum=90, decimals=1, step=1, flag="Assumed",
        ),
        FieldSpec(
            "panels.azimuth_deg", "Direction", "Compass direction the panels face: 0 = south, -90 = east, 90 = west, 180 = north. South-east is -45.",
            unit="degrees", minimum=-180, maximum=180, decimals=1, step=5,
        ),
        FieldSpec(
            "panels.system_losses", "System losses", "Combined losses from the inverter, cables, heat and dirt. Typically 10-15%.",
            unit="%", minimum=0, maximum=99.9, decimals=1, step=1, scale=100,
        ),
        FieldSpec("latitude", "Latitude", "Decimal degrees of the panels' location. Positive is north of the equator.", unit="degrees", minimum=-90, maximum=90, decimals=4, step=0.01),
        FieldSpec("longitude", "Longitude", "Decimal degrees of the panels' location. Positive is east of Greenwich.", unit="degrees", minimum=-180, maximum=180, decimals=4, step=0.01),
        FieldSpec("timezone", "Time zone", "The time zone of the location. Start typing to search the list.", kind="timezone"),
    ),
)

HOUSE_CAR = TabSpec(
    "House and car",
    (
        FieldSpec(
            "house.baseload_kw", "House baseload", "Average power the household uses without the car. Monthly kWh divided by 730 gives a rough figure.",
            unit="kW", minimum=0, maximum=20, decimals=2, step=0.05,
        ),
        FieldSpec(
            "car.usable_battery_kwh", "Usable battery", "The net (usable) battery capacity in kWh. Manufacturers often list the gross capacity, which is higher.",
            unit="kWh", minimum=1, maximum=200, decimals=1, step=1, flag="Estimate",
        ),
        FieldSpec(
            "car.consumption_kwh_per_100km", "Consumption", "Real-world average energy use of the car. Usually higher in winter than in summer.",
            unit="kWh/100 km", minimum=5, maximum=60, decimals=1, step=0.5, flag="Estimate",
        ),
        FieldSpec(
            "car.km_per_day", "Driving per day", "Average distance driven per day. Weekly distance divided by 7 gives a rough figure.",
            unit="km", minimum=0, maximum=1000, decimals=1, step=5,
        ),
        FieldSpec(
            "car.minimum_pct", "Minimum charge level", "The battery should never drop below this level. Must be below the target.",
            unit="%", minimum=0, maximum=100, decimals=0, step=5,
        ),
        FieldSpec(
            "car.target_pct", "Target charge level", "Everyday maximum charge level. Must be above the minimum.",
            unit="%", minimum=1, maximum=100, decimals=0, step=5,
        ),
    ),
)

CHARGER = TabSpec(
    "Charger",
    (
        FieldSpec(
            "charger.min_amps", "Minimum current", "The lowest current the charger and car both accept, often 6 A.",
            unit="A", kind="int", minimum=1, maximum=80, step=1,
        ),
        FieldSpec(
            "charger.max_amps", "Maximum current", "The highest current the charger and the home connection allow. Must not be below the minimum.",
            unit="A", kind="int", minimum=1, maximum=80, step=1,
        ),
        FieldSpec("charger.volts", "Voltage", "Mains voltage at the charger, typically 230 V for a single-phase connection.", unit="V", minimum=100, maximum=400, decimals=0, step=1),
        FieldSpec(
            "charger.efficiency", "Charging efficiency", "Share of the energy taken from the grid that ends up in the battery, after losses in charger and car.",
            unit="%", minimum=1, maximum=100, decimals=1, step=1, scale=100, flag="Estimate",
        ),
    ),
)

PLANNING = TabSpec(
    "Planning and notifications",
    (
        FieldSpec(
            "planning.margin", "Planning margin", "Plan on this share of the forecast surplus. Lower is more cautious.",
            unit="%", minimum=1, maximum=100, decimals=0, step=5, scale=100,
        ),
        FieldSpec(
            "planning.import_tolerance_kwh", "Import tolerance", "Grid energy a window may draw before its power is lowered.",
            unit="kWh", minimum=0, maximum=5, decimals=2, step=0.05,
        ),
        FieldSpec(
            "planning.min_window_minutes", "Minimum window length", "Shorter windows are dropped.",
            unit="minutes", kind="int", minimum=5, maximum=240, step=5,
        ),
        FieldSpec(
            "notify.lead_minutes", "Notification lead time", "How long before a window starts you are told.",
            unit="minutes", kind="int", minimum=0, maximum=120, step=5,
        ),
    ),
)

# the Away rules tab is not a list of numbers: it has its own widget (tabs.py)
INTERFACE = TabSpec(
    "Interface",
    (
        FieldSpec(
            "interface.mode",
            "Show the app as",
            "How the app shows itself. A tray icon needs a desktop with a system tray (KDE Plasma, or GNOME with the "
            "AppIndicator extension). Applies the next time the app starts.",
            kind="choice",
            choices=(
                ("auto", "Automatic: a tray icon if there is a tray, otherwise a window"),
                ("tray", "Tray icon with a popup"),
                ("window", "A window"),
            ),
        ),
    ),
)

TABS: tuple[TabSpec, ...] = (SOLAR, HOUSE_CAR, CHARGER, PLANNING, INTERFACE)
