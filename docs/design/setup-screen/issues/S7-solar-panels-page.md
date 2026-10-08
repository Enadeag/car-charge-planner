# Solar panels page

Status: done (not committed yet)
Type: feature
Blocked by: S3, S5

## Acceptance

- Size: kWp directly, or panels x watts each with the result shown; limits as in Settings.
- Direction: eight compass buttons (N to NW) plus a degrees field, kept in step; tilt presets (flat about 10, typical pitched about 35, steep about 45) plus an exact angle.
- "I don't know": fills typical values (spec section 4) and records the affected keys as typical.
- Where-to-find lines for each value; the east-west note.
- Pure helpers for panels x watts, compass to degrees and back, and preset matching, unit-tested.

## Comments

- `app/setup/solar.py` (`SolarPage`, third in `PAGES`); pure helpers in `app/setup/panels.py` (`panels_to_kwp`, the eight compass directions with their azimuths and short and long names, `azimuth_for`, `compass_key_for` with north as 180 and -180 alike and `None` between two buttons, the three tilt presets and `tilt_preset_for`); the typical table in `app/setup/typical.py`.
- Size: total kWp, or number of panels x watts each, with the sum shown ("= 4.80 kWp") and used only when that way is chosen; limits as Settings (0.1-100 kWp). Direction: eight compass buttons around an empty middle plus a degrees box, kept in step in both directions (typing 20 presses none). Tilt: flat (10), pitched (35), steep (45) plus an exact angle, also in step. Each has its where-to-find line; the east-west note says: total size, facing south, a flat tilt, "only an approximation".
- **What counts as an answer**: changing a value, or clicking a button that is already down (confirming the typical value), removes that key from the typical list. The losses are never asked, so they stay typical. "I don't know these" sets the typical size, direction and tilt and remembers them as typical; a note lists only the values still typical ("Typical values in use: 5.0 kWp, tilt 35°").
- **Typical values table** (`TYPICAL_VALUES`, spec section 4, now settled): 5.0 kWp, tilt 35, south, losses 14%; house 0.5 kW; battery 60 kWh, consumption 18 kWh/100 km, 40 km a day; charger one phase, 16 A maximum, 6 A minimum, 230 V, efficiency 90%. A new install's draft now **starts with these values** (all marked typical), and a test checks that all together make a valid config. **A difference to know about, for the S11 review:** the built-in dataclass defaults (`Config()`, what Settings' Reset tab shows) are still the older ones (4.8 kWp facing south-east, 0.6 kW, 70 kWh, 32 A). Unifying them would change many test expectations; Settings' Reset gives defaults, the wizard gives typical values.
- Visual check in light: compass and tilt buttons, note text and the degrees box (limited in width); the compass buttons have a minimum, not a fixed, width so a longer abbreviation in another language widens them. Mutation-checked: the answer not leaving the typical list, "I don't know" not remembering, the kWp arithmetic.
