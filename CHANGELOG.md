# Changelog

All notable changes are listed here. The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and the project follows [Semantic Versioning](https://semver.org/).

## [Unreleased]

### Added
- The dashboard warns when a forecast provider refuses its API key, as the Android app does: "Open-Meteo refused the API key. Check it under Forecast in Settings.", a line per provider above the day, also while another provider's or an older forecast gives the plan, and above "No forecast" when there is none (#391).

### Changed
- Solar panels are entered as the **number of panels**, a **direction** and a **tilt** chosen from a list, the **year placed** and, if you know it, the **power per panel** in Wp, in set-up and in Settings; the line below says what they give together ("Together about 3.6 kWp"). Left empty, the power is the usual one for the year placed. The losses and the name are under **More settings**, and the panels no longer have "I don't know". An array entered before as a size in kWp keeps that size until you enter its number of panels, and a tilt or direction entered in degrees stays as it is until you choose one (#448).
- The settings file is now version 2, which 0.6.0 and older cannot read: they ask you to update the app. Files of version 1 are still read (#448).
- Set-up no longer asks for the house's own use: the app plans with a typical household's, 12 kWh per day. The step is now **Away periods**. In Settings the value is under **More settings** on the Car and house page, called **Household use** and asked in **kWh per day**, without charging the car, as your energy supplier shows your use per day, instead of the average kW; the settings file still keeps the kW, and a value you leave alone is saved exactly as it was (#450).
- The charger is entered as its **Charge power at home**, in set-up and in Settings: 3.7 kW, 7.4 kW, 11 kW, 22 kW or **Other**, the lower of what the charger and the car can take. A power fills in the phases and the maximum charge current at 230 V (3.7 kW is one phase of 16 A, 11 kW three phases of 16 A); the phases, the minimum and maximum charge current, the voltage and the charging efficiency are under **More settings**, which **Other** opens. A charger saved before that is none of the four, such as one at 240 V or of 25 A, shows **Other** with those settings open, never a rounded power. In set-up the field starts empty and must be chosen, as the phases decide from how much solar power the car can charge; "I don't know" no longer fills it in. In set-up and in Settings "I don't know" sits under **More settings** and fills in the minimum current, the voltage and the efficiency only. The settings file does not change (#451).
- Settings, Planning shows what everyone needs first: the minimum and target charge level, the charging hours and the **notification lead time**, now chosen from **At the start** or 5, 10, 15, 30 or 60 minutes ahead. A lead saved before that is none of these, such as 20 minutes, stays as it is and shows as a choice of its own until you choose another. The planning margin, the import tolerance and the minimum charge window length are under **More settings**. On the Car and house page and in set-up's Car card "Other driving per day" is now **Daily distance**, and the trip surcharge is under **More settings** with the household use. The settings file does not change (#452).
- The Location page, in set-up and in Settings, says at the top which place you chose ("Chosen place: Utrecht, Netherlands", or "No place chosen yet"), and folds the latitude, the longitude and the time zone away under **Coordinates and time zone**: closed once a place is chosen, open without one or when a coordinate has a problem. The place's name is kept in the settings file next to the coordinates, and goes when you type other coordinates. With a forecast provider that has no place search, the page shows only the coordinates and the time zone, before anything is searched (#449).
- The Forecast page, in set-up and in Settings, folds a provider's optional settings away, as the free services need none: Open-Meteo's server address and API key under **API key and server**, Forecast.Solar's API key under **API key**. The fold is open when you stored a key or a server address of your own, or the keychain is locked, and stays closed once you close it while Settings is open; a problem in one of its fields opens it. **Test** and the **API keys** card stay outside, and a key or address in a closed fold is saved as it was (#459).
- No field shows **Typical value** any more, in set-up or in Settings: a value "I don't know" filled in looks like any other. The app still keeps which values are typical in the settings file, and set-up's welcome now says that a typical value can be changed there or later in Settings (#458).
- A day without a charge window now says which of two things it is, on the desktop and on Android: **Charging not needed** when the battery shows the level the plan charges to (the target, or more before a trip), with nothing else in the day card, or **No charge window** with "There is too little solar power while the car is home." when the car could use a charge. Neither shows a battery chip in the day card; the Car battery card still shows the levels on every day. A day a grid charge starts on, a trip's or the grid advice's on an earlier day than the one it is about, says only **No charge window**, with its battery chip, as the day a trip leaves does. A day the car is away on a trip, the day a trip leaves and the grid advice's day say what they said before (#460).

### Android

## [Android 0.1.0] - 2026-11-13

The first release of the Android app.

- First-run set-up: Welcome (what leaves the phone), Location (search for a place, shown at the top as "Chosen place: …" once chosen, with the coordinates and the time zone folded under **Coordinates and time zone**), Panels (the number of panels, direction, tilt, year placed and power per panel, with the losses and the name under **More settings**), Car (the battery, the consumption and the daily distance, with the trip surcharge under **More settings**), Charger (the charge power at home, chosen from 3.7, 7.4, 11 and 22 kW or Other, with the phases, the currents, the voltage and the efficiency under **More settings**), Away periods (the weekly away periods, whose times are chosen on Material's time picker, on the phone's 12- or 24-hour clock) and Check, which fetches a forecast once. Set-up plans with a typical household use, which Settings shows in kWh per day under **More settings** on the Car and house page, whose line in the list gives the battery only. Car and Charger have "I don't know: use typical values", which fills in typical values (#458). In English and Dutch.
- After set-up, the **dashboard**: a tab for each day of the plan, Today first, which you can also swipe between. Each day shows its charge window with the current to set and its power, about how many kWh it charges, its later windows, and its solar, battery and grid figures; a day without a window says why, with its battery figure for the grid advice, a trip's departure, a trip the car is away on or a grid charge that starts that day (then "No charge window" alone): otherwise "Charging not needed" alone, or "No charge window" with too little solar power while the car is home, both without a battery figure, as the battery card has the levels (#460). Today also shows the grid advice when the battery would end a day below its minimum. Every day has a chart of the expected solar power, the house's use and the charge windows, with a legend and the same in one sentence under it, and a bar of the car's battery with marks at the minimum and target charge levels; the chart's time axis shows fewer hours when the text is large. Every day also lists the coming one-off away periods: **Add** enters one on that day, all day or part of it with Material's time picker, and **Remove** takes one away at once, with **Undo**; both are kept in `away.json` in the app's files and make the plan again. Today's battery card has **Update battery level**, which asks for the level the car shows as a whole number in % from 0 to 100, saves it in `battery.json` in the app's files and makes the plan again; until you enter one, today's note says the plan assumes the level and where to enter it. The footer credits the forecast provider and says when the forecast was updated, when the fallback provider stood in and when the provider is busy; a forecast that could not be refreshed gets a warning above the plan. **Refresh** and **Settings** are in the top bar, a pull down refreshes too, and the plan is made again when you come back to the app after more than 15 minutes. Times are in the home's time zone, on the phone's 12- or 24-hour clock.
- **Trips** on the dashboard: below the battery card, each day's **Trips** card lists the trips the car is away for that day, worded as on the desktop ("Away 07:30 – 18:00 · 300 km there and back"), or says "No trip entered for this day.". Each trip has **Not with this car** and **I can charge at the destination**, which save as you tick them, and **Remove**, which takes it away at once, with **Undo**; after each change the plan is made again at once, and the alarm follows the new plan, such as one at a trip's charge start. The trips are kept in `trips.json` in the app's files, in the desktop's format, so a file copied from the desktop shows its trips.
- **Add** on each day's Trips card enters a trip, and **Change** beside a trip's Remove changes it, in a full-screen entry with **Close** and **Save**: the departure's and the return's day (Material's date picker, from today up to 20 days ahead) and time (the time picker, on the phone's 12- or 24-hour clock), the distance there and back in km and **I can charge at the destination**, in the home's time zone, with the desktop's checks at Save; a distance left as it showed keeps the trip's own, and Back asks before it throws a change away.
- **Trip advice** on the dashboard, as on the desktop: below the day card, a card for each trip on the tab of the day it leaves, and also on today's tab when it asks you to do something (charge above your target, charge from the grid, or plan a charging stop). It says the trip, what to charge and when ("Charge to 85% from the grid. Start tonight at 21:00. Set 16 A (3.7 kW), and a charge limit of 85% in the car or the charger."), how many km the battery falls short, and the battery level it starts from; **Adjust** goes to today's tab and opens the entry of the battery level. TalkBack reads each card as one sentence.
- On a day the car is away on a trip and that has no charge window, the day card says the car is away on a trip, and on the day a trip leaves only "No charge window", as the trip's advice says what the battery needs. On that day the battery's levels, its chip and what TalkBack reads for the bar also give the level after the trip ("50 → 74%, 70% after the trip").
- The app takes its colours from your wallpaper on Android 12 and later; the colours for solar, grid and the car stay the same, always with words.
- **Settings**, with the desktop's seven pages: Solar panels (every panel array), Location (the place chosen, as in set-up; the list shows its name, or the coordinates without one), Car and house, Charger (the list shows its power, such as "11 kW", or for Other its phases and currents), Forecast (the providers, **Check connection** and the API keys, with Show and Hide, each provider's server address and API key folded under **API key and server** or **API key**, open when one is stored (#459)), Away (the weekly away periods) and Planning (the charge levels, the charging hours on the time picker and the notification lead time, chosen from At the start or 5 to 60 minutes ahead, with the planning margin, the import tolerance and the minimum charge window under **More settings**). Each row of the list has an icon, a thin line sets the rows apart, and a row's values wrap rather than being cut off. Each page has its own **Save** and asks before Back throws edits away. The Forecast page allows no screenshot. In English and Dutch.
- **Notifications** for what the engine tells you, as on the desktop: a charge window that starts soon, a grid charge, when to start charging for a trip, the battery check (a tap, or **Enter battery level**, opens the entry of the level) and a charging stop, in English and Dutch, on the phone's 12- or 24-hour clock, in three channels you can set in Android's settings: Charge windows, Battery and trip checks, and Problems ("Forecast not updated", for a settings file that cannot be used or an API key the provider refused). A locked screen shows only "Charge window soon" or the title. They also come while the app is closed: the app asks the provider about once an hour, sets an alarm shortly before the next notice from the forecast already on the phone, and sets that alarm again after the phone restarts or the app is updated; nothing runs while notifications are not allowed. The dashboard asks for the permission with a card (**Turn on notifications**, or **Open notification settings** after a refusal), which × closes for good, and Settings has a **Notifications** row before the licences. The Forecast page has **Wi-Fi only** for the hourly update while the app is closed, and the welcome page says the app asks the provider about once an hour then. A provider that refused its API key gets a warning on the dashboard. The switch and the notifications' state are kept in two small files in the app's files folder; only the switch goes along to your next phone.
- **Open source licences**, the last row of Settings: the app's licence and those of the libraries it brings, read in the app, without a network.
- A settings file the app cannot use shows its details (in English) and **Set up again**; one of a newer version asks you to update the app and is left as it is.
- The API keys are kept encrypted with a key in the Android Keystore, and never in a plain file.
- The app needs Android 10 or newer.
- The release is an APK signed with the app's own release key, with `SHA256SUMS` and the key's certificate fingerprint, on the repository's GitHub Releases, tagged `android-v0.1.0` (#272).
- The README, the install guide, the guide and the privacy page describe the Android app: what it is and what it does not have next to the desktop app, installing, checking and updating the APK, the notification permission, what to do when the phone puts the app to sleep, what is different on the phone, and what the app keeps and sends (#279).

## [0.6.0] - 2026-11-10

Two fixes: saving API keys no longer loses the keys of the other providers, and a notification lead time of 0 minutes now tells you at the start of a charge window. Screen readers are no longer supported.

### Changed
- Screen readers are no longer supported: Narrator, NVDA and TalkBack are no longer checked before a release, and the code built only for them is being removed (#435). What stays: large fonts and scaling (200%) without cut-off text, contrast in light and dark, no meaning by colour alone, a label on every control, and keyboard use on the desktop.
- A file written by a newer version of the app (`engine.json`, `app.json`, `trips.json`, `away.json`, `battery.json`, `trip-notices.json` or `secrets.json`) is no longer moved aside and replaced when you save: it is left as it is, and saving fails until you update the app.

### Fixed
- A notification lead time of 0 minutes no longer means you are never told: a charge window is told at its start, or as soon as the app sees it running, and a trip's planned grid charge within 10 minutes of its start; leads above 0 are unchanged.
- Saving API keys no longer deletes the keys of the other providers when the keychain cannot read one of the entries: that entry is left as it is, the other changes are saved, and entering a new value for it replaces it.
- **Check connection** in Settings and set-up says what the provider said when it refuses the API key, such as "Forecast.Solar refused the request (status 401): check the API key", without "gave an answer the app does not understand" in front.

### Security
- The Java runtime this release brings is Temurin 25.0.5+8, with Java's security fixes of October 2026 (0.5.0 brought 25.0.4.1+1). OpenJDK has reported compromised eMudhra intermediate certificates (CVE-2026-114820, rated 9.1; <https://openjdk.org/emudhra>). Temurin answers this in its own way, in this build already: its list of trusted certificates no longer holds the affected eMudhra roots but one that Mozilla still trusts (<https://adoptium.net/news/2026/10/temurin-october-2026-emudhra>), where OpenJDK's own fix blocks the compromised certificates. The app makes HTTPS requests only to the forecast provider you chose.

## [0.5.0] - 2026-10-22

Trips: enter a drive, and the plan makes sure the battery holds enough when you leave, with advice and notifications for when to charge and the charge limit to set. Grid charges now start within your charging hours. On Linux the app also comes as a .deb and an .rpm.

### Added
- Trips: **Add trip** on the dashboard's Trips card enters a drive from home and back, with its departure, return, the distance there and back and whether you can charge at the destination. Each day's tab lists its trips, with **Not with this car** (the plan ignores the trip and the car stays home), **Change** and **Remove**. The trips are kept in `trips.json` in the data folder until a day after their return.
- The plan makes sure the car can make each trip: the trip first, then solar power, and the grid only for what is still missing. For a trip it charges above your target level, up to 100%, as late as possible; the grid part is done an hour before the departure. Trips close together are charged for together, and when even a full battery is not enough, the advice says how many kilometres it is short and to plan a charging stop.
- A trip advice card on the dashboard says what to do before each trip: the level to charge to, the solar and grid parts, when to start, and the charge limit to set. It shows the battery level it assumes, with **Adjust** to enter the one the car shows. The day a trip leaves shows the battery level after the trip, and a day away on a trip no longer says the car needs no charging.
- Charging hours: **Start charging between** (Settings, Planning; 07:00 to 22:00 by default, and may span midnight) sets when a grid charge may start. A charge may run on after them.
- Trip surcharge (Settings, Car and house; 15% by default): the share added to the consumption for the energy of a trip, as long drives and drives in winter use more.
- Notifications for trips: when to start charging for a trip's grid charge, a battery check that asks whether the assumed level is right when a trip needs a grid charge or has little to spare (a click, or the **Enter battery level** button on Linux, opens the entry of the level), and "Plan a charging stop" when the battery cannot make a trip. They come within the charging hours and once per trip. Their state is kept in `trip-notices.json` in the data folder.
- Linux: a `.deb` for Debian and Ubuntu and an `.rpm` for Fedora and openSUSE, next to the tar.gz. They install the app in `/opt/car-charge-planner` with a menu entry for every user; `sudo apt install ./car-charge-planner_<version>_amd64.deb` or `sudo dnf install ./car-charge-planner-<version>-1.x86_64.rpm`, and a new version installs over the old one. `install-launcher` in a package install says the package puts the app in the menu already, instead of adding a second entry, and notifications are tied to the package's menu entry (ADR 0007).
- Windows: the app follows **Text size** (Settings, Accessibility), on top of display scaling: the text in the dashboard, Settings and set-up grows by as much, also when you change it while the app runs.
- Ready for a Flatpak (not on Flathub yet, #217): in the Flatpak's sandbox, start at login goes through the desktop's Background portal, `install-launcher` and `uninstall-launcher` say the Flatpak puts the app in the menu itself, and the tray icon, notifications, keychain and second start work as outside it (ADR 0008).

### Changed
- Grid advice now starts within the charging hours, at 07:00 by default instead of in the middle of the night, and, like its notification, names the charge limit to set in the car or the charger, such as "Set 6 A (1.4 kW), and a charge limit of 42% in the car or the charger."
- Settings, Car and house: "Distance per day" is now **Other driving per day**, the short drives that are not trips; trips come on top of it. The setting keeps its place in the settings file.
- A notification's click opens what it is about: the battery check opens the entry of the battery level.

### Fixed
- The number fields in Settings and set-up no longer take "NaN" or "∞" as a value.
- A settings file with a number too large to hold, such as `1e400`, is refused like any other value out of range, instead of being planned with as infinity.
- Windows: the text size watcher closes its registry key and event also when following the text size fails (#223).

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
- The API keys are read from the system keychain once and kept in memory until you save new ones, instead of at every plan, and only when the saved forecast is too old to use. A keychain that cannot be used, or does not answer within 5 seconds, holds nothing up and is tried again after 5 minutes. On Linux the app keeps one connection to the Secret Service instead of opening one for every question, and first checks that there is one.
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

[Unreleased]: https://github.com/Enadeag/car-charge-planner/compare/v0.6.0...HEAD
[0.6.0]: https://github.com/Enadeag/car-charge-planner/compare/v0.5.0...v0.6.0
[0.5.0]: https://github.com/Enadeag/car-charge-planner/compare/v0.4.0...v0.5.0
[0.4.0]: https://github.com/Enadeag/car-charge-planner/compare/v0.3.0...v0.4.0
[0.3.0]: https://github.com/Enadeag/car-charge-planner/compare/v0.2.0...v0.3.0
[0.2.0]: https://github.com/Enadeag/car-charge-planner/compare/v0.1.0...v0.2.0
[0.1.0]: https://github.com/Enadeag/car-charge-planner/releases/tag/v0.1.0
[Android 0.1.0]: https://github.com/Enadeag/car-charge-planner/releases/tag/android-v0.1.0
