"""What the Finish page shows: the answers grouped as place, panels, car, charger, house and away. No Qt."""

from __future__ import annotations

from dataclasses import dataclass

from app import units
from app.i18n import NC_, _, number, pgettext, translate
from app.names import weekday_short
from planner.model import Config

from .state import SetupState

_COMPASS = (
    (0, NC_("compass direction", "South")),
    (-45, NC_("compass direction", "South-east")),
    (-90, NC_("compass direction", "East")),
    (-135, NC_("compass direction", "North-east")),
    (180, NC_("compass direction", "North")),
    (135, NC_("compass direction", "North-west")),
    (90, NC_("compass direction", "West")),
    (45, NC_("compass direction", "South-west")),
)


def compass_label(azimuth: float) -> str:
    """"South-east (-45°)" for the eight compass directions, plain degrees for anything else."""
    degrees = round(azimuth)
    angle = 180 if degrees == -180 else degrees  # north is 180 whichever way round
    names = dict(_COMPASS)
    if azimuth == degrees and angle in names:
        return _("{direction} ({degrees}\u00b0)").format(direction=translate(names[angle]), degrees=angle)
    return _("{degrees}\u00b0").format(degrees=number(azimuth, 0 if azimuth == degrees else 1))


@dataclass(frozen=True)
class RecapLine:
    label: str
    value: str
    typical: bool = False  # the value is a typical one, not the owner's own


@dataclass(frozen=True)
class RecapGroup:
    key: str  # "place", "panels", "car", "charger", "house", "away"
    title: str
    page: str  # the page that changes it
    lines: tuple[RecapLine, ...]


def _line(label: str, value: str, config: Config, *keys: str) -> RecapLine:
    return RecapLine(label, value, typical=any(key in config.setup.typical for key in keys))


def _distance(config: Config, km: float) -> str:
    system = config.interface.units
    return f"{number(units.to_display(units.DISTANCE, km, system), 0)} {units.unit_label(units.DISTANCE, system)}"


def _consumption(config: Config, kwh_per_100km: float) -> str:
    system = config.interface.units
    decimals = units.display_decimals(units.CONSUMPTION, 1, system)
    return f"{number(units.to_display(units.CONSUMPTION, kwh_per_100km, system), decimals)} {units.unit_label(units.CONSUMPTION, system)}"


def build_recap(state: SetupState) -> list[RecapGroup]:
    cfg = state.config
    place = state.place_label or (
        _("{latitude}, {longitude}").format(latitude=number(cfg.latitude, 4), longitude=number(cfg.longitude, 4)) if cfg.is_set_up else _("not chosen yet")
    )
    p, car, ch = cfg.panels, cfg.car, cfg.charger
    if cfg.away_rules:
        away = pgettext("list separator", ", ").join(
            _("{weekday} {start}-{end}").format(weekday=weekday_short(r.weekday), start=f"{r.start:%H:%M}", end=f"{r.end:%H:%M}") for r in cfg.away_rules
        )
    else:
        away = _("the car is treated as home all day")
    return [
        RecapGroup("place", _("Place"), "location", (
            RecapLine(_("Location"), place),
            RecapLine(_("Time zone"), cfg.timezone),
        )),
        RecapGroup("panels", _("Solar panels"), "solar", (
            _line(_("Size"), _("{kwp} kWp").format(kwp=number(p.kwp, 2)), cfg, "panels.kwp"),
            _line(_("Direction"), compass_label(p.azimuth_deg), cfg, "panels.azimuth_deg"),
            _line(_("Tilt"), _("{degrees}°").format(degrees=number(p.tilt_deg, 0)), cfg, "panels.tilt_deg"),
            _line(_("System losses"), _("{percent}%").format(percent=number(p.system_losses * 100, 0)), cfg, "panels.system_losses"),
        )),
        RecapGroup("car", _("Car"), "car", (
            _line(_("Usable battery"), _("{kwh} kWh").format(kwh=number(car.usable_battery_kwh, 0)), cfg, "car.usable_battery_kwh"),
            _line(_("Consumption"), _consumption(cfg, car.consumption_kwh_per_100km), cfg, "car.consumption_kwh_per_100km"),
            _line(_("Driving per day"), _distance(cfg, car.km_per_day), cfg, "car.km_per_day"),
            RecapLine(_("Charge level"), _("keep above {minimum}%, charge up to {target}%").format(minimum=number(car.minimum_pct), target=number(car.target_pct))),
        )),
        RecapGroup("charger", _("Charger"), "car", (
            _line(_("Phases"), number(ch.phases), cfg, "charger.phases"),
            _line(_("Current"), _("{low} to {high} A").format(low=number(ch.min_amps), high=number(ch.max_amps)), cfg, "charger.min_amps", "charger.max_amps"),
        )),
        RecapGroup("house", _("House"), "house", (
            _line(_("Own use"), _("{kw} kW").format(kw=number(cfg.house.baseload_kw, 2)), cfg, "house.baseload_kw"),
        )),
        RecapGroup("away", _("Car away"), "away", (RecapLine(_("Away times"), away),)),
    ]
