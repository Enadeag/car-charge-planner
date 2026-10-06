"""The tray shell: a tray icon with a popup, as used on KDE Plasma (and on GNOME with the
AppIndicator extension)."""

from __future__ import annotations

import time

from PySide6.QtCore import QPointF, Qt
from PySide6.QtGui import QColor, QIcon, QPainter, QPixmap, QPolygonF
from PySide6.QtWidgets import QApplication, QMenu, QSystemTrayIcon


from ..controller import Controller, ViewUpdate
from ..popup import Popup

CLICK_CLOSE_GRACE_S = 0.5
COLORS = {"active": "#2e9e5b", "upcoming": "#3b82c4", "none": "#7a7f87", "grid": "#d98c1f", "no-forecast": "#c4553b"}


def make_icon(state: str) -> QIcon:
    pix = QPixmap(64, 64)
    pix.fill(Qt.GlobalColor.transparent)
    p = QPainter(pix)
    p.setRenderHint(QPainter.RenderHint.Antialiasing)
    p.setBrush(QColor(COLORS[state]))
    p.setPen(Qt.PenStyle.NoPen)
    p.drawEllipse(4, 4, 56, 56)
    p.setBrush(QColor("white"))
    bolt = [(36, 10), (18, 36), (30, 36), (26, 54), (46, 26), (34, 26)]
    p.drawPolygon(QPolygonF([QPointF(x, y) for x, y in bolt]))
    p.end()
    return QIcon(pix)


class TrayShell:
    def __init__(self, controller: Controller) -> None:
        self.controller = controller
        self.popup = Popup(controller.tz)
        self.popup.battery_submitted.connect(controller.set_battery)
        self.popup.away_added.connect(controller.add_away_period)
        self.popup.away_removed.connect(controller.remove_away_period)
        self.popup.settings_requested.connect(self._on_settings_requested)

        self.tray = QSystemTrayIcon(make_icon("none"))
        menu = QMenu()
        menu.addAction("Refresh forecast", lambda: controller.refresh(force=True))
        menu.addAction("Settings...", lambda: controller.open_settings())
        menu.addSeparator()
        menu.addAction("Quit", QApplication.quit)
        self.menu = menu
        self.tray.setContextMenu(menu)
        self.tray.activated.connect(self._on_activated)

        controller.view_changed.connect(self._on_view_changed)

    def start(self) -> None:
        self.tray.show()
        self.controller.start()

    def show_plan(self) -> None:
        self.controller.rebuild()
        self.popup.show_near(self.tray.geometry())

    def _on_view_changed(self, update: ViewUpdate) -> None:
        self.popup.tz = update.tz
        self.tray.setIcon(make_icon(update.vm.state))
        self.tray.setToolTip(update.vm.tooltip)
        self.popup.update_view(update.vm, update.state, update.today, update.rules)

    def _on_settings_requested(self, tab: str) -> None:
        self.popup.hide()
        self.controller.open_settings(tab)

    def _on_activated(self, reason: QSystemTrayIcon.ActivationReason) -> None:
        if reason in (QSystemTrayIcon.ActivationReason.Trigger, QSystemTrayIcon.ActivationReason.MiddleClick):
            if self.popup.isVisible():
                self.popup.hide()
            elif time.monotonic() - self.popup.auto_closed_at < CLICK_CLOSE_GRACE_S:
                pass  # this click is what just closed it (it took the focus): do not reopen
            else:
                self.show_plan()
