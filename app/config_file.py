"""The user-facing config file: location, default contents, creation on demand."""

from __future__ import annotations

import os
from pathlib import Path

DEFAULT_CONFIG_TOML = """\
# Car charge planner configuration. Restart the app after editing.
# Values marked (assumed) are estimates: replace them with real numbers.

timezone = "Europe/Amsterdam"
latitude = 0.0
longitude = 0.0

[panels]
kwp = 4.8            # (assumed) 12 x 400 Wp
tilt_deg = 35        # (assumed)
azimuth_deg = -45    # 0 = south, -90 = east, 90 = west; south-east = -45
system_losses = 0.14

[house]
baseload_kw = 0.6    # from 430 kWh/month, aircon included, car excluded

[car]
usable_battery_kwh = 70          # (estimate) CarBrand publishes 73.1 gross only
consumption_kwh_per_100km = 18   # (estimate)
km_per_day = 85.714285714285714  # 600 km per week
minimum_pct = 30
target_pct = 80

[charger]
min_amps = 6
max_amps = 32
volts = 230
efficiency = 0.9     # (estimate) grid to battery

[planning]
margin = 0.85                 # plan on 85% of the forecast surplus
import_tolerance_kwh = 0.2
min_window_minutes = 45

[notify]
lead_minutes = 15

[away]
# weekday: 0 = Monday ... 6 = Sunday
recurring = [{weekday = 3, start = "06:00", end = "19:00"}]
"""


def config_path() -> Path:
    base = Path(os.environ.get("XDG_CONFIG_HOME", Path.home() / ".config"))
    return base / "car-charge-planner" / "config.toml"


def ensure_config(path: Path | None = None) -> Path:
    """Create the default config file if missing; never overwrite an existing one."""
    path = path or config_path()
    if not path.exists():
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(DEFAULT_CONFIG_TOML)
    return path
