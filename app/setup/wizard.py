"""The set-up wizard window: pages, Back and Next, and one write at the end.

Nothing is saved until Finish: the answers live in a `SetupState`, and Finish checks them, writes the config
once and hands the result to `on_finish` (the controller applies it). Closing earlier asks "Leave set-up?" and
writes nothing. Opening it again starts from the current config, so "Run set-up again" is pre-filled.
"""

from __future__ import annotations

from pathlib import Path
from typing import Callable, Mapping

from PySide6.QtWidgets import QDialog, QHBoxLayout, QLabel, QMessageBox, QPushButton, QStackedWidget, QVBoxLayout

from app import colours
from app.i18n import _
from forecast.check import Attempt, try_provider
from forecast.provider import ForecastProvider
from forecast.registry import ProviderRegistry, registry as builtin
from forecast.secret_store import SecretsError, load_secrets
from planner.config_io import write_config
from planner.model import Config, ConfigError

from .finish import FinishPage
from .pages import Page
from .state import SetupState
from .welcome import WelcomePage

# the pages in the order of the flow; the Location, Solar, Car and Hub pages are added by their own tickets
PAGES: list[type[Page]] = [WelcomePage, FinishPage]


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
    """Not modal, like Settings: the tray and the popup keep working while it is open."""

    def __init__(
        self,
        cfg: Config,
        config_file: Path,
        on_finish: Callable[[Config], None],
        registry: ProviderRegistry | None = None,
        check: Callable[[Config], Attempt] | None = None,
        page_classes: list[type[Page]] | None = None,
        environ: Mapping[str, str] | None = None,
        qt_locale_name: str | None = None,
    ) -> None:
        super().__init__(None)
        self.setWindowTitle(_("Car charge planner: Set-up"))
        self.setModal(False)
        self.setMinimumWidth(560)
        self.setMinimumHeight(420)
        self.config_file = config_file
        self.on_finish = on_finish
        self.registry = registry or builtin
        self.check = check or self._real_check
        self.confirm_leave: Callable[[], bool] = lambda: ask_leave(self)
        self.state = SetupState.starting_from(cfg, environ, qt_locale_name)
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

    # -- what pages ask of the wizard ---------------------------------------------------------------------------

    @property
    def page_ids(self) -> set[str]:
        return set(self.pages)

    def primary_provider(self) -> ForecastProvider | None:
        primary = self.state.config.forecast.primary
        return self.registry.get(primary) if primary in self.registry else None

    def _real_check(self, cfg: Config) -> Attempt:
        provider = self.primary_provider()
        if provider is None:
            return Attempt(False, message=_("The forecast provider {id!r} is not known to this version of the app.").format(id=cfg.forecast.primary))
        try:
            secrets = load_secrets(self.config_file.with_name("secrets.toml"))
        except SecretsError:
            secrets = {}
        return try_provider(provider, cfg, secrets)

    # -- moving about -------------------------------------------------------------------------------------------

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
        if self.current == self.sequence[-1]:
            self.finish()
            return
        following = self.sequence[self.sequence.index(self.current) + 1] if self.current in self.sequence else self.sequence[0]
        self.navigate(following)

    # -- the end ------------------------------------------------------------------------------------------------

    def finish(self) -> bool:
        """Check everything together, write the config once, hand it over. False (and nothing written) on a problem."""
        self.pages[self.current].leave()
        try:
            cfg = self.state.result()
        except ConfigError as err:
            self._show_errors(str(err).split("\n")[1:] or [str(err)])
            return False
        try:
            write_config(cfg, self.config_file)
        except OSError as err:
            self._show_errors([_("Could not write {file}: {error}").format(file=self.config_file, error=err)])
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
