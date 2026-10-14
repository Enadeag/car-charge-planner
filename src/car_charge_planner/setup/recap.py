"""What the Finish page shows: the answers grouped as place, panels, car, charger, house and away. No Qt."""

from __future__ import annotations

from dataclasses import dataclass

from car_charge_planner import units
from car_charge_planner.config.model import Config, panel_key
from car_charge_planner.i18n import _, number, pgettext, translate
from car_charge_planner.names import weekday_short
from car_charge_planner.planner.model import Panels

from .panels import COMPASS
from .state import SetupState


def compass_label(azimuth: float) -> str:
    """"South-east (-45°)" for the eight compass directions, plain degrees for anything else."""
    degrees = round(azimuth)
    angle = 180 if degrees == -180 else degrees  # north is 180 whichever way round
    names = {a: long_name for _key, _short, long_name, a in COMPASS}
    if azimuth == degrees and angle in names:
        return _("{direction} ({degrees}\u00b0)").format(direction=translate(names[angle]), degrees=angle)
    return _("{degrees}\u00b0").format(degrees=number(azimuth, 0 if azimuth == degrees else 1))


def array_title(index: int, panels: Panels) -> str:
    """What a panel array is called: its name, or "Array 2"."""
    return panels.name or _("Array {number}").format(number=index + 1)


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


def _distance(system: str, km: float) -> str:
    return f"{number(units.to_display(units.DISTANCE, km, system), 0)} {units.unit_label(units.DISTANCE, system)}"


def _consumption(system: str, kwh_per_100km: float) -> str:
    decimals = units.display_decimals(units.CONSUMPTION, 1, system)
    return f"{number(units.to_display(units.CONSUMPTION, kwh_per_100km, system), decimals)} {units.unit_label(units.CONSUMPTION, system)}"


def _losses(panels: Panels) -> str:
    return _("{percent}%").format(percent=number(panels.system_losses * 100, 0))


def _panel_lines(cfg: Config) -> tuple[RecapLine, ...]:
    """One array: its size, direction, tilt and losses. Several: the total size, then a line for each array."""
    arrays = cfg.system.panels
    if len(arrays) == 1:
        p = arrays[0]
        return (
            _line(_("Size"), _("{kwp} kWp").format(kwp=number(p.kwp, 2)), cfg, "panels.kwp"),
            _line(_("Direction"), compass_label(p.azimuth_deg), cfg, "panels.azimuth_deg"),
            _line(_("Tilt"), _("{degrees}°").format(degrees=number(p.tilt_deg, 0)), cfg, "panels.tilt_deg"),
            _line(_("System losses"), _losses(p), cfg, "panels.system_losses"),
        )
    keys = range(len(arrays))
    lines = [_line(_("Total size"), _("{kwp} kWp").format(kwp=number(cfg.system.total_kwp, 2)), cfg, *(panel_key(i, "kwp") for i in keys))]
    for index, p in enumerate(arrays):
        value = _("{kwp} kWp, facing {direction}, tilt {tilt}°").format(
            kwp=number(p.kwp, 2), direction=compass_label(p.azimuth_deg), tilt=number(p.tilt_deg, 0)
        )
        lines.append(_line(array_title(index, p), value, cfg, *(panel_key(index, name) for name in ("kwp", "azimuth_deg", "tilt_deg"))))
    losses = list(dict.fromkeys(_losses(p) for p in arrays))  # one figure when they are all the same
    lines.append(_line(_("System losses"), pgettext("list separator", ", ").join(losses), cfg, *(panel_key(i, "system_losses") for i in keys)))
    return tuple(lines)


def build_recap(state: SetupState) -> list[RecapGroup]:
    cfg = state.config
    place = state.place_label or (
        _("{latitude}, {longitude}").format(latitude=number(cfg.system.latitude, 4), longitude=number(cfg.system.longitude, 4)) if cfg.is_set_up else _("not chosen yet")
    )
    car, ch = cfg.system.car, cfg.system.charger
    if cfg.system.away_rules:
        away = pgettext("list separator", ", ").join(
            _("{weekday} {start}-{end}").format(weekday=weekday_short(r.weekday), start=f"{r.start:%H:%M}", end=f"{r.end:%H:%M}") for r in cfg.system.away_rules
        )
    else:
        away = _("the car is treated as home all day")
    return [
        RecapGroup("place", _("Place"), "location", (
            RecapLine(_("Location"), place),
            RecapLine(_("Time zone"), cfg.system.timezone),
        )),
        RecapGroup("panels", _("Solar panels"), "solar", _panel_lines(cfg)),
        RecapGroup("car", _("Car"), "car", (
            _line(_("Usable battery"), _("{kwh} kWh").format(kwh=number(car.usable_battery_kwh, 0)), cfg, "car.usable_battery_kwh"),
            _line(_("Consumption"), _consumption(state.units, car.consumption_kwh_per_100km), cfg, "car.consumption_kwh_per_100km"),
            _line(_("Driving per day"), _distance(state.units, car.km_per_day), cfg, "car.km_per_day"),
            RecapLine(_("Charge level"), _("keep above {minimum}%, charge up to {target}%").format(minimum=number(cfg.planning.minimum_pct), target=number(cfg.planning.target_pct))),
        )),
        RecapGroup("charger", _("Charger"), "car", (
            _line(_("Phases"), number(ch.phases), cfg, "charger.phases"),
            _line(_("Highest current"), _("{amps} A").format(amps=number(ch.max_amps)), cfg, "charger.max_amps"),
            _line(_("Lowest current"), _("{amps} A").format(amps=number(ch.min_amps)), cfg, "charger.min_amps"),
        )),
        RecapGroup("house", _("House"), "house", (
            _line(_("Own use"), _("{kw} kW").format(kw=number(cfg.system.house.baseload_kw, 2)), cfg, "house.baseload_kw"),
        )),
        RecapGroup("away", _("Car away"), "away", (RecapLine(_("Away times"), away),)),
    ]
