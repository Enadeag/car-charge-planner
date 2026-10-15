# Changelog

All notable changes are listed here. The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and the project follows [Semantic Versioning](https://semver.org/).

## [Unreleased]

The app is rewritten in Java with JavaFX, and the Python version is removed. What it plans is the same.

### Added
- A new dashboard, in the tray icon's popup or a window of its own: a tab per day with that day's charge window or grid advice, a chart of the expected sun against the house's own use, and the car battery, whose level you update there.
- The dashboard's Car away card: mark the car away on a day, all day or between two times, and remove a coming one. The weekly away times are in Settings.
- Dutch, beside English. A language is now one Java resource bundle (`messages_<code>.properties`); see CONTRIBUTING.md.
- A choice between light, dark and the system's appearance, in Settings, App.
- Set-up checks the forecast and shows a preview of the dashboard before Finish.
- `car-charge-planner start-at-login on` and `off`, the same as the switch in Settings.

### Changed
- Linux: a download with its own Java runtime, `car-charge-planner-<version>-linux.tar.gz`, replaces installing with pipx. Unpack it and start `bin/car-charge-planner`.
- Windows: a new installer, `car-charge-planner-<version>.exe`, with its own Java runtime. It installs in `%LOCALAPPDATA%\Programs\car-charge-planner` and needs no administrator rights. It is not code-signed yet.
- Settings is one window with a page per category, and set-up shows the same pages. Language and appearance change at once when you save.
- The settings are JSON files: `engine.json` (your system, planning, forecast providers and weekly away times) and `app.json` (the app's own settings), in the same folder as before. The battery level, away days, the windows you were told about and the forecast cache are in `~/.local/share/car-charge-planner/` (Windows: `%LOCALAPPDATA%\car-charge-planner\`). API keys stay in the system keychain; without one they are kept in `secrets.json`, readable only by you.

### Removed
- The Python version: installing with pipx, the PyInstaller and Inno Setup installer, the gettext translations, and the commands `test-notification`, `install-autostart`, `uninstall-autostart`, `install-launcher`, `uninstall-launcher` and the terminal planner.
- Reading the settings of 0.2.0 and earlier (`config.toml`, `app.toml`, `state.json`): run set-up once; the old files are left alone. On Windows the new installer does not remove 0.2.0: uninstall that one in Settings, Apps.

## [0.2.0] - 2026-10-14

Windows support, with an installer. Released without announcement, like 0.1.0.

### Added
- Windows 10 and later: an installer (`car-charge-planner-<version>-setup.exe`) that needs no administrator rights, adds a Start menu entry and can start the app at login. It is not code-signed yet; check the download against `SHA256SUMS`. Uninstalling keeps your settings and keys.
- Notifications through the tray icon on Windows, with the app's name and icon.
- "Start at login" in Settings (Interface tab), on every system.
- API keys are kept in the system's keychain: Secret Service on Linux, Credential Manager on Windows. Without a keychain they stay in a file only you can read, and Settings says so. An existing key file is moved once.
- A `car-charge-planner quit` command that asks the running app to quit. The installer and uninstaller use it, so a running app never blocks an update or stays running after an uninstall.
- A second start that cannot reach the running app now says so, instead of exiting silently.
- An About screen, in the tray menu and the window menu, with the version, the licence and where the source is.
- Documentation for Windows and Linux: installing, the supported systems, where the files live, starting at login.

### Changed
- The licence is now the GNU General Public License, version 3 or later, with an additional term that asks modified versions to keep the author attribution. Version 0.1.0 stays under the MIT licence.
- Settings, state and cache follow each system's usual folders. On Linux nothing moves.
- The system time zone is read with tzlocal.

### Fixed
- On the day the clocks go back, the repeated hour is no longer lost from charge windows and grid advice, and a forecast in local time is no longer rejected for that day. On the 23-hour day a day's driving counts as a whole day.
- Quitting could crash now and then while Python shut down.
- Files are read and written as UTF-8 on every system.

## [0.1.0] - 2026-10-08

First version, for Linux.

### Added
- A plan of when, and at what charge current, to charge an electric car so that as much as possible comes from your own solar panels, from a weather-based forecast.
- A tray icon with a popup, or a window on desktops without a tray; desktop notifications before each charge window.
- A set-up wizard for the first run, and a Settings window for everything the wizard asks.
- More than one panel array: panels on different roof faces (east and west, a garage) each with their own size, tilt and direction, and an optional name. The forecast is the sum of the arrays.
- Forecast providers: Open-Meteo and Forecast.Solar built in, with a fallback provider and place search.
- Metric and imperial units, one-phase and three-phase chargers, weekly away rules.
- Translatable texts (gettext); a language is one `.po` file.
- Installation with `pipx` as the command `car-charge-planner`.
- `config.toml` and `state.json` say which file format they are in, so later versions can upgrade them. A config file from a newer version of the app is refused with a clear message, and a newer state file is left alone. A config file written before this release, with a single `[panels]` table, is read as one panel array.

[Unreleased]: https://github.com/Enadeag/car-charge-planner/compare/v0.2.0...HEAD
[0.2.0]: https://github.com/Enadeag/car-charge-planner/compare/v0.1.0...v0.2.0
[0.1.0]: https://github.com/Enadeag/car-charge-planner/releases/tag/v0.1.0
