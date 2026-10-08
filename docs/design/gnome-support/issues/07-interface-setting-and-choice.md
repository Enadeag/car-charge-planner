# Interface setting and shell choice

Status: resolved
Type: task
Blocked by: 06

Spec 4.7.

## Acceptance

- `Config.interface.mode` (`auto` | `tray` | `window`), default `auto`; read, validated and written (`planner/model.py`, `config_io.py`); an unknown value is a readable config error.
- `choose_shell(mode, tray_available)`: `auto` gives tray when a tray exists, else window; `tray` without a tray falls back to window and says so; `window` always gives window.
- The Settings window gets a **Interface** tab with a choice field (a new field kind) and a hint; it states that a change applies at the next start.
- `main()` uses it; the "No system tray available" exit is gone.
- Tests: the matrix of (mode, tray available); config round trip; the tab saves the value.

## Comments

Implemented: `Config.interface.mode` (`planner/model.py`, `config_io.py`; default `auto`; validated; a config without the section means `auto`), `choose_shell` and `ShellChoice` in `app/shells/__init__.py`, `wait_for_tray`, `create_shell` and `announce_tray_fallback` in `app/main.py`, a new field kind `choice` and the **Interface** tab in `app/settings/` (tab order: Solar, House and car, Charger, Planning and notifications, Interface, Away rules). 29 tests in `tests/test_interface.py` (364 in all), including the whole app started as a subprocess on a desktop without a tray.

Behaviour:
- `auto`: tray shell if a system tray exists, else the window shell (silently: nothing asked for is missing). `tray`: tray shell, or the window shell plus a one-time notification ("No system tray found ... change it in Settings, on the Interface tab") if there is no tray. `window`: always a window, and the tray is not even looked for.
- **"No system tray available" no longer ends the app.** It used to exit with code 1.
- Waiting for the tray: at login the panel (KDE) or the tray extension (GNOME) may still be starting, and asking at once would pick the window on a desktop that does have a tray. `wait_for_tray` asks again every 250 ms: up to 5 s when started with `--background` (autostart), 1 s otherwise. A desktop without a tray therefore starts as a window 1 s late (5 s in the background).
- The shell is chosen once at start. Saving a changed interface mode while the app runs sends a notification ("applies the next time the app starts"); the field's hint says so too.

Not changed: KDE with `auto` still gets the tray shell (the tray exists, so no waiting).

Left for ticket 09: the autostart entry does not add `--background` yet.
