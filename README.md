# Car charge planner

A tray app for Linux desktops that tells you when to charge your electric car so that as much of your own solar energy as possible is used and as little as possible is fed back to the grid.

It is advisory only: it recommends a **charge window** and a **charge power**, and you set the current in your charger's app yourself.

It plans from a description of your set-up: your solar panels (size, tilt, direction), your location, your home charger's current limits and your car's battery. You enter all of it in the Settings window. The values it starts with are **examples, not measurements**: replace them with yours, or the plan will not fit your home.

It suits a fixed-price energy contract, where the goal is simply to use your own solar power instead of feeding it back. It does not look at hourly energy prices.

## What it does

- Fetches a solar forecast from [Open-Meteo](https://open-meteo.com) (free, no account) and turns it into expected surplus: panel output minus the house's own use.
- Recommends charge windows within the hours the car is at home, with one constant power per window (in kW and amps), aiming for almost no grid draw.
- Tray icon: green while a window is active, blue when one is coming, grey when there is none, orange when a grid charge is needed, red when there is no forecast.
- Click the icon for a popup with two pages. **Plan** answers "when do I charge?": the next window, the battery level you expect after it, grid-charge advice when needed, a short table of the next days, and a battery and away status line. **Update** holds the daily inputs.
- Right-click the icon for **Settings...**, a window with all configuration, grouped over tabs.
- Sends a desktop notification a few minutes before each window (the lead time is a setting), once per window. Clicking the notification shows the plan. Notifications go straight to the desktop's notification service (D-Bus), so they work with or without a tray.
- **Grid-charge advice:** if the battery would end a day below your minimum charge level, it says how much to charge from the grid and picks the time that uses the most leftover sun.

### Inputs (the popup's Update page)

| Input | Notes |
| --- | --- |
| Battery level | You enter it by hand ("Set battery"). Flagged red when never entered or a day old. |
| Car away | Add a one-off day (all day or a time range) and remove it again. The weekly rules are listed there too, read-only; the **Edit weekly rules in Settings...** button opens Settings on the Away rules tab. |

Where things are edited: **settings** (panel size, charger, weekly away rules and so on) only in the Settings window; **daily inputs** (battery level, one-off away days) in the popup. Nothing is editable in both places. Everything you enter in the popup saves immediately and replans at once. Close the popup with the × in its corner, Escape, a click on the tray icon, or a click outside it.

## Current state

The planner, the tray app and the Settings window work. Design notes and reviews are under `.scratch/`.

Limits to know about:

- **The plan is forecast-only.** It uses a weather forecast and the values you enter, not measured data: no smart-meter, inverter or car readings. The more accurate your panel, battery and consumption values, the better the plan. Values tagged **Assumed** or **Estimate** in Settings are the ones most worth checking.
- **You enter the battery level by hand**, and away times by hand or as weekly rules. There is no calendar sync and no connection to the car.
- **You set the charge current yourself**, in your charger's own app. The app only recommends it.
- **Tested on KDE Plasma under Wayland.** GNOME support is built and tested automatically but not yet tried on a real GNOME desktop (see Running on GNOME).

## Requirements

- Linux. The tray icon needs a desktop with a system tray (KDE Plasma; GNOME only with an extension). Without one the app shows a window instead; see Running on GNOME. Developed on KDE Plasma under Wayland.
- Python 3.12 or newer (developed on 3.14).
- Internet access for the forecast. Without it the app uses its cached forecast and says so.

## Setup

```bash
# in the folder you cloned this repository into
python3 -m venv .venv
.venv/bin/pip install PySide6-Essentials jeepney pytest
```

## Run

Start the tray app:

```bash
.venv/bin/python -m app
```

It runs until you choose **Quit** in the tray icon's right-click menu. Starting it again while it runs does not start a second copy: it asks the running one to show its popup and exits. The first start creates the config file.

Stop a running instance from a terminal:

```bash
pkill -f "python -m app$"
```

### First run

The first start creates the config file and shows **Not set up yet**. The app knows nothing about you at that point: it has no location, it **fetches nothing** (no request leaves your machine) and there is no plan.

1. Open **Settings...** (the button on the plan page, or the menu) and, on the first tab, enter your **latitude and longitude**. Both at 0 means "not set". Saving without a location is refused.
2. Check the other values, especially your panels, car and charger. Whatever you leave alone starts as an **example value**, not a measurement.
3. Press Save. The app fetches its first forecast and shows the plan.

Your time zone is taken from the system, and there are no away rules to begin with, so the car is treated as home all week until you add some (Settings, Away rules). A guided set-up screen that walks you through all of this is planned.

### Check that clicking a notification works on your desktop

```bash
.venv/bin/python -m app test-notification
```

This sends a labelled test notification and waits up to a minute for you to click it. It says whether the click reached the app. If it does not, the notification itself still works; only the click-to-open step does not.

### Without the tray: print the plan in a terminal

```bash
.venv/bin/python -m planner.cli --live --battery 45
```

| Option | Meaning |
| --- | --- |
| `--live` | Real Open-Meteo forecast, cached for an hour. `--refresh` ignores the cache. Needs a location in the config (`--config`). |
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

## Running on GNOME

**Status:** the GNOME support is built and covered by automated tests, but it has not yet been tried on a real GNOME desktop. The check list for that is in `.scratch/gnome-support/issues/10-verify-on-gnome.md`.

**Target:** GNOME 48 to 51 (the current release and the three before it, as of October 2026), in a Wayland session. GNOME 50 and later have no X11 session at all.

GNOME has no system tray of its own, so the app shows a window there instead of a tray icon and popup. The window has the same two pages (Plan and Update), a menu button (Settings, Refresh forecast, Quit) and the same notifications.

| What | Needed? | Notes |
| --- | --- | --- |
| A Wayland session | Required | The Qt libraries in the PySide6 package include the Wayland plugin; no extra Qt package is needed. |
| Python 3.12+, `PySide6-Essentials`, `jeepney` | Required | As under Setup. |
| The notification service | Required | GNOME Shell provides it. |
| A launcher entry | Recommended | Run `python -m app install-launcher`. GNOME ties the app's windows and notifications to it, so they show its name and icon. |
| The **AppIndicator and KStatusNotifierItem Support** extension | **Optional** | Only needed if you want the tray icon and popup instead of a window. |

**The optional tray extension.** With it, the app shows its tray icon with a popup, as on KDE. Ubuntu ships it enabled. Elsewhere, install it and turn it on in the Extensions app, then log out and in:

| Distribution | Install |
| --- | --- |
| Fedora | `sudo dnf install gnome-shell-extension-appindicator` |
| Debian | `sudo apt install gnome-shell-extension-appindicator` |
| Arch | `sudo pacman -S gnome-shell-extension-appindicator` |
| Others | [extensions.gnome.org](https://extensions.gnome.org/extension/615/appindicator-support/) |

Its current version lists GNOME Shell 45 to 51. A new GNOME release can outrun an extension for a while; the window mode is the fallback for exactly that.

**How it behaves**

- **Start it** from the application menu (after `install-launcher`) or with `python -m app`. Starting it again shows the window that is already running instead of starting a second copy.
- **Closing the window hides it.** The app keeps running in the background so you still get notifications; the first time you close it, it tells you so. Quit from the window's menu or with Ctrl+Q.
- **At login:** `python -m app install-autostart` starts it hidden in the background. If you set up autostart before this version, run the command again to update it.
- **Choose how it shows itself** under Settings, on the Interface tab: Automatic (a tray icon if the desktop has one, otherwise a window), Tray icon, or Window. Automatic waits a few seconds for a tray at login, since the tray can appear a moment after the app starts. The change applies the next time the app starts.
- **Clicking a notification** shows the plan. Check that it works on your desktop with `python -m app test-notification`.
- **Look and feel:** it is a Qt application, so without an Adwaita Qt theme it can look less native on GNOME. Light and dark follow the system.

## Configuration

Open **Settings...** from the tray icon's right-click menu. Settings are grouped over six tabs:

| Tab | What it holds |
| --- | --- |
| Solar and location | Panel size, tilt, direction, system losses, latitude, longitude, time zone |
| House and car | House baseload, usable battery, consumption, driving per day, minimum and target charge level |
| Charger | Minimum and maximum current, voltage, charging efficiency |
| Planning and notifications | Planning margin, import tolerance, minimum window length, notification lead time |
| Interface | Whether the app shows itself as a tray icon with a popup, as a window, or automatically (a tray if the desktop has one, otherwise a window). Applies the next time the app starts. |
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
| Single-instance socket | `$XDG_RUNTIME_DIR/car-charge-planner.sock` (a private directory under the temp directory if there is no runtime directory) |

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
| `app/` | `controller.py` (what the app does: refresh, plan, announce, the owner's edits) and `shells/` (how it shows itself: the tray icon with its popup). The popup (`popup.py`) is a window around the reusable `planview.py`. Also notifications over D-Bus (`notifier.py`), the launcher and autostart entries, theme-aware colours. |
| `app/settings/` | The Settings window: field definitions, tabs, window. |
| `tests/` | Test suite and the recorded forecast. |
| `CONTEXT.md` | Glossary of the domain terms used throughout (feed-in, surplus, charge window, ...). |
| `.scratch/car-charge-planner/` | The planner's spec, tickets and review. |
| `.scratch/settings-window/` | The Settings window's spec, tickets and review. |
| `.scratch/popup-redesign/` | The popup redesign: problem, design and review notes. |
| `docs/agents/` | Configuration for the engineering skills (issue tracker, labels). |

## How it plans, briefly

1. Surplus per hour = panel output (size x forecast irradiance x losses) minus the house baseload, scaled by a planning margin (85% by default) so it errs towards too little power rather than grid draw.
2. A step counts only if the surplus covers at least your charger's minimum current (6 A by default, about 1.4 kW on a single-phase 230 V connection).
3. Within the hours the car is home, it picks the window and power that bring in the most solar for the energy the car needs, up to your target charge level (80% by default).
4. It projects the battery day by day, subtracting your average driving per day (a setting). If a day would end more than one point under your minimum charge level, it adds the grid-charge advice.
5. A window you have been notified about is kept as announced while it runs, so the power on screen matches what you set.

The full design is in `.scratch/car-charge-planner/spec.md`.

## Attribution

Weather data by [Open-Meteo.com](https://open-meteo.com), licensed CC BY 4.0, used here for non-commercial personal use.
