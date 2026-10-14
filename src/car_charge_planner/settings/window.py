"""The Settings window: tabs of fields, Save and Cancel."""

from __future__ import annotations

from collections.abc import Callable

from PySide6.QtCore import Qt
from PySide6.QtGui import QAction, QActionGroup, QFontMetrics, QGuiApplication
from PySide6.QtWidgets import (
    QDialog,
    QHBoxLayout,
    QLabel,
    QMenu,
    QMessageBox,
    QPushButton,
    QTabWidget,
    QToolButton,
    QVBoxLayout,
)

from car_charge_planner import colours
from car_charge_planner.app_settings import AppSettings, DesktopSettings
from car_charge_planner.app_settings import problems as app_problems
from car_charge_planner.config.model import Config
from car_charge_planner.engine import Engine
from car_charge_planner.forecast.secret_store import Secrets
from car_charge_planner.i18n import _
from car_charge_planner.identity import launch_command
from car_charge_planner.platform import StartAtLogin, start_at_login

from .fields import INTERFACE, SOLAR, START_AT_LOGIN, TABS
from .forecast_tab import ForecastTab, storage_note
from .tabs import AwayRulesTab, FieldTab, SolarTab


def ask_discard(parent) -> bool:
    answer = QMessageBox.question(
        parent,
        _("Settings"),
        _("Discard your changes?"),
        QMessageBox.StandardButton.Discard | QMessageBox.StandardButton.Cancel,
        QMessageBox.StandardButton.Cancel,
    )
    return answer == QMessageBox.StandardButton.Discard


