# Car Charge Planner: Spec

Status: draft, agreed in grilling session 2026-10-06
Vocabulary: see `CONTEXT.md` (**Feed-in**, **Self-consumption**, **Surplus**, **Charge window**, **Charge power**, **Away period**, **Minimum charge level**).

## 1. Purpose

A tray application for Linux desktops (developed on KDE Plasma under Wayland). It tells the user when to charge their electric car, and at what **charge power**, so that as much solar energy as possible is **self-consumed** and as little as possible is **fed in**.

Single user, single home, single car. Not a product: no accounts, no multi-tenancy, no cloud.

## 2. Background and constraints

The first version was designed around one example set-up. Every figure here is an **example**, not a measurement or a recommendation; users enter their own in the Settings window.

| Item | Value |
| --- | --- |
| Location | A mid-latitude European city (time zone Europe/Amsterdam) |
| Solar | 12 panels of roughly 400 Wp, roughly south-east, tilt unknown. |
| Charger | Single-phase, 6-32 A (1.4-7.4 kW); the user can change the current in the charger's own app |
| Car | A recent electric car, charging only, no vehicle-to-home |
| Contract | Fixed price. Grid price is irrelevant to planning. |
| Net metering | The feed-in scheme is ending, so feed-in becomes worth far less than grid power costs |
| Usage | ~600 km/week, car at home most of the week |
| Desktop | KDE Plasma, Wayland, Python 3.14 available |

Consequences:

- At best the **surplus** is ~3-4 kW at midday, below the 7.4 kW charger maximum. Matching the surplus needs a reduced **charge power**.
- Winter surplus is a few kWh/day, far below the ~14 kWh/day the car uses. The tool will often recommend grid charging in winter. That is expected and must be presented honestly.

## 3. Scope

### In scope (v1)

1. Forecast-only planning (no measurements).
2. Tray icon with a popup showing today's **charge window(s)**, recommended **charge power**, expected solar share, and a 2-3 day outlook.
3. A notification 15 minutes before a recommended **charge window** starts.
4. Manual input in the popup: current battery %, and marking **away periods** for specific days or time ranges.
5. A fixed recurring **away period**: one weekday, 06:00-19:00 (an example rule) (editable in config).
6. Grid-charge advice when the car would otherwise fall below the **minimum charge level**.
7. Config file with all assumptions.
8. Autostart at login.

### Out of scope (v1)

- Controlling the charger or the car (advisory only). Possible later through a charger maker's cloud API (account login, set the dynamic current).
- Reading the car's battery level from the carmaker's connected services (unofficial APIs).
- Real production or consumption data: an inverter maker's monitoring API (needs an account), P1 smart-meter reader.
- Calendar sync for **away periods** (ticket 01).
- Dynamic tariffs, vehicle-to-home, multiple cars or chargers.

## 4. Behaviour

### 4.1 Inputs

| Input | Source |
| --- | --- |
| Hourly (or 15-minute) solar irradiance on the panel plane, for up to 16 days | Open-Meteo `GET https://api.open-meteo.com/v1/forecast` with `latitude`, `longitude`, `timezone`, `tilt`, `azimuth`, hourly `global_tilted_irradiance` (and `cloud_cover` for display). Azimuth: 0 = south, -90 = east, so south-east is -45. |
| Panel size, tilt, azimuth, system losses | Config |
| House baseload | Config (constant) |
| Current battery % | Manual, popup |
| **Away periods** | Config (recurring) + manual (popup) |
| Car and charger parameters | Config |

Open-Meteo is free, keyless and non-commercial; attribution (CC BY 4.0) goes in the popup's About text. Fetch at most every 60 min (well inside the 5,000/hour limit), cache the last successful response on disk, and keep planning from cache when offline, showing the cache's age.

### 4.2 Expected surplus

For each time step `t`:

