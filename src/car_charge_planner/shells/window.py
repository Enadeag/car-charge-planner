"""The window shell: the plan in an ordinary window, for desktops without a tray (stock GNOME).

Closing the window hides it and the app keeps running, so notifications continue; Quit is in the window's menu.
"""

from __future__ import annotations

from PySide6.QtGui import QAction, QGuiApplication, QKeySequence
from PySide6.QtWidgets import QApplication, QMainWindow, QMenu, QToolButton, QVBoxLayout, QWidget

from ..app_windows import AppWindows
from ..controller import Controller
from ..engine import ViewUpdate
from ..i18n import _
from ..identity import APP_NAME
from ..planview import PlanView
from .icons import make_icon

BACKGROUND_HINT = "background"  # the one-time note about closing the window
MIN_WIDTH = 480


class PlanWindow(QMainWindow):
    """Closing hides instead of ending the app."""

    def __init__(self, on_hidden_by_close) -> None:
        super().__init__(None)
        self._on_hidden_by_close = on_hidden_by_close

    def closeEvent(self, event) -> None:
        event.ignore()
        self.hide()
        self._on_hidden_by_close()


class WindowShell:
    def __init__(self, controller: Controller) -> None:
        self.controller = controller
        self.windows = AppWindows(controller, self.show_plan)
        self._placed = False  # centre the window the first time it is shown, then leave it where the owner puts it
        self.window = PlanWindow(self._after_close)
        self.window.setWindowTitle(APP_NAME)
        self.window.setWindowIcon(make_icon("none"))
        self.window.setMinimumWidth(MIN_WIDTH)

        self.view = PlanView(controller.engine.tz)
        self.view.battery_submitted.connect(controller.engine.set_battery)
        self.view.away_added.connect(controller.engine.add_away_period)
        self.view.away_removed.connect(controller.engine.remove_away_period)
        self.view.settings_requested.connect(self.windows.open_settings)
        self.view.setup_requested.connect(self.windows.open_setup)
        self.view.layout_changed.connect(self._fit)

        central = QWidget()
        box = QVBoxLayout(central)
        box.setContentsMargins(16, 10, 16, 12)
        box.addWidget(self.view)
        self.window.setCentralWidget(central)

        # what the tray's context menu is on the tray shell
        self.menu = QMenu(self.window)
        self.settings_action = self.menu.addAction(_("Settings..."))
        self.settings_action.triggered.connect(lambda: self.windows.open_settings())
        self.setup_action = self.menu.addAction(_("Set up..."))
        self.setup_action.triggered.connect(lambda: self.windows.open_setup())
        self.refresh_action = self.menu.addAction(_("Refresh forecast"))
        self.refresh_action.triggered.connect(lambda: controller.engine.refresh(force=True))
        self.about_action = self.menu.addAction(_("About..."))
        self.about_action.triggered.connect(lambda: self.windows.open_about())
        self.menu.addSeparator()
        self.quit_action = QAction(_("Quit"), self.window)
        self.quit_action.setShortcut(QKeySequence.StandardKey.Quit)
        self.quit_action.triggered.connect(lambda: QApplication.quit())
        self.menu.addAction(self.quit_action)
        self.window.addAction(self.quit_action)  # so the shortcut works anywhere in the window

        self.menu_button = QToolButton()
        self.menu_button.setText("☰")
        self.menu_button.setToolTip(_("Menu"))
        self.menu_button.setAutoRaise(True)
        self.menu_button.setFixedSize(28, 28)
        self.menu_button.setPopupMode(QToolButton.ToolButtonPopupMode.InstantPopup)
        self.menu_button.setStyleSheet("QToolButton { font-size: 14pt; } QToolButton::menu-indicator { image: none; }")
        self.menu_button.setMenu(self.menu)
        self.view.add_to_header(self.menu_button)

        controller.view_changed.connect(self._on_view_changed)

    def start(self, background: bool = False) -> None:
        if not background:
            self.show_plan()
        self.controller.start()

    def show_plan(self) -> None:
        self.controller.engine.rebuild()
        if self.window.isMinimized():
            self.window.showNormal()
        self._centre_once()
        self.window.show()
        self.window.raise_()
        self.window.activateWindow()

    def _centre_once(self) -> None:
        """Best effort: Wayland compositors place ordinary windows themselves and ignore this."""
        if self._placed:
            return
        self._placed = True
        screen = QGuiApplication.primaryScreen()
        if screen is None:
            return
        self.window.adjustSize()
        area = screen.availableGeometry()
        self.window.move(area.center().x() - self.window.width() // 2, area.center().y() - self.window.height() // 2)

    def _on_view_changed(self, update: ViewUpdate) -> None:
        self.view.tz = update.tz
        self.view.update_view(update.vm, update.state, update.today, update.rules)
        self.window.setWindowTitle(update.vm.headline)
        self.window.setWindowIcon(make_icon(update.vm.state))

    def _fit(self) -> None:
        self.window.centralWidget().layout().activate()  # pyright: ignore[reportOptionalMemberAccess]  # both exist once built
        self.window.layout().activate()  # pyright: ignore[reportOptionalMemberAccess]
        self.window.adjustSize()

    def _after_close(self) -> None:
        """The first time the window is closed, say that the app keeps running (once, ever)."""
        if self.controller.engine.mark_seen(BACKGROUND_HINT):
            self.controller.notifier.send(
                _("Still running in the background"),
                _(
                    "{app} keeps running so you get notifications. Open it again from the application menu; "
                    "quit it from its menu ({shortcut})."
                ).format(app=APP_NAME, shortcut=self.quit_action.shortcut().toString(QKeySequence.SequenceFormat.NativeText)),
            )
