"""A tab of input fields built from FieldSpecs."""

from __future__ import annotations

from dataclasses import replace
from datetime import time
from html import escape
from typing import Any, cast
from zoneinfo import available_timezones

from PySide6.QtCore import QTime, Signal
from PySide6.QtWidgets import (
    QComboBox,
    QCompleter,
    QDoubleSpinBox,
    QFrame,
    QGridLayout,
    QHBoxLayout,
    QLabel,
    QLineEdit,
    QPushButton,
    QScrollArea,
    QSpinBox,
    QTimeEdit,
    QVBoxLayout,
    QWidget,
)

from car_charge_planner import colours, units
from car_charge_planner.i18n import _, available_languages, language_name
from car_charge_planner.names import weekdays_long
from car_charge_planner.planner.model import (
    TYPICAL_KEYS,
    AwayRule,
    Config,
    Panels,
    Setup,
    base_key,
    panel_key,
    split_panel_key,
    typical_keys,
    with_array,
    without_array,
)

from .fields import LOCATION_FIELDS, FieldSpec, TabSpec, array_fields, get_value, set_value


class SecretInput(QWidget):
    """A text box that shows dots, with a button to look at what was typed."""

    textChanged = Signal(str)

    def __init__(self) -> None:
        super().__init__()
        self.line = QLineEdit()
        self.line.setEchoMode(QLineEdit.EchoMode.Password)
        self.line.textChanged.connect(self.textChanged)
        self.toggle = QPushButton(_("Show"))
        self.toggle.setCheckable(True)
        self.toggle.toggled.connect(self._show)
        row = QHBoxLayout(self)
        row.setContentsMargins(0, 0, 0, 0)
        row.addWidget(self.line, 1)
        row.addWidget(self.toggle)

    def _show(self, visible: bool) -> None:
        self.line.setEchoMode(QLineEdit.EchoMode.Normal if visible else QLineEdit.EchoMode.Password)
        self.toggle.setText(_("Hide") if visible else _("Show"))

    def text(self) -> str:
        return self.line.text()

    def setText(self, value: str) -> None:
        self.line.setText(value)


def timezone_box() -> QComboBox:
    """An editable list of every time zone, searchable by typing any part of a name."""
    box = QComboBox()
    box.setEditable(True)
    box.setInsertPolicy(QComboBox.InsertPolicy.NoInsert)
    box.addItems(sorted(available_timezones()))
    completer = QCompleter(box.model(), box)
    completer.setCaseSensitivity(completer.caseSensitivity().CaseInsensitive)
    completer.setFilterMode(completer.filterMode().MatchContains)
    box.setCompleter(completer)
    return box


LANGUAGE_KEY = "interface.language"


def shown_value(spec: FieldSpec, stored, system: str = units.METRIC):
    """The stored value as the input box shows it (in the chosen unit system, for distances and consumption)."""
    if spec.kind == "choice":
        return stored  # the stored value itself: a text for most choices, a number for the charger's phases
    if spec.kind in ("timezone", "text", "secret"):
        return str(stored)
    if spec.kind == "int":
        return int(stored)
    if spec.quantity:
        return round(units.to_display(spec.quantity, float(stored), system), units.display_decimals(spec.quantity, spec.decimals, system))
    return round(float(stored) * spec.scale, spec.decimals)


