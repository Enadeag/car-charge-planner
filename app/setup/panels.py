"""Helpers for the Solar panels page: panels x watts, compass directions, tilt presets. No Qt."""

from __future__ import annotations

from app.i18n import NC_

# the eight compass directions as buttons: (key, short name, long name, azimuth). Azimuth: 0 = south, -90 = east,
# 90 = west, 180 = north: the convention of the planner and of the forecast providers.
COMPASS = (
    ("nw", NC_("compass, short", "NW"), NC_("compass direction", "North-west"), 135),
    ("n", NC_("compass, short", "N"), NC_("compass direction", "North"), 180),
    ("ne", NC_("compass, short", "NE"), NC_("compass direction", "North-east"), -135),
    ("w", NC_("compass, short", "W"), NC_("compass direction", "West"), 90),
    ("e", NC_("compass, short", "E"), NC_("compass direction", "East"), -90),
    ("sw", NC_("compass, short", "SW"), NC_("compass direction", "South-west"), 45),
    ("s", NC_("compass, short", "S"), NC_("compass direction", "South"), 0),
    ("se", NC_("compass, short", "SE"), NC_("compass direction", "South-east"), -45),
)

# roof tilts people recognise: (key, a name for it, degrees)
TILT_PRESETS = (
    ("flat", NC_("roof tilt", "Flat roof"), 10),
    ("pitched", NC_("roof tilt", "Pitched roof"), 35),
    ("steep", NC_("roof tilt", "Steep roof"), 45),
)


def panels_to_kwp(count: int, watts_each: float) -> float:
    """The total size of a set of panels: 12 panels of 400 W is 4.8 kWp. Rounded to the 10 W Settings keeps."""
    return round(count * watts_each / 1000, 2)


def azimuth_for(key: str) -> int:
    return next(azimuth for k, _short, _long, azimuth in COMPASS if k == key)


def compass_key_for(azimuth: float) -> str | None:
    """The button for these degrees, or None when they are between two (south-south-east is not a button).
    North is 180 and -180 alike."""
    degrees = round(azimuth)
    if degrees != azimuth:
        return None
    wanted = 180 if degrees == -180 else degrees
    return next((key for key, _short, _long, angle in COMPASS if angle == wanted), None)


def tilt_preset_for(tilt: float) -> str | None:
    """The preset that is exactly this tilt, or None."""
    return next((key for key, _name, degrees in TILT_PRESETS if degrees == tilt), None)
