# GNOME support: Spec

Status: draft, 2026-10-06, requested ("support for GNOME as well, for the notification and the screens including the popup"). Same project, not a second app.
Builds on: `docs/design/planner/spec.md`, `docs/design/popup-redesign/spec.md`, `docs/design/settings-window/spec.md`. Vocabulary: `CONTEXT.md`.

## 1. Purpose

Run the app on GNOME as well as KDE Plasma, with the same planner and the same screens. The development machine runs KDE; GNOME is an additional target.

## 2. Why this is more than a setting

The planner, forecast, state, config, Settings window and autostart are desktop-neutral. Three things are not:

| Piece | KDE today | GNOME |
| --- | --- | --- |
| **Presence** | A tray icon (StatusNotifierItem) | No system tray. Only with the "AppIndicator and KStatusNotifierItem Support" extension (Ubuntu ships it on; vanilla GNOME and Fedora do not). Without it the app today prints "No system tray available" and exits; after this work it falls back to the window shell (section 4.3). |
| **The popup** | A frameless tool window shown near the tray icon, closed by click-away | GNOME on Wayland does not let apps place their own windows, and there is no tray icon to anchor to. The popup would land wherever the compositor puts it. |
| **Notifications** | `QSystemTrayIcon.showMessage`, which depends on a tray | The freedesktop notification service works, but Qt's tray path may show nothing when there is no tray. GNOME also ties a notification to an app through its desktop entry. |

A fourth, softer difference: GNOME users often lack a Qt platform theme for the Adwaita look, so Qt widgets can look out of place (they stay usable, and light/dark should follow the system; to be checked).

## 3. Decisions

| Topic | Decision |
| --- | --- |
| One project or two | One. A separate GTK/libadwaita app would double the UI work; a GNOME Shell extension is a JavaScript codebase that needs changes at GNOME releases. Both stay out of scope (section 8). |
| Mechanism | Split **what the app does** (a controller) from **how it shows itself** (a shell). Two shells: **tray shell** (today) and **window shell** (new). |
| Choosing | Setting `interface.mode`: `auto` (default), `tray` or `window`. `auto` picks the tray shell when a system tray is available, else the window shell. |
| Notifications | Sent straight to the freedesktop notification service over D-Bus, from both shells. The notification names the app by its desktop entry. |
| Screens | The Plan and Update pages become one reusable `PlanView`. The tray shell wraps it in the frameless popup (unchanged look and closing rules); the window shell puts it in a normal window. The Settings window is unchanged. |

### To confirm with the maintainer (assumed here)

1. **Closing the window in window mode hides it and the app keeps running**, so notifications continue. With no tray there is no visible sign that it runs, so a note says so the first time, and the window has an explicit **Quit** (menu and Ctrl+Q). The alternative, "close = quit", would silence notifications.
2. **Starting at login in window mode starts hidden** (`--background`); launching the app again from the launcher shows the running window instead of starting a second copy.
3. The `interface.mode` setting lives on its own small **Interface** tab in the Settings window.

## 4. Behaviour

### 4.1 Controller and shells

- `Controller` holds everything that is not drawing: config, forecast refresh and the late-result guard, planning, grid advice, announcing windows, the battery and away edits, `apply_config`. It exposes a signal "the view changed" carrying the view model, state, today and the weekly rules, and a way to ask for the plan to be shown or Settings opened.
- A **shell** implements: start, show or hide the plan, reflect the status (state colour and tooltip or window title), and offer menu actions (Settings, Refresh, Quit).
- Behaviour on KDE must not change in any visible way (see section 7).

### 4.2 Tray shell

Today's `TrayApp` presentation: tray icon, context menu, the frameless popup, click-away, the tray-click toggle guard. Used on KDE and on GNOME with the tray extension.

### 4.3 Window shell

- A normal top-level window holding `PlanView` (Plan and Update pages), with the window manager's title bar. No x button of its own, no click-away.
- The window title and icon reflect the state (`Charge now`, `Next: Today 11:00-15:00`, ...), so the taskbar and Alt+Tab show it too.
- A menu button in the window: Settings..., Refresh forecast, Quit. These are what the tray's context menu is on KDE.
- The close button hides the window; the app keeps running. Quit exits.
- Opens centred on first show; remembers nothing else.
- Starts hidden with `--background`.

### 4.4 Single instance

A second launch must not start a second app. It tells the running instance to show its plan (tray shell: open the popup; window shell: show and raise the window) and exits. The lock file is replaced by a local socket the first instance listens on.

### 4.5 Notifications

- `Notifier` calls `org.freedesktop.Notifications.Notify` with the app name, an icon, the summary and body, an expiry, and the hint `desktop-entry = car-charge-planner`.
- A default action ("Open") shows the plan when the notification is clicked: the window shell raises the window; the tray shell opens the popup at its last position, or near the screen's top right if it has none.
- If the service is unavailable the app keeps running and logs the failure; it never crashes on a failed notification.
- Both shells use it. `QSystemTrayIcon.showMessage` is no longer used.

### 4.6 Launcher and icon

- An SVG icon (the same bolt on a coloured disc as the tray icon) shipped in the project.
- `python -m app install-launcher` writes `~/.local/share/applications/car-charge-planner.desktop` (name, comment, icon, `StartupWMClass`, category) and the icon under `~/.local/share/icons/hicolor/scalable/apps/`; `uninstall-launcher` removes both. The autostart entry reuses the same name and adds `--background` in window mode.
- The app sets its desktop file name, so the Wayland app id matches the launcher and GNOME ties windows and notifications to it.

