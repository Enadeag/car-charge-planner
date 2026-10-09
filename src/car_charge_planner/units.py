"""Metric and imperial units for distances and consumption shown to the user. No Qt.

Everything stored and planned stays metric (km, kWh/100 km). Consumption converts by the **reciprocal**: 18 kWh/100 km
is 100/18 km per kWh, which is 62.1371/18 = 3.45 miles per kWh.
"""

from __future__ import annotations

import math
import os
from collections.abc import Mapping

METRIC = "metric"
IMPERIAL = "imperial"
SYSTEMS = (METRIC, IMPERIAL)

KM_PER_MILE = 1.609344
IMPERIAL_REGIONS = ("US", "GB", "LR", "MM")  # the countries that still measure road distances in miles

DISTANCE = "distance"  # stored in km
CONSUMPTION = "consumption"  # stored in kWh per 100 km


def km_to_miles(km: float) -> float:
    return km / KM_PER_MILE


def miles_to_km(miles: float) -> float:
    return miles * KM_PER_MILE


def kwh_per_100km_to_miles_per_kwh(kwh_per_100km: float) -> float:
    """100 km on x kWh is 100/x km per kWh. Not defined for no consumption at all."""
    if kwh_per_100km <= 0:
        raise ValueError("consumption must be above 0")
    return 100 / (kwh_per_100km * KM_PER_MILE)


def miles_per_kwh_to_kwh_per_100km(miles_per_kwh: float) -> float:
    if miles_per_kwh <= 0:
        raise ValueError("miles per kWh must be above 0")
    return 100 / (miles_per_kwh * KM_PER_MILE)


def unit_label(quantity: str, system: str) -> str:
    """The unit symbol (not translated: symbols are the same in every language)."""
    if quantity == DISTANCE:
        return "mi" if system == IMPERIAL else "km"
    if quantity == CONSUMPTION:
        return "mi/kWh" if system == IMPERIAL else "kWh/100 km"
    raise ValueError(f"unknown quantity {quantity!r}")


def suffix(quantity: str, system: str) -> str:
    return f" {unit_label(quantity, system)}"


def to_display(quantity: str, stored: float, system: str) -> float:
    """A stored (metric) value as the chosen system shows it."""
    if system != IMPERIAL:
        return stored
    if quantity == DISTANCE:
        return km_to_miles(stored)
    if quantity == CONSUMPTION:
        return kwh_per_100km_to_miles_per_kwh(stored)
    raise ValueError(f"unknown quantity {quantity!r}")


def from_display(quantity: str, shown: float, system: str) -> float:
    """What a user typed, in the chosen system, as the stored (metric) value."""
    if system != IMPERIAL:
        return shown
    if quantity == DISTANCE:
        return miles_to_km(shown)
    if quantity == CONSUMPTION:
        return miles_per_kwh_to_kwh_per_100km(shown)
    raise ValueError(f"unknown quantity {quantity!r}")


def display_range(quantity: str, minimum: float, maximum: float, system: str) -> tuple[float, float]:
    """The limits of an input box, converted. The reciprocal turns them round: the largest consumption is the smallest range."""
    if system != IMPERIAL:
        return minimum, maximum
    low, high = sorted((to_display(quantity, minimum, system), to_display(quantity, maximum, system)))
    if quantity == CONSUMPTION:
        return math.floor(low * 10) / 10, math.ceil(high * 10) / 10
    return math.floor(low), math.ceil(high)


def display_decimals(quantity: str, metric_decimals: int, system: str) -> int:
    if system == IMPERIAL and quantity == CONSUMPTION:
        return 2  # 3.45 mi/kWh: a tenth is too coarse on a range of about 1 to 12
    return metric_decimals


def display_step(quantity: str, metric_step: float, system: str) -> float:
    if system == IMPERIAL and quantity == CONSUMPTION:
        return 0.05
    return metric_step


def _region(locale_name: str) -> str:
    """'en_GB.UTF-8' -> 'GB'; '' for the C locale and for names without a region."""
    name = locale_name.strip().split("@")[0].split(".")[0].replace("-", "_")
    if name.lower() in ("", "c", "posix"):
        return ""
    parts = name.split("_")
    return next((p.upper() for p in parts[1:] if len(p) == 2 and p.isalpha()), "")


def suggest_system(environ: Mapping[str, str] | None = None, qt_locale_name: str = "") -> str:
    """Imperial for the regions in `IMPERIAL_REGIONS`, metric elsewhere.

    The region comes from the locale categories in the order the C library uses them: LC_ALL, then LC_MEASUREMENT
    (the one that is about units), then LANG. With none set, `qt_locale_name` (Qt's system locale, "en_US") is used.
    """
    env = os.environ if environ is None else environ
    chosen = next((env[k] for k in ("LC_ALL", "LC_MEASUREMENT", "LANG") if env.get(k)), qt_locale_name)
    return IMPERIAL if _region(chosen) in IMPERIAL_REGIONS else METRIC


def system_qt_locale_name() -> str:
    from PySide6.QtCore import QLocale

    return QLocale.system().name()
