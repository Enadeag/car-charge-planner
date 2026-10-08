"""The Away page: the weekly times the car is not at home."""

from __future__ import annotations

from dataclasses import replace
from datetime import time

from PySide6.QtWidgets import QButtonGroup, QLabel, QPushButton, QRadioButton, QVBoxLayout, QHBoxLayout, QWidget

from app import colours
from app.i18n import _
from app.settings.tabs import RuleRow
from planner.model import AwayRule

from .home import away_problems
from .pages import Page
from .solar import note

NEW_RULE = AwayRule(0, time(8, 0), time(17, 0))  # a Monday at work: a place to start from


class AwayPage(Page):
    id = "away"
    in_sequence = False  # opened from the hub, left with Done

    @property
    def title(self) -> str:
        return _("When is the car away?")

    def __init__(self, wizard) -> None:
        super().__init__(wizard)
        self.rows: list[RuleRow] = []
        layout = QVBoxLayout(self)
        layout.addWidget(note(_("No charging is planned while the car is out, for example at work. Without this the car is treated as home all day, every day.")))

        self.at_home = QRadioButton(_("The car is usually home"))
        self.some_days = QRadioButton(_("The car is away at set times each week"))
        group = QButtonGroup(self)
        group.addButton(self.at_home)
        group.addButton(self.some_days)
        layout.addWidget(self.at_home)
        layout.addWidget(self.some_days)

        self.rules_box = QWidget()
        rules = QVBoxLayout(self.rules_box)
        rules.setContentsMargins(24, 0, 0, 0)
        self.rows_layout = QVBoxLayout()
        rules.addLayout(self.rows_layout)
        row = QHBoxLayout()
        self.add_button = QPushButton(_("Add a time"))
        self.add_button.setAutoDefault(False)
        row.addWidget(self.add_button)
        row.addStretch()
        rules.addLayout(row)
        rules.addWidget(note(_("One time covers one weekday: add several for several days. Each runs from a start to an end on the same day.")))
        layout.addWidget(self.rules_box)

        self.errors = QLabel()
        self.errors.setWordWrap(True)
        self.errors.setStyleSheet(f"color: {colours.error(self)};")
        layout.addWidget(self.errors)
        layout.addStretch()

        self.at_home.clicked.connect(self._chose_home)
        self.some_days.clicked.connect(self._chose_some_days)
        self.add_button.clicked.connect(lambda: self._add(NEW_RULE))

    def next_label(self) -> str:
        return _("Done")

    # -- showing what the answers say ------------------------------------------------------------------------------

    def enter(self) -> None:
        rules = self.state.config.away_rules
        self._rebuild(rules)
        if rules:
            self.some_days.setChecked(True)
        else:
            group = self.at_home.group()
            group.setExclusive(False)
            self.at_home.setChecked("away" in self.state.done)  # "usually home" is only an answer once it was chosen
            self.some_days.setChecked(False)
            group.setExclusive(True)
        self.rules_box.setVisible(self.some_days.isChecked())
        self._refresh()

    def _rebuild(self, rules) -> None:
        for row in self.rows:
            self.rows_layout.removeWidget(row)
            row.hide()
            row.deleteLater()
        self.rows = []
        for rule in rules:
            self._row(rule)

    def _row(self, rule: AwayRule) -> RuleRow:
        row = RuleRow(rule, self._remove, self._edited)
        self.rows.append(row)
        self.rows_layout.addWidget(row)
        return row

    # -- what the person does -----------------------------------------------------------------------------------------

    def _commit(self) -> None:
        cfg = self.state.config
        self.state.config = replace(cfg, away_rules=tuple(row.rule() for row in self.rows))
        self._refresh()
        self.changed.emit()

    def _chose_home(self) -> None:
        self._rebuild(())
        self.rules_box.setVisible(False)
        self.state.done.add("away")
        self._commit()

    def _chose_some_days(self) -> None:
        self.rules_box.setVisible(True)
        if not self.rows:
            self._add(NEW_RULE)

    def _add(self, rule: AwayRule) -> None:
        self._row(rule)
        self.some_days.setChecked(True)
        self.rules_box.setVisible(True)
        self.state.done.add("away")
        self._commit()

    def _remove(self, row: RuleRow) -> None:
        self.rows.remove(row)
        self.rows_layout.removeWidget(row)
        row.hide()
        row.deleteLater()
        self._commit()

    def _edited(self) -> None:
        self.state.done.add("away")
        self._commit()

    def _refresh(self) -> None:
        self.errors.setText("\n".join(f"• {line}" for line in away_problems(self.state.config)))

    def can_continue(self) -> bool:
        return not away_problems(self.state.config)
