# Car charge planner

A personal tray app for KDE Plasma that tells you when to charge the car so that as much of your own solar energy as possible is used and as little as possible is fed back to the grid.

It is advisory only: it recommends a **charge window** and a **charge power**, and you set the current in your charger's app yourself.

Built for one home (Hometown, 12 solar panels, single-phase 7.4 kW ChargerBrand charger, CarBrand CarModel, fixed energy contract). The numbers it assumes are in the config file.

## What it does

- Fetches a solar forecast from [Open-Meteo](https://open-meteo.com) (free, no account) and turns it into expected surplus: panel output minus the house's own use.
- Recommends charge windows within the hours the car is at home, with one constant power per window (in kW and amps), aiming for almost no grid draw.
- Tray icon: green while a window is active, blue when one is coming, grey when there is none, orange when a grid charge is needed, red when there is no forecast.
- Click the icon for a popup with two pages. **Plan** answers "when do I charge?": the next window, the battery level you expect after it, grid-charge advice when needed, a short table of the next days, and a battery and away status line. **Update** holds the daily inputs.
- Right-click the icon for **Settings...**, a window with all configuration, grouped over tabs.
- Sends a desktop notification 15 minutes before each window, once per window.
- **Grid-charge advice:** if the battery would end a day below the minimum (30%), it says how much to charge from the grid and picks the time that uses the most leftover sun.

### Inputs (the popup's Update page)

| Input | Notes |
| --- | --- |
| Battery level | You enter it by hand ("Set battery"). Flagged red when never entered or a day old. |
| Car away | Add a one-off day (all day or a time range) and remove it again. The weekly rules are listed there too, read-only; the **Edit weekly rules in Settings...** button opens Settings on the Away rules tab. |

Where things are edited: **settings** (panel size, charger, weekly away rules and so on) only in the Settings window; **daily inputs** (battery level, one-off away days) in the popup. Nothing is editable in both places. Everything you enter in the popup saves immediately and replans at once. Close the popup with the × in its corner, Escape, a click on the tray icon, or a click outside it.

## Current state

All six planner tickets and all seven Settings-window tickets are done and reviewed against their specs (`.scratch/car-charge-planner/review.md`, `.scratch/settings-window/review.md`). The test suite passes; run it as shown under Tests.

Not done or not verified:

- **Panel size and tilt are assumed** (4.8 kWp, 35 degrees, south-east) until you get access to the InverterBrand account (planner ticket 08).
- The CarModel's real consumption, charging losses and minimum charge current are estimates (planner ticket 09).
- Calendar sync for away periods is saved for later (planner ticket 01).
- No measured data yet: no P1 smart-meter reader, no InverterBrand production data. The plan is forecast-only.
- Popup placement on Wayland was checked offscreen only.

## Requirements

- Linux with KDE Plasma (tray icon via StatusNotifier). Developed on KDE Plasma under Wayland.
- Python 3.12 or newer (developed on 3.14).
- Internet access for the forecast. Without it the app uses its cached forecast and says so.

## Setup

```bash
cd ~/projects/car-charge-notification
python3 -m venv .venv
.venv/bin/pip install PySide6-Essentials jeepney pytest
```

## Run

Start the tray app:

```bash
.venv/bin/python -m app
```

It runs until you choose **Quit** in the tray icon's right-click menu. A second start is refused, so you cannot get two icons. The first start creates the config file.

Stop a running instance from a terminal:

```bash
pkill -f "python -m app$"
```

### Without the tray: print the plan in a terminal

```bash
.venv/bin/python -m planner.cli --live --battery 45
```

| Option | Meaning |
| --- | --- |
| `--live` | Real Open-Meteo forecast, cached for an hour. `--refresh` ignores the cache. |
| `--demo summer\|overcast\|mixed\|winter` | Invented forecast, no network. |
| `--forecast file.json` | Your own forecast: a list of `{"start", "end", "gti"}` (ISO times, W/m2). |
| `--battery N` | Current battery percentage (required). |
| `--now ISO` | Pretend it is another time. |
| `--config path` | Use another config file. |

## Start automatically at login

```bash
.venv/bin/python -m app install-autostart
```

This writes `~/.config/autostart/car-charge-planner.desktop`, which starts the app at your next login. It stores absolute paths to this venv and folder, so run it again if you move the project.

## Remove the autostart entry

```bash
.venv/bin/python -m app uninstall-autostart
```

This deletes only that `.desktop` file. A running instance keeps running until you quit it.

## Application menu entry and icon

```bash
.venv/bin/python -m app install-launcher
```

This adds the app to your application menu with its own icon (the bolt on a blue disc) and writes the launcher to `~/.local/share/applications/car-charge-planner.desktop` and the icon to `~/.local/share/icons/hicolor/scalable/apps/`. The desktop uses the same entry to tie the app's notifications and windows to it, so they show its name and icon. Like the autostart entry, it stores absolute paths, so run it again if you move the project.

To remove both files:

```bash
.venv/bin/python -m app uninstall-launcher
```

## Configuration

Open **Settings...** from the tray icon's right-click menu. Settings are grouped over five tabs:

| Tab | What it holds |
| --- | --- |
| Solar and location | Panel size, tilt, direction, system losses, latitude, longitude, time zone |
| House and car | House baseload, usable battery, consumption, driving per day, minimum and target charge level |
| Charger | Minimum and maximum current, voltage, charging efficiency |
| Planning and notifications | Planning margin, import tolerance, minimum window length, notification lead time |
| Away rules | Weekly times the car is not at home, one row per rule |

- **Save** checks all values together. If something is wrong it lists every problem and writes nothing. Otherwise it writes the file and applies the change straight away (no restart). Changing tilt, direction, location or time zone fetches a fresh forecast.
- The Save button is a split button. Its main part saves and **keeps the window open**. The arrow beside it opens a menu with **Save** and **Save and close**; picking one changes what the button does (the label follows) but does not save yet. The window opens in Save mode every time.
- The button next to Save reads **Close** while nothing is unsaved and **Cancel** while there are edits. Cancel, the window's close button and Escape ask "Discard your changes?" only if something changed since the last save.
- **Reset tab** puts the current tab back to the built-in defaults. Nothing is saved until you press Save.
- Pressing Enter in a field does what the Save button currently does.
- Values tagged **Assumed** or **Estimate** are guesses until you replace them with real numbers.

The settings live in `~/.config/car-charge-planner/config.toml`, a plain TOML file. The Settings window manages it: saving rewrites the whole file and does not keep comments. The app reads the file only at startup, so if you want to edit it by hand, do it while the app is closed. An invalid value makes the app show a dialog listing the problems and exit.

## Where things are stored

| What | Path |
| --- | --- |
| Config | `~/.config/car-charge-planner/config.toml` |
| Battery level, away periods, announced windows | `~/.local/state/car-charge-planner/state.json` |
| Forecast cache | `~/.cache/car-charge-planner/forecast.json` |
| Single-instance lock | `~/.cache/car-charge-planner.lock` |

Delete the state file to reset your entries. The other files are recreated as needed.

## Tests

```bash
.venv/bin/pytest -q
```

No network or display is needed: the forecast client runs against a recorded response, and the Qt tests run offscreen.

## Project layout

| Folder | Contents |
| --- | --- |
| `planner/` | The planning logic, with no network or UI: surplus, windows, projection, grid advice, config reading and writing (`config_io.py`), CLI. |
| `forecast/` | Open-Meteo client and cache. |
| `storage/` | Reading and writing `state.json`. |
| `app/` | Tray, the popup (`popup.py`, a window around the reusable `planview.py`), notifications over D-Bus (`notifier.py`), the launcher and autostart entries, theme-aware colours. |
| `app/settings/` | The Settings window: field definitions, tabs, window. |
| `tests/` | Test suite and the recorded forecast. |
| `CONTEXT.md` | Glossary of the domain terms used throughout (feed-in, surplus, charge window, ...). |
| `.scratch/car-charge-planner/` | The planner's spec, tickets and review. |
| `.scratch/settings-window/` | The Settings window's spec, tickets and review. |
| `.scratch/popup-redesign/` | The popup redesign: problem, design and review notes. |
| `docs/agents/` | Configuration for the engineering skills (issue tracker, labels). |

## How it plans, briefly

1. Surplus per hour = panel output (size x forecast irradiance x losses) minus the house baseload, scaled by a planning margin of 85% so it errs towards too little power rather than grid draw.
2. A step counts only if the surplus covers at least the charger's minimum (6 A, 1.4 kW).
3. Within the hours the car is home, it picks the window and power that bring in the most solar for the energy the car needs, up to the 80% target.
4. It projects the battery day by day, subtracting average driving (about 86 km a day). If a day would end more than one point under 30%, it adds the grid-charge advice.
5. A window you have been notified about is kept as announced while it runs, so the power on screen matches what you set.

The full design is in `.scratch/car-charge-planner/spec.md`.

## Attribution

Weather data by [Open-Meteo.com](https://open-meteo.com), licensed CC BY 4.0, used here for non-commercial personal use.
