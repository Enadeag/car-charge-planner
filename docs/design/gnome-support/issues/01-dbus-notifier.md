# Send notifications over D-Bus

Status: resolved
Type: task
Blocked by: none

`app/notifier.py` (spec 4.5). Helps KDE too: no more dependence on the tray for notifications.

## Acceptance

- `Notifier.send(title, body, expire_ms=20000)` calls `org.freedesktop.Notifications.Notify` with app name, icon name, summary, body, a default "Open" action and the hint `desktop-entry = car-charge-planner`.
- The bus is injectable; tests use a fake and check the arguments, the hint and the action.
- A missing service or a failed call is logged and never raises into the app.
- The tray code uses it instead of `QSystemTrayIcon.showMessage`; notification texts unchanged.
- Verified once on a KDE desktop by watching D-Bus, as was done for the first notification.

## Comments

Implemented in `app/notifier.py` (`Notifier`, `JeepneyBus`, `NotifyError`, `default_icon`), used by `TrayApp` in place of `QSystemTrayIcon.showMessage`. 13 tests in `tests/test_notifier.py`; the older notification tests use a `FakeNotifier` from `tests/conftest.py`.

Findings:
- **QtDBus cannot make this call from Python.** `Notify` takes an unsigned replaces-id; PySide6 sends every Python int as a signed `INT32` (also through `QDBusInterface.callWithArgumentList`, and `QDBusInterface.call` does not accept this many arguments), and the service answers "No such method ... signature 'sisssasa{sv}i'". `QDBusArgument` has no way to append a typed value from Python. Hence the new dependency **`jeepney`** (pure Python, no compiling); added to `pyproject.toml`, the README setup line and the GNOME requirements table.
- The `desktop-entry` hint is sent as a variant of type `s`; a `QDBusVariant` passed through QtDBus came out double-wrapped.
- A notification is one short blocking call (2 s timeout) on the UI thread, once per announced window; a missing service fails immediately and is logged once.
- Checked on the real desktop: the service receives the app name, `UINT32 0`, the icon, summary, body, the `default`/`Open` action, the `desktop-entry` hint and the expiry, and returns a notification id.
- The icon is `car-charge-planner` once ticket 02 has installed it, `dialog-information` before that.
