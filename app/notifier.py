"""Desktop notifications over D-Bus (org.freedesktop.Notifications), the same on KDE and GNOME.

Qt's tray-icon messages need a tray, and GNOME has none by default. The notification service
itself is available on both, so the app talks to it directly. QtDBus cannot make this call from
Python (it sends the replaces-id as a signed integer, the protocol wants unsigned), hence the
small pure-Python `jeepney` library.
"""

from __future__ import annotations

import logging
import os
from pathlib import Path
from typing import Protocol

log = logging.getLogger(__name__)

from .identity import APP_NAME, DESKTOP_ENTRY, ICON_NAME  # noqa: F401  (DESKTOP_ENTRY ties a notification to the launcher entry)

FALLBACK_ICON = "dialog-information"
NOTIFY_SIGNATURE = "susssasa{sv}i"
DEFAULT_ACTION = ("default", "Open")
REMEMBER = 50  # notification ids kept for matching clicks later


class Bus(Protocol):
    def notify(
        self,
        app_name: str,
        replaces_id: int,
        icon: str,
        summary: str,
        body: str,
        actions: list[str],
        hints: dict[str, tuple[str, object]],
        expire_ms: int,
    ) -> int:
        """Call Notify and return the new notification's id."""


class NotifyError(Exception):
    """The notification service answered with an error."""


class JeepneyBus:
    """The real session bus. Opens a short connection per call: a few notifications a day."""

    TIMEOUT_S = 2

    @staticmethod
    def build_message(app_name, replaces_id, icon, summary, body, actions, hints, expire_ms):
        from jeepney import DBusAddress, new_method_call

        address = DBusAddress(
            "/org/freedesktop/Notifications",
            bus_name="org.freedesktop.Notifications",
            interface="org.freedesktop.Notifications",
        )
        return new_method_call(
            address, "Notify", NOTIFY_SIGNATURE, (app_name, replaces_id, icon, summary, body, actions, hints, expire_ms)
        )

    def notify(self, app_name, replaces_id, icon, summary, body, actions, hints, expire_ms) -> int:
        from jeepney import MessageType
        from jeepney.io.blocking import open_dbus_connection

        message = self.build_message(app_name, replaces_id, icon, summary, body, actions, hints, expire_ms)
        with open_dbus_connection(bus="SESSION") as connection:
            reply = connection.send_and_get_reply(message, timeout=self.TIMEOUT_S)
        if reply.header.message_type == MessageType.error:
            raise NotifyError(str(reply.body))
        return int(reply.body[0])


def default_icon() -> str:
    """The app's icon once the launcher is installed, a stock icon until then."""
    data_home = Path(os.environ.get("XDG_DATA_HOME", Path.home() / ".local" / "share"))
    installed = data_home / "icons" / "hicolor" / "scalable" / "apps" / f"{ICON_NAME}.svg"
    return ICON_NAME if installed.exists() else FALLBACK_ICON


class Notifier:
    def __init__(self, bus: Bus | None = None, icon: str | None = None) -> None:
        self.bus: Bus = bus or JeepneyBus()
        self.icon = icon if icon is not None else default_icon()
        self.sent_ids: list[int] = []
        self._warned = False

    def send(self, title: str, body: str, expire_ms: int = 20_000) -> int | None:
        """Show a notification. Returns its id, or None when it could not be shown; never raises."""
        try:
            notification_id = self.bus.notify(
                APP_NAME,
                0,
                self.icon,
                title,
                body,
                list(DEFAULT_ACTION),
                {"desktop-entry": ("s", DESKTOP_ENTRY)},
                expire_ms,
            )
        except Exception as err:  # no bus, no service, no library: the app must carry on
            if not self._warned:  # once, so a missing service does not fill the log every minute
                log.warning("Could not show a notification: %s", err)
                self._warned = True
            return None
        self.sent_ids = [*self.sent_ids, notification_id][-REMEMBER:]
        return notification_id
