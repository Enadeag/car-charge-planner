# Installing

How to install, check, update and uninstall Car charge planner on Windows and Linux.

The downloads are on the [release page](https://github.com/Enadeag/car-charge-planner/releases). Each brings its own Java runtime: nothing else needs to be installed. Internet access is needed for the forecast; without it the app uses its cached forecast and says so.

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

## Check a download

Each release has a `SHA256SUMS` file with the checksum of every download. Compare it with the file you downloaded before you run it.

**Windows.** In PowerShell, in the download folder, run `(Get-FileHash .\car-charge-planner-<version>.exe -Algorithm SHA256).Hash` and compare it with the line for the installer in `SHA256SUMS` from the same release (`Get-Content .\SHA256SUMS`). Capitals don't matter. If they differ, delete the file and download it again.

**Linux.** In the download folder, run:

```bash
sha256sum --ignore-missing -c SHA256SUMS
```

## Where a download comes from

GitHub attests each download of a release: a signed record that it was built by this repository's release workflow from the release's tag. With the [GitHub CLI](https://cli.github.com), check a download with `gh attestation verify <file> -R Enadeag/car-charge-planner`; it says which workflow run built it, or fails.

Each release also has an SBOM (CycloneDX) for Linux, `car-charge-planner-<version>-linux.cdx.json`, and one for Windows, `-windows.cdx.json`, which list every library and the Java runtime the downloads hold, with their versions. The Linux one covers the `tar.gz`, the `.deb` and the `.rpm`: they hold the same app.

## Coming from 0.2.0

Version 0.2.0 and earlier were a different program, written in Python. Their settings are not read: run set-up once; the old files are left alone.

On Windows that version was installed in `%LOCALAPPDATA%\Programs\Car charge planner`. The new installer does not remove it: uninstall it in Settings, Apps.