class FieldTab(QWidget):
    changed = Signal()  # any box edited, or the tab (re)loaded

    def __init__(self, tab: TabSpec, cfg: Config, secrets: dict[str, str] | None = None) -> None:
        """`secrets` holds the values of the tab's secret fields by field key; they are not part of the Config."""
        super().__init__()
        self._secrets = dict(secrets or {})
        self.units = units.METRIC  # set_units() changes how distances and consumption are shown
        self._exact: dict[str, float] = {}  # an edited value as it was before a unit switch rounded what the box shows
        self.typical: set[str] = set()  # this tab's settings that are still typical values (tagged until edited)
        self.hints: dict[str, QLabel] = {}
        self.key = tab.key
        self.title = _(tab.title)
        languages = available_languages()
        # the language choice exists only when there is a language to choose besides English
        self.specs = tuple(s for s in tab.fields if s.key != LANGUAGE_KEY or languages)
        self.languages = languages
        self.inputs: dict[str, Any] = {}  # a box of another Qt class for each kind of field
        self.baseline: dict[str, object] = {}  # exact values the boxes were last filled from
        grid = QGridLayout(self)
        self.grid = grid
        self.labels: list[QLabel] = []
        grid.setColumnStretch(1, 1)
        grid.setVerticalSpacing(2)
        row = 0
        for spec in self.specs:
            widget = self._make_input(spec)
            self.inputs[spec.key] = widget
            label = QLabel(_(spec.label))
            self.labels.append(label)
            grid.addWidget(label, row, 0)
            grid.addWidget(widget, row, 1)
            hint = QLabel()
            hint.setWordWrap(True)
            hint.setTextFormat(hint.textFormat().RichText)
            hint.setStyleSheet(f"{colours.MUTED_STYLE} font-size: 9pt; margin-bottom: 8px;")
            self.hints[spec.key] = hint
            grid.addWidget(hint, row + 1, 1)
            row += 2
        grid.setRowStretch(row, 1)
        self.load(cfg)
        self.changed.connect(self._refresh_hints)

    def _tag(self, spec: FieldSpec) -> str:
        """The tag before a hint: the field's own ("Required"), or "Typical value" while the value is one."""
        if spec.flag:
            return _(spec.flag)
        if spec.key in self.typical and not self._edited(spec):
            return _("Typical value")
        return ""

    def _refresh_hints(self) -> None:
        for spec in self.specs:
            tag = self._tag(spec)
            flag = f"<b style='color:{colours.warning(self)}'>{tag}.</b> " if tag else ""
            self.hints[spec.key].setText(f"{flag}{escape(_(spec.hint))}")

    def _edited(self, spec: FieldSpec) -> bool:
        """Has the owner changed this field since it was loaded?"""
        return spec.kind != "secret" and self.stored_value(spec) != self.baseline[spec.key]

    def collect_secrets(self) -> dict[str, str]:
        """What the tab's secret fields hold now, by field key (an empty one is left out)."""
        return {s.key: self.stored_value(s) for s in self.specs if s.kind == "secret" and self.stored_value(s)}

    def _choices(self, spec: FieldSpec) -> list[tuple[str | int, str]]:
        if spec.key == LANGUAGE_KEY:
            installed = sorted({"en", *self.languages})
            return [("auto", _("Automatic: follow the system")), *((code, language_name(code)) for code in installed)]
        return [(value, _(label)) for value, label in spec.choices]

    def _make_input(self, spec: FieldSpec) -> QWidget:
        if spec.kind == "choice":
            box = QComboBox()
            for value, label in self._choices(spec):
                box.addItem(label, value)
            box.currentIndexChanged.connect(lambda _i: self.changed.emit())
            return box
        if spec.kind == "text":
            line = QLineEdit()
            line.textChanged.connect(lambda _text: self.changed.emit())
            return line
        if spec.kind == "secret":
            secret = SecretInput()
            secret.textChanged.connect(lambda _text: self.changed.emit())
            return secret
        if spec.kind == "timezone":
            box = timezone_box()
            box.currentTextChanged.connect(lambda _text: self.changed.emit())
            return box
        if spec.kind == "int":
            spin = QSpinBox()
            spin.setRange(int(spec.minimum), int(spec.maximum))
            spin.setSingleStep(int(spec.step))
        else:
            spin = QDoubleSpinBox()
            self._shape(spec, spin)
        if spec.unit and not spec.quantity:
            spin.setSuffix(f" {_(spec.unit)}")
        spin.valueChanged.connect(lambda _value: self.changed.emit())
        return spin

    def _shape(self, spec: FieldSpec, spin: QDoubleSpinBox) -> None:
        """Range, decimals, step and unit of a decimal box, in the unit system being shown."""
        if spec.quantity:
            low, high = units.display_range(spec.quantity, spec.minimum, spec.maximum, self.units)
            spin.setRange(low, high)
            spin.setDecimals(units.display_decimals(spec.quantity, spec.decimals, self.units))
            spin.setSingleStep(units.display_step(spec.quantity, spec.step, self.units))
            spin.setSuffix(units.suffix(spec.quantity, self.units))
        else:
            spin.setRange(spec.minimum, spec.maximum)
            spin.setDecimals(spec.decimals)
            spin.setSingleStep(spec.step)

    def set_units(self, system: str) -> None:
        """Show distances and consumption in another unit system. What they stand for does not change: the stored
        (metric) values are kept as they are, so switching back and forth cannot drift."""
        if system == self.units:
            return
        held = {s.key: self.stored_value(s) for s in self.specs if s.quantity}  # in the system that is shown now
        self.units = system
        for spec in self.specs:
            if spec.quantity:
                widget = self.inputs[spec.key]
                self._shape(spec, widget)
                self._set_widget(spec, held[spec.key])
                if held[spec.key] != self.baseline[spec.key]:
                    self._exact[spec.key] = held[spec.key]  # 100 km shows as 62.1 mi: coming back must give 100, not 99.9
                else:
                    self._exact.pop(spec.key, None)

    def _set_widget(self, spec: FieldSpec, stored) -> None:
        widget = self.inputs[spec.key]
        value = shown_value(spec, stored, self.units)
        if spec.kind == "timezone":
            widget.setCurrentText(value)
        elif spec.kind in ("text", "secret"):
            widget.setText(value)
        elif spec.kind == "choice":
            widget.setCurrentIndex(max(widget.findData(value), 0))
        else:
            widget.setValue(value)

    def load(self, cfg: Config) -> None:
        """Fill the boxes from a config and remember its exact values."""
        for spec in self.specs:
            stored = self._secrets.get(spec.key, "") if spec.kind == "secret" else get_value(cfg, spec.key)
            self.baseline[spec.key] = stored
            self._exact.pop(spec.key, None)
            self._set_widget(spec, stored)
        self.typical = {s.key for s in self.specs if s.key in cfg.setup.typical}
        self._refresh_hints()

    def reset(self) -> None:
        """Back to the built-in values, which are typical ones again."""
        self.load(Config())
        self.typical = {s.key for s in self.specs if base_key(s.key) in TYPICAL_KEYS}
        self._refresh_hints()

    def stored_value(self, spec: FieldSpec) -> Any:
        """What to store for a field. A box that still shows the baseline gives the exact baseline back."""
        widget = self.inputs[spec.key]
        baseline = self.baseline[spec.key]
        if spec.kind == "choice":
            data = widget.currentData()
            return baseline if data == baseline else data
        if spec.kind in ("text", "secret"):
            text = widget.text().strip()  # a pasted key often carries a space or a newline
            return baseline if text == baseline else text
        if spec.kind == "timezone":
            text = widget.currentText().strip()
            return baseline if text == baseline else text
        shown = widget.value()
        reference = self._exact.get(spec.key, baseline)
        if shown == shown_value(spec, reference, self.units):
            return reference  # untouched: no rounding drift (85.714... must not become 85.7)
        if spec.kind == "int":
            return int(shown)
        if spec.quantity:
            return round(units.from_display(spec.quantity, shown, self.units), 10)
        return round(shown / spec.scale, 10)

    def apply_to(self, cfg: Config) -> Config:
        for spec in self.specs:
            if spec.kind != "secret":  # secrets are not in the Config: see collect_secrets
                cfg = set_value(cfg, spec.key, self.stored_value(spec))
        return replace(cfg, setup=Setup(self._typical_after(cfg)))

    def _typical_after(self, cfg: Config) -> tuple[str, ...]:
        """The typical list with this tab's part brought up to date: a setting the owner edited is no longer typical;
        one that is still typical (or was reset) is. Settings of other tabs are left as they were."""
        own = {s.key for s in self.specs}
        still = {s.key for s in self.specs if s.key in self.typical and not self._edited(s)}
        kept = {key for key in cfg.setup.typical if key not in own} | still
        return tuple(key for key in typical_keys(cfg) if key in kept)

    def align_labels(self, width: int) -> None:
        """Make the label column at least this wide, so that tabs stacked on one page line up."""
        self.grid.setColumnMinimumWidth(0, width)

    def label_width(self) -> int:
        return max((label.sizeHint().width() for label in self.labels), default=0)


