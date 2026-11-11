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
- **Nothing else.** The battery level, your away periods and trips, your panels' number, power per panel and year placed, their size (except to Forecast.Solar, which gets it in kWp), the house and car settings and the plan itself never leave the computer.
- **Local only.** Notifications go through your desktop's own notification service. A second start finds the running app through a lock file and a socket in a folder only you can open. Nothing listens on the network.

## What is kept, and where

Everything the app keeps is on your computer. Delete any of it to start over.

| What | Linux | Windows |
| --- | --- | --- |
| Settings file: your system, planning preferences, forecast providers and weekly away periods | `~/.config/car-charge-planner/engine.json` | `%APPDATA%\car-charge-planner\engine.json` |
| The app's own settings (language, appearance, units, tray or window) | `~/.config/car-charge-planner/app.json` | `%APPDATA%\car-charge-planner\app.json` |
| The battery level you entered last | `~/.local/share/car-charge-planner/battery.json` | `%LOCALAPPDATA%\car-charge-planner\battery.json` |
| Away periods for a specific day | `~/.local/share/car-charge-planner/away.json` | `%LOCALAPPDATA%\car-charge-planner\away.json` |
| Your trips: when you leave and are back, the distance and whether you can charge there | `~/.local/share/car-charge-planner/trips.json` | `%LOCALAPPDATA%\car-charge-planner\trips.json` |
| The windows you were notified about | `~/.local/share/car-charge-planner/announced.json` | `%LOCALAPPDATA%\car-charge-planner\announced.json` |
| What you were notified about each trip, by its departure | `~/.local/share/car-charge-planner/trip-notices.json` | `%LOCALAPPDATA%\car-charge-planner\trip-notices.json` |
| Forecast cache: the last forecast of each provider, with when the app may ask again and whether the last request failed, one file per provider | `~/.local/share/car-charge-planner/forecasts/` | `%LOCALAPPDATA%\car-charge-planner\forecasts\` |
| Log: what the app did, the newest in `.0.log` and two older ones of up to 1 MB | `~/.local/share/car-charge-planner/car-charge-planner.0.log` | `%LOCALAPPDATA%\car-charge-planner\car-charge-planner.0.log` |
| API keys, if you use a paid plan (never in the settings file) | The system keychain; `~/.config/car-charge-planner/secrets.json` only where there is none (see [API keys](#api-keys)) | Credential Manager; `%LOCALAPPDATA%\car-charge-planner\secrets.json` only where it cannot be used |
| Start at login | `~/.config/autostart/car-charge-planner.desktop` | The `Run` key of your user in the registry |
| `instance.lock`, which keeps a second copy from running, and `instance.sock`, through which a second start reaches the running app | `$XDG_RUNTIME_DIR/car-charge-planner/`; without a usable `XDG_RUNTIME_DIR`, `~/.local/share/car-charge-planner/run/` | `%LOCALAPPDATA%\car-charge-planner\` |

On Linux `XDG_CONFIG_HOME` and `XDG_DATA_HOME` move the config and data folders, as usual, and the app keeps both folders and every file it writes readable only by you (a folder made by an older version is made private when the app starts; a file named with `--config=` keeps its folder as it is). On Windows, `%APPDATA%` is usually `C:\Users\<you>\AppData\Roaming` and `%LOCALAPPDATA%` is `C:\Users\<you>\AppData\Local`. Settings, About lists the folders of your computer.

How the app reads and writes these files, and what it does with a damaged one, is in the [guide](guide.md#the-files-and-the-log).

In the Android app, which is not released yet, the engine's files (the settings file, the battery level you entered last, `battery.json`, the away periods you entered for a day, `away.json`, your trips, `trips.json`, and what you were notified about each trip, `trip-notices.json`, among them) are kept in the app's own files folder on the phone, which no other app can read, in the same format as the desktop's. They never leave the phone except in Android's transfer to your next phone, which takes them all along; the API keys stay out of that. Beside them the app keeps a few files of its own: two small preferences files (Android's DataStore) in `files/datastore/`, which the engine does not use, and in the no-backup folder the schedule of the work in the background and the encrypted API keys. Its log goes to Logcat:

| What | File | In the transfer to your next phone |
| --- | --- | --- |
| The phone's own settings: **Wi-Fi only** (Settings, Forecast), whether the hourly update while the app is closed waits for Wi-Fi | `files/datastore/settings.preferences_pb` | Yes |
| The notifications' state on this phone: whether you closed the dashboard's card about notifications, whether you refused Android's question for them, whether a "Forecast not updated" notification was posted, and how many rounds in the background in a row could not plan | `files/datastore/state.preferences_pb` | No: it belongs to this phone, where notifications are allowed or not |
| The schedule of the work in the background (Android's WorkManager): which work is due and when, nothing of your settings | `no_backup/androidx.work.workdb`, with its `-wal` and `-shm` files | No: Android never takes the no-backup folder along, in a backup or a transfer; the app sets the schedule up again when you first open it on the next phone |
| The API keys, each encrypted (see [API keys](#api-keys)) | `no_backup/keychain.json`; a copy that could not be read is kept beside it as `keychain.json.damaged` | No: you type them again on the next phone |
| Log: what the app did, from INFO in the release build: the kind of each notification, file paths, and a failure with its whole stack trace, never an API key. The times of your trips and charge windows are logged only at DEBUG, which only the debug build writes | Android's Logcat, no file. No other app can read it, but anyone with the phone can over USB debugging (`adb logcat`), and it goes into a bug report you take on the phone and may attach | No |

If one of the two preferences files is damaged, the app starts it again empty: **Wi-Fi only** is then off until you switch it on again.

The Android app's notifications go through Android's own notification service on the phone, like any app's, and their words are made on the phone; the app sends them nowhere else. Like any app's notifications, they are also seen by a watch paired with the phone and by an app you gave access to your notifications.

### Background

Once the Android app is set up and notifications are allowed, it asks the forecast provider about once an hour while it is closed, so it can tell you before a charge window starts: only with a network connection, and with **Wi-Fi only** switched on only on Wi-Fi or another network without a data limit; a low battery does not stop it, though Android's battery saver may delay it. It sends the same as when the app is open, to the same provider and fallback, and nothing else. Forecast.Solar's public plan allows 12 requests an hour for everything behind your internet address, so the phone shares that limit with the desktop app and any other device at home that asks it. The app also asks once when you open it, after a change you saved, and after the phone starts or the app is updated, for which Android lets it start at boot (the permission `RECEIVE_BOOT_COMPLETED`). Android's WorkManager, which runs the hourly update, adds two permissions of its own: `WAKE_LOCK`, to keep the phone awake while the update runs, and `ACCESS_NETWORK_STATE`, to wait for a network (or Wi-Fi only). The alarm that wakes the phone shortly before a notice uses only the forecast already on the phone and sends nothing; with a lead time under about 10 minutes a notice may come late. While notifications are not allowed, none of this runs, until you allow them on the dashboard's card or open the app with them allowed.

WorkManager keeps this schedule in its own database (in the table above). After a force stop nothing runs until you open the app again, and some phone makers stop apps in the background on their own; how to keep the app out of their "sleeping apps" is for the install guide ([#279](https://github.com/Enadeag/car-charge-planner/issues/279)).

### API keys

API keys are kept by the system, not in a plain file:

| System | Where the API keys are kept |
| --- | --- |
| Linux | The Secret Service: GNOME Keyring, KWallet or another that offers it, as items with the attribute `application` set to `car-charge-planner` |
| Windows | Credential Manager (Windows Credentials), as generic credentials whose names start with `car-charge-planner:` |
| Android (not released yet) | `keychain.json` in the app's no-backup folder: each value encrypted with AES-256-GCM, under a key in the Android Keystore that never leaves the phone and that not even the app can read out. Never in a backup or the transfer to your next phone, so you type the API keys again there; there is no `secrets.json`. Should the Keystore lose that key, the API keys can no longer be read and you type them again too |

The entries are named `forecast/<provider>/<name>`, after the setting, and one more entry, `index`, lists the names (no values) because a keychain cannot be asked what it holds. Without a usable keychain (a Linux machine with no Secret Service, for one) the API keys are kept in `secrets.json`, readable only by you, and the app says so. On Windows that file is in `%LOCALAPPDATA%`, so a roaming profile does not copy it to other computers (a file an older version kept beside `engine.json` is moved there once). The app reads the API keys from the keychain once and keeps them in memory until you save new ones; a keychain that cannot be used, or does not answer within a few seconds, is tried again five minutes later.

In the Android app, which is not released yet, the Forecast page of Settings shows the API keys masked until you choose Show; while that page is open, the phone allows no screenshot of it and no preview among the recent apps, and the app asks autofill to leave its fields alone.

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
| [SLF4J](https://www.slf4j.org) | the log of dbus-java and of the engine, passed on to the app's | MIT |

Their licence texts are in the `legal` folder of the Linux folder and of the Windows install folder, one file per library; the runtime's own notices, for OpenJDK and JavaFX, are in `lib/runtime/legal` of the Linux folder (`runtime\legal` in the Windows install folder). For development only: [JUnit](https://junit.org) (EPL-2.0), [AssertJ](https://assertj.github.io/doc/) and [ArchUnit](https://www.archunit.org) (both Apache-2.0).

The weather and solar data come from the providers above under their own terms. The recorded answers the tests use are credited in [`engine/src/test/resources/.../forecast/providers/NOTICE.md`](../engine/src/test/resources/nl/enadeag/carchargeplanner/engine/forecast/providers/NOTICE.md).
