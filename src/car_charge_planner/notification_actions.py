"""Hear when the owner clicks one of our notifications (ActionInvoked over D-Bus), on a background thread.

The service names the clicked notification by id; only ids this app sent count (`Notifier.owns`).
"""

from __future__ import annotations

import logging
import threading
from collections.abc import Callable
from typing import Protocol

from PySide6.QtCore import QObject, Signal

from .notifier import Notifier

log = logging.getLogger(__name__)

DEFAULT_ACTION = "default"  # the key of the click on the notification itself
RECEIVE_TIMEOUT_S = 0.5  # how often the thread looks at the stop flag


class ActionSource(Protocol):
    def run(self, on_action: Callable[[int, str], None], should_stop: Callable[[], bool]) -> None:
        """Call on_action(notification_id, action_key) for each click until should_stop() is true."""


class JeepneySource:
    def run(self, on_action: Callable[[int, str], None], should_stop: Callable[[], bool]) -> None:
        from jeepney import HeaderFields, MessageType
        from jeepney.bus_messages import MatchRule, message_bus
        from jeepney.io.blocking import open_dbus_connection

        rule = MatchRule(
            type="signal",
            interface="org.freedesktop.Notifications",
            member="ActionInvoked",
            path="/org/freedesktop/Notifications",
        )
        with open_dbus_connection(bus="SESSION") as connection:
            connection.send_and_get_reply(message_bus.AddMatch(rule), timeout=2)
            while not should_stop():
                try:
                    message = connection.receive(timeout=RECEIVE_TIMEOUT_S)
                except TimeoutError:
                    continue
                fields = message.header.fields
                if (
                    message.header.message_type == MessageType.signal
                    and fields.get(HeaderFields.member) == "ActionInvoked"
                    and len(message.body) == 2
                ):
                    notification_id, key = message.body
                    on_action(int(notification_id), str(key))


class ActionListener(QObject):
    action_invoked = Signal(int, str)  # notification id, action key
    _received = Signal(int, str)  # emitted on the listening thread, queued onto the Qt thread

    def __init__(self, source: ActionSource | None = None) -> None:
        super().__init__()
        self.source = source or JeepneySource()
        self._stop = threading.Event()
        self._thread: threading.Thread | None = None
        self._received.connect(self.action_invoked)

    @property
    def running(self) -> bool:
        return self._thread is not None and self._thread.is_alive()

    def start(self) -> None:
        if self.running:
            return
        self._stop.clear()
        self._thread = threading.Thread(target=self._run, name="notification-actions", daemon=True)
        self._thread.start()

    def stop(self) -> None:
        self._stop.set()
        if self._thread is not None:
            self._thread.join(timeout=RECEIVE_TIMEOUT_S * 4)

    def _run(self) -> None:
        try:
            self.source.run(self._received.emit, self._stop.is_set)
        except Exception as err:  # no bus, no library: clicks then do nothing, the app carries on
            log.warning("Not listening for notification clicks: %s", err)


def action_handler(notifier: Notifier, show_plan: Callable[[], None]) -> Callable[[int, str], None]:
    """What a click does: show the plan, but only for the default action of a notification we sent."""

    def handle(notification_id: int, key: str) -> None:
        if key == DEFAULT_ACTION and notifier.owns(notification_id):
            show_plan()

    return handle