class ArrayBox(QWidget):
    """One panel array on the Solar tab: its fields, and with several arrays a heading and a Remove button."""

    def __init__(self, index: int, cfg: Config, on_remove) -> None:
        super().__init__()
        count = len(cfg.panels)
        self.index = index
        layout = QVBoxLayout(self)
        layout.setContentsMargins(0, 0, 0, 0)
        heading = QHBoxLayout()
        name = cfg.panels[index].name
        self.heading = QLabel()
        self._name_edited(name)
        self.heading.setStyleSheet("font-weight: bold;")
        self.remove_button = QPushButton(_("Remove"))
        self.remove_button.setToolTip(_("Remove this array. Nothing is saved until you press Save."))
        self.remove_button.setAutoDefault(False)
        self.remove_button.clicked.connect(lambda: on_remove(self.index))
        heading.addWidget(self.heading, 1)
        heading.addWidget(self.remove_button)
        layout.addLayout(heading)
        for widget in (self.heading, self.remove_button):
            widget.setVisible(count > 1)  # one array looks as it always did: no heading, nothing to remove
        self.fields = FieldTab(TabSpec("solar", "", array_fields(index, named=count > 1)), cfg)
        layout.addWidget(self.fields)
        if count > 1:
            self.fields.inputs[panel_key(index, "name")].textChanged.connect(self._name_edited)

    def _name_edited(self, name: str) -> None:
        number = self.index + 1
        name = name.strip()
        self.heading.setText(_("Array {number}: {name}").format(number=number, name=name) if name else _("Array {number}").format(number=number))


