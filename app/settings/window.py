"""The Settings window: tabs of fields, Save and Cancel (settings spec 3.1)."""

from __future__ import annotations

from pathlib import Path
from typing import Callable

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

from app import colours
from planner.config_io import write_config
from planner.model import Config, ConfigError, require_location, validate

from .fields import TABS
from .tabs import AwayRulesTab, FieldTab


def ask_discard(parent) -> bool:
    answer = QMessageBox.question(
        parent,
        "Settings",
        "Discard your changes?",
        QMessageBox.StandardButton.Discard | QMessageBox.StandardButton.Cancel,
        QMessageBox.StandardButton.Cancel,
    )
    return answer == QMessageBox.StandardButton.Discard


class SettingsWindow(QDialog):
    """Not modal: the tray and popup keep working while it is open."""

    def __init__(self, cfg: Config, config_file: Path, on_save: Callable[[Config], None]) -> None:
        super().__init__(None)
        self.setWindowTitle("Car charge planner: Settings")
        self.setModal(False)
        self.setMinimumWidth(660)
        self.stored = cfg
        self.config_file = config_file
        self.on_save = on_save
        self.confirm_discard: Callable[[], bool] = lambda: ask_discard(self)
        self.closes_after_save = False

        self.tabs = QTabWidget()
        self.field_tabs: list[FieldTab] = [FieldTab(spec, cfg) for spec in TABS]
        self.away_tab = AwayRulesTab(cfg)
        self.all_tabs = [*self.field_tabs, self.away_tab]  # every tab has load(cfg) and apply_to(cfg)
        for tab in self.all_tabs:
            self.tabs.addTab(tab, tab.title)

        self.errors = QLabel()
        self.errors.setWordWrap(True)
        self.errors.setVisible(False)

        self.reset_button = QPushButton("Reset tab")
        self.reset_button.setToolTip("Put this tab back to the built-in defaults. Nothing is saved until you press Save.")
        self.reset_button.clicked.connect(self.reset_tab)
        self.cancel_button = QPushButton("Close")  # reads "Cancel" while there are unsaved edits
        self.cancel_button.clicked.connect(self.reject)
        for button in (self.reset_button, self.cancel_button):
            button.setAutoDefault(False)  # Enter runs the save action below, not whichever button is first
        metrics = QFontMetrics(self.cancel_button.font())
        self.cancel_button.setMinimumWidth(max(metrics.horizontalAdvance(t) for t in ("Cancel", "Close")) + 32)  # no jump when the label flips

        # One split button: the main part runs the current mode, the arrow picks the mode.
        self.save_button = QToolButton()
        self.save_button.setToolButtonStyle(Qt.ToolButtonStyle.ToolButtonTextOnly)
        self.save_button.setPopupMode(QToolButton.ToolButtonPopupMode.MenuButtonPopup)
        menu = QMenu(self.save_button)
        modes = QActionGroup(menu)
        modes.setExclusive(True)
        self.save_action = self._mode_action(menu, modes, "Save", "Save and keep this window open")
        self.save_close_action = self._mode_action(menu, modes, "Save and close", "Save, then close this window")
        self.save_button.setMenu(menu)
        modes.triggered.connect(self.set_mode)
        widest = max(QFontMetrics(self.save_button.font()).horizontalAdvance(a.text()) for a in modes.actions())
        self.save_button.setMinimumWidth(widest + 56)  # room for the arrow, so the button does not resize
        self.save_button.clicked.connect(self.run_save)
        self.set_mode(self.save_action)  # every time the window opens: Save, window stays open
        for tab in self.all_tabs:
            tab.changed.connect(self.refresh_close_button)
        buttons = QHBoxLayout()
        buttons.addWidget(self.reset_button)
        buttons.addStretch()
        buttons.addWidget(self.cancel_button)
        buttons.addWidget(self.save_button)

        self.refresh_close_button()
        layout = QVBoxLayout(self)
        layout.addWidget(self.tabs)
        layout.addWidget(self.errors)
        layout.addLayout(buttons)

    def _mode_action(self, menu: QMenu, group: QActionGroup, text: str, tip: str) -> QAction:
        action = menu.addAction(text)
        action.setCheckable(True)
        action.setToolTip(tip)
        group.addAction(action)
        return action

    # -- reading the boxes -------------------------------------------------

    def current_config(self) -> Config:
        cfg = self.stored
        for tab in self.all_tabs:
            cfg = tab.apply_to(cfg)
        return cfg

    def is_dirty(self) -> bool:
        return self.current_config() != self.stored

    # -- actions -----------------------------------------------------------

    def reset_tab(self) -> None:
        self.all_tabs[self.tabs.currentIndex()].load(Config())
        self.refresh_close_button()

    def refresh_close_button(self) -> None:
        """"Close" when nothing is unsaved, "Cancel" when closing would throw edits away."""
        if self.is_dirty():
            self.cancel_button.setText("Cancel")
            self.cancel_button.setToolTip("Discard your unsaved changes and close this window")
        else:
            self.cancel_button.setText("Close")
            self.cancel_button.setToolTip("Close this window")

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
        """Validate, write, apply. True when saved; on any problem nothing is written and False is returned."""
        try:
            cfg = require_location(validate(self.current_config()))
        except ConfigError as err:
            self.show_errors(str(err).split("\n")[1:] or [str(err)])
            return False
        except TypeError as err:
            self.show_errors([f"A value has the wrong type: {err}"])
            return False
        try:
            write_config(cfg, self.config_file)
        except OSError as err:
            self.show_errors([f"Could not write {self.config_file}: {err}"])
            return False
        self.stored = cfg
        self.errors.setVisible(False)
        for tab in self.all_tabs:
            tab.load(cfg)  # the window stays usable: what is saved is now the baseline for "changed"
        self.refresh_close_button()
        self.on_save(cfg)
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

    def show_tab(self, title: str) -> None:
        for index in range(self.tabs.count()):
            if self.tabs.tabText(index) == title:
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
