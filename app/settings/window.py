"""The Settings window: tabs of fields, Save and Cancel (settings spec 3.1)."""

from __future__ import annotations

from pathlib import Path
from typing import Callable

from PySide6.QtCore import Qt
from PySide6.QtGui import QGuiApplication
from PySide6.QtWidgets import (
    QDialog,
    QHBoxLayout,
    QLabel,
    QMessageBox,
    QPushButton,
    QTabWidget,
    QVBoxLayout,
)

from app import colours
from planner.config_io import write_config
from planner.model import Config, ConfigError, validate

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
        self.cancel_button = QPushButton("Cancel")
        self.cancel_button.clicked.connect(self.reject)
        self.save_button = QPushButton("Save")
        self.save_button.setDefault(True)
        self.save_button.clicked.connect(self.save)
        buttons = QHBoxLayout()
        buttons.addWidget(self.reset_button)
        buttons.addStretch()
        buttons.addWidget(self.cancel_button)
        buttons.addWidget(self.save_button)

        layout = QVBoxLayout(self)
        layout.addWidget(self.tabs)
        layout.addWidget(self.errors)
        layout.addLayout(buttons)

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

    def save(self) -> None:
        try:
            cfg = validate(self.current_config())
        except ConfigError as err:
            self.show_errors(str(err).split("\n")[1:] or [str(err)])
            return
        except TypeError as err:
            self.show_errors([f"A value has the wrong type: {err}"])
            return
        try:
            write_config(cfg, self.config_file)
        except OSError as err:
            self.show_errors([f"Could not write {self.config_file}: {err}"])
            return
        self.stored = cfg
        self.errors.setVisible(False)
        self.on_save(cfg)
        self.accept()

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

    def show_centred(self) -> None:
        screen = QGuiApplication.primaryScreen()
        self.adjustSize()
        if screen:
            area = screen.availableGeometry()
            self.move(area.center().x() - self.width() // 2, area.center().y() - self.height() // 2)
        self.show()
        self.raise_()
        self.activateWindow()
