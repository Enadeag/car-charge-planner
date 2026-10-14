# Findings: a Java (JavaFX) desktop version of the tray app

Spike date: 2026-10-14/15. Throwaway code in this directory; how to run each experiment: [README.md](README.md).

**Test systems**

- Linux: CachyOS (Arch), KDE Plasma 6 on Wayland, one 1920x1200 screen at 100 % scale, OpenJDK 27.
- Windows: Windows 11 Enterprise VM, Temurin JDK 25.0.4. **Nobody was logged in on the VM during the spike**,
  so everything ran over ssh in session 0. That session has no desktop, no taskbar and no tray, and
  rendering falls back to software. So for Windows there is **no visual check of the tray,
  the menu or the notification**. See "What still needs a human" at the end.

**Libraries and versions** (latest on Maven Central on 2026-10-14)

| Library | Version | Used for |
|---|---|---|
| org.openjfx:javafx-controls | 27 | UI |
| io.github.mkpaz:atlantafx-base | 3.0.0 (built for Java 25 / JavaFX 27) | Primer light/dark theme, ToggleSwitch |
| com.github.hypfvieh:dbus-java-core + dbus-java-transport-native-unixsocket | 5.2.2 | SNI tray, notifications, Secret Service (pure Java, no JNI) |
| com.dorkbox:SystemTray | 4.4 (last release 2023) | tray experiment 1b only (Maven profile `dorkbox`) |
| com.github.javakeyring:java-keyring | 1.0.4 (last release 2023) | keychain |
| org.slf4j:slf4j-simple | 2.0.20 | logging for dbus-java |
| Maven wrapper / Maven | 3.3.4 / 3.9.16 | build; `--release 25` |

---

## 1. Tray icon on Linux KDE Wayland: **works, with our own StatusNotifierItem** (AWT does not work, dorkbox works with caveats)

| | Icon appears | Left click opens a window | Right-click menu | Icon changes at runtime | Crisp |
|---|---|---|---|---|---|
| (a) AWT `SystemTray` | yes, through `xembedsniproxy` | **no** | **no** | yes | **no**: a 24x24 opaque light-grey square |
| (b) dorkbox SystemTray 4.4 | yes (it picks GTK AppIndicator through JNA) | **no**, left click opens the menu (an AppIndicator limit) | yes | yes | yes |
| (c) own SNI + dbusmenu over dbus-java 5.2.2 | yes | **yes** | **yes**, the native Plasma menu | **yes** (icon and menu labels) | **yes** |

Evidence (all screenshots cropped to the tray or the window; the 4x ones are nearest-neighbour enlargements):

- `screenshots/linux-awt-tray-dot-toggle-4x.png`: AWT icon without and with the dot. You can see the grey XEmbed square.
  The proxy registers as `Id "JavaEmbeddedFrame"`. `IconPixmap` is one 24x24 image with an opaque
  background (read over D-Bus). Real clicks (left and right) on the icon reached neither the
  `MouseListener` nor the `PopupMenu`: no log line, no window, no menu.
- `screenshots/linux-dorkbox-tray-4x.png`, `screenshots/linux-dorkbox-leftclick-opens-menu.png`.
- `screenshots/linux-sni-tray-dot-toggle-4x.png`: our SNI icon without and with the green status dot.
  It is round, has a transparent background and is drawn from the 22 px pixmap: crisp.
- `screenshots/linux-sni-leftclick-window.png`: the JavaFX window after a left click on the icon.
- `screenshots/linux-sni-rightclick-menu.png`: the Plasma menu, with a disabled status line ("Status: charging")
  that is updated at runtime and a separator. Clicking "Toggle status dot" arrived as `dbusmenu Event id=2 clicked`.

How this was tested: the clicks were real pointer input from a virtual uinput tablet, not D-Bus calls.
The same harness made the SNI clicks work, so the AWT result is not a test artefact.

Notes on (c), `Sni.java` (~330 lines including the dbusmenu):
- It implements `org.kde.StatusNotifierItem` (properties, `Activate`, `ContextMenu`, `NewIcon` / `NewToolTip` signals)
  and `com.canonical.dbusmenu` (`GetLayout`, `GetGroupProperties`, `Event`, `AboutToShow`, `LayoutUpdated`).
  It registers with `org.kde.StatusNotifierWatcher`.
