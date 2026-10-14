"""The Car and charger page: what the car and the charger are, in words a person knows."""

from __future__ import annotations

from dataclasses import replace

from PySide6.QtCore import Qt
from PySide6.QtWidgets import (
    QButtonGroup,
    QDoubleSpinBox,
    QFormLayout,
    QFrame,
    QHBoxLayout,
    QLabel,
    QPushButton,
    QRadioButton,
    QScrollArea,
    QSpinBox,
    QVBoxLayout,
    QWidget,
)

from car_charge_planner import colours, units
from car_charge_planner.i18n import _, number
from car_charge_planner.setup.vehicle import CAR_KEYS, CHARGER_KEYS, daily_to_weekly, problems, weekly_to_daily

from .pages import Page, fold
from .solar import heading, note
from .typical import with_typical

KWH = " kWh"
PERCENT = " %"
AMPS = " A"
VOLTS = " V"
WEEKLY_KM_MAX = 7000  # a week: far more than a person drives, and the limit of the box


class CarPage(Page):
    id = "car"

    @property
    def title(self) -> str:
        return _("Your car and charger")

    def __init__(self, wizard) -> None:
        super().__init__(wizard)
        self._filling = False
        self.system = units.METRIC
        outer = QVBoxLayout(self)
        outer.setContentsMargins(0, 0, 0, 0)
        content = QWidget()
        content.setAutoFillBackground(False)
        layout = QVBoxLayout(content)
        scroll = QScrollArea()
        scroll.setWidgetResizable(True)
        scroll.setFrameShape(QFrame.Shape.NoFrame)
        scroll.viewport().setAutoFillBackground(False)
        scroll.setWidget(content)
        outer.addWidget(scroll)

        row = QHBoxLayout()
        row.addWidget(heading(_("The car")))
        self.car_unknown = QPushButton(_("I don't know the car's numbers"))
        self.car_unknown.setAutoDefault(False)
        self.car_unknown.setToolTip(_("Use typical values for the battery, consumption and distance. You can change them later in Settings."))
        row.addStretch()
        row.addWidget(self.car_unknown)
        layout.addLayout(row)
        self.car_note = note("")
        layout.addWidget(self.car_note)

        form = QFormLayout()
        self.battery = QDoubleSpinBox()
        self.battery.setRange(1, 200)
        self.battery.setDecimals(0)
        self.battery.setSuffix(KWH)
        form.addRow(_("Usable battery"), self.battery)
        layout.addLayout(form)
        layout.addWidget(note(_("Typical: about 40 to 100 kWh. Give the usable (net) capacity: makers often list the gross figure, which is higher.")))

        form = QFormLayout()
        self.consumption = QDoubleSpinBox()
        form.addRow(_("Consumption"), self.consumption)
        layout.addLayout(form)
        self.consumption_note = note("")
        layout.addWidget(self.consumption_note)

        form = QFormLayout()
        self.weekly = QDoubleSpinBox()
        form.addRow(_("Distance driven in a week"), self.weekly)
        layout.addLayout(form)
        self.weekly_note = note(_("Everything added up and averaged over the year: work, shopping, holidays. The plan uses a seventh of it a day."))
        layout.addWidget(self.weekly_note)

        form = QFormLayout()
        self.minimum = QSpinBox()
        self.minimum.setRange(0, 99)
        self.minimum.setSuffix(PERCENT)
        self.target = QSpinBox()
        self.target.setRange(1, 100)
        self.target.setSuffix(PERCENT)
        form.addRow(_("Never below"), self.minimum)
        form.addRow(_("Charge up to"), self.target)
        layout.addLayout(form)
        layout.addWidget(note(_("The battery should never drop below the first; if it would, the plan advises charging from the grid. The second is the everyday maximum: 80 is gentle on the battery.")))

        row = QHBoxLayout()
        row.addWidget(heading(_("The charger")))
        self.charger_unknown = QPushButton(_("I don't know the charger's numbers"))
        self.charger_unknown.setAutoDefault(False)
        self.charger_unknown.setToolTip(_("Use typical values for the phases and the current. You can change them later in Settings."))
        row.addStretch()
        row.addWidget(self.charger_unknown)
        layout.addLayout(row)
        self.charger_note = note("")
        layout.addWidget(self.charger_note)

        row = QHBoxLayout()
        self.one_phase = QRadioButton(_("One phase"))
        self.three_phases = QRadioButton(_("Three phases"))
        group = QButtonGroup(self)
        group.addButton(self.one_phase)
        group.addButton(self.three_phases)
        row.addWidget(QLabel(_("Phases")))
        row.addWidget(self.one_phase)
        row.addWidget(self.three_phases)
        row.addStretch()
        layout.addLayout(row)
        layout.addWidget(note(_("The charger's label or manual says, and so does the fuse box (three fuses in a row, or a three-pole one). Some chargers can be switched to single-phase charging, which suits solar power better: a three-phase charger needs about three times the surplus before it can start.")))

        form = QFormLayout()
        self.max_amps = QSpinBox()
        self.max_amps.setRange(1, 80)
        self.max_amps.setSuffix(AMPS)
        self.min_amps = QSpinBox()
        self.min_amps.setRange(1, 80)
        self.min_amps.setSuffix(AMPS)
        form.addRow(_("Highest current"), self.max_amps)
        form.addRow(_("Lowest current"), self.min_amps)
        layout.addLayout(form)
        layout.addWidget(note(_("Per phase. The highest is what the charger and the home connection allow: typically 16 or 32 A. The lowest is what the charger and car both accept, often 6 A.")))

        self.advanced_toggle = fold(_("Advanced"))
        layout.addWidget(self.advanced_toggle, 0, Qt.AlignmentFlag.AlignLeft)
        self.advanced_box = QWidget()
        form = QFormLayout(self.advanced_box)
        self.volts = QSpinBox()
        self.volts.setRange(100, 400)
        self.volts.setSuffix(VOLTS)
        form.addRow(_("Voltage"), self.volts)
        form.addRow(note(_("Mains voltage at the charger: 230 V in most of Europe.")))
        layout.addWidget(self.advanced_box)

        layout.addStretch()
        self.errors = QLabel()  # outside the scrolling part: a problem must be seen wherever the page is scrolled to
        self.errors.setWordWrap(True)
        self.errors.setStyleSheet(f"color: {colours.error(self)};")
        outer.addWidget(self.errors)

        self.car_unknown.clicked.connect(lambda: self._use_typical(CAR_KEYS))
        self.charger_unknown.clicked.connect(lambda: self._use_typical(CHARGER_KEYS))
        self.advanced_toggle.toggled.connect(self.advanced_box.setVisible)
        self.battery.valueChanged.connect(lambda v: self._car("car.usable_battery_kwh", usable_battery_kwh=v))
        self.consumption.valueChanged.connect(self._consumption_edited)
        self.weekly.valueChanged.connect(self._weekly_edited)
        self.minimum.valueChanged.connect(lambda v: self._preference(minimum_pct=v))
        self.target.valueChanged.connect(lambda v: self._preference(target_pct=v))
        self.one_phase.toggled.connect(self._phases_edited)
        self.max_amps.valueChanged.connect(lambda v: self._charger("charger.max_amps", max_amps=v))
        self.min_amps.valueChanged.connect(lambda v: self._charger("charger.min_amps", min_amps=v))
        self.volts.valueChanged.connect(lambda v: self._charger("charger.volts", volts=v))

    def enter(self) -> None:
        self.system = self.state.units  # the units chosen on the first page, which may have changed
        self._shape_unit_boxes()
        self._show()
        self.advanced_toggle.setChecked(False)
        self.advanced_box.setVisible(False)

    def _shape_unit_boxes(self) -> None:
        """Ranges, decimals and unit of the two boxes that follow the units. A box clamps its value to a new range and says
        it changed: that is not the person's answer, so nothing is recorded meanwhile."""
        self._filling = True
        try:
            self._shape()
        finally:
            self._filling = False

    def _shape(self) -> None:
        low, high = units.display_range(units.CONSUMPTION, 5, 60, self.system)
        self.consumption.setRange(low, high)
        self.consumption.setDecimals(units.display_decimals(units.CONSUMPTION, 1, self.system))
        self.consumption.setSingleStep(units.display_step(units.CONSUMPTION, 0.5, self.system))
        self.consumption.setSuffix(units.suffix(units.CONSUMPTION, self.system))
        _low, high_km = units.display_range(units.DISTANCE, 0, WEEKLY_KM_MAX, self.system)
        self.weekly.setRange(0, high_km)
        self.weekly.setDecimals(0)
        self.weekly.setSingleStep(10)
        self.weekly.setSuffix(units.suffix(units.DISTANCE, self.system))
        decimals = units.display_decimals(units.CONSUMPTION, 1, self.system)
        low_value, high_value = sorted((units.to_display(units.CONSUMPTION, 22, self.system), units.to_display(units.CONSUMPTION, 14, self.system)))
        low_text, high_text = number(low_value, decimals), number(high_value, decimals)
        self.consumption_note.setText(
            _("Typical: {low} to {high} {unit}. The car's trip computer average is a good guide; it is higher in winter.").format(
                low=low_text, high=high_text, unit=units.unit_label(units.CONSUMPTION, self.system)
            )
        )

    def _show(self) -> None:
        cfg = self.state.config
        self._filling = True
        self.battery.setValue(cfg.system.car.usable_battery_kwh)
        self.consumption.setValue(units.to_display(units.CONSUMPTION, cfg.system.car.consumption_kwh_per_100km, self.system))
        self.weekly.setValue(units.to_display(units.DISTANCE, daily_to_weekly(cfg.system.car.km_per_day), self.system))
        self.minimum.setValue(round(cfg.planning.minimum_pct))
        self.target.setValue(round(cfg.planning.target_pct))
        self.one_phase.setChecked(cfg.system.charger.phases == 1)
        self.three_phases.setChecked(cfg.system.charger.phases == 3)
        self.max_amps.setValue(cfg.system.charger.max_amps)
        self.min_amps.setValue(cfg.system.charger.min_amps)
        self.volts.setValue(round(cfg.system.charger.volts))
        self._filling = False
        self._refresh()

    def _refresh(self) -> None:
        typical = self.state.config.setup.typical
        self.car_note.setText(_("Typical values in use for some of these. Enter your own, or leave them.") if any(k in typical for k in CAR_KEYS) else "")
        self.charger_note.setText(_("Typical values in use for some of these. Enter your own, or leave them.") if any(k in typical for k in CHARGER_KEYS) else "")
        found = problems(self.state.config)
        self.errors.setText("\n".join(f"• {line}" for line in found))

    def can_continue(self) -> bool:
        return not problems(self.state.config)

    def _car(self, key: str, **values) -> None:
        if self._filling:
            return
        cfg = self.state.config
        self.state.config = cfg.with_system(car=replace(cfg.system.car, **values))
        self.state.give_value(key)
        self._refresh()
        self.changed.emit()

    def _consumption_edited(self, value: float) -> None:
        if not self._filling:
            self._car("car.consumption_kwh_per_100km", consumption_kwh_per_100km=round(units.from_display(units.CONSUMPTION, value, self.system), 6))

    def _weekly_edited(self, value: float) -> None:
        if not self._filling:
            km_per_week = units.from_display(units.DISTANCE, value, self.system)
            self._car("car.km_per_day", km_per_day=round(weekly_to_daily(km_per_week), 6))

    def _preference(self, **values) -> None:
        """The charge levels are preferences: not typical values, so nothing to remember."""
        if self._filling:
            return
        self.state.config = self.state.config.with_planning(**values)
        self._refresh()
        self.changed.emit()

    def _charger(self, key: str, **values) -> None:
        if self._filling:
            return
        cfg = self.state.config
        self.state.config = cfg.with_system(charger=replace(cfg.system.charger, **values))
        self.state.give_value(key)
        self._refresh()
        self.changed.emit()

    def _phases_edited(self, _checked: bool) -> None:
        if not self._filling:
            self._charger("charger.phases", phases=1 if self.one_phase.isChecked() else 3)

    def _use_typical(self, keys: tuple[str, ...]) -> None:
        """"I don't know": the typical values for this half, remembered as typical."""
        self.state.config = with_typical(self.state.config, *keys)
        self.state.use_typical(*keys)
        self._show()
        self.changed.emit()
