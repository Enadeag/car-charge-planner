# Launcher entry and icon

Status: resolved
Type: task
Blocked by: none

Spec 4.6.

## Acceptance

- `app/assets/car-charge-planner.svg`: the bolt on a coloured disc.
- `python -m app install-launcher` writes `~/.local/share/applications/car-charge-planner.desktop` and the icon under `~/.local/share/icons/hicolor/scalable/apps/`; `uninstall-launcher` removes both and reports when nothing was found. Absolute paths to the venv, like the autostart entry.
- The app sets its desktop file name so the window's app id matches the launcher.
- Tests: contents of the written files, install and uninstall, idempotent install, uninstall of nothing.
- Nothing is installed during tests or by this ticket's code on its own; the user runs the command.

## Comments

Implemented: `app/identity.py` (one place for the app name, desktop id and icon name), `app/launcher.py`, `app/assets/car-charge-planner.svg`, the commands `install-launcher` / `uninstall-launcher` in `app/__main__.py`. 12 tests in `tests/test_launcher.py`, including the real commands run against a temp data directory.

Notes:
- The launcher, autostart entry, notification hint and the window's Wayland app id all come from `identity.py`; a test checks they agree. `TrayApp`'s `main()` now sets the desktop file name, so the app id matches the launcher.
- The autostart entry gained the same name, comment and `Icon=`.
- `default_icon()` in the notifier switches from the stock icon to the app's own as soon as the launcher is installed.
- **Not run on a real machine**: nothing was installed. The user runs `python -m app install-launcher` (today `car-charge-planner install-launcher`) when ready.
- Not done: refreshing desktop caches (`update-desktop-database`, icon cache). The shells pick up the new files on their own; if a menu is slow to show the entry, logging out and in will do it.
- Found when the app was restarted on a KDE desktop: `QGuiApplication.setDesktopFileName()` makes Qt register the id with the desktop portal, which looks for a matching `.desktop` file; with no launcher installed it logged "Could not register app ID: App info not found". The app now sets the id only when `launcher.is_installed()`, which is also how it behaved before. After `install-launcher` the id is set at the next start. (The window shell in ticket 06 should rely on `is_installed()` the same way, not assume the entry exists.)
