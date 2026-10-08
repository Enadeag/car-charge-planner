# Clicking a notification shows the plan

Status: resolved (the click itself is unverified on a real desktop)
Type: task
Blocked by: 01, 06

Spec 4.5.

## Acceptance

- The default action from the notification calls the active shell's "show plan": window shell raises the window; tray shell opens the popup at its last position, or near the top right of the screen if there is none.
- `ActionInvoked` is handled for notifications this app sent only (matching ids).
- Tests with the fake bus: the action opens the plan in each shell; an unknown id is ignored.

## Comments

Implemented: `app/notification_actions.py` (`ActionListener`, `JeepneySource`, `action_handler`), `Notifier.owns(id)`, placement fallback in `Popup.show_near`, `app/selftest.py` (`python -m app test-notification`), wiring in `app/main.py`. 20 tests in `tests/test_notification_actions.py` (384 in all).

How it works:
- A daemon thread keeps a jeepney connection to the session bus, registers a match rule for `org.freedesktop.Notifications.ActionInvoked` and passes each signal to the Qt side as a queued signal. Receiving uses jeepney, like sending: QtDBus would need a slot declared with the exact `uint` type, and it already got unsigned types wrong when sending.
- The handler acts only on the `default` action of an id this app sent (`Notifier.owns`; ids of other apps' notifications share the same number space and are ignored). It calls the active shell's `show_plan()`: the window shell shows and raises its window; the tray shell opens the popup.
- A notification has no tray icon to anchor to, so the popup now reopens where it was last shown, or near the top right of the screen if it never was. A real anchor (a click on the tray icon) still wins.
- If the bus or library is missing the listener logs once and the app carries on; clicks then do nothing.

Verified for real on a desktop session: the listener registers the exact match rule on the session bus without warnings, a notification sent through the service gets an id, and a real `ActionInvoked` signal on the session bus is received (the test emits one for an id nobody owns).
**Not verified**: an actual click on a notification, which cannot be done from here. Whether KDE and GNOME send `ActionInvoked` with key `default` for a click on the notification body follows the freedesktop specification; `python -m app test-notification` lets the user check it on each desktop (added to ticket 10).
Mutation checks: removing the id-ownership check, the action-key check, the remembered position, or its remembering each makes a test fail.
