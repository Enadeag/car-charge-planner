"""The popup behind the tray icon. It closes with the x, Escape, a click on the tray icon, or a click outside it.

A frameless tool window around a `PlanView` (the content). The window behaviour lives here:
the close button, Escape, click-away, and placing the window near the tray icon.
"""

from __future__ import annotations

import time as clock
from datetime import date
from zoneinfo import ZoneInfo

from PySide6.QtCore import QEvent, QPoint, QTimer, Qt, Signal
from PySide6.QtGui import QGuiApplication
from PySide6.QtWidgets import QApplication, QPushButton, QVBoxLayout, QWidget

from planner.model import AwayRule
from storage.state import State

from .i18n import _
from .planview import PLAN, UPDATE, PlanView

__all__ = ["PLAN", "UPDATE", "PlanView", "Popup"]  # PLAN and UPDATE are re-exported for the tray and tests
from .view_model import ViewModel

AWAY_CLOSE_DELAY_MS = 150  # long enough for a dropdown of ours to take over the focus and give it back


class Popup(QWidget):
    battery_submitted = Signal(float)
    away_added = Signal(object)  # AwayPeriod
    away_removed = Signal(object)  # AwayPeriod
    settings_requested = Signal(str)  # key of the Settings tab to open

    def __init__(self, tz: ZoneInfo | None = None) -> None:
        super().__init__(None, Qt.WindowType.Tool | Qt.WindowType.FramelessWindowHint)
        self._was_active = False  # click-away only counts once the popup really had the focus
        self.auto_closed_at = 0.0  # when focus loss last closed it (the tray uses this to avoid a reopen)
        self.last_position: QPoint | None = None  # where it was last placed, for when there is no icon to anchor to
        self.setMinimumWidth(440)
        self.setObjectName("popup")
        self.setStyleSheet("#popup { border: 1px solid palette(mid); background: palette(window); }")

        self.view = PlanView(tz)
        close = QPushButton("\u00d7")
        close.setFlat(True)
        close.setToolTip(_("Close"))
        close.setFixedSize(28, 28)
        close.setStyleSheet("font-size: 16pt;")
        close.clicked.connect(self.hide)
        self.view.add_to_header(close)
        for name in ("battery_submitted", "away_added", "away_removed", "settings_requested"):
            getattr(self.view, name).connect(getattr(self, name))
        self.view.layout_changed.connect(self.adjustSize)

        outer = QVBoxLayout(self)
        outer.setContentsMargins(16, 10, 16, 12)
        outer.addWidget(self.view)
        self.view.fit_to_page()

    # The content is the view's: let code and tests reach its widgets and methods through the popup.
    def __getattr__(self, name: str):
        if name == "view":  # not built yet (during construction)
            raise AttributeError(name)
        return getattr(self.view, name)

    @property
    def tz(self) -> ZoneInfo:
        return self.view.tz

    @tz.setter
    def tz(self, value: ZoneInfo) -> None:
        self.view.tz = value

    def update_view(
        self,
        vm: ViewModel,
        state: State | None = None,
        today: date | None = None,
        rules: tuple[AwayRule, ...] = (),
    ) -> None:
        self.view.update_view(vm, state, today, rules)

    def keyPressEvent(self, event) -> None:
        if event.key() == Qt.Key.Key_Escape:
            self.hide()
        else:
            super().keyPressEvent(event)

    def changeEvent(self, event) -> None:
        """Clicking outside takes the focus away: close, like other tray popups."""
        if event.type() == QEvent.Type.ActivationChange and self.isVisible():
            if self.isActiveWindow():
                self._was_active = True
            elif self._was_active:
                QTimer.singleShot(AWAY_CLOSE_DELAY_MS, self._close_if_inactive)
        super().changeEvent(event)

    def _close_if_inactive(self) -> None:
        if not self.isVisible() or self.isActiveWindow():
            return
        if QApplication.activePopupWidget() is not None:
            return  # one of our own dropdowns is open
        self.auto_closed_at = clock.monotonic()
        self.hide()

    def hideEvent(self, event) -> None:
        self._was_active = False
        super().hideEvent(event)

    def show_near(self, anchor) -> None:
        self.view.tabs.setCurrentIndex(PLAN)  # always open on the plan
        screen = QGuiApplication.screenAt(anchor.center()) or QGuiApplication.primaryScreen()
        area = screen.availableGeometry()
        self.view.fit_to_page()
        if anchor.isValid():
            x = min(max(area.left() + 8, anchor.center().x() - self.width() // 2), area.right() - self.width() - 8)
            top_panel = anchor.center().y() < area.center().y()
            y = area.top() + 8 if top_panel else area.bottom() - self.height() - 8
        elif self.last_position is not None:  # no tray icon to go by (a notification click, a second start)
            x, y = self.last_position.x(), self.last_position.y()
        else:
            x, y = area.right() - self.width() - 8, area.top() + 8  # near the top right, where notifications appear
        self.move(x, y)
        self.last_position = QPoint(x, y)
        self.show()
        self.activateWindow()
