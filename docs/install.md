# Installing

How to install, check, update and uninstall Car charge planner on Windows and Linux.

The downloads are on the [release page](https://github.com/Enadeag/car-charge-planner/releases). Each brings its own Java runtime: nothing else needs to be installed. Internet access is needed for the forecast; without it the app uses its cached forecast and says so.

## Windows

Download `car-charge-planner-<version>.exe` and `SHA256SUMS`, and [check the download](#check-a-download). The installer needs no administrator rights. It installs for you only, in `%LOCALAPPDATA%\Programs\car-charge-planner`, and adds a Start menu entry and a desktop shortcut. The first start opens set-up.

**"Windows protected your PC".** The installer is not code-signed yet (that waits for SignPath), so Microsoft Defender SmartScreen warns about a new download from an unknown publisher. Once the checksum matches, click **More info**, then **Run anyway**.

**Update:** quit the app (tray icon, **Quit**), then run the new installer. It replaces the old version and keeps your settings.

**Uninstall:** in Settings, Apps, Installed apps. This removes the program and its shortcuts. It keeps your settings, data and API keys: the folders are listed under [What is kept, and where](privacy.md#what-is-kept-and-where), and the keys are in Credential Manager. Delete them if you want them gone.

## Linux

Download `car-charge-planner-<version>-linux.tar.gz` and `SHA256SUMS`, [check the download](#check-a-download) and unpack it where you keep programs:

```bash
tar -xzf car-charge-planner-<version>-linux.tar.gz -C ~/.local/opt    # or any folder you like
~/.local/opt/car-charge-planner/bin/car-charge-planner
```

The first start opens set-up. The archive is not signed yet either.

**Add it to the menu.** Run `~/.local/opt/car-charge-planner/bin/car-charge-planner install-launcher` once. It puts the app in your application menu (KDE, GNOME and others) by writing a desktop entry to `$XDG_DATA_HOME/applications` and the icons to `$XDG_DATA_HOME/icons/hicolor` (by default under `~/.local/share`), without administrator rights. GNOME ties the app's notifications to this entry. It names the launcher it was run from, so run it again after you move the folder.

**Update:** quit the app, replace the folder with the new one, and start it again. If it starts at login, switch that off and on again when the folder moved, and run `install-launcher` again if you added the app to the menu.

**Uninstall:** switch off start at login (Settings, App), run `car-charge-planner uninstall-launcher` if you added the app to the menu (it removes exactly the files `install-launcher` wrote), quit the app and delete the folder. Your settings and data stay where they are, listed under [What is kept, and where](privacy.md#what-is-kept-and-where); delete them if you want them gone.

## Check a download

Each release has a `SHA256SUMS` file with the checksum of every download. Compare it with the file you downloaded before you run it.

**Windows.** In PowerShell, in the download folder, run `(Get-FileHash .\car-charge-planner-<version>.exe -Algorithm SHA256).Hash` and compare it with the line for the installer in `SHA256SUMS` from the same release (`Get-Content .\SHA256SUMS`). Capitals don't matter. If they differ, delete the file and download it again.

**Linux.** In the download folder, run:

```bash
sha256sum --ignore-missing -c SHA256SUMS
```

## Where a download comes from

GitHub attests each download of a release: a signed record that it was built by this repository's release workflow from the release's tag. With the [GitHub CLI](https://cli.github.com), check a download with `gh attestation verify <file> -R Enadeag/car-charge-planner`; it says which workflow run built it, or fails.

Each release also has an SBOM per download (`car-charge-planner-<version>-linux.cdx.json` and `-windows.cdx.json`, CycloneDX), which lists every library and the Java runtime it holds, with their versions.

## Coming from 0.2.0

Version 0.2.0 and earlier were a different program, written in Python. Their settings are not read: run set-up once; the old files are left alone.

On Windows that version was installed in `%LOCALAPPDATA%\Programs\Car charge planner`. The new installer does not remove it: uninstall it in Settings, Apps.
