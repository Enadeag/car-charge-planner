# Installing

How to install, check, update and uninstall Car charge planner on Windows, Linux and Android.

The downloads are on the [release page](https://github.com/Enadeag/car-charge-planner/releases). Each download for Windows and Linux brings its own Java runtime: nothing else needs to be installed. Internet access is needed for the forecast; without it the app uses its cached forecast and says so. The Android app has [its own part](#android) below.

## Windows

Download `car-charge-planner-<version>.exe` and `SHA256SUMS`, and [check the download](#check-a-download). The installer needs no administrator rights. It installs for you only, in `%LOCALAPPDATA%\Programs\car-charge-planner`, and adds a Start menu entry and a desktop shortcut. The first start opens set-up.

**"Windows protected your PC".** The installer is not code-signed (see [ADR 0003](adr/0003-pyinstaller-unsigned-until-signpath.md)), so Microsoft Defender SmartScreen warns about a new download from an unknown publisher. Once the checksum matches, click **More info**, then **Run anyway**.

**Update:** quit the app (tray icon, **Quit**), then run the new installer. It replaces the old version and keeps your settings.

**Uninstall:** in Settings, Apps, Installed apps. This removes the program and its shortcuts. It keeps your settings, data and API keys: the folders are listed under [What is kept, and where](privacy.md#what-is-kept-and-where), and the keys are in Credential Manager. Delete them if you want them gone.

## Linux

There are three Linux downloads with the same app: a `.deb` for Debian and Ubuntu, an `.rpm` for Fedora and openSUSE, and a `tar.gz` for any other distribution. They are for 64-bit Intel and AMD processors (x86-64). None of them updates itself: install the next release yourself, as below. A Flathub version with automatic updates is planned ([ADR 0007](adr/0007-linux-packages.md), #217).

### Debian and Ubuntu

Download `car-charge-planner_<version>_amd64.deb` and `SHA256SUMS`, [check the download](#check-a-download) and install it:

```bash
sudo apt install ./car-charge-planner_<version>_amd64.deb
```

It installs the app in `/opt/car-charge-planner` and puts it in the application menu for every user; start it from there, or run `/opt/car-charge-planner/bin/car-charge-planner`. The first start opens set-up. `install-launcher` is not needed: it says the package puts the app in the menu already.

**Coming from the tar.gz:** run `/opt/car-charge-planner/bin/car-charge-planner uninstall-launcher` once if you had added the unpacked app to the menu, so it is not there twice, and switch start at login off and on again (Settings, App) so it starts the installed app. Then delete the old folder.

**"No writable system menu directory found".** The package adds its menu entry with `xdg-desktop-menu`, which needs the system's menu folders, `/etc/xdg/menus` and `/usr/share/desktop-directories`. Desktops with an application menu (KDE Plasma, GNOME, Xfce and others) have them; if yours does not, create them with `sudo mkdir -p /etc/xdg/menus /usr/share/desktop-directories` and install again. The same goes for the `.rpm`.

**Update:** quit the app (tray icon, **Quit**), then install the new `.deb` the same way, over the old one. Your settings and start at login stay.

**Uninstall:** switch off start at login (Settings, App), quit the app, then `sudo apt remove car-charge-planner`. This removes the program and its menu entry. Your settings and data stay where they are, listed under [What is kept, and where](privacy.md#what-is-kept-and-where); delete them if you want them gone.

### Fedora and openSUSE

Download `car-charge-planner-<version>-1.x86_64.rpm` and `SHA256SUMS`, [check the download](#check-a-download) and install it:

```bash
sudo dnf install ./car-charge-planner-<version>-1.x86_64.rpm      # Fedora
sudo zypper install ./car-charge-planner-<version>-1.x86_64.rpm   # openSUSE
```

It installs the app in `/opt/car-charge-planner` and puts it in the application menu for every user, as the `.deb` does; coming from the tar.gz works the same too. Each release is tested on Fedora; openSUSE should work the same, but is not tested.

**Update:** quit the app, then install the new `.rpm` the same way: it replaces the old version and keeps your settings and start at login.

**Uninstall:** switch off start at login (Settings, App), quit the app, then `sudo dnf remove car-charge-planner` (openSUSE: `sudo zypper remove car-charge-planner`). Your settings and data stay, as above.

### Other distributions: the tar.gz

Download `car-charge-planner-<version>-linux.tar.gz` and `SHA256SUMS`, [check the download](#check-a-download) and unpack it where you keep programs:

```bash
tar -xzf car-charge-planner-<version>-linux.tar.gz -C ~/.local/opt    # or any folder you like
~/.local/opt/car-charge-planner/bin/car-charge-planner
```

The first start opens set-up. The archive is not signed yet either.

**Add it to the menu.** Run `~/.local/opt/car-charge-planner/bin/car-charge-planner install-launcher` once. It puts the app in your application menu (KDE, GNOME and others) by writing a desktop entry to `$XDG_DATA_HOME/applications` and the icons to `$XDG_DATA_HOME/icons/hicolor` (by default under `~/.local/share`), without administrator rights. GNOME ties the app's notifications to this entry. It names the launcher it was run from, so run it again after you move the folder.

**Update:** quit the app, replace the folder with the new one, and start it again. If it starts at login, switch that off and on again when the folder moved, and run `install-launcher` again if you added the app to the menu.

**Uninstall:** switch off start at login (Settings, App), run `car-charge-planner uninstall-launcher` if you added the app to the menu (it removes exactly the files `install-launcher` wrote), quit the app and delete the folder. Your settings and data stay where they are, listed under [What is kept, and where](privacy.md#what-is-kept-and-where); delete them if you want them gone.

## Android

The Android app is not released yet; the first release will be 0.1.0.

It needs Android 10 or newer. It is an APK on the release page, `car-charge-planner-android-<version>.apk` of the release tagged `android-v<version>`, which is not on Google Play; you can download it on the phone, or on a computer and copy it over. F-Droid is planned and not there yet. The APK is signed with the app's own release key, and does not update itself.

**Install.**

1. Download `car-charge-planner-android-<version>.apk` and `SHA256SUMS`, and [check the download](#check-a-download).
2. Open the APK on the phone, from your browser's downloads or from Files. Android asks you to allow the app you opened it from, your browser or Files, to install apps; allow it, and choose **Install**. You can turn that permission off again afterwards (Settings, Apps, Special access, Install unknown apps; the names differ a little per phone).
3. Play Protect may warn about an app it does not know. Once the checks above match, choose to install anyway.
4. Open the app. The first start opens set-up, which [the guide](guide.md#the-android-app) walks through.

**Update:** the app does not update itself and does not look for a new release. Download the next release's APK and install it the same way, over the old one: it keeps your settings, trips and API keys. Android only accepts the update when it is signed with the same key, which every release is. After an update the app sets up its alarm for the next notice again by itself.

**Uninstall:** in Settings, Apps, Car charge planner, **Uninstall**. This removes the app and what it kept on the phone: your settings, trips, battery level, forecast cache and API keys, as [Data and privacy](privacy.md#what-is-kept-and-where) lists them. The app has no account, and Android does not back its files up to the cloud, so nothing is kept elsewhere.

### Notifications

The app tells you before each charge window starts, with what to set in your charger's app. Since Android 13 an app needs your permission to show notifications.

- **When it asks.** Not at the first start. After set-up the dashboard shows a card, **Get a notice before each charge window**, that says why, with **Turn on notifications**. Only that button makes Android ask. On Android 10 to 12 notifications are on already, and the card shows only when you have blocked them for the app.
- **If you refuse,** or have blocked them in Android's settings, the app shows no notices and does nothing in the background: it does not ask the forecast provider while it is closed. The dashboard and the plan work as before, with the forecast fetched when you open the app. The card then offers **Open notification settings**, as Android does not ask again; turn notifications on there, and the app takes it up again when you come back to it. **×** on the card closes it for good; Settings has a **Notifications** row that opens Android's settings for the app at any time.
- **What you can set there.** The app has three channels in Android's settings: **Charge windows**, **Battery and trip checks** and **Problems** ("Forecast not updated", for a settings file the app cannot use or an API key the provider refused). The lock screen shows only "Charge window soon" for a charge window or a planned grid charge, and the title of the other notices.

### Notices while the app is closed

The app tells you about a charge window through an alarm that Android lets go off while the phone sleeps. Android may deliver it a few minutes late, and on Android 12 and later, when the phone saves battery, sometimes later still, so a notification lead time of only a few minutes is best effort: choose 15 minutes or more (Settings, Planning) if you want the notice in time. Two notices less than nine minutes apart may come up to nine minutes late. The app needs no "Alarms & reminders" permission, and does not ask to be left out of Android's battery optimisation.

If you force-stop the app (Settings, Apps, Car charge planner, Force stop), Android also stops its alarm and its hourly update, and nothing runs until you open the app again. Some phone makers put apps to sleep on their own when they think they are unused. On a Samsung phone, open Settings, Battery (or Device care), Background usage limits, and make sure Car charge planner is not under Sleeping apps or Deep sleeping apps; you can add it to Never sleeping apps. Other makers have a similar list; [dontkillmyapp.com](https://dontkillmyapp.com) describes them per maker.

## Check a download

Each release has a `SHA256SUMS` file with the checksum of every download. Compare it with the file you downloaded before you run it.

**Windows.** In PowerShell, in the download folder, run `(Get-FileHash .\car-charge-planner-<version>.exe -Algorithm SHA256).Hash` and compare it with the line for the installer in `SHA256SUMS` from the same release (`Get-Content .\SHA256SUMS`). Capitals don't matter. If they differ, delete the file and download it again.

**Linux.** In the download folder, run:

```bash
sha256sum --ignore-missing -c SHA256SUMS
```

**Android.** The APK is signed with the app's release key, so there are two checks. Do them on a computer, with the APK downloaded there, and copy it to the phone afterwards:

1. The checksum, as for Linux, or as for Windows with the APK's name.
2. The signing certificate. `apksigner verify --print-certs car-charge-planner-android-<version>.apk` (`apksigner` is in the build-tools of the Android SDK) prints a line `Signer #1 certificate SHA-256 digest`, which must show the same fingerprint, in 64 hexadecimal digits, as the release's notes say and as [`android/release-certificate.sha256`](../android/release-certificate.sha256) records. If it does not, do not install the APK.

## Where a download comes from

GitHub attests each download of a release, the Android APK too: a signed record that it was built by this repository's release workflow from the release's tag (`android-v<version>` for the APK). With the [GitHub CLI](https://cli.github.com), check a download with `gh attestation verify <file> -R Enadeag/car-charge-planner`; it says which workflow run built it, or fails.

Each release also has an SBOM (CycloneDX) for Linux, `car-charge-planner-<version>-linux.cdx.json`, and one for Windows, `-windows.cdx.json`, which list every library and the Java runtime the downloads hold, with their versions. The Linux one covers the `tar.gz`, the `.deb` and the `.rpm`: they hold the same app.

## Coming from 0.2.0

Version 0.2.0 and earlier were a different program, written in Python. Their settings are not read: run set-up once; the old files are left alone.

On Windows that version was installed in `%LOCALAPPDATA%\Programs\Car charge planner`. The new installer does not remove it: uninstall it in Settings, Apps.
