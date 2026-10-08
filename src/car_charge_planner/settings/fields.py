"""What the Settings window shows: one FieldSpec per config value. Data only, no Qt."""

from __future__ import annotations

from dataclasses import dataclass, replace

from car_charge_planner.planner.model import Config, panel_key, setting, with_setting

from ..i18n import N_

UNITS_CHOICES = (("metric", N_("Metric (km, kWh/100 km)")), ("imperial", N_("Imperial (miles, mi/kWh)")))


@dataclass(frozen=True)
class FieldSpec:
    key: str  # "panels.kwp" ("panels.2.kwp" for the second panel array), or "latitude" for a top-level value
    label: str
    hint: str
    unit: str = ""
    kind: str = "float"  # "float" | "int" | "timezone" | "choice" | "text" | "secret" (masked)
    minimum: float = 0  # limits of the input box, in shown units; validate() stays the judge
    maximum: float = 1000
    decimals: int = 1
    step: float = 1.0
    scale: float = 1.0  # shown = stored * scale, so a fraction can be shown as a percent
    quantity: str = ""  # "distance" | "consumption": stored metric, shown in the chosen unit system (app/units.py)
    flag: str = ""  # a tag before the hint, for what the field itself says (a provider's "Required" setting)
    choices: tuple[tuple[str | int, str], ...] = ()  # for kind "choice": (stored value, label shown)


@dataclass(frozen=True)
class TabSpec:
    key: str  # what code calls the tab by; the title is what the user reads, in their language
    title: str
    fields: tuple[FieldSpec, ...]


def _provider_key(key: str) -> tuple[str, str] | None:
    """"forecast.<provider id>.<setting>" -> (id, setting): a setting a forecast provider declares."""
    parts = key.split(".")
    return (parts[1], parts[2]) if len(parts) == 3 and parts[0] == "forecast" else None


def get_value(cfg: Config, key: str):
    if (provider := _provider_key(key)) is not None:
        return cfg.forecast.settings.get(provider[0], {}).get(provider[1], "")
    return setting(cfg, key)


def set_value(cfg: Config, key: str, value) -> Config:
    if (provider := _provider_key(key)) is not None:
        provider_id, setting = provider
        settings = {pid: dict(values) for pid, values in cfg.forecast.settings.items()}
        values = settings.setdefault(provider_id, {})
        if value:
            values[setting] = value
        else:
            values.pop(setting, None)  # an empty setting is no setting
        if not values:
            settings.pop(provider_id)
        return replace(cfg, forecast=replace(cfg.forecast, settings=settings))
    return with_setting(cfg, key, value)


# One panel array's fields, with the keys of the first array; array_fields() gives them for any array.
PANEL_FIELDS = (
    FieldSpec(
        "panels.kwp", N_("Panel size"),
        N_("Peak power of the panels together, as stated on the installer's datasheet or in the inverter app. With more than one array: of this array."),
        unit="kWp", minimum=0.1, maximum=100, decimals=2, step=0.1,
    ),
    FieldSpec(
        "panels.tilt_deg", N_("Tilt"), N_("Angle of the panels from horizontal: 0 is flat, 90 is vertical."),
        unit=N_("degrees"), minimum=0, maximum=90, decimals=1, step=1,
    ),
    FieldSpec(
        "panels.azimuth_deg", N_("Direction"), N_("Compass direction the panels face: 0 = south, -90 = east, 90 = west, 180 = north. South-east is -45."),
        unit=N_("degrees"), minimum=-180, maximum=180, decimals=1, step=5,
    ),
    FieldSpec(
        "panels.system_losses", N_("System losses"), N_("Combined losses from the inverter, cables, heat and dirt. Typically 10-15%."),
        unit="%", minimum=0, maximum=99.9, decimals=1, step=1, scale=100,
    ),
)
ARRAY_NAME = FieldSpec("panels.name", N_("Name"), N_("Optional: what you call this array, such as \"East roof\" or \"Garage\"."), kind="text")

LOCATION_FIELDS = (
    FieldSpec("latitude", N_("Latitude"), N_("Decimal degrees of the panels' location. Positive is north. 0 and 0 together mean not set."), unit=N_("degrees"), minimum=-90, maximum=90, decimals=4, step=0.01),
    FieldSpec("longitude", N_("Longitude"), N_("Decimal degrees of the panels' location. Positive is east of Greenwich."), unit=N_("degrees"), minimum=-180, maximum=180, decimals=4, step=0.01),
    FieldSpec("timezone", N_("Time zone"), N_("The time zone of the location. Start typing to search the list."), kind="timezone"),
)


def array_fields(index: int, named: bool) -> tuple[FieldSpec, ...]:
    """The fields of panel array `index` (from 0), keyed "panels.kwp" for the first and "panels.2.kwp" for the
    second; with a name field first when `named` (there is more than one array)."""
    specs = ((ARRAY_NAME,) if named else ()) + PANEL_FIELDS
    return tuple(replace(spec, key=panel_key(index, spec.key.split(".")[1])) for spec in specs)


# The Solar tab as a list: one array and the location. With several arrays it repeats the array's fields
# (settings/tabs.py, SolarTab).
SOLAR = TabSpec("solar", N_("Solar and location"), PANEL_FIELDS + LOCATION_FIELDS)

