"""Desktop notifications through the tray icon (`QSystemTrayIcon.showMessage`), for Windows and macOS."""

from __future__ import annotations

import logging
from collections.abc import Callable

from PySide6.QtCore import QObject, Signal
from PySide6.QtWidgets import QSystemTrayIcon

log = logging.getLogger(__name__)

DEFAULT_ACTION = "default"


class TrayNotifier(QObject):
    """Same interface as the D-Bus `Notifier`, and its own click listener: it emits `action_invoked` itself.

    `messageClicked` names no message, so a click counts for the latest message sent and for that one only.
    """

    action_invoked = Signal(int, str)  # notification id, action key

    def __init__(self, available: Callable[[], bool] = QSystemTrayIcon.isSystemTrayAvailable) -> None:
        super().__init__()
        self._available = available
        self._tray: QSystemTrayIcon | None = None
        self._last_id = 0
        self._outstanding = False
        self._warned = False

    def use_tray(self, tray: QSystemTrayIcon) -> None:
        self._tray = tray
        tray.messageClicked.connect(self._on_clicked)

    def owns(self, notification_id: int) -> bool:
        return self._outstanding and notification_id == self._last_id

    def send(self, title: str, body: str, expire_ms: int = 20_000) -> int | None:
        """Show a notification. Returns its id, or None when there is no tray to show it in; never raises."""
        tray = self._tray
        if tray is None or not self._available():
            if not self._warned:  # once, so a missing tray does not fill the log every minute
                log.warning("Could not show a notification: there is no tray icon")
                self._warned = True
            return None
        self._last_id += 1
        self._outstanding = True
        tray.showMessage(title, body, tray.icon(), expire_ms)
        return self._last_id

    def start(self) -> None:
        """Nothing to start: clicks arrive on the Qt thread."""

    def stop(self) -> None:
        """Nothing to stop."""

    def _on_clicked(self) -> None:
        if self._outstanding:
            self.action_invoked.emit(self._last_id, DEFAULT_ACTION)  # handlers ask owns(): still outstanding here
            self._outstanding = False
