# Notifications through the tray icon

Status: ready-for-agent
Type: task
Blocked by: M2

Spec section 2 (Notifications), section 6 (macOS risk).

## Acceptance

- A notifier backend built on `QSystemTrayIcon.showMessage` for Windows and macOS, with the same interface as the D-Bus notifier (`send(title, body, expire_ms)`, `owns(id)`); a click (`messageClicked`) calls the same "show the popup" handler as today.
- The right backend is chosen by system; `jeepney` is not imported on Windows and macOS.
- `test-notification` works through the tray on Windows and macOS: it shows a notification and waits for a click, exit code as before.
- With no usable tray icon (should not happen on these systems) the notifier says so once and the app keeps running.
- Unit tests with a fake tray icon: send, click, click of someone else's message ignored, no tray.
- A note in the ticket comments for M9 and M12: what to look at (appearance, name and icon on the notification, whether the click opens the popup).

## Comments
