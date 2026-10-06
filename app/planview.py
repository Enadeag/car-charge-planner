"""The plan and the daily inputs, without any window behaviour.

Two pages so each stays calm: **Plan** answers "when do I charge?" and nothing else;
**Update** holds the daily inputs (battery level, one-off away days).
Weekly away rules are settings: shown here read-only, edited in the Settings window.

`PlanView` is a plain widget. The tray popup (`popup.py`) puts it in a frameless window with a
close button and click-away; another shell can put it in an ordinary window.
"""

from __future__ import annotations

from datetime import date, datetime, time, timedelta
from zoneinfo import ZoneInfo

from PySide6.QtCore import QTime, Qt, Signal
from PySide6.QtWidgets import (
    QComboBox,
    QDoubleSpinBox,
    QGridLayout,
    QHBoxLayout,
    QLabel,
    QProgressBar,
    QPushButton,
    QStackedWidget,
    QTabBar,
    QTimeEdit,
    QVBoxLayout,
    QWidget,
)

from planner.model import AwayPeriod, AwayRule
from storage.state import State

from . import colours
from .away_input import away_summary, describe, describe_rule, make_away
from .view_model import ATTRIBUTION, ViewModel, day_label

PLAN, UPDATE = 0, 1
SMALL = "font-size: 9pt;"


def _muted(text: str = "", small: bool = True) -> QLabel:
    label = QLabel(text)
    label.setWordWrap(True)
    label.setStyleSheet(f"{colours.MUTED_STYLE} {SMALL if small else ''}")
    return label


def _heading(text: str) -> QLabel:
    label = QLabel(text)
    label.setStyleSheet(f"{colours.MUTED_STYLE} {SMALL} font-weight: bold;")
    return label


def _discard(widget) -> None:
    """Remove a widget now: hidden and detached at once, memory freed later."""
    if widget is not None:
        widget.hide()
        widget.setParent(None)
        widget.deleteLater()


def _clear(layout) -> None:
    while layout.count():
        item = layout.takeAt(0)
        if item.layout():
            _clear(item.layout())
        _discard(item.widget())


