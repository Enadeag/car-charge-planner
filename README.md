# Car charge planner

[![CI](https://github.com/Enadeag/car-charge-planner/actions/workflows/ci.yml/badge.svg)](https://github.com/Enadeag/car-charge-planner/actions/workflows/ci.yml)
[![Licence: GPL-3.0-or-later](https://img.shields.io/badge/licence-GPL--3.0--or--later-blue.svg)](LICENSE)
![Java 25](https://img.shields.io/badge/java-25-blue.svg)

A desktop app for Linux and Windows that tells you when to charge your electric car so that as much of your own solar energy as possible is used and as little as possible is fed back to the grid. It suits a fixed-price energy contract: it does not look at hourly energy prices.

It is advisory only: it recommends a **charge window** and a **charge power**, and you set the charge current in your charger's app yourself.

It plans from a description of your set-up: your solar panels (size, tilt, direction), your location, your home charger's charge current limits and your car's battery, entered in set-up and changed in Settings later. Where you do not know a value, the app uses a **typical value** and says so: replace it with yours, or the plan will not fit your home.

<picture><source media="(prefers-color-scheme: dark)" srcset="docs/screenshots/java/charts-dark.png"><img alt="The dashboard three times: today with grid advice, a day with a charge window and the expected solar power, and the battery level being entered." src="docs/screenshots/java/charts-light.png"></picture>

The pictures are of an example home; a real desktop theme looks a little different.

## What it does

- Turns a solar forecast ([Open-Meteo](https://open-meteo.com) by default, or [Forecast.Solar](https://forecast.solar); both free, no account; a second can be a fallback) into expected surplus: panel output minus the house's own use.
- Recommends charge windows within the hours the car is at home, with one constant power per window (in amps and kW), aiming for almost no grid draw.
- **Grid advice:** if the battery would end a day below your minimum charge level, it says how much to charge from the grid and when.
- Shows the plan in a tray icon and on the **dashboard**, and notifies you a few minutes before each window.
- Reads nothing from the car: you enter the battery level by hand. Reading it from the car is not planned for now; [ADR 0006](docs/adr/0006-integrations-built-in.md) records which car makers offer their owners an official route.

**Current state:** all of this works, rewritten from Python and Qt in Java with JavaFX ([ADR 0005](docs/adr/0005-java-with-javafx.md); original design in [docs/design](docs/design/README.md)). Mind the [limits](docs/guide.md#limits-to-know-about).

## Supported systems

| System | Status |
| --- | --- |
| **Linux, KDE Plasma** (x64) | Supported. Developed and used under Wayland. |
| Linux, GNOME | Untested. No tray of its own: the app shows a window, or a tray icon with the AppIndicator extension. |
| Other Linux desktops (XFCE, Cinnamon, Sway, X11 sessions and so on) | Untested. Without a system tray (StatusNotifierItem) the app shows a window. |
| **Windows 11** (x64) | Supported, with an installer. |
| Windows 10 (x64) | Untested. The same installer, but nobody has confirmed it on Windows 10 yet. |
| macOS | Not built. |

"Supported" means a person has checked every release on that system. Tried it on your desktop? Please tell us, whether it works or not, with the **Does it work on my desktop?** issue form.

## Install

Download from the [release page](https://github.com/Enadeag/car-charge-planner/releases); each download brings its own Java runtime.

- **Windows:** run `car-charge-planner-<version>.exe`, which needs no administrator rights. It is not code-signed yet: [check the download](docs/install.md#check-a-download) first.
- **Debian, Ubuntu:** `sudo apt install ./car-charge-planner_<version>_amd64.deb`.
- **Fedora, openSUSE:** `sudo dnf install ./car-charge-planner-<version>-1.x86_64.rpm` (or `zypper install`).
- **Other Linux:** unpack `car-charge-planner-<version>-linux.tar.gz` and start `bin/car-charge-planner`; `install-launcher` adds it to the menu.

The `.deb` and `.rpm` put the app in `/opt/car-charge-planner` and in the application menu. No download updates itself yet: install a new release over the old one.

[Installing](docs/install.md) has the details, updates and uninstalling.

## More

- [Guide](docs/guide.md): set-up, the dashboard, Settings, how it plans, the command line.
- [Accessibility](docs/accessibility.md): keyboard, screen readers (not Orca on Linux, yet), contrast and scaling.

## Data and privacy

The app runs on your computer: no account, no telemetry, no update check. It contacts only the forecast provider you chose, for a forecast for your location and panels; your battery level, away periods and plan never leave the computer. [Data and privacy](docs/privacy.md) has the details, and the third-party software the app brings.

## Contributing

Bug reports, desktop reports, translations and pull requests are welcome: start with [CONTRIBUTING.md](CONTRIBUTING.md), which also says how to build and run the app from a clone. Please follow the [Code of Conduct](CODE_OF_CONDUCT.md); report security problems as described in [SECURITY.md](SECURITY.md). What changed in each release is in the [changelog](CHANGELOG.md).

## Licence

Copyright (C) 2026 Kelvin Vroegop. Car charge planner is free software under the [GNU General Public License](LICENSE), version 3 or (at your option) any later version, with additional terms under sections 7(b) and 7(c) that ask modified versions to keep the author attribution in their About screen: see [ADDITIONAL-TERMS.md](ADDITIONAL-TERMS.md). The short notice is in [NOTICE](NOTICE). Version 0.1.0 was released under the MIT licence; later versions are under the GPL.

## Attribution

Weather data by [Open-Meteo.com](https://open-meteo.com), licensed CC BY 4.0, used here for non-commercial personal use. Place data: [GeoNames](https://www.geonames.org), via the Open-Meteo geocoding service. Solar forecasts by [Forecast.Solar](https://forecast.solar).
