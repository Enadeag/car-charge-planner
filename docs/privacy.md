# Data and privacy

The app runs on your computer and has no account, no telemetry and no update check. This is everything that leaves it, and everything it keeps.

## What is sent, and to whom

Only the **forecast provider** you chose (and the fallback, if you set one) is contacted, and only to ask for a forecast. Whatever you ask it, it also sees your internet address, as any website does. Set-up's first step says what the chosen provider is sent. What each one is sent, and the terms that go with using it (read on 2026-10-07; the providers can change them):

| Provider | What is sent | Free plan | Credit shown in the app | Terms |
| --- | --- | --- | --- | --- |
| Open-Meteo | Your location (latitude and longitude), your time zone and the tilt and direction of your panels. With an API key, the key too. | Non-commercial use only, under 10,000 requests a day. The app asks once for each panel array. | Weather data: Open-Meteo.com (CC BY 4.0) | [open-meteo.com/en/terms](https://open-meteo.com/en/terms) |
| Forecast.Solar | Your location (latitude and longitude), the tilt and direction of your panels and their size in kWp. With an API key, the key too. | The public plan needs no key: today and tomorrow, one panel array ("plane"), 12 requests an hour for everything behind the same internet address. The app asks once for each panel array. | Solar forecast: Forecast.Solar | [doc.forecast.solar/account_models](https://doc.forecast.solar/account_models) |

- **Commercial use.** The free plans are for personal use. If you use the app for a business, take the provider's own commercial plan or run your own server (Open-Meteo can be self-hosted or taken as a paid plan, with its own server address and key: both go in Settings, on the Forecast page). Forecast.Solar's pages do not say whether the public plan may be used commercially; ask them.
- **Credit.** The credit above is shown on the dashboard next to the age of the forecast, because both providers ask for it (Open-Meteo's data is CC BY 4.0). It is shown for whichever provider made the forecast on screen.
- **Place search.** The place you type in the search box of set-up's or Settings' Location page is sent to the place search of the forecast provider you have chosen (Open-Meteo by default), which answers with matching places and their coordinates. The page says so. A provider without a place search is never sent it: you type coordinates instead. The place names come from [GeoNames](https://www.geonames.org) (CC BY 4.0), via Open-Meteo.
- **Nothing else.** The battery level, your away periods and trips, your panels' size (except to Forecast.Solar, which needs it), the house and car settings and the plan itself never leave the computer.
- **Local only.** Notifications go through your desktop's own notification service. A second start finds the running app through a lock file and a socket in a folder only you can open. Nothing listens on the network.

## What is kept, and where

Everything the app keeps is on your computer. Delete any of it to start over.

| What | Linux | Windows |
| --- | --- | --- |
| Settings file: your system, planning preferences, forecast providers, weekly away periods and the calendar's settings | `~/.config/car-charge-planner/engine.json` | `%APPDATA%\car-charge-planner\engine.json` |
| The app's own settings (language, appearance, units, tray or window) | `~/.config/car-charge-planner/app.json` | `%APPDATA%\car-charge-planner\app.json` |
| The battery level you entered last | `~/.local/share/car-charge-planner/battery.json` | `%LOCALAPPDATA%\car-charge-planner\battery.json` |
| Away periods for a specific day | `~/.local/share/car-charge-planner/away.json` | `%LOCALAPPDATA%\car-charge-planner\away.json` |
| Your trips: when you leave and are back, the distance and whether you can charge there | `~/.local/share/car-charge-planner/trips.json` | `%LOCALAPPDATA%\car-charge-planner\trips.json` |
| The windows you were notified about | `~/.local/share/car-charge-planner/announced.json` | `%LOCALAPPDATA%\car-charge-planner\announced.json` |
| What you were notified about each trip, by its departure | `~/.local/share/car-charge-planner/trip-notices.json` | `%LOCALAPPDATA%\car-charge-planner\trip-notices.json` |
| Your consents for the calendar, each with its date and the version of its text (removed when you withdraw them) | `~/.local/share/car-charge-planner/calendar-consents.json` | `%LOCALAPPDATA%\car-charge-planner\calendar-consents.json` |
| Forecast cache: the last forecast of each provider, one file per provider | `~/.local/share/car-charge-planner/forecasts/` | `%LOCALAPPDATA%\car-charge-planner\forecasts\` |
| Log: what the app did, the newest in `.0.log` and two older ones of up to 1 MB | `~/.local/share/car-charge-planner/car-charge-planner.0.log` | `%LOCALAPPDATA%\car-charge-planner\car-charge-planner.0.log` |
| API keys, if you use a paid plan (never in the settings file) | The system keychain; `~/.config/car-charge-planner/secrets.json` only where there is none (see [API keys](#api-keys)) | Credential Manager; `%LOCALAPPDATA%\car-charge-planner\secrets.json` only where it cannot be used |
| Start at login | `~/.config/autostart/car-charge-planner.desktop` | The `Run` key of your user in the registry |
| `instance.lock`, which keeps a second copy from running, and `instance.sock`, through which a second start reaches the running app | `$XDG_RUNTIME_DIR/car-charge-planner/`; without a usable `XDG_RUNTIME_DIR`, `~/.local/share/car-charge-planner/run/` | `%LOCALAPPDATA%\car-charge-planner\` |

On Linux `XDG_CONFIG_HOME` and `XDG_DATA_HOME` move the config and data folders, as usual, and the app keeps both folders and every file it writes readable only by you (a folder made by an older version is made private when the app starts; a file named with `--config=` keeps its folder as it is). On Windows, `%APPDATA%` is usually `C:\Users\<you>\AppData\Roaming` and `%LOCALAPPDATA%` is `C:\Users\<you>\AppData\Local`. Settings, About lists the folders of your computer.

How the app reads and writes these files, and what it does with a damaged one, is in the [guide](guide.md#the-files-and-the-log).

### API keys

API keys are kept by the system, not in a file:

| System | Where the API keys are kept |
| --- | --- |
| Linux | The Secret Service: GNOME Keyring, KWallet or another that offers it, as items with the attribute `application` set to `car-charge-planner` |
| Windows | Credential Manager (Windows Credentials), as generic credentials whose names start with `car-charge-planner:` |

The entries are named `forecast/<provider>/<name>`, after the setting, and one more entry, `index`, lists the names (no values) because a keychain cannot be asked what it holds. Without a usable keychain (a Linux machine with no Secret Service, for one) the API keys are kept in `secrets.json`, readable only by you, and the app says so. On Windows that file is in `%LOCALAPPDATA%`, so a roaming profile does not copy it to other computers (a file an older version kept beside `engine.json` is moved there once). The app reads the API keys from the keychain once and keeps them in memory until you save new ones; a keychain that cannot be used, or does not answer within a few seconds, is tried again five minutes later.

A locked keychain is not a missing one. When your login did not unlock it (common with automatic login), the API keys are not available yet: the dashboard and the tray say to unlock your keychain so the app can read your API key, and the app plans with them as soon as you unlock it. Settings and set-up show the API key fields locked, as the API keys cannot be read; choose "Enter a new API key" to replace one. A save only writes the API keys you entered or emptied, so the others stay as they are. When you save an API key while the keychain is locked, your desktop asks for the keychain's password (GNOME's or KDE's own prompt), the status says so, and the app waits up to two minutes for it. Only when you dismiss that prompt do the API keys go to `secrets.json` instead, and the page says so; when nobody answers, or the keychain cannot be unlocked that way, nothing is saved and the app says "Not saved: your keychain is locked. Unlock your keychain and save again." When the API keys cannot be stored for another reason, nothing is saved either; the app says so and the log says why. Cancel stops the wait.

## Third-party software

The downloads bring these with them:

| Software | Used for | Licence |
| --- | --- | --- |
| The Java runtime ([OpenJDK](https://openjdk.org), cut down with jlink) | Running the app | GPL-2.0-only with the Classpath Exception |
| [JavaFX](https://openjfx.io) | The dashboard, set-up and Settings | GPL-2.0-only with the Classpath Exception |
| [AtlantaFX](https://github.com/mkpaz/atlantafx) | The look of the windows, light and dark | MIT |
| [dbus-java](https://github.com/hypfvieh/dbus-java) | The tray icon, notifications and the keychain on Linux | MIT |
| [Jackson](https://github.com/FasterXML/jackson) | Reading and writing the JSON files and the providers' answers | Apache-2.0 |
| [SLF4J](https://www.slf4j.org) | dbus-java's and iCal4j's log, passed on to the app's | MIT |
| [iCal4j](https://github.com/ical4j/ical4j) | Reading the calendar, its repeating events and its time zones | BSD-3-Clause |
| [Apache Commons Lang](https://commons.apache.org/proper/commons-lang/) and [Commons Codec](https://commons.apache.org/proper/commons-codec/) | Used by iCal4j | Apache-2.0 |
| [ThreeTen-Extra](https://www.threeten.org/threeten-extra/) | Used by iCal4j | BSD-3-Clause |

Their licence texts are in the `legal` folder of the Linux folder and of the Windows install folder, one file per library; the runtime's own notices, for OpenJDK and JavaFX, are in `lib/runtime/legal` of the Linux folder (`runtime\legal` in the Windows install folder). For development only: [JUnit](https://junit.org) (EPL-2.0), [AssertJ](https://assertj.github.io/doc/) and [ArchUnit](https://www.archunit.org) (both Apache-2.0).

The weather and solar data come from the providers above under their own terms. The recorded answers the tests use are credited in [`src/test/resources/.../forecast/providers/NOTICE.md`](../src/test/resources/nl/enadeag/carchargeplanner/engine/forecast/providers/NOTICE.md).