HOUSE_CAR = TabSpec(
    "house_car",
    N_("House and car"),
    (
        FieldSpec(
            "house.baseload_kw", N_("House baseload"), N_("Average power the household uses without the car. Monthly kWh divided by 730 gives a rough figure."),
            unit="kW", minimum=0, maximum=20, decimals=2, step=0.05,
        ),
        FieldSpec(
            "car.usable_battery_kwh", N_("Usable battery"), N_("The net (usable) battery capacity in kWh. Manufacturers often list the gross capacity, which is higher."),
            unit="kWh", minimum=1, maximum=200, decimals=1, step=1,
        ),
        FieldSpec(
            "car.consumption_kwh_per_100km", N_("Consumption"), N_("Real-world average energy use of the car. Usually higher in winter than in summer."),
            unit="kWh/100 km", minimum=5, maximum=60, decimals=1, step=0.5, quantity="consumption",
        ),
        FieldSpec(
            "car.km_per_day", N_("Driving per day"), N_("Average distance driven per day. Weekly distance divided by 7 gives a rough figure."),
            unit="km", minimum=0, maximum=1000, decimals=1, step=5, quantity="distance",
        ),
        FieldSpec(
            "car.minimum_pct", N_("Minimum charge level"), N_("The battery should never drop below this level. Must be below the target."),
            unit="%", minimum=0, maximum=100, decimals=0, step=5,
        ),
        FieldSpec(
            "car.target_pct", N_("Target charge level"), N_("Everyday maximum charge level. Must be above the minimum."),
            unit="%", minimum=1, maximum=100, decimals=0, step=5,
        ),
    ),
)

CHARGER = TabSpec(
    "charger",
    N_("Charger"),
    (
        FieldSpec(
            "charger.phases",
            N_("Phases"),
            N_(
                "Whether the charger charges on one phase or three. The charger's label or manual says, and so does the "
                "fuse box. Three phases deliver three times the power at the same current. Some chargers can be switched "
                "to single-phase charging, which suits solar surplus better. Currents below are per phase."
            ),
            kind="choice",
            choices=((1, N_("One phase")), (3, N_("Three phases"))),
        ),
        FieldSpec(
            "charger.min_amps", N_("Minimum current"), N_("The lowest current the charger and car both accept, often 6 A. Per phase on a three-phase charger."),
            unit="A", kind="int", minimum=1, maximum=80, step=1,
        ),
        FieldSpec(
            "charger.max_amps", N_("Maximum current"), N_("The highest current the charger and the home connection allow, per phase. Must not be below the minimum."),
            unit="A", kind="int", minimum=1, maximum=80, step=1,
        ),
        FieldSpec("charger.volts", N_("Voltage"), N_("Mains voltage at the charger, typically 230 V for a single-phase connection."), unit="V", minimum=100, maximum=400, decimals=0, step=1),
        FieldSpec(
            "charger.efficiency", N_("Charging efficiency"), N_("Share of the energy taken from the grid that ends up in the battery, after losses in charger and car."),
            unit="%", minimum=1, maximum=100, decimals=1, step=1, scale=100,
        ),
    ),
)

PLANNING = TabSpec(
    "planning",
    N_("Planning and notifications"),
    (
        FieldSpec(
            "planning.margin", N_("Planning margin"), N_("Plan on this share of the forecast surplus. Lower is more cautious."),
            unit="%", minimum=1, maximum=100, decimals=0, step=5, scale=100,
        ),
        FieldSpec(
            "planning.import_tolerance_kwh", N_("Import tolerance"), N_("Grid energy a window may draw before its power is lowered."),
            unit="kWh", minimum=0, maximum=5, decimals=2, step=0.05,
        ),
        FieldSpec(
            "planning.min_window_minutes", N_("Minimum window length"), N_("Shorter windows are dropped."),
            unit=N_("minutes"), kind="int", minimum=5, maximum=240, step=5,
        ),
        FieldSpec(
            "notify.lead_minutes", N_("Notification lead time"), N_("How long before a window starts you are told."),
            unit=N_("minutes"), kind="int", minimum=0, maximum=120, step=5,
        ),
    ),
)

# the Away rules tab is not a list of numbers: it has its own widget (tabs.py)
INTERFACE = TabSpec(
    "interface",
    N_("Interface"),
    (
        FieldSpec(
            "interface.mode",
            N_("Show the app as"),
            N_("How the app shows itself. A tray icon needs a desktop with a system tray (KDE Plasma, or GNOME with the "
            "AppIndicator extension). Applies the next time the app starts."),
            kind="choice",
            choices=(
                ("auto", N_("Automatic: a tray icon if there is a tray, otherwise a window")),
                ("tray", N_("Tray icon with a popup")),
                ("window", N_("A window")),
            ),
        ),
        FieldSpec(
            "interface.units",
            N_("Units"),
            N_("How distances and consumption are shown: kilometres and kWh/100 km, or miles and miles per kWh. Energy and power are the same in both."),
            kind="choice",
            choices=UNITS_CHOICES,
        ),
        FieldSpec(
            "interface.language",
            N_("Language"),
            N_("The language of the app. Applies the next time the app starts."),
            kind="choice",
            choices=(),  # filled from the installed catalogues; the field is left out when there are none
        ),
    ),
)

TABS: tuple[TabSpec, ...] = (SOLAR, HOUSE_CAR, CHARGER, PLANNING, INTERFACE)