```
pv_kw(t)      = kwp * (gti(t) / 1000) * (1 - system_losses)
surplus_kw(t) = pv_kw(t) - baseload_kw
usable_kw(t)  = max(0, surplus_kw(t)) * planning_margin
```

`planning_margin` (default 0.85) deliberately under-predicts so the recommended **charge power** leans away from importing from the grid (decided: importing at the full contract rate is worse than a little feed-in).

### 4.3 Charge power

The user sets the power by hand in your charger's app, so it must be stable and simple:

- Convert to amps at the configured voltage (230 V, single-phase): `amps = floor(usable_kw * 1000 / 230)`.
- Valid range 6-32 A. A step with `usable_kw` below the 6 A power (1.4 kW) is **not chargeable from solar**.
- A **charge window** has **one** recommended power for its whole length (no minute-by-minute changes; the user changes it once).
- Recommended power is the highest value for which the expected grid import over the window stays at or below a small tolerance (config, default 0.2 kWh), with the **planning margin** already applied.

### 4.4 Charge windows

Per day, within the car's home time (outside **away periods**):

1. Find contiguous chargeable steps.
2. For each candidate window and power, compute `solar_kwh_delivered` (energy that fits under the usable curve).
3. Choose the window(s) that deliver the energy the car needs (see 4.5), preferring fewer, longer windows and higher solar share. Break ties toward earlier windows to leave room for forecast error.
4. Windows shorter than a configurable minimum (default 45 min) are dropped.

### 4.5 Energy need and projection

```
energy_needed_kwh = (target_pct - current_pct)/100 * usable_battery_kwh / charging_efficiency
```

- `target_pct` = 80 (everyday charging maximum, config).
- Daily driving energy = `km_per_day * consumption_kwh_per_100km / 100`, with `km_per_day` defaulting to 600/7 and **away periods** able to carry an extra km value later.
- The planner projects the battery % forward across the forecast days, assuming the user follows the plan.

### 4.6 Grid-charge advice

If the projection says the battery would drop below the **minimum charge level** (30%) before the next day that offers a usable solar window, the popup states plainly that a grid charge is needed. It names the least-bad window (smallest expected grid import), at the lowest power that gets the car to safety in time. Otherwise, on days with no usable window, it says "no good window today" and nothing else.

### 4.7 When data is stale or missing

- No current battery %: show the plan for the last known value, flagged "battery level is N days old", and prompt for an update.
- Forecast older than 6 h with no network: show the plan, flagged stale; suppress window-start notifications.
- No forecast ever fetched: show "no forecast" and nothing else.

## 5. User interface

### Tray icon

- Neutral when no window is active or imminent.
- Highlighted when a **charge window** is active now.
- Tooltip: next window and power.

### Popup (click)

1. **Now / next**: next window (start-end), recommended power (kW and amps), expected solar share (%), expected energy.
2. **Battery**: field to enter the current %, with the projected % at the end of the window.
3. **Outlook**: today plus the next 2-3 days, one line each ("Sat: best 11:30-15:30, 3.1 kW", "Sun: no good window").
4. **Away periods**: day picker (all day or time range) to add or remove manual entries.
5. Grid-charge advice when it applies (4.6), visually distinct from solar windows.
6. Forecast age and Open-Meteo attribution.

### Notifications

One desktop notification 15 minutes before each recommended **charge window**: "Charge window 11:30-15:30 at 3.1 kW (about 16 A). Set the current in your charger's app." Not sent for stale-forecast cases or for windows already started.

## 6. Architecture

Keep the planner a plain, UI-free module so it can be tested without Qt or the network.

```
planner/        pure Python, no I/O
  model.py      dataclasses: Config, ForecastStep, AwayPeriod, Window, Plan
  surplus.py    4.2
  windows.py    4.3-4.4
  projection.py 4.5-4.6
forecast/       Open-Meteo client + on-disk cache
storage/        manual state (battery %, away periods) as JSON under XDG state dir
app/            PySide6: tray, popup, notification scheduler, refresh timer
```