### 4.7 Setting

`[interface] mode = "auto" | "tray" | "window"`. In the Settings window: an **Interface** tab with a choice field and a hint. A change applies at the next start (the shell is chosen once at startup); the window says so. If `tray` is chosen but no tray exists, the app falls back to the window shell and says so in a notification or a one-time dialog.

## 5. Architecture

```
app/controller.py       Controller (QObject): everything but drawing
app/notifier.py         Notifier: D-Bus notifications (injectable bus, for tests)
app/planview.py         PlanView: the Plan and Update pages, no window chrome
app/popup.py            Popup: frameless wrapper around PlanView (click-away, x, anchoring)
app/shells/tray.py      TrayShell
app/shells/window.py    WindowShell
app/shells/__init__.py  choose_shell(mode, tray_available)
app/instance.py         single-instance server and client (local socket)
app/assets/             the SVG icon
app/launcher.py         install-launcher / uninstall-launcher
planner/model.py        Config gains `interface.mode`; planner/config_io.py writes it
```

`TrayApp` and `main()` in `app/tray.py` are replaced by the above; `make_icon` moves with the tray shell.

## 6. Testing

- Controller: the existing tray tests (replan on config change, late fetch discarded, announcing, pinned windows) move to the controller and must pass unchanged in meaning.
- Notifier: against a fake bus: the call's arguments, the desktop-entry hint, the default action, a missing service.
- Shell choice: pure function over (mode, tray available).
- Window shell (offscreen): shows the plan, title follows the state, close hides and keeps the app alive, Quit exits, `--background` starts hidden, the menu opens Settings.
- Single instance: a second client reaches the first; no server means the client starts normally.
- Launcher: the desktop file and icon are written and removed; contents checked.
- Config and Settings: the new key round-trips; the Interface tab saves it.

## 7. Compatibility rule

On KDE with `mode = auto`, the app must behave as it does now: same tray icon, same popup, same closing, same Settings entry. Only the notification path changes (D-Bus instead of the tray), and its text stays the same.

## 8. Out of scope

- A GTK4/libadwaita rewrite or a second UI.
- A GNOME Shell extension, or any panel integration beyond what the AppIndicator extension provides.
- X11-only or sway/Hyprland specifics (the window shell should work there, untested).
- Packaging (Flatpak, distro packages).

## 9. Open items and risks

- **Not testable on the development machine**: it runs KDE with no GNOME installed. Everything is covered by logic tests; looks and behaviour on GNOME need a GNOME session (a VM or a live image). Ticket 10 is that check, to be done by someone with a GNOME desktop.
- **Which GNOME**: distribution, version, and whether the tray extension is installed. Unknown.
- **Qt theming on GNOME**: no Adwaita Qt theme means a non-native look. Decide after seeing it.
- **Notification click on the tray shell** cannot place the popup at the tray icon (no icon geometry from a notification), hence the fallback position.

## 10. Supported GNOME versions and requirements

Facts checked on 2026-10-06 (sources at the end of this section).

**Target: GNOME 48, 49, 50 and 51**, that is the current release (51, out 16 September 2026) and the three before it.

| Requirement | Status | Notes |
| --- | --- | --- |
| A Wayland session | **Required** | GNOME 50 removed the X11 session entirely; X11 was already disabled at compile time in 49. The app must start under Wayland. |
| `org.freedesktop.Notifications` | **Required** | GNOME Shell provides it. |
| Python 3.12+, `PySide6-Essentials` and `jeepney` | **Required** | As on KDE. `jeepney` is a small pure-Python D-Bus library used to send notifications (QtDBus cannot build the call from Python: it sends the id as a signed integer, the protocol needs unsigned). |
| The Qt Wayland platform plugin | **Required, included** | Checked: the PySide6 wheel ships `libqwayland.so` and the Wayland shell-integration plugins, and the app runs on the `wayland` platform on the development machine. No distribution package is needed. |
| A launcher entry (`install-launcher`) | **Required for notifications to be tied to the app** | GNOME matches notifications and windows to the desktop entry. |
| **AppIndicator and KStatusNotifierItem Support** extension | **Optional** | Only needed for the tray icon and popup (the tray shell). Without it the window shell is used and everything else works. |

About the extension:
- GNOME has no native tray in 50 or 51. Extension version 66 lists GNOME Shell 45 to 51; version 64 lists 45 to 50.
- Ubuntu ships it enabled. Fedora, Debian and Arch package it as `gnome-shell-extension-appindicator`; elsewhere install it from extensions.gnome.org and enable it in the Extensions app.
- Other tray extensions exist (Status Tray, Trayble); they are not tested or documented here.
- An extension can lag behind a new GNOME release for a while. That is why window mode is the default fallback and not an error.

Not a requirement: an Adwaita Qt theme. Without one the Qt widgets look less native on GNOME but stay usable (ticket 10 records how it looks).

Sources: release calendar https://release.gnome.org/calendar/ ; extension https://extensions.gnome.org/extension/615/appindicator-support/ and https://github.com/ubuntu/gnome-shell-extension-appindicator ; GNOME 50 and X11 https://linuxiac.com/gnome-50-ends-the-x11-era-after-decades/ .
