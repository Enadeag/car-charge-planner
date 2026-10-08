# Split Controller from the tray shell

Status: resolved
Type: task
Blocked by: 01, 03

Behaviour-preserving refactor (spec 4.1, 4.2, 7).

## Acceptance

- `app/controller.py: Controller` owns config, refresh and the late-result guard, planning, grid advice, announcing and pinning, battery and away edits, `apply_config`. No `QSystemTrayIcon`, no popup. It uses `Notifier`.
- `app/shells/tray.py: TrayShell` owns the tray icon, context menu, popup and the tray-click toggle guard, and reacts to the controller's "view changed" signal.
- `main()` builds Controller plus TrayShell; `python -m app` behaves exactly as before on KDE.
- The tests in `test_apply_config.py`, `test_popup_offscreen.py`, `test_settings_*` and the notification tests pass with at most import and construction changes, and they keep their meaning.
- A shell interface (start, show plan, reflect status, menu actions) is written down in `app/shells/__init__.py`.

## Comments

Implemented: `app/controller.py` (`Controller`, `ViewUpdate`), `app/shells/__init__.py` (the `Shell` protocol: `start`, `show_plan`; a shell listens to `controller.view_changed`), `app/shells/tray.py` (`TrayShell`, `make_icon`), `app/main.py` (`main()`); `app/tray.py` is gone and `python -m app` starts through `app/main.py`.

How it is cut:
- The controller owns config, forecast refresh and the late-result guard, planning, grid advice, announcing and pinning, battery and away edits, `apply_config`, and the Settings window (one window for any shell). After every change it emits `view_changed(ViewUpdate(vm, state, today, rules, tz))`.
- `TrayShell` owns the tray icon, menu, popup and the tray-click toggle guard; it connects the popup's signals to controller methods and redraws on `view_changed`.
- The edit handlers are public controller methods now: `set_battery`, `add_away_period`, `remove_away_period`.

Tests: all 296 still pass with only these kinds of change: a test-support class `TrayApp` in `tests/conftest.py` builds the Controller and TrayShell pair as `main()` does and exposes the older attribute names (logic on the controller, drawing on the shell); monkeypatch targets moved from `app.tray.*` to `app.controller.*`; imports of `Popup` and `make_icon`; the three renamed handlers. No assertion was changed.

Not changed on purpose: behaviour on KDE. The tray icon, menu, popup, closing rules, Settings and notifications are as before; `main()` still refuses to start without a system tray (ticket 07 removes that).