- Single process, Python + PySide6 (Qt's `QSystemTrayIcon` maps to KDE's StatusNotifierItem on Wayland).
- Config: TOML at `~/.config/car-charge-planner/config.toml`; state: JSON at `~/.local/state/car-charge-planner/`; forecast cache in `~/.cache/car-charge-planner/`.
- Autostart: a systemd user service (or KDE autostart `.desktop` file) so the app launches at login.
- Refresh: re-fetch the forecast hourly and recompute the plan on every refresh and every manual edit.
- Time handling: all internal times timezone-aware (`Europe/Amsterdam`); beware DST days (23 and 25 hour days).

> Update: notifications are no longer sent through the tray icon. They go straight to the freedesktop notification service over D-Bus, so they work on desktops without a tray (see `docs/design/gnome-support/spec.md`).

## 7. Config defaults

These values are edited in the Settings window (see `docs/design/settings-window/spec.md`); they are stored in `config.toml`.

| Key | Default | Note |
| --- | --- | --- |
| `panels.kwp` | 4.8 | 12 x 400 Wp, **assumed** |
| `panels.tilt_deg` | 35 | **assumed** |
| `panels.azimuth_deg` | -45 | south-east |
| `panels.system_losses` | 0.14 | |
| `house.baseload_kw` | 0.6 | example household, car excluded |
| `car.usable_battery_kwh` | 70 | estimate; carmakers often publish the gross capacity only |
| `car.consumption_kwh_per_100km` | 18 | conservative estimate |
| `car.km_per_day` | 85.7 | 600 km/week |
| `car.minimum_pct` | 30 | **Minimum charge level** |
| `car.target_pct` | 80 | everyday maximum |
| `charger.min_amps` / `max_amps` | 6 / 32 | single-phase |
| `charger.volts` | 230 | |
| `charger.efficiency` | 0.90 | grid-to-battery, estimate |
| `planning.margin` | 0.85 | |
| `planning.import_tolerance_kwh` | 0.2 | |
| `planning.min_window_minutes` | 45 | |
| `notify.lead_minutes` | 15 | |
| `away.recurring` | an example weekday 06:00-19:00 | |

Every "assumed" or "estimate" value is expected to be corrected; the app must make the config easy to find (menu entry that opens the file).

## 8. Testing

Test the pure planner with table-driven cases (no network, no Qt):

- Clear summer day: one long window, power near the surplus peak.
- Overcast day: no window; no nag when battery is comfortably above 30%.
- Dip below **minimum charge level** before the next sunny day: grid-charge advice.
- **Away period** (an example weekday 06:00-19:00) removes the day's window; evening-only home hours.
- Surplus below 1.4 kW: step not chargeable.
- Planning margin: expected import never exceeds tolerance on the fixture.
- DST transition days; midnight-spanning windows.
- Stale and missing data states (4.7).

Forecast client: recorded Open-Meteo JSON fixture; test parsing and the cache fallback. UI: manual smoke test on the real desktop (tray visible, popup, notification).

## 9. Delivery order (vertical slices)

1. Planner core with a fixture forecast, driven by a CLI that prints today's plan. (No UI.)
2. Open-Meteo client + cache feeding the CLI.
3. Tray icon + popup (read-only plan and outlook).
4. Manual battery % and **away period** entry, persistent.
5. Window-start notification + autostart.
6. Grid-charge advice.

## 10. Open items

- [ ] Get the real panel values (kWp, tilt, production history) from the installer or inverter account. Replace the assumed values; calibrate the forecast against real production.
- [ ] Check in your charger's app that current can be set from the user's login (said yes; confirm 6 A minimum and whether it resets on re-plug).
- [ ] Verify the car's real minimum charge current and single-phase behaviour (some cars need 7 A).
- [ ] Verify real-world consumption and AC charging losses after some weeks of use; update config.
- [ ] Later add-ons: P1 reader (measure net import/export, evaluate plan quality), calendar sync (ticket 01), charger API control.