class SolarTab(QWidget):
    """The Solar tab: the fields of each panel array, a way to add and remove arrays, and the location.

    With one array it looks like a plain list of fields. The arrays are rebuilt when one is added or removed; what
    was typed so far is kept (it is read back first)."""

    changed = Signal()

    def __init__(self, tab: TabSpec, cfg: Config) -> None:
        super().__init__()
        self.key = tab.key
        self.title = _(tab.title)
        self.loaded = cfg
        self.arrays: list[Panels] = []  # the arrays the boxes started from; the boxes give their values
        self.boxes: list[ArrayBox] = []
        # Several arrays are taller than the other tabs: the content scrolls instead of being squeezed (as on the
        # Forecast tab).
        content = QWidget()
        content.setAutoFillBackground(False)
        layout = QVBoxLayout(content)
        scroll = QScrollArea()
        scroll.setWidgetResizable(True)
        scroll.setFrameShape(QFrame.Shape.NoFrame)
        scroll.viewport().setAutoFillBackground(False)
        scroll.setWidget(content)
        outer = QVBoxLayout(self)
        outer.setContentsMargins(0, 0, 0, 0)
        outer.addWidget(scroll)
        self.arrays_layout = QVBoxLayout()
        layout.addLayout(self.arrays_layout)
        self.add_button = QPushButton(_("Add another array"))
        self.add_button.setAutoDefault(False)
        self.add_button.setToolTip(_("For panels that face another way or have another tilt, such as an east and a west roof."))
        self.add_button.clicked.connect(self.add_array)
        add_line = QHBoxLayout()
        add_line.addWidget(self.add_button)
        add_line.addStretch()
        layout.addLayout(add_line)
        self.location_heading = QLabel(_("Location"))
        self.location_heading.setStyleSheet("font-weight: bold; margin-top: 12px;")
        layout.addWidget(self.location_heading)
        self.location = FieldTab(TabSpec(tab.key, tab.title, LOCATION_FIELDS), cfg)
        self.location.changed.connect(self.changed.emit)
        layout.addWidget(self.location)
        layout.addStretch(1)
        self.load(cfg)

    # -- what the window asks of a tab ----------------------------------------------------------------------------

    def _tabs(self) -> list[FieldTab]:
        return [box.fields for box in self.boxes] + [self.location]

    @property
    def specs(self) -> tuple[FieldSpec, ...]:
        return tuple(spec for tab in self._tabs() for spec in tab.specs)

    @property
    def inputs(self) -> dict[str, Any]:
        return {key: widget for tab in self._tabs() for key, widget in tab.inputs.items()}

    @property
    def hints(self) -> dict[str, QLabel]:
        return {key: label for tab in self._tabs() for key, label in tab.hints.items()}

    def load(self, cfg: Config) -> None:
        self.loaded = cfg
        self._show_arrays(cfg)
        self.location.load(cfg)

    def reset(self) -> None:
        """Back to the built-in values: one array, typical again, and no location."""
        self._show_arrays(replace(Config(), setup=Setup(TYPICAL_KEYS)))
        self.location.reset()
        self.changed.emit()

    def set_units(self, system: str) -> None:
        for tab in self._tabs():
            tab.set_units(system)

    def apply_to(self, cfg: Config) -> Config:
        own = [key for key in cfg.setup.typical if split_panel_key(key) is None]  # the arrays' flags come from the boxes
        cfg = replace(cfg, panels=tuple(self.arrays), setup=Setup(tuple(own)))
        for tab in self._tabs():
            cfg = tab.apply_to(cfg)
        return cfg

    # -- adding and removing arrays -------------------------------------------------------------------------------

    def add_array(self) -> None:
        """One more array, with typical values (tagged as such) until the owner enters their own."""
        self._show_arrays(with_array(self.apply_to(self.loaded)))
        self.changed.emit()

    def remove_array(self, index: int) -> None:
        self._show_arrays(without_array(self.apply_to(self.loaded), index))
        self.changed.emit()

    def _show_arrays(self, cfg: Config) -> None:
        for box in self.boxes:
            self.arrays_layout.removeWidget(box)
            box.hide()
            box.deleteLater()
        self.arrays = list(cfg.panels)
        self.boxes = [ArrayBox(index, cfg, self.remove_array) for index in range(len(cfg.panels))]
        for box in self.boxes:
            box.fields.changed.connect(self.changed.emit)
            self.arrays_layout.addWidget(box)
            box.show()  # at once: a layout shows a widget added to a visible window only on the next event loop turn
        width = max(tab.label_width() for tab in self._tabs())
        for tab in self._tabs():
            tab.align_labels(width)
            tab.grid.setContentsMargins(0, 0, 0, 0)  # the Solar tab's own margins are enough


