"""The set-up wizard window: pages, Back and Next, and one write at the end.

Nothing is saved until Finish checks the answers (a `SetupState`), has the engine save them (and the desktop app the
chosen units) and calls `on_finish`. Closing earlier asks "Leave set-up?" and writes nothing. Opening it again starts
from the current config.
"""

from __future__ import annotations

from collections.abc import Callable, Mapping
from dataclasses import replace

from PySide6.QtWidgets import QDialog, QHBoxLayout, QLabel, QMessageBox, QPushButton, QStackedWidget, QVBoxLayout

from car_charge_planner import colours
from car_charge_planner.app_settings import DesktopSettings
from car_charge_planner.config.model import Config, ConfigError
from car_charge_planner.engine import Engine
from car_charge_planner.forecast.check import Attempt
from car_charge_planner.forecast.provider import ForecastProvider
from car_charge_planner.i18n import _

from .away import AwayPage
from .car import CarPage
from .finish import FinishPage
from .house import HousePage
from .hub import HubPage
from .location import LocationPage
from .pages import Page
from .solar import SolarPage
from .state import SetupState
from .welcome import WelcomePage

# the pages in the order of the flow; House and Away are opened from the hub and not part of the sequence
PAGES: list[type[Page]] = [WelcomePage, LocationPage, SolarPage, CarPage, HubPage, HousePage, AwayPage, FinishPage]


def ask_leave(parent) -> bool:
    answer = QMessageBox.question(
        parent,
        _("Set-up"),
        _("Leave set-up? Nothing has been saved yet."),
        QMessageBox.StandardButton.Discard | QMessageBox.StandardButton.Cancel,
        QMessageBox.StandardButton.Cancel,
    )
    return answer == QMessageBox.StandardButton.Discard


class SetupWizard(QDialog):
    def __init__(
        self,
        engine: Engine,
        app_settings: DesktopSettings,
        on_finish: Callable[[Config], None],
        check: Callable[[Config, Callable[[Attempt], None]], None] | None = None,
        page_classes: list[type[Page]] | None = None,
        environ: Mapping[str, str] | None = None,
        qt_locale_name: str | None = None,
    ) -> None:
        super().__init__(None)
        self.setWindowTitle(_("Car charge planner: Set-up"))
        self.setModal(False)
        self.setMinimumWidth(560)
        self.setMinimumHeight(420)
        self.engine = engine
        self.app_settings = app_settings
        self.on_finish = on_finish
        self.registry = engine.registry
        self.check = check or self._real_check
        self.confirm_leave: Callable[[], bool] = lambda: ask_leave(self)
        self.state = SetupState.starting_from(engine.cfg, environ, qt_locale_name, app_settings.current)
        self.finished_ok = False
        self.history: list[str] = []

        self.pages: dict[str, Page] = {}
        self.sequence: list[str] = []
        self.stack = QStackedWidget()
        for cls in page_classes or PAGES:
            page = cls(self)
            self.pages[cls.id] = page
            if cls.in_sequence:
                self.sequence.append(cls.id)
            self.stack.addWidget(page)
            page.changed.connect(self._refresh)

        self.step = QLabel()
        self.step.setStyleSheet(f"{colours.MUTED_STYLE} font-size: 9pt;")
        self.heading = QLabel()
        font = self.heading.font()
        font.setPointSizeF(font.pointSizeF() * 1.4)
        font.setBold(True)
        self.heading.setFont(font)
        self.errors = QLabel()
        self.errors.setWordWrap(True)
        self.errors.setVisible(False)
        self.back_button = QPushButton(_("Back"))
        self.back_button.clicked.connect(self.back)
        self.next_button = QPushButton(_("Next"))
        self.next_button.setDefault(True)
        self.next_button.clicked.connect(self.next)
        buttons = QHBoxLayout()
        buttons.addStretch()
        buttons.addWidget(self.back_button)
        buttons.addWidget(self.next_button)

        layout = QVBoxLayout(self)
        layout.addWidget(self.step)
        layout.addWidget(self.heading)
        layout.addWidget(self.stack, 1)
        layout.addWidget(self.errors)
        layout.addLayout(buttons)
        self.current = ""
        self._show(self.sequence[0])

    @property
    def page_ids(self) -> set[str]:
        return set(self.pages)

    def primary_provider(self) -> ForecastProvider | None:
        primary = self.state.config.forecast.primary
        return self.registry.get(primary) if primary in self.registry else None

    def _real_check(self, cfg: Config, done: Callable[[Attempt], None]) -> None:
        self.engine.check_provider(cfg.forecast.primary, cfg, None, done)  # with the saved keys

    def _show(self, page_id: str) -> None:
        if self.current:
            self.pages[self.current].leave()
        self.current = page_id
        page = self.pages[page_id]
        self.errors.setVisible(False)
        page.enter()
        self.stack.setCurrentWidget(page)
        self.heading.setText(page.title)
        if page_id in self.sequence:
            self.step.setText(_("Step {n} of {total}").format(n=self.sequence.index(page_id) + 1, total=len(self.sequence)))
        else:
            self.step.setText("")
        self._refresh()

    def _refresh(self) -> None:
        page = self.pages[self.current]
        self.back_button.setEnabled(bool(self.history))
        self.next_button.setText(page.next_label() or _("Next"))
        self.next_button.setEnabled(page.can_continue())

    def navigate(self, page_id: str) -> None:
        """Go to a page, remembering where we were so Back returns."""
        if page_id not in self.pages or page_id == self.current:
            return
        self.history.append(self.current)
        self._show(page_id)

    def back(self) -> None:
        if self.history:
            self._show(self.history.pop())

    def next(self) -> None:
        page = self.pages[self.current]
        if not page.can_continue():
            return
        if self.current not in self.sequence:  # a page the hub opened: done, back to where we came from
            self.back()
            return
        if self.current == self.sequence[-1]:
            self.finish()
            return
        following = self.sequence[self.sequence.index(self.current) + 1] if self.current in self.sequence else self.sequence[0]
        self.navigate(following)

    def finish(self) -> bool:
        """Check everything together, have the engine save it once, hand it over. False (and nothing saved) on a problem."""
        self.pages[self.current].leave()
        try:
            cfg = self.state.result()
        except ConfigError as err:
            self._show_errors(str(err).split("\n")[1:] or [str(err)])
            return False
        problems = self.engine.save_settings(cfg) or self.app_settings.save(replace(self.app_settings.current, units=self.state.units))
        if problems:
            self._show_errors(problems)
            return False
        self.finished_ok = True
        self.on_finish(cfg)
        self.accept()
        return True

    def _show_errors(self, lines: list[str]) -> None:
        self.errors.setStyleSheet(f"color: {colours.error(self)};")
        self.errors.setText("\n".join(f"• {line.lstrip('- ').strip()}" for line in lines if line.strip()))
        self.errors.setVisible(True)

    def reject(self) -> None:
        """The close button, Escape: asks first, unless nothing was done. Never writes."""
        if self.finished_ok or not (self.state.touched or self.history):
            super().reject()
            return
        if self.confirm_leave():
            super().reject()