- The icon is sent as ARGB pixmaps in 16/22/24/32/48/64 px, so the status dot can be drawn in Java2D at runtime.
- Plasma calls `ProvideXdgActivationToken` before `Activate` (seen in the log: `kwin-221`). A native Wayland
  window could use that token to get focus. JavaFX windows are X11 windows under XWayland
  (confirmed with `xprop`), and they came to the front and got focus without the token.
- dbus-java's pure Java unix-socket transport worked without JNI or extra native libraries.
- dbus-java does not handle `org.freedesktop.DBus.Properties` for you in this style, so `Get` / `GetAll`
  are written by hand. The `Variant` signatures need care (`a(iiay)`, `(sa(iiay)ss)`, `(ia{sv}av)`).

Caveats:
- dorkbox 4.4: ~8 MB of extra jars (Kotlin stdlib, coroutines, javassist, JNA twice). It loads GTK3
  into the JVM next to JavaFX's GTK3. It writes the tray images to a world-readable
  `/tmp/<app>Cache_<user>` directory. Its bundled `dorkbox.Updates` contains `http://updates.dorkbox.com/`
  (an update ping you have to switch off with `Updates.ENABLE = false`; found in the jar, not seen on the wire).
  It has had no release since 2023. Not recommended.
- GNOME: not tested (no GNOME session). An SNI needs the AppIndicator/KStatusNotifierItem extension there,
  the same as for the current Python app. On Ubuntu it is on by default. Without it, (b) and (c) show nothing
  and (a) would need the old XEmbed tray, which GNOME no longer has. So it is the same requirement as today.

## 2. Tray icon on Windows: **not verified by eye** (AWT is the expected answer)

- `java.awt.SystemTray` cannot be used over ssh: in session 0 AWT is headless (`HeadlessException: The application
  does not have desktop access`). The VM had no logged-in user, so a scheduled task with `/IT` had no desktop
  to run on. A libvirt screenshot of the VM console showed only the lock screen. I did not log in, because
  that needs the maintainer's password.
- The code is ready: `spike.AwtTray` (menu, left-click window, a dot toggle every 5 s, balloon) and
  `spike.App` (the tray app). The app hands AWT a 64 px image with `setImageAutoSize(true)`, which should
  stay sharp at 125/150 %. **This is the main open item**; the command is at the end.
- Expected, from the JDK and from wide use, not checked here: icon, menu, left click and `setImage` all work on Windows.
  The AWT `PopupMenu` looks dated (old Win32 menu, no dark mode). A modern menu means drawing your own small undecorated
  JavaFX window at the click position.

## 3. Notification with a click action

**Linux: works** (`spike.LinuxNotify`, dbus-java). `Notify` with actions `default` + `open` ("Open planner")
and hints `desktop-entry` / `urgency`. The signals arrived in this order: `ActivationToken`, `ActionInvoked`,
`NotificationClosed`.
- `screenshots/linux-notification-with-action.png`: the Plasma notification with the button.
- `screenshots/linux-notification-click-raised-window.png`: the window after the click. Two cases were tested:
  (1) the window hidden (in the tray), then a click on the button: the window appears on top with focus;
  (2) the window already open but behind a maximised browser, then a click on the notification body: the window
  comes to the front, above the browser (JavaFX reported `focused=false` at that instant, but it was visibly raised).

**Windows: not verified** (no desktop session). `TrayIcon.displayMessage` exists, and per the JDK's Windows peer
a click on the balloon/toast is delivered as an `ActionEvent` on the `TrayIcon`. The spike logs it
(`TrayIcon ActionEvent ...` in `AwtTray`). On Windows 10/11 these show as toasts that carry the *Java* identity
(no app name or icon control, no buttons). For real toasts with buttons and an AUMID you need WinRT
(JNA/Panama or a small helper), which is a separate piece of work.

## 4. Dashboard card in JavaFX + AtlantaFX: **works, looks modern**

