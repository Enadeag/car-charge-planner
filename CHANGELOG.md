# Changelog

All notable changes are listed here. The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and the project follows [Semantic Versioning](https://semver.org/).

## [Unreleased]

## [0.4.0] - 2026-10-20

The first public release: the repository is open, and every download comes with its checksums, an SBOM and a build attestation.

### Added
- `car-charge-planner test-notification` asks the running app to show a test notification in your language, with the app's name, icon and "Open dashboard" button; clicking it opens the dashboard. Nothing is planned or recorded for it. When the app does not run, it says so and ends with exit code 4; when it runs but cannot be reached, with exit code 3.
- A log file, `car-charge-planner.0.log` in the data folder, readable only by you: Settings, About shows where it is, and a bug report can quote it. `CAR_CHARGE_PLANNER_LOG_LEVEL=DEBUG` logs more.
- The log tells when a forecast provider stops or starts giving forecasts, when the settings file cannot be used or can be used again, where the API keys are kept, and each notification of a charge window. It never holds an API key, a request's address or your coordinates.
- Each release has an SBOM per download (`car-charge-planner-<version>-linux.cdx.json`, `-windows.cdx.json`) listing every library and the Java runtime in it, and GitHub attests where each file was built: `gh attestation verify <file> -R Enadeag/car-charge-planner` checks it. The release notes name the Java runtime the downloads bring.
- The downloads hold a `legal` folder with the app's licence (`LICENSE`, `NOTICE`, `ADDITIONAL-TERMS.md`) and a file per library it brings; the Windows installer shows the GPL before it installs.

### Changed
- JavaFX's native libraries are part of the app's runtime, so JavaFX no longer unpacks them into the `.openjfx` folder in your home folder at every start. The runtime no longer holds the `java` and `keytool` commands or the Flight Recorder.
- The API keys are read from the system keychain once and kept in memory until you save new ones, instead of at every plan, and only when a forecast provider is about to be asked. A keychain that cannot be used, or does not answer within 5 seconds, holds nothing up and is tried again after 5 minutes. On Linux the app keeps one connection to the Secret Service instead of opening one for every question, and first checks that there is one.
- Saving the API keys or the settings no longer waits for a forecast that is being fetched.
- Settings and set-up open at once and say "Reading your settings…" until the keychain and the files have answered, and Save and Finish say "Saving…" while they store; meanwhile the dashboard and the tray keep working. Closed, these windows are let go of instead of hidden, so they take no memory while the app runs in the tray.
- The texts say keychain, API key, solar power, charge current, charge window and away period throughout, and English writes no space before %, as in "92% solar". The window titles and the dashboard show the app's name the same way everywhere.
- Dutch: set-up is the Instelhulp ("Instelhulp opnieuw doorlopen", "Instelhulp…"), and the texts say sleutelbos, zonnestroom, laadstroom, laadvenster, afwezig and "uit het net"; where a detail stays in English, the text says "(in het Engels)".
- Settings and set-up say "Some values need attention: see the marked fields." when they refuse a value.
- Linux: the tray menu has a separator before Quit, as on Windows.

### Fixed
- Only one copy of the app runs, also when its socket cannot be made, such as with a folder path too long for a socket: the running copy holds a lock file (`instance.lock`), which the system lets go of when it ends or crashes. A second start that cannot reach the running copy says "Car charge planner is already running, but could not be reached." and ends with exit code 3; `quit` says it runs but cannot be reached, instead of "not running"; the running copy logs a warning.
- Linux: the tray icon comes back when the panel restarts, such as after Plasma's shell crashed: the app registers it again with the new panel.
- The dashboard's battery level entry no longer saves the level shown before when what you typed is not a whole number: it says why and stays open, and nothing is saved until you fix it or cancel.
- An away period entered on the dashboard that has already ended by the time you save it is refused, also when the plan shown was made a while ago; before, only the time of the plan counted.
- A screen reader reads several charge windows in a chart as a list ("Charging: 10:00 to 12:00, 14:00 to 15:00.") instead of as one "Charge window".
- Settings in Dutch no longer shows an English reason when the saved API keys cannot be read; the reason goes to the log.
- Settings and set-up check the settings of every chosen forecast provider before they save, not only those of the first one.
- When the API keys cannot be stored, Settings and set-up say so in your language and save nothing after them; the reason goes to the log.
- `quit` and `test-notification` say why they could not ask the running app when its folder cannot be checked, instead of saying it does not run; they then end with exit code 1. Commands no longer change that folder.
- API keys that were kept in `secrets.json` while the keychain could not be used move into the keychain as soon as it can be used again, also when you unlock it outside the app; a save right after unlocking keeps the keys that were only in the file.
- Linux: removing an API key while the keychain is locked no longer counts as done while the key stays: the save asks to unlock the keychain, as storing a key does.
- A `secrets.json` that cannot be read is kept when you save, instead of being replaced, so the keys in it can still be recovered.

### Security
- Linux without a usable `XDG_RUNTIME_DIR`: the lock and the socket go to `~/.local/share/car-charge-planner/run` (or under `XDG_DATA_HOME`) instead of `/tmp/car-charge-planner-<user>`, which another user could make first. When the folder is another user's, a link, or one others may write in, the app does not start and says why; your own folder that others may only read is made owner-only (0700), and the log says so, unless it holds a file of another user or a hard link, or lies in a folder others may write in.
- The Linux tar.gz holds its files as root's and not writable by group or others, so unpacked as root (such as into `/opt`) they belong to root instead of to the build machine's user id; the same source gives the same tar.gz.
- A locked keychain (common on Linux with automatic login) no longer sends the API keys to `secrets.json`. The keys count as not available yet: the dashboard and the tray say to unlock the keychain, and the next plan after unlocking uses them. Saving a key while it is locked has your desktop ask for the keychain's password; only when you dismiss that prompt do the keys go to the file, and when it cannot be unlocked that way nothing is saved and the app asks you to unlock it and save again. While it is locked the key fields show as locked, the status says when to look for the unlock prompt, and a save writes only the keys you entered or emptied, so no other stored key is lost.
- Windows: without a usable Credential Manager the API keys are kept in `%LOCALAPPDATA%\car-charge-planner\secrets.json`, which stays on this computer, instead of in the roaming folder beside `engine.json`; a file there is moved once.
- A forecast or place search answer is read up to a limit (2 MB for a forecast, 256 kB for a place search) and must come whole within the timeout, body included; a larger or slower answer counts as the provider not answering, instead of filling the memory or holding the app up. The connection to a provider gives up after 10 seconds.
- A server address for Open-Meteo must be https, or plain http only to this computer (localhost, 127.0.0.1 or [::1]), without a user name, `?` or `#`; Settings and set-up say so when you save another one.
- A Forecast.Solar API key may hold only letters and digits; Settings and set-up say so when you save another one.
- What a provider's server writes in an error is shown as "<provider> says: …", at most 200 characters, without control or direction characters, and with every stored API key replaced by `***`, also in the log.
- Linux: a click on a notification counts only when the desktop's notification server reports it, not when another program sends the same signal.

## [0.3.0] - 2026-10-16

The app is rewritten in Java with JavaFX, and the Python version is removed. What it plans is the same.

### Added
- A new dashboard, in the tray icon's popup or a window of its own: a tab per day with that day's charge window or grid advice, a chart of the expected sun against the house's own use, and the car battery, whose level you update there.
- The dashboard's Car away card: mark the car away on a day, all day or between two times, and remove a coming one. The weekly away times are in Settings.
- Dutch, beside English. A language is now one Java resource bundle (`messages_<code>.properties`); see CONTRIBUTING.md.
- A choice between light, dark and the system's appearance, in Settings, App.
- Set-up checks the forecast and shows a preview of the dashboard before Finish.
- `car-charge-planner start-at-login on` and `off`, the same as the switch in Settings.
- Linux: `car-charge-planner install-launcher` puts the app in the KDE and GNOME menus with its icon, without root; `uninstall-launcher` removes it again.
- Keyboard and screen reader use: Tab reaches every control in reading order with a visible focus ring, Enter and Escape save and cancel in every window and entry, and on Windows Enter on the tray icon (after Win+B) opens the dashboard. Narrator and NVDA read a name for every control, the solar chart hour by hour, and what a Save or a refused value says. Orca on Linux cannot read the app yet (JavaFX has no AT-SPI support). See Accessibility in the README.

### Changed
- Linux: a download with its own Java runtime, `car-charge-planner-<version>-linux.tar.gz`, replaces installing with pipx. Unpack it and start `bin/car-charge-planner`.
- Windows: a new installer, `car-charge-planner-<version>.exe`, with its own Java runtime. It installs in `%LOCALAPPDATA%\Programs\car-charge-planner` and needs no administrator rights. It is not code-signed yet.
- Settings is one window with a page per category, and set-up shows the same pages. Language and appearance change at once when you save.
- The settings are JSON files: `engine.json` (your system, planning, forecast providers and weekly away times) and `app.json` (the app's own settings), in the same folder as before. The battery level, away days, the windows you were told about and the forecast cache are in `~/.local/share/car-charge-planner/` (Windows: `%LOCALAPPDATA%\car-charge-planner\`). API keys stay in the system keychain; without one they are kept in `secrets.json`, readable only by you.

### Removed
- The Python version: installing with pipx, the PyInstaller and Inno Setup installer, the gettext translations, and the commands `test-notification` (it comes back in #133), `install-autostart` and `uninstall-autostart` (now `start-at-login on` and `off`) and the terminal planner.
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

[Unreleased]: https://github.com/Enadeag/car-charge-planner/compare/v0.4.0...HEAD
[0.4.0]: https://github.com/Enadeag/car-charge-planner/compare/v0.3.0...v0.4.0
[0.3.0]: https://github.com/Enadeag/car-charge-planner/compare/v0.2.0...v0.3.0
[0.2.0]: https://github.com/Enadeag/car-charge-planner/compare/v0.1.0...v0.2.0
[0.1.0]: https://github.com/Enadeag/car-charge-planner/releases/tag/v0.1.0
