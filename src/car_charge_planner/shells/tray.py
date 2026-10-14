"""The tray shell: a tray icon with a popup, as used on KDE Plasma (and on GNOME with the AppIndicator extension)."""

from __future__ import annotations

import sys
import time

from PySide6.QtCore import QRect, QSize
from PySide6.QtGui import QCursor
from PySide6.QtWidgets import QApplication, QMenu, QSystemTrayIcon

from ..app_windows import AppWindows
from ..controller import Controller, ViewUpdate
from ..i18n import _
from ..popup import Popup
from .icons import make_icon

CLICK_CLOSE_GRACE_S = 0.5
__all__ = ["CLICK_CLOSE_GRACE_S", "TrayShell", "make_icon"]
class TrayShell:
    def __init__(self, controller: Controller) -> None:
        self.controller = controller
        self.windows = AppWindows(controller, self.show_plan)
        self.popup = Popup(controller.tz)
        self.popup.battery_submitted.connect(controller.set_battery)
        self.popup.away_added.connect(controller.add_away_period)
        self.popup.away_removed.connect(controller.remove_away_period)
        self.popup.settings_requested.connect(self._on_settings_requested)
        self.popup.setup_requested.connect(self._on_setup_requested)

        self.tray = QSystemTrayIcon(make_icon("none"))
        menu = QMenu()
        menu.addAction(_("Refresh forecast"), lambda: controller.refresh(force=True))
        menu.addAction(_("Settings..."), lambda: self.windows.open_settings())
        menu.addAction(_("Set up..."), lambda: self.windows.open_setup())
        menu.addAction(_("About..."), lambda: self.windows.open_about())
        menu.addSeparator()
        menu.addAction(_("Quit"), QApplication.quit)
        self.menu = menu
        self.tray.setContextMenu(menu)
        self.tray.activated.connect(self._on_activated)

        controller.view_changed.connect(self._on_view_changed)

    def start(self, background: bool = False) -> None:
        self.tray.show()  # a tray icon is always in the background: nothing to hide
        self.controller.start()

    def show_plan(self) -> None:
        self.controller.rebuild()
        self.popup.show_near(self.tray.geometry())

    def _click_anchor(self) -> QRect:
        """Where the icon is. Windows reports no geometry for an icon in the overflow flyout, but the click was on it."""
        geometry = self.tray.geometry()
        if geometry.isValid() or sys.platform == "linux":  # on Wayland the pointer position is not known to the app
            return geometry
        return QRect(QCursor.pos(), QSize(1, 1))

    def _on_view_changed(self, update: ViewUpdate) -> None:
        self.popup.tz = update.tz
        self.tray.setIcon(make_icon(update.vm.state))
        self.tray.setToolTip(update.vm.tooltip)
        self.popup.update_view(update.vm, update.state, update.today, update.rules)

    def _on_setup_requested(self) -> None:
        self.popup.hide()
        self.windows.open_setup()

    def _on_settings_requested(self, tab: str) -> None:
        self.popup.hide()
        self.windows.open_settings(tab)

    def _on_activated(self, reason: QSystemTrayIcon.ActivationReason) -> None:
        if reason in (QSystemTrayIcon.ActivationReason.Trigger, QSystemTrayIcon.ActivationReason.MiddleClick):
            if self.popup.isVisible():
                self.popup.hide()
            elif time.monotonic() - self.popup.auto_closed_at < CLICK_CLOSE_GRACE_S:
                pass  # this click is what just closed it (it took the focus): do not reopen
            else:
                self.controller.rebuild()
                self.popup.show_near(self._click_anchor())
