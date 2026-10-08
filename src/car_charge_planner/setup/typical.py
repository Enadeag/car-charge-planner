"""Typical values: what the app uses for a setting the owner could not give ("I don't know"). No Qt.

They are common values, not guesses about this owner: a 5 kWp roof facing south at 35 degrees, a charger on one
phase at 16 A. Settings and the plan page flag them as typical until the owner enters their own.
"""

from __future__ import annotations

from car_charge_planner.planner.model import TYPICAL_KEYS, Config, base_key, with_setting

TYPICAL_VALUES: dict[str, float | int] = {
    "panels.kwp": 5.0,
    "panels.tilt_deg": 35,
    "panels.azimuth_deg": 0,  # south
    "panels.system_losses": 0.14,
    "house.baseload_kw": 0.5,
    "car.usable_battery_kwh": 60,
    "car.consumption_kwh_per_100km": 18,
    "car.km_per_day": 40,
    "charger.phases": 1,
    "charger.max_amps": 16,
    "charger.min_amps": 6,
    "charger.volts": 230,
    "charger.efficiency": 0.90,
}
assert set(TYPICAL_VALUES) == set(TYPICAL_KEYS)  # a typical value for every setting that can be one


def with_values(config: Config, values: dict[str, float | int]) -> Config:
    """The config with these settings (by key, "panels.kwp", "panels.2.kwp") set."""
    for key, value in values.items():
        config = with_setting(config, key, value)
    return config


def with_typical(config: Config, *keys: str) -> Config:
    """The config with the typical value for each of these settings (the same for every panel array)."""
    return with_values(config, {key: TYPICAL_VALUES[base_key(key)] for key in keys})