`spike.Dashboard` / `spike.Card`: a "Next charge window" hero card with pills (sun amber, car green, grid blue),
today's solar curve on a `Canvas` (filled amber area with a gradient, the charge window in green, a dashed "now" line
with a marker), and a battery bar with min and target markers. Primer Light/Dark from AtlantaFX, plus ~150 lines
of CSS on top of AtlantaFX's colour tokens. It follows the system light/dark preference
(`Platform.getPreferences().getColorScheme()`, live) and has a toggle in the header that switches the theme live.
The fonts are the system font: Noto Sans on this Linux, Segoe UI on Windows. Rendering looked clean on both.

- `screenshots/linux-card-window-dark.png`: the real window on KDE, with the Breeze title bar.
- `screenshots/linux-card-window-light-after-toggle.png`: the same window after clicking the toggle.
- `screenshots/linux-card-light.png`, `linux-card-dark.png`, `linux-card-light@2x.png`: node snapshots.
- `screenshots/windows-card-light-snapshot.png`, `windows-card-dark-snapshot.png`, `windows-card-light-snapshot@1.5x.png`:
  rendered on the Windows VM in session 0 (software pipeline, because D3D9 could not start without a desktop).
  The text uses Segoe UI; on a real desktop the D3D pipeline renders the same scene.

Caveats: on Linux JavaFX 27 still runs through XWayland. That was sharp at 100 %. At fractional scales (125/150 %)
XWayland apps can come out blurry or the wrong size, depending on the KDE "Legacy applications (X11)" scaling setting.
**Not tested**, because the screen here is at 100 %. JavaFX only scales by whole numbers (GDK_SCALE) on Linux.

## 5. Single instance + "show": **works on both**