class RuleRow(QWidget):
    def __init__(self, rule: AwayRule, on_remove, on_change) -> None:
        super().__init__()
        self.weekday = QComboBox()
        self.weekday.addItems(weekdays_long())
        self.weekday.setCurrentIndex(rule.weekday)
        self.start = QTimeEdit(QTime(rule.start.hour, rule.start.minute))
        self.end = QTimeEdit(QTime(rule.end.hour, rule.end.minute))
        for edit in (self.start, self.end):
            edit.setDisplayFormat("HH:mm")
        self.weekday.currentIndexChanged.connect(lambda _i: on_change())
        for edit in (self.start, self.end):
            edit.timeChanged.connect(lambda _t: on_change())
        remove = QPushButton(_("Remove"))
        remove.clicked.connect(lambda: on_remove(self))
        self.remove_button = remove
        row = QHBoxLayout(self)
        row.setContentsMargins(0, 0, 0, 0)
        row.addWidget(self.weekday, 2)
        row.addWidget(QLabel(_("from")))
        row.addWidget(self.start, 1)
        row.addWidget(QLabel(_("to")))
        row.addWidget(self.end, 1)
        row.addWidget(remove)

    def rule(self) -> AwayRule:
        return AwayRule(
            self.weekday.currentIndex(),
            cast("time", self.start.time().toPython()),  # the stub says `object`
            cast("time", self.end.time().toPython()),
        )


class AwayRulesTab(QWidget):
    """Weekly times the car is not at home: one row per rule."""

    changed = Signal()  # a row edited, added or removed

    DEFAULT_NEW = AwayRule(0, time(8, 0), time(17, 0))

    def __init__(self, cfg: Config) -> None:
        super().__init__()
        self.key = "away"
        self.title = _("Away rules")
        self.rows: list[RuleRow] = []
        layout = QVBoxLayout(self)
        intro = QLabel(
            _(
                "Times each week when the car is not at home. No charging is planned then. "
                "One rule covers one weekday: add several rules for several days."
            )
        )
        intro.setWordWrap(True)
        intro.setStyleSheet(f"{colours.MUTED_STYLE} font-size: 9pt;")
        layout.addWidget(intro)
        self.rows_box = QVBoxLayout()
        layout.addLayout(self.rows_box)
        self.empty_note = QLabel(_("No rules: the car is treated as home all week."))
        self.empty_note.setStyleSheet(colours.MUTED_STYLE)
        layout.addWidget(self.empty_note)
        self.add_button = QPushButton(_("Add rule"))
        self.add_button.clicked.connect(lambda: self.add_row(self.DEFAULT_NEW))
        add_line = QHBoxLayout()
        add_line.addWidget(self.add_button)
        add_line.addStretch()
        layout.addLayout(add_line)
        layout.addStretch()
        self.load(cfg)

    def add_row(self, rule: AwayRule) -> RuleRow:
        row = RuleRow(rule, self.remove_row, self.changed.emit)
        self.rows.append(row)
        self.rows_box.addWidget(row)
        self.empty_note.setVisible(False)
        self.changed.emit()
        return row

    def remove_row(self, row: RuleRow) -> None:
        self.rows.remove(row)
        self.rows_box.removeWidget(row)
        row.deleteLater()
        row.hide()
        self.empty_note.setVisible(not self.rows)
        self.changed.emit()

    def load(self, cfg: Config) -> None:
        for row in list(self.rows):
            self.remove_row(row)
        for rule in cfg.away_rules:
            self.add_row(rule)
        self.empty_note.setVisible(not self.rows)

    def apply_to(self, cfg: Config) -> Config:
        return replace(cfg, away_rules=tuple(row.rule() for row in self.rows))
