"""`car-charge-planner test-notification`: does a click on a notification reach the app on this desktop?"""

from __future__ import annotations

from collections.abc import Callable

from PySide6.QtCore import QCoreApplication, QEventLoop, QTimer
from PySide6.QtWidgets import QApplication, QSystemTrayIcon

from .i18n import _
from .notification_actions import DEFAULT_ACTION, ActionListener, click_listener
from .notifier import Notifying, make_notifier
from .shells.icons import make_icon
from .tray_notifier import TrayNotifier

TIMEOUT_S = 60


def run(
    timeout_s: float = TIMEOUT_S,
    notifier: Notifying | None = None,
    listener: ActionListener | TrayNotifier | None = None,
    out: Callable[[str], None] = print,
) -> int:
    """Send a test notification and wait for a click on it. Exit code 0 when the click arrived."""
    notifier = notifier or make_notifier()
    # a tray icon needs a QApplication; it must be shown for its balloon to appear, and stay alive while we wait
    tray = None
    if isinstance(notifier, TrayNotifier):
        app = QApplication.instance() or QApplication([])
        tray = QSystemTrayIcon(make_icon("none"))
        tray.show()
        notifier.use_tray(tray)
    else:
        app = QCoreApplication.instance() or QCoreApplication([])  # the event loop needs one
    listener = listener or click_listener(notifier)

    notification_id = notifier.send(
        _("Car charge planner: test notification"),
        _("Click this notification to check that clicks reach the app. (This is only a test.)"),
        expire_ms=int(timeout_s * 1000),
    )
    if notification_id is None:
        out(_("The notification could not be sent: is a notification service running on this session?"))
        return 1
    out(_("Test notification sent (id {id}). Click it within {seconds} seconds.").format(id=notification_id, seconds=f"{timeout_s:g}"))

    loop = QEventLoop()
    clicked = []

    def on_action(clicked_id: int, key: str) -> None:
        if clicked_id == notification_id and key == DEFAULT_ACTION:
            clicked.append(1)
            loop.quit()

    listener.action_invoked.connect(on_action)
    listener.start()
    QTimer.singleShot(int(timeout_s * 1000), loop.quit)
    try:
        loop.exec()
    finally:
        listener.stop()
        app.processEvents()  # deliver anything still queued
        if tray is not None:
            tray.hide()

    if clicked:
        out(_("Click received: clicking a notification works on this desktop."))
        return 0
    out(
        _(
            "No click received. If you clicked it, this desktop may not report clicks to the app; "
            "if you did not, run the command again and click it."
        )
    )
    return 1
