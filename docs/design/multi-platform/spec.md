# Windows and macOS: Spec

Status: agreed in a grilling session, 2026-10-07. Vocabulary: `CONTEXT.md` (**start at login**, **supported / beta**). Related: the open-source plan (package rename 04, launch gate 11).

## 1. Goal

The app runs on Windows 10+ and macOS as well as Linux (KDE, GNOME), with installers for the first two. Windows ships with the public launch; macOS follows it. The planner, forecast, view model, popup, Settings and translations are already portable; the work is the places where the app talks to the operating system.

## 2. Decisions

| Topic | Decision |
| --- | --- |
| Support level | **Windows: supported** (the maintainer tests every release). **macOS: beta** until a person has checked a release on it. CI runs the tests on all three systems on every change. |
| Order | Windows **before** the public launch; macOS after it. The package rename (open-source 04) comes first: every installer needs a real package. |
| Notifications | Qt's tray notification (`QSystemTrayIcon.showMessage`, clicks through `messageClicked`) on Windows and macOS; D-Bus stays on Linux. Only macOS moves to a native approach if the click does not arrive in a real test. |
| Installers | **PyInstaller**, then one wrapper per system: **Inno Setup** `.exe` (Windows), `.app` in a `.dmg` (macOS). Built in GitHub Actions. Linux keeps `pipx`. |
| Signing | **Unsigned at first.** The README explains the SmartScreen and Gatekeeper warnings; the build takes signing from CI secrets when they exist. |
| Start at login | A **"Start when I log in"** checkbox in Settings (Interface tab) on all three systems, through the platform layer. The terminal commands stay for Linux and source users. Windows: a per-user `Run` registry key. macOS: a LaunchAgent. Linux: the existing XDG autostart entry. |
| macOS look | **Menu bar only, no Dock icon** (`LSUIElement` in `Info.plist`). |
| CI | A matrix on Linux, Windows and macOS; **all three must pass**. Tests that make sense on one system only carry a marker. Demote macOS to advisory only if its runners prove flaky. |
| Files | **`platformdirs`** for config, state and cache. Linux paths stay exactly as today (a test proves it). |
| Time zone | **`tzlocal`** on all three systems; our own Linux detection (`/etc/timezone`, `/etc/localtime`) is removed. `tzdata` is a Windows-only dependency. |
| Releases | Pushing a version tag makes CI build the Windows installer, the macOS dmg and the Linux wheel and attach them to a **draft** GitHub Release. The maintainer writes the notes and publishes. |
| Not in scope | Flatpak and AppImage; auto-update; code signing and notarisation; a Dock icon in window mode; 32-bit or Windows 7/8; a native notification library (unless the macOS test forces it). |

Floors, from the PySide6 6.11.2 wheels: **Windows 10+ (x64 and arm64)**, **macOS 13+ (universal2)**, Linux with glibc 2.34+ (x64) or 2.39+ (arm64).

## 3. What is Linux-only today

| Area | Where | Becomes |
| --- | --- | --- |
| Paths | XDG variables in `config_file.py`, `launcher.py`, `autostart.py`, `notifier.py`, `forecast/open_meteo.py`, `storage/state.py` | one `app/paths.py` on `platformdirs` |
| Time zone | `planner/model.py` (`find_timezone`, `system_timezone`) | `tzlocal` |
| Single instance | `app/instance.py` (`os.getuid()`, file modes, `XDG_RUNTIME_DIR`) | per-user name from `getpass.getuser()`; modes only where they exist |
| Notifications | `notifier.py`, `notification_actions.py` (D-Bus, `jeepney`) | a backend per system; the tray backend for Windows and macOS |
| Autostart and launcher | `autostart.py`, `launcher.py` (`.desktop` files) | `app/platform/` with Linux, Windows, macOS implementations of "start at login" and "application menu entry" |
| Dependencies | `jeepney` always | `jeepney; sys_platform == "linux"`, `tzdata; sys_platform == "win32"` |

## 4. Design

- **The platform layer** is a small package `app/platform/` (after the rename, `car_charge_planner/platform/`) with one module per system and a function that picks the right one. Callers (Settings, `main`, the CLI) know only the interface: `start_at_login.enable/disable/is_enabled`, `menu_entry.install/uninstall`, `notifier backend`, `paths`.
- **Notifications on Windows and macOS** need the tray icon to exist; both systems always have a tray or menu bar, so the "no tray" fallback is a Linux matter. The notification test command (`test-notification`) works through the tray there.
- **The installers** wrap a PyInstaller bundle. The Windows installer creates the Start menu entry and sets an AppUserModelID so notifications carry the app's name and icon; its uninstaller removes the entry and the `Run` key and leaves the user's data. The macOS bundle sets `LSUIElement` and the bundle id.
- **Compiled translations** (`.mo`) are built before bundling (see I4) and included as package data.
- **Tests**: marks `linux`, `windows`, `macos` skip what does not apply; the platform layer is tested through an injected fake registry, plist directory and environment, so most of it runs everywhere.

## 5. Order of work

```
(open-source 04: package rename) -> M1 CI matrix (informational for Windows and macOS)
M1 -> M2 paths -> M3 time zone and dependencies -> M4 single instance -> M5 notifications
M5 -> M6 start at login and launcher -> M7 tray and popup on Windows and macOS
M6, M7 -> M8 Windows installer -> M9 Windows verification (maintainer) -> [open-source 11 may proceed]
M8 -> M10 release workflow (draft release)
M10 -> M11 macOS bundle and dmg -> M12 macOS verification (a friend, after launch)
M9 -> M13 docs; M12 -> M13 flips macOS from beta
```

## 6. Risks

- The Qt tray notification on macOS may not report clicks, or not show at all from an unsigned app. M12 finds out; the fallback is a native macOS backend (a follow-up ticket).
- Windows may hide the tray icon in the overflow area. The first-run notice and README say where to find it, and the window mode stays available.
- `tzlocal` on Linux must give the same answer as the code it replaces for the common set-ups; a test pins this with fake systems.
- PyInstaller bundles are large (about 100 MB). Accepted.
- Without a Mac, macOS bugs surface late. CI covers the offscreen suite there, and the checklist (M12) covers the rest.