class SettingsWindow(QDialog):
    """Not modal: the tray and popup keep working while it is open. It saves the system and planning settings through
    the engine, and the Interface tab through the desktop app's own settings."""

    def __init__(
        self, engine: Engine, app_settings: DesktopSettings, on_setup: Callable[[], None] | None = None, login: StartAtLogin | None = None
    ) -> None:
        super().__init__(None)
        self.setWindowTitle(_("Car charge planner: Settings"))
        self.setModal(False)
        self.setMinimumWidth(660)
        self.engine = engine
        self.app_settings = app_settings
        cfg = self.stored = engine.cfg
        self.stored_app: AppSettings = app_settings.current
        self.on_setup = on_setup
        self.confirm_discard: Callable[[], bool] = lambda: ask_discard(self)
        self.closes_after_save = False
        self.login = login or start_at_login()
        self.stored_login = self._login_state()  # the system knows; the config file does not

        self.tabs = QTabWidget()
        keys = engine.saved_keys()
        self.stored_secrets: Secrets = keys.values
        self.interface_tab = FieldTab(INTERFACE, self.stored_app, switches={START_AT_LOGIN: self.stored_login})
        self.field_tabs: list[FieldTab | SolarTab] = [
            self.interface_tab if spec is INTERFACE else SolarTab(spec, cfg) if spec is SOLAR else FieldTab(spec, cfg) for spec in TABS
        ]
        self.forecast_tab = ForecastTab(engine, self.stored_secrets, lambda: self.current_config(), keys.problem, storage_note(engine.key_storage()))
        self.away_tab = AwayRulesTab(cfg)
        planning = next(i for i, t in enumerate(self.field_tabs) if t.key == "planning")
        self.all_tabs = [*self.field_tabs[: planning + 1], self.forecast_tab, *self.field_tabs[planning + 1 :], self.away_tab]
        self.config_parts = [tab for tab in self.all_tabs if tab is not self.interface_tab]  # what fills the engine's Config
        for tab in self.all_tabs:
            self.tabs.addTab(tab, tab.title)

        self.errors = QLabel()
        self.errors.setWordWrap(True)
        self.errors.setVisible(False)

        self.reset_button = QPushButton(_("Reset tab"))
        self.reset_button.setToolTip(_("Put this tab back to the built-in defaults. Nothing is saved until you press Save."))
        self.reset_button.clicked.connect(self.reset_tab)
        self.cancel_button = QPushButton(_("Close"))  # reads "Cancel" while there are unsaved edits
        self.cancel_button.clicked.connect(self.reject)
        for button in (self.reset_button, self.cancel_button):
            button.setAutoDefault(False)  # Enter runs the save action below, not whichever button is first
        metrics = QFontMetrics(self.cancel_button.font())
        self.cancel_button.setMinimumWidth(max(metrics.horizontalAdvance(t) for t in (_("Cancel"), _("Close"))) + 32)  # no jump when the label flips

        # One split button: the main part runs the current mode, the arrow picks the mode.
        self.save_button = QToolButton()
        self.save_button.setToolButtonStyle(Qt.ToolButtonStyle.ToolButtonTextOnly)
        self.save_button.setPopupMode(QToolButton.ToolButtonPopupMode.MenuButtonPopup)
        menu = QMenu(self.save_button)
        modes = QActionGroup(menu)
        modes.setExclusive(True)
        self.save_action = self._mode_action(menu, modes, _("Save"), _("Save and keep this window open"))
        self.save_close_action = self._mode_action(menu, modes, _("Save and close"), _("Save, then close this window"))
        self.save_button.setMenu(menu)
        modes.triggered.connect(self.set_mode)
        widest = max(QFontMetrics(self.save_button.font()).horizontalAdvance(a.text()) for a in modes.actions())
        self.save_button.setMinimumWidth(widest + 56)  # room for the arrow, so the button does not resize
        self.save_button.clicked.connect(self.run_save)
        self.set_mode(self.save_action)  # every time the window opens: Save, window stays open
        for tab in self.all_tabs:
            tab.changed.connect(self.refresh_close_button)
        self.interface_tab.changed.connect(self._sync_units)
        self._sync_units()  # the car fields show what the Units choice says
        self.setup_button = QPushButton(_("Run set-up again..."))
        self.setup_button.setToolTip(_("Open the set-up wizard, filled in with what is saved now."))
        self.setup_button.setAutoDefault(False)
        self.setup_button.setVisible(on_setup is not None)
        self.setup_button.clicked.connect(self.run_setup)
        buttons = QHBoxLayout()
        buttons.addWidget(self.reset_button)
        buttons.addWidget(self.setup_button)
        buttons.addStretch()
        buttons.addWidget(self.cancel_button)
        buttons.addWidget(self.save_button)

        self.refresh_close_button()
        layout = QVBoxLayout(self)
        layout.addWidget(self.tabs)
        layout.addWidget(self.errors)
        layout.addLayout(buttons)

    def _sync_units(self) -> None:
        """The Units choice is shown at once on every field that has a unit system (no need to save first)."""
        system = self.interface_tab.inputs["interface.units"].currentData()
        for tab in self.field_tabs:
            tab.set_units(system)

    def _mode_action(self, menu: QMenu, group: QActionGroup, text: str, tip: str) -> QAction:
        action = menu.addAction(text)
        action.setCheckable(True)
        action.setToolTip(tip)
        group.addAction(action)
        return action

    def current_config(self) -> Config:
        cfg = self.stored
        for tab in self.config_parts:
            cfg = tab.apply_to(cfg)
        return cfg

    def current_app_settings(self) -> AppSettings:
        return self.interface_tab.apply_to(self.stored_app)

    def current_secrets(self) -> Secrets:
        return self.forecast_tab.collect_secrets()

    def _login_state(self) -> bool:
        try:
            return self.login.is_enabled()
        except OSError:
            return False

    def current_login(self) -> bool:
        return self.interface_tab.collect_switches()[START_AT_LOGIN]

    def is_dirty(self) -> bool:
        return (
            self.current_config() != self.stored
            or self.current_app_settings() != self.stored_app
            or self.current_secrets() != self.stored_secrets
            or self.current_login() != self.stored_login
        )

    def reset_tab(self) -> None:
        tab = self.all_tabs[self.tabs.currentIndex()]
        tab.reset() if hasattr(tab, "reset") else tab.load(Config())
        self.refresh_close_button()

    def run_setup(self) -> None:
        """Close this window (asking first if there is something unsaved) and open the wizard, which starts from what
        is saved."""
        if self.is_dirty() and not self.confirm_discard():
            return
        QDialog.reject(self)  # closed without asking again
        if self.on_setup is not None:
            self.on_setup()

    def refresh_close_button(self) -> None:
        """"Close" when nothing is unsaved, "Cancel" when closing would throw edits away."""
        if self.is_dirty():
            self.cancel_button.setText(_("Cancel"))
            self.cancel_button.setToolTip(_("Discard your unsaved changes and close this window"))
        else:
            self.cancel_button.setText(_("Close"))
            self.cancel_button.setToolTip(_("Close this window"))

    def set_mode(self, action: QAction) -> None:
        """Choose what the save button does. Only switches the mode; nothing is saved yet."""
        action.setChecked(True)
        self.closes_after_save = action is self.save_close_action
        self.save_button.setText(action.text())
        self.save_button.setToolTip(action.toolTip())

    def run_save(self) -> None:
        """What the main part of the save button, and the Enter key, do."""
        if self.save() and self.closes_after_save:
            self.accept()

    def keyPressEvent(self, event) -> None:
        if event.key() in (Qt.Key.Key_Return, Qt.Key.Key_Enter):
            self.run_save()
            return
        super().keyPressEvent(event)

    def save(self) -> bool:
        """Check, then have the engine store and apply its part and the desktop app its own. True when saved; on a
        problem found by the checks nothing is saved and False is returned."""
        cfg, secrets, app = self.current_config(), self.current_secrets(), self.current_app_settings()
        problems = self.engine.check_settings(cfg, secrets) + app_problems(app)
        if problems:
            self.show_errors(problems)
            return False
        if not self._apply_login():
            return False
        problems = self.engine.save_settings(cfg, secrets if secrets != self.stored_secrets else None)
        if problems:
            self.show_errors(problems)
            return False
        cfg = self.stored = self.engine.cfg
        problems = self.app_settings.save(app)
        if problems:  # the engine's part is saved: from here on only the Interface tab differs from what is stored
            for tab in self.config_parts:
                tab.load(cfg)
            self.show_errors(problems)
            self.refresh_close_button()
            return False
        self.stored_app = self.app_settings.current
        self.stored_secrets = secrets
        self.forecast_tab.remember_secrets(secrets)
        self.forecast_tab.show_storage(storage_note(self.engine.key_storage()))  # a keychain that failed just now means the file
        self.errors.setVisible(False)
        self.interface_tab.switches[START_AT_LOGIN] = self.stored_login
        for tab in self.config_parts:
            tab.load(cfg)  # the window stays usable: what is saved is now the baseline for "changed"
        self.interface_tab.load(self.stored_app)
        self.refresh_close_button()
        return True

    def _apply_login(self) -> bool:
        """Make the system match the Start at login box. On a failure the box goes back to what the system has
        and False is returned: nothing is saved, the other edits stay."""
        wanted = self.current_login()
        if wanted == self.stored_login:
            return True
        try:
            if wanted:
                self.login.enable(launch_command())
            else:
                self.login.disable()
        except OSError as err:
            self.interface_tab.set_switch(START_AT_LOGIN, self._login_state())
            self.show_errors([_("Could not change starting at login: {error}").format(error=err)])
            return False
        self.stored_login = wanted
        return True

    def show_errors(self, lines: list[str]) -> None:
        bullets = [f"• {line.lstrip('- ').strip()}" for line in lines if line.strip()]
        self.errors.setStyleSheet(f"color: {colours.error(self)};")
        self.errors.setText("\n".join(bullets))
        self.errors.setVisible(True)

    def reject(self) -> None:
        """Cancel, the close button and Escape all end up here."""
        if self.is_dirty() and not self.confirm_discard():
            return
        super().reject()

    def show_tab(self, key: str) -> None:
        for index, tab in enumerate(self.all_tabs):
            if tab.key == key:
                self.tabs.setCurrentIndex(index)
                return

    def show_centred(self) -> None:
        screen = QGuiApplication.primaryScreen()
        self.adjustSize()
        if screen:
            area = screen.availableGeometry()
            self.move(area.center().x() - self.width() // 2, area.center().y() - self.height() // 2)
        self.show()
        self.raise_()
        self.activateWindow()
