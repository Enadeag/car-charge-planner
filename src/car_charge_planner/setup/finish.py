"""The last page: what was answered, grouped, with a way back to each group, and one test of the forecast."""

from __future__ import annotations

import threading
from html import escape

from PySide6.QtCore import QObject, Signal
from PySide6.QtWidgets import QFrame, QGridLayout, QHBoxLayout, QLabel, QPushButton, QScrollArea, QVBoxLayout, QWidget

from car_charge_planner import colours
from car_charge_planner.i18n import _, ngettext
from car_charge_planner.view_model import typical_line

from .pages import Page
from .recap import build_recap


class _Done(QObject):
    finished = Signal(bool, str)  # worked, message


class FinishPage(Page):
    id = "finish"

    @property
    def title(self) -> str:
        return _("Finish")

    def __init__(self, wizard) -> None:
        super().__init__(wizard)
        self._done = _Done()
        self._done.finished.connect(self._show_check)
        self._threads: list[threading.Thread] = []
        outer = QVBoxLayout(self)
        outer.setContentsMargins(0, 0, 0, 0)

        self.content = QWidget()
        self.content.setAutoFillBackground(False)
        self.recap = QVBoxLayout(self.content)
        scroll = QScrollArea()
        scroll.setWidgetResizable(True)
        scroll.setFrameShape(QFrame.Shape.NoFrame)
        scroll.viewport().setAutoFillBackground(False)
        scroll.setWidget(self.content)
        outer.addWidget(scroll, 1)

        row = QHBoxLayout()
        self.check_status = QLabel()
        self.check_status.setWordWrap(True)
        self.check_button = QPushButton(_("Check again"))
        self.check_button.setAutoDefault(False)
        self.check_button.clicked.connect(self.run_check)
        row.addWidget(self.check_status, 1)
        row.addWidget(self.check_button)
        outer.addLayout(row)

    def enter(self) -> None:
        self._clear(self.recap)
        link = colours.link(self)
        grid = QGridLayout()  # one grid for every group, so the values line up from group to group
        grid.setHorizontalSpacing(14)
        grid.setVerticalSpacing(3)
        row = 0
        for group in build_recap(self.state):
            if row:
                grid.setRowMinimumHeight(row, 8)
                row += 1
            title = QLabel(group.title)
            title.setStyleSheet("font-weight: bold;")
            grid.addWidget(title, row, 0, 1, 2)
            if group.page in self.wizard.page_ids:
                change = QLabel(f"<style>a {{ color: {link}; }}</style><a href='{group.page}'>{escape(_('Change'))}</a>")
                change.linkActivated.connect(self.wizard.navigate)
                grid.addWidget(change, row, 2)
            row += 1
            for line in group.lines:
                label = QLabel(line.label)
                label.setStyleSheet(colours.MUTED_STYLE)
                value = QLabel(_("{value} (typical value)").format(value=line.value) if line.typical else line.value)
                value.setWordWrap(True)
                grid.addWidget(label, row, 0)
                grid.addWidget(value, row, 1, 1, 2)
                row += 1
        grid.setColumnStretch(1, 1)
        grid.setColumnMinimumWidth(0, 130)
        self.recap.addLayout(grid)
        text, _tab = typical_line(self.state.config.setup.typical)
        if text:
            note = QLabel(text)
            note.setWordWrap(True)
            note.setStyleSheet(f"{colours.MUTED_STYLE} font-size: 9pt;")
            self.recap.addWidget(note)
        self.recap.addStretch()
        self.check_button.setEnabled(self.state.has_location)
        if self.state.has_location:
            self.run_check()
        else:
            self.check_status.setStyleSheet(f"color: {colours.error(self)};")
            self.check_status.setText(_("Choose a place first: the plan needs to know where the panels are."))

    @staticmethod
    def _clear(layout) -> None:
        while layout.count():
            item = layout.takeAt(0)
            if item.widget() is not None:
                item.widget().hide()
                item.widget().deleteLater()
            elif item.layout() is not None:
                FinishPage._clear(item.layout())

    def can_continue(self) -> bool:
        return self.state.has_location  # a failed check does not stop Finish

    def next_label(self) -> str:
        return _("Finish")

    def run_check(self) -> None:
        """One test fetch of the forecast with the chosen place, on a worker thread. A failure only says so."""
        provider = self.wizard.primary_provider()
        name = provider.name if provider else _("the forecast service")
        self.check_button.setEnabled(False)
        self.check_status.setStyleSheet(colours.MUTED_STYLE)
        self.check_status.setText(_("Asking {provider} for a forecast...").format(provider=name))
        cfg = self.state.config
        place = self.state.place_label or f"{cfg.latitude:.2f}, {cfg.longitude:.2f}"

        def work() -> None:
            attempt = self.wizard.check(cfg)
            if attempt.worked:
                text = ngettext("Forecast received for {place}: {n} day.", "Forecast received for {place}: {n} days.", attempt.days)
                self._done.finished.emit(True, text.format(place=place, n=attempt.days))
            else:
                self._done.finished.emit(False, _("{reason} You can still finish; the app tries again by itself.").format(reason=attempt.message))

        thread = threading.Thread(target=work, daemon=True)
        self._threads.append(thread)
        thread.start()

    def _show_check(self, worked: bool, message: str) -> None:
        self.check_status.setStyleSheet(f"color: {colours.success(self) if worked else colours.warning(self)};")
        self.check_status.setText(message)
        self.check_button.setEnabled(self.state.has_location)