class PlanView(QWidget):
    battery_submitted = Signal(float)
    away_added = Signal(object)  # AwayPeriod
    away_removed = Signal(object)  # AwayPeriod
    settings_requested = Signal(str)  # title of the Settings tab to open
    layout_changed = Signal()  # the view resized itself to the page shown: a wrapping window should follow

    def __init__(self, tz: ZoneInfo | None = None) -> None:
        super().__init__(None)
        self.tz = tz or ZoneInfo("Europe/Amsterdam")

        # Our own header row: a QTabWidget only stretches its tab strip over the tabs plus its corner
        # widget, which left a close button stranded short of the right edge.
        self.tabs = QTabBar()
        self.tabs.setExpanding(False)
        self.tabs.setDrawBase(False)
        self.tabs.addTab("Plan")
        self.tabs.addTab("Update")
        self.header = QWidget()
        self.header.setObjectName("header")
        self.header.setStyleSheet("#header { border-bottom: 1px solid palette(mid); }")
        self.header_row = QHBoxLayout(self.header)
        self.header_row.setContentsMargins(0, 0, 0, 0)
        self.header_row.setSpacing(0)
        self.header_row.addWidget(self.tabs)
        self.header_row.addStretch()

        self.plan_page = self._build_plan_page()
        self.update_page = self._build_update_page()
        self.stack = QStackedWidget()
        self.stack.addWidget(self.plan_page)
        self.stack.addWidget(self.update_page)
        self.tabs.currentChanged.connect(self._on_tab_changed)

        self.footer = _muted()
        self.footer.setToolTip(ATTRIBUTION)

        outer = QVBoxLayout(self)
        outer.setContentsMargins(0, 0, 0, 0)  # the wrapping window sets the margins
        outer.setSpacing(8)
        outer.addWidget(self.header)
        outer.addWidget(self.stack)
        outer.addWidget(self.footer)
        self.fit_to_page()

    # -- the Plan page ---------------------------------------------------------

    def _build_plan_page(self) -> QWidget:
        page = QWidget()
        box = QVBoxLayout(page)
        box.setContentsMargins(2, 14, 2, 4)
        box.setSpacing(10)

        self.headline = QLabel()
        font = self.headline.font()
        font.setPointSizeF(font.pointSizeF() * 1.5)
        font.setBold(True)
        self.headline.setFont(font)
        self.detail = QLabel()
        self.detail.setWordWrap(True)
        self.projection = QLabel()
        head = QVBoxLayout()
        head.setSpacing(4)
        for widget in (self.headline, self.detail, self.projection):
            head.addWidget(widget)
        box.addLayout(head)

        self.advice_box = QWidget()
        advice = QVBoxLayout(self.advice_box)
        advice.setContentsMargins(0, 0, 0, 0)
        advice.setSpacing(2)
        self.advice_title = QLabel()
        self.advice_title.setWordWrap(True)
        self.advice_detail = QLabel()
        self.advice_detail.setWordWrap(True)
        self.advice_reason = _muted()
        for widget in (self.advice_title, self.advice_detail, self.advice_reason):
            advice.addWidget(widget)
        box.addWidget(self.advice_box)

        self.outlook_box = QWidget()
        outlook = QVBoxLayout(self.outlook_box)
        outlook.setContentsMargins(0, 0, 0, 0)
        outlook.setSpacing(4)
        outlook.addWidget(_heading("Next days"))
        self.outlook_grid = QGridLayout()
        self.outlook_grid.setHorizontalSpacing(14)
        self.outlook_grid.setVerticalSpacing(3)
        outlook.addLayout(self.outlook_grid)
        self.quiet = _muted(small=False)
        outlook.addWidget(self.quiet)
        box.addWidget(self.outlook_box)

        # compact status lines; the buttons jump to the Update page
        status = QGridLayout()
        status.setHorizontalSpacing(10)
        status.setVerticalSpacing(6)
        self.battery_status = QLabel()
        self.gauge = QProgressBar()
        self.gauge.setRange(0, 100)
        self.gauge.setTextVisible(False)
        self.gauge.setFixedHeight(8)
        self.gauge.setMaximumWidth(90)
        self.edit_battery = QPushButton("Edit")
        self.edit_battery.setFlat(True)
        self.edit_battery.clicked.connect(lambda: self.show_update("battery"))
        self.away_status = QLabel()
        self.away_status.setWordWrap(True)
        self.manage_away = QPushButton("Manage")
        self.manage_away.setFlat(True)
        self.manage_away.clicked.connect(lambda: self.show_update())
        status.addWidget(self.battery_status, 0, 0)
        status.addWidget(self.gauge, 0, 1, Qt.AlignmentFlag.AlignVCenter)
        status.addWidget(self.edit_battery, 0, 2)
        status.addWidget(self.away_status, 1, 0, 1, 2)
        status.addWidget(self.manage_away, 1, 2)
        status.setColumnStretch(0, 1)
        box.addLayout(status)
        return page

    # -- the Update page -------------------------------------------------------

    def _build_update_page(self) -> QWidget:
        page = QWidget()
        box = QVBoxLayout(page)
        box.setContentsMargins(2, 14, 2, 4)
        box.setSpacing(8)

        box.addWidget(_heading("Battery level"))
        self.battery_input = QDoubleSpinBox()
        self.battery_input.setRange(0, 100)
        self.battery_input.setDecimals(0)
        self.battery_input.setSuffix(" %")
        self.battery_input.setValue(50)
        set_button = QPushButton("Set battery")
        set_button.setToolTip("Saves immediately")
        set_button.clicked.connect(lambda: self.battery_submitted.emit(self.battery_input.value()))
        self.battery_input.lineEdit().returnPressed.connect(set_button.click)
        battery_row = QHBoxLayout()
        battery_row.addWidget(self.battery_input)
        battery_row.addWidget(set_button)
        battery_row.addStretch()
        box.addLayout(battery_row)
        self.battery = QLabel()
        self.battery.setWordWrap(True)
        box.addWidget(self.battery)
        box.addSpacing(8)

        box.addWidget(_heading("Car away"))
        self.away_list = QVBoxLayout()
        self.away_list.setSpacing(4)
        box.addLayout(self.away_list)
        edit_rules = QPushButton("Edit weekly rules in Settings...")
        edit_rules.setFlat(True)
        edit_rules.clicked.connect(lambda: self.settings_requested.emit("Away rules"))
        edit_rules_row = QHBoxLayout()
        edit_rules_row.addWidget(edit_rules)
        edit_rules_row.addStretch()
        box.addLayout(edit_rules_row)

        self.away_day = QComboBox()
        self.away_kind = QComboBox()
        self.away_kind.addItems(["All day", "Time range"])
        self.away_from = QTimeEdit(QTime(9, 0))
        self.away_to = QTimeEdit(QTime(17, 0))
        for edit in (self.away_from, self.away_to):
            edit.setDisplayFormat("HH:mm")
            edit.setVisible(False)
        self.away_kind.currentIndexChanged.connect(self._on_kind_changed)
        add_button = QPushButton("Add")
        add_button.setToolTip("Saves immediately")
        add_button.clicked.connect(self._on_add_away)
        add_row = QHBoxLayout()
        for widget in (self.away_day, self.away_kind, self.away_from, self.away_to, add_button):
            add_row.addWidget(widget)
        add_row.addStretch()
        box.addLayout(add_row)
        self.away_error = QLabel()
        self.away_error.setWordWrap(True)
        self.away_error.setVisible(False)
        box.addWidget(self.away_error)
        box.addStretch()
        return page

    # -- filling in the view ---------------------------------------------------

    def update_view(
        self,
        vm: ViewModel,
        state: State | None = None,
        today: date | None = None,
        rules: tuple[AwayRule, ...] = (),
    ) -> None:
        self.headline.setText(vm.headline)
        self.detail.setText(vm.detail)
        self.projection.setText(vm.projection)
        self.projection.setVisible(bool(vm.projection))

        self.advice_box.setVisible(vm.advice is not None)
        if vm.advice is not None:
            self.advice_title.setText(vm.advice.title)
            self.advice_title.setStyleSheet(f"color: {colours.warning(self)}; font-weight: bold;")
            self.advice_detail.setText(vm.advice.detail)
            self.advice_reason.setText(vm.advice.reason)

        _clear(self.outlook_grid)
        for row, item in enumerate(vm.outlook):
            for column, text in enumerate((item.day, item.span, item.power, item.energy)):
                label = QLabel(text)
                if column == 0:
                    label.setStyleSheet("font-weight: bold;")
                self.outlook_grid.addWidget(label, row, column)
        self.outlook_grid.setColumnStretch(4, 1)
        self.quiet.setText(vm.quiet_days)
        self.quiet.setVisible(bool(vm.quiet_days))
        self.outlook_box.setVisible(bool(vm.outlook) or bool(vm.quiet_days))

        short = f"Battery {vm.battery_pct:.0f}%" + (" · please update" if vm.battery_stale else "")
        self.battery_status.setText(short)
        self.battery_status.setStyleSheet(f"color: {colours.error(self)};" if vm.battery_stale else "")
        self.gauge.setValue(round(vm.battery_pct))
        self.battery.setText(vm.battery_line)
        self.battery.setStyleSheet(f"color: {colours.error(self)};" if vm.battery_stale else "")
        self.tabs.setTabText(UPDATE, "Update •" if vm.battery_stale else "Update")
        self.footer.setText(vm.footer)

        now = datetime.combine(today, time(0), self.tz) if today else datetime.now(self.tz)
        periods = state.away if state is not None else ()
        self.away_status.setText(away_summary(rules, periods, now, self.tz))
        if state is not None:
            if not self.battery_input.hasFocus():
                self.battery_input.setValue(round(state.battery.pct))
            self._fill_away(state.away, rules)
        if today is not None:
            self._fill_days(today)
        self.fit_to_page()

    def _fill_days(self, today: date) -> None:
        chosen = self.away_day.currentData()
        self.away_day.clear()
        for offset in range(8):
            day = today + timedelta(days=offset)
            self.away_day.addItem(day_label(day, today), day)
        index = self.away_day.findData(chosen)
        self.away_day.setCurrentIndex(max(index, 0))

    def _fill_away(self, periods: tuple[AwayPeriod, ...], rules: tuple[AwayRule, ...]) -> None:
        """Weekly rules first, read-only (they are settings); then one-off days, which can be removed here."""
        _clear(self.away_list)
        for rule in rules:
            self.away_list.addWidget(_muted(f"{describe_rule(rule)}  (weekly rule)", small=False))
        if not rules and not periods:
            self.away_list.addWidget(_muted("No absences planned: the car is treated as home all week.", small=False))
        for period in periods:
            row = QHBoxLayout()
            row.addWidget(QLabel(describe(period, self.tz)))
            row.addStretch()
            remove = QPushButton("Remove")
            remove.setFlat(True)
            remove.clicked.connect(lambda _=False, p=period: self.away_removed.emit(p))
            row.addWidget(remove)
            self.away_list.addLayout(row)

    # -- behaviour -------------------------------------------------------------

    def add_to_header(self, widget: QWidget) -> None:
        """Put a control at the right end of the header row (the popup's close button)."""
        self.header_row.addWidget(widget)

    def show_update(self, focus: str | None = None) -> None:
        self.tabs.setCurrentIndex(UPDATE)
        if focus == "battery":
            self.battery_input.setFocus()
            self.battery_input.selectAll()

    def _on_tab_changed(self, index: int) -> None:
        self.stack.setCurrentIndex(index)
        self.fit_to_page()

    def fit_to_page(self, *_args) -> None:
        """A stack is as tall as its tallest page. Make it as tall as the page being shown."""
        page = self.stack.currentWidget()
        page.layout().activate()
        self.stack.setFixedHeight(page.sizeHint().height())
        self.adjustSize()
        self.layout_changed.emit()

    def _on_kind_changed(self) -> None:
        ranged = self.away_kind.currentIndex() == 1
        self.away_from.setVisible(ranged)
        self.away_to.setVisible(ranged)
        self.fit_to_page()

    def _on_add_away(self) -> None:
        try:
            period = make_away(
                self.away_day.currentData(),
                self.away_kind.currentIndex() == 0,
                self.away_from.time().toPython(),
                self.away_to.time().toPython(),
                self.tz,
            )
        except ValueError as err:
            self.away_error.setStyleSheet(f"color: {colours.error(self)};")
            self.away_error.setText(str(err))
            self.away_error.setVisible(True)
            return
        self.away_error.setVisible(False)
        self.away_added.emit(period)