`spike.App`: an AF_UNIX socket via `java.nio` in a per-user directory (`$XDG_RUNTIME_DIR/car-charge-planner-spike/`
on Linux, `%LOCALAPPDATA%\car-charge-planner-spike\` on Windows). A second launch connects, sends `show` and exits.
The first instance shows and raises its window. A stale socket after a crash (`kill -9` / `Stop-Process -Force`)
is detected ("Connection refused") and taken over.

- Linux log: `another instance is running; asked it to show its window` -> first instance: `instance message: show`,
  `window shown on request`. The window was visible on screen.
- Windows (Windows 11, over ssh): the same log lines. The socket file was created under `%LOCALAPPDATA%`. Stale takeover
  worked (`Connection refused: connect`).

**Must carry over to a real port** (flagged by a security review; the spike deliberately does not do these):
- An owner-only directory (0700, create it and verify the owner/mode) and a 0600 socket, plus a peer check
  (`SO_PEERCRED`-style uid check on Linux, or verify the socket owner), as `instance.py` in the Python app does.
  The spike's fallback directory under `~/.cache` is created with default permissions.
- A per-connection read timeout so a silent peer cannot block the accept loop. `handOff()` must delete the socket
  only on "connection refused", not on any `IOException`.
- Log only recognised commands, not the raw message.

## 6. Secret storage: **works on Linux, with caveats; Windows not verified**

Linux:
- **java-keyring 1.0.4 does not work together with dbus-java 5.** It depends on `secret-service` 1.8.1, which is built for
  dbus-java 4. With dbus-java 5.2.2 on the class path, `Keyring.create()` throws "No available keyring backend found".
  With its own dbus-java 4.2.1 class path it works (store, read and delete, GNOME_KEYRING backend). You cannot have
  both dbus-java versions in one class path.
- `secret-service` 3.0.0-alpha (built for dbus-java 5) has different package names, so java-keyring cannot use it. Used directly,
  store and read worked, but **its `deleteItem` locked the default collection and then asked for the keyring password**.
  That happened once against the maintainer's real login keyring during this spike (see the end of this file), and it
  happened again, reproducibly, in a throwaway keyring. Do not use it.
- **Own Secret Service client over dbus-java 5 (`spike.SecretDbus`, ~100 lines): works.** It stored, read back and
  deleted an item in a throwaway gnome-keyring on a private session bus (`keyring-sandbox.sh`):
  `created .../collection/login/1`, `read back: OK, identical`, `deleted; items left: 0`.
  It uses the "plain" session algorithm, so the secret crosses the user's own session bus unencrypted. libsecret uses
  DH by default; a port should add `dh-ietf1024-sha256-aes128-cbc-pkcs7` or accept plain. If the collection is locked it
  stops and says so; a real port needs to call `Unlock` and handle the prompt.
- On this desktop `org.freedesktop.secrets` is served by gnome-keyring-daemon (KDE's ksecretd is also present). Not tested
  against KWallet's Secret Service implementation.

Windows: java-keyring chose `WINDOWS_CREDENTIAL_STORE`, but `CredWrite` failed with **error 1312 (no logon session)**.
That is expected over key-based ssh, which has no credential-bearing logon session. It says nothing about a normal desktop
session. **Needs one run in a logged-in session** (command at the end). java-keyring uses JNA on Windows (~3 MB of jars).

## 7. Packaging with jpackage: **works on both**

Linux (`./package.sh`: jlink with `java.base, java.desktop, java.logging, jdk.net, jdk.security.auth, jdk.unsupported,
javafx.controls`, `--strip-debug --compress zip-6`; the runtime comes from OpenJDK 27):

| | Java spike (app-image) | Python app (current, from the dev venv) |
|---|---|---|
| On disk | 67 MB (runtime 60 MB, jars 4.7 MB of which ~3 MB JNA only for java-keyring) | not comparable: dev venv 390 MB (PySide6 245 MB); no PyInstaller build was at hand |
| Download | 48.5 MB `.tar.gz` | n/a |
| Launch -> window visible | 634-717 ms (6 runs, warm disk cache) | n/a (starts in the tray) |
| Launch -> tray icon registered | 535-591 ms (`--hidden`) | 428 ms |
| Idle memory, in tray only | PSS 225-246 MB (RSS 248-269 MB); 208 MB with `-XX:+UseSerialGC -Xmx64m` | PSS 91 MB (RSS 108 MB) |
| Idle memory, window shown | PSS 267 MB (RSS 295 MB); 205-226 MB tuned | n/a |

Where the Java memory goes (NMT + pmap, tuned run): the JVM itself commits only ~72 MB (heap 19, code 13, class/metaspace).
About 50 MB sits in one native malloc arena that is not the JVM's: GTK, Mesa/LLVM (the Intel driver loads `libLLVM`) or
glib. That is a guess, not attributed. `MALLOC_ARENA_MAX=2` did not help, and neither did `-Dprism.order=sw`. Another 23 MB is
the mapped `modules` image. Expect a JavaFX tray app on Linux to sit at about 2-2.5x the memory of the Python app.

Windows (`package.ps1`; Temurin 25 runtime; measured in session 0 with software rendering and no tray):

| | |
|---|---|
| app-image | 62.4 MB on disk, 45.2 MB zipped |
| installer (`--type exe`, per-user) | 45.2 MB `.exe`; installs 62.4 MB into `%LOCALAPPDATA%\car-charge-planner-spike` (installed and uninstalled again with `msiexec /x`) |
| Launch -> window shown | 2114 ms on the first run (JavaFX unpacks its DLLs into `~\.openjfx\cache`, Defender scans), then 898-918 ms |
| Idle | working set 128 MB with the window shown / 115 MB in the tray (launcher stub 7 MB + JVM process); private bytes 138 / 217 MB |

The Python app on the VM could not be measured: no user session, and its install folder was empty.

What the installer took: **WiX**. WiX 7 (`winget WiXToolset.WiXCLI`) refuses to run until you accept its
"Open Source Maintenance Fee" EULA (`WIX7015`). I did not accept it, and uninstalled WiX 7. WiX 3.14 via winget wants to switch on
the .NET 3.5 Windows feature (failed, and I did not force it). What worked: .NET SDK 8 + `dotnet tool install --global wix
--version 5.0.2` + the `WixToolset.Util.wixext` and `WixToolset.UI.wixext` 5.0.2 extensions. jpackage 25 drives WiX 5 fine.
The installer is unsigned, like the Python one today (ADR 0003).

Packaging caveat: the runtime is jlinked from JavaFX's Maven jars. Those carry the native libraries inside, and JavaFX unpacks
them on first start into `~/.openjfx/cache` (Linux) and `%USERPROFILE%\.openjfx\cache` (Windows). A real build should jlink from
Gluon's JavaFX **jmods**, so the `.so` / `.dll` files sit in the runtime and nothing is written to the home directory.

**GraalVM native-image (Gluon): not tried.** Gluon's GluonFX plugin can build native desktop JavaFX images
(Linux/Windows/macOS) with start-up in the 100-200 ms range and lower memory. Here it would mean reachability
metadata for dbus-java (reflection on interface proxies, `Struct`/`Tuple` fields), for AWT on Windows, and for java-keyring/JNA.
You would also need a GraalVM per OS in CI, and build and debug cycles get slower. The app spends its life idle in the tray,
so start-up time hardly matters. Memory would improve, but the bigger part of the Linux footprint is native GTK/Mesa memory
that native-image does not remove. **Not worth it for this app**; revisit only if memory becomes a real complaint.

## 8. Compose Multiplatform comparison: **not done** (optional; time went to the Linux keyring and Windows packaging issues).

---

## Recommendation

A JavaFX rewrite of this tray app is **technically sound on Linux**. The riskiest item, the KDE Wayland tray, works well
*if you do not use AWT*: write a small StatusNotifierItem + dbusmenu on dbus-java 5 (about 300 lines, already in
`Sni.java`). It gives a crisp icon, a left click that opens the window, a native Plasma menu and a live status dot.
Use the same library for notifications (`org.freedesktop.Notifications` with actions; tested, the window comes up)
and for the Secret Service (an own small client; java-keyring cannot share dbus-java 5). Skip dorkbox.

On Windows use AWT `SystemTray` for the icon and `displayMessage` for simple notifications. java-keyring covers
Credential Manager, and jpackage + WiX 5 gives a ~45 MB per-user installer. These Windows parts are **not yet seen
working on a desktop**; that is the one check that should happen before deciding (below).

Costs to weigh: the download is ~45-48 MB (jlinked runtime). Idle memory is roughly 2-2.5x the Python app on Linux
(~210-260 MB vs ~90 MB PSS). Linux JavaFX runs through XWayland, so fractional scaling needs a check. Real Windows toasts
with buttons need WinRT interop.

## What still needs a human

1. **Windows tray, menu, balloon click, high-DPI sharpness** (question 2/3). Log in on the VM (the project is already in
   `C:\spike\java`), open PowerShell and run:
   `cd C:\spike\java; .\mvnw.cmd -q compile exec:exec -Dmain=spike.AwtTray "-Dargs=--notify-after-3s"`
   Check: the icon in the notification area (set Windows to 150 % first to check sharpness), the dot toggling every 5 s,
   right-click menu, left click opens a window, and that clicking the balloon logs `TrayIcon ActionEvent`.
2. **Windows keychain** (question 6), same session: `.\mvnw.cmd -q compile exec:exec -Dmain=spike.Secret`
   (expected: `java-keyring backend: WINDOWS_CREDENTIAL_STORE`, `stored and read back: OK, identical`, `deleted again`).
3. **Windows packaged app, real desktop**: `powershell -ExecutionPolicy Bypass -File package.ps1`, then run
   `.\target\dist\car-charge-planner-spike\car-charge-planner-spike.exe` twice (the second launch must bring up the first window),
   and check the working set in Task Manager after a minute.
4. **Linux at 125/150 % scale**: set a fractional scale in KDE display settings, then
   `cd spike/java && ./mvnw -q compile exec:exec -Dmain=spike.Card`, and look at text sharpness and size.
5. **Your login keyring was locked by this spike** (the `secret-service` 3.0.0-alpha test in question 6, before I switched
   to a throwaway keyring). An "Unlock Login Keyring" prompt was left open on the KDE desktop. Unlock it with your password.
   One example entry (fake value) was left behind; remove it with
   `secret-tool clear service car-charge-planner-spike account example-api-token`.
