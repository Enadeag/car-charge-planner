# Changelog

All notable changes are listed here. The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and the project follows [Semantic Versioning](https://semver.org/).

## [Unreleased]

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

[Unreleased]: https://github.com/Enadeag/car-charge-planner/compare/v0.1.0...HEAD
[0.1.0]: https://github.com/Enadeag/car-charge-planner/releases/tag/v0.1.0
