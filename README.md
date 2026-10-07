# Car charge planner

A tray app for Linux desktops that tells you when to charge your electric car so that as much of your own solar energy as possible is used and as little as possible is fed back to the grid.

It is advisory only: it recommends a **charge window** and a **charge power**, and you set the current in your charger's app yourself.

It plans from a description of your set-up: your solar panels (size, tilt, direction), your location, your home charger's current limits and your car's battery. You enter all of it in the Settings window. The values it starts with are **examples, not measurements**: replace them with yours, or the plan will not fit your home.

It suits a fixed-price energy contract, where the goal is simply to use your own solar power instead of feeding it back. It does not look at hourly energy prices.

## What it does

- Fetches a solar forecast from a forecast provider ([Open-Meteo](https://open-meteo.com) by default, or [Forecast.Solar](https://forecast.solar); both free, no account) and turns it into expected surplus: panel output minus the house's own use. A second provider can be set as a fallback.
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
| `--live` | The forecast from the providers in the config (Open-Meteo by default), cached. `--refresh` ignores the cache interval. Needs a location in the config (`--config`). |
| `--demo summer\|overcast\|mixed\|winter` | Invented forecast, no network. |
| `--forecast file.json` | Your own forecast: a list of `{"start", "end", "power_kw"}` (ISO times with a time zone, expected solar power in kW). Files in the earlier `gti` (W/m2) format are refused with a message saying so. |
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

Open **Settings...** from the tray icon's right-click menu. Settings are grouped over seven tabs:

| Tab | What it holds |
| --- | --- |
| Solar and location | Panel size, tilt, direction, system losses, latitude, longitude, time zone |
| House and car | House baseload, usable battery, consumption, driving per day, minimum and target charge level |
| Charger | One or three phases, minimum and maximum current (per phase), voltage, charging efficiency |
| Planning and notifications | Planning margin, import tolerance, minimum window length, notification lead time |
| Forecast | Which forecast provider to use, an optional fallback provider, and each one's settings (a server address or an API key, if it takes any). Each provider is described with what it sends, its limits and its terms, and a **Test** button asks it for a forecast right now without saving anything. API keys are kept apart from the config file, in `secrets.toml`. |
| Interface | Whether the app shows itself as a tray icon with a popup, as a window, or automatically (a tray if the desktop has one, otherwise a window); the language (once a translation is installed); and the **units**: metric (km, kWh/100 km) or imperial (miles, miles per kWh) for the distance and consumption fields, which change at once. Stored values stay metric. The mode and language apply the next time the app starts. |
| Away rules | Weekly times the car is not at home, one row per rule |

- **Save** checks all values together. If something is wrong it lists every problem and writes nothing. Otherwise it writes the file and applies the change straight away (no restart). Changing panel size, tilt, direction, location, time zone or the forecast providers fetches a fresh forecast.
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
| Forecast cache | `~/.cache/car-charge-planner/forecast-<provider>.json`, one per provider |
| API keys | `~/.config/car-charge-planner/secrets.toml` (private, only if you use a paid plan) |
| Single-instance socket | `$XDG_RUNTIME_DIR/car-charge-planner.sock` (a private directory under the temp directory if there is no runtime directory) |

Delete the state file to reset your entries. The other files are recreated as needed.

## Tests

```bash
.venv/bin/pytest -q
```

No network or display is needed: the forecast client runs against a recorded response, and the Qt tests run offscreen.

## Translations

All texts the app shows are translatable (gettext). English is the source language and the only one provided so far; a language is one `.po` file. For developers, with Babel installed (`pip install babel`):

```bash
.venv/bin/python -m app.i18n_tools extract     # collect the marked texts into app/locale/car-charge-planner.pot
.venv/bin/python -m app.i18n_tools init nl     # start a new language
.venv/bin/python -m app.i18n_tools update      # bring every language up to date with the template
.venv/bin/python -m app.i18n_tools compile     # .po -> .mo, which is what the app loads
```

The language follows your system (`LANGUAGE`, `LC_ALL`, `LC_MESSAGES`, `LANG`, then the desktop's setting); with no matching catalogue the app stays in English. Compiled catalogues (`.mo`) are not in the repository: run `compile` once before trying a language from a clone. Try one with `LANGUAGE=nl .venv/bin/python -m app`. Once a translation is installed, Settings > Interface offers a Language choice (applied at the next start). To add a language, see [CONTRIBUTING.md](CONTRIBUTING.md).

## Project layout

| Folder | Contents |
| --- | --- |
| `planner/` | The planning logic, with no network or UI: surplus, windows, projection, grid advice, config reading and writing (`config_io.py`), CLI. |
| `forecast/` | The forecast providers (`providers/`), the service that chooses between them and caches their answers (`service.py`), and the secrets file. See [docs/providers.md](docs/providers.md). |
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

1. Surplus per hour = the forecast provider's expected solar power (for Open-Meteo: panel size x forecast irradiance x losses) minus the house baseload, scaled by a planning margin (85% by default) so it errs towards too little power rather than grid draw.
2. A step counts only if the surplus covers at least your charger's minimum current (6 A by default: about 1.4 kW on a single-phase 230 V charger, about 4.1 kW on a three-phase one; set the phases on the Charger tab).
3. Within the hours the car is home, it picks the window and power that bring in the most solar for the energy the car needs, up to your target charge level (80% by default).
4. It projects the battery day by day, subtracting your average driving per day (a setting). If a day would end more than one point under your minimum charge level, it adds the grid-charge advice.
5. A window you have been notified about is kept as announced while it runs, so the power on screen matches what you set.

The full design is in `.scratch/car-charge-planner/spec.md`.

## Data and privacy

The app runs on your computer and has no account, no telemetry and no update check. This is everything that
leaves it, and everything it keeps.

### What is sent, and to whom

Only the **forecast provider** you chose (and the fallback, if you set one) is contacted, and only to ask for a
forecast. Whatever you ask it, it also sees your internet address, as any website does. What each one is sent,
in its own words (these texts come from the providers' descriptions in the app, and a test keeps this section in
step with them), and the terms that go with using it (read on 2026-10-07; the providers can change them):

| Provider | What is sent | Free plan | Credit shown in the app | Terms |
| --- | --- | --- | --- | --- |
| Open-Meteo | Your location (latitude and longitude) and the tilt and direction of your panels. With an API key, the key too. | Non-commercial use only, under 10,000 requests a day. | Weather data: Open-Meteo.com (CC BY 4.0) | [open-meteo.com/en/terms](https://open-meteo.com/en/terms) |
| Forecast.Solar | Your location (latitude and longitude), the tilt and direction of your panels and their size in kWp. With an API key, the key too. | The public plan needs no key: today and tomorrow, one plane, 12 requests an hour for everything behind the same internet address. | Solar forecast: Forecast.Solar | [doc.forecast.solar/account_models](https://doc.forecast.solar/account_models) |

- **Commercial use.** The free plans are for personal use. If you use the app for a business, take the provider's
  own commercial plan or run your own server (Open-Meteo can be self-hosted or taken as a paid plan, with its own
  server address and key: both go in Settings, on the Forecast tab). Forecast.Solar's pages do not say whether the
  public plan may be used commercially; ask them.
- **Credit.** The credit above is shown in the app next to the age of the forecast, because both providers ask for
  it (Open-Meteo's data is CC BY 4.0). It is shown for whichever provider made the forecast on screen.
- **Nothing else.** The battery level, your away times, your panels' size (except to Forecast.Solar, which needs
  it), the house and car settings and the plan itself never leave the computer. The place you type is only ever
  sent as coordinates.
- **Local only.** The notifications go over the desktop's own message bus on your computer. The second-start check
  (clicking the launcher again shows the running window) uses a local socket in a private folder. Nothing listens
  on the network.

### What is kept, and where

See [Where things are stored](#where-things-are-stored). In short: your settings and, if you use a paid plan, your
API keys (in `secrets.toml`, readable only by you, and never in the settings file); your battery level and away
times; a cache of the last forecast of each provider. Delete any of them to start over. Uninstalling the launcher
or autostart entry removes only those entries.

### Third-party software

| Package | Used for | Licence |
| --- | --- | --- |
| [PySide6](https://doc.qt.io/qtforpython-6/) (Qt for Python; also Shiboken) | The windows, the tray icon and the popup | LGPL-3.0-only, or GPL-2.0-only / GPL-3.0-only |
| [jeepney](https://gitlab.com/takluyver/jeepney) | Notifications over D-Bus | MIT |
| [Babel](https://babel.pocoo.org) (development only) | Collecting and compiling the translations | BSD-3-Clause |
| [pytest](https://pytest.org) (development only) | The tests | MIT |

The weather and solar data come from the providers above under their own terms. The recorded answers the tests
use are credited in `tests/fixtures/NOTICE.md`.

## Attribution

Weather data by [Open-Meteo.com](https://open-meteo.com), licensed CC BY 4.0, used here for non-commercial personal use. Solar forecasts by [Forecast.Solar](https://forecast.solar).
