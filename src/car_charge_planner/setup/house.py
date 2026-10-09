"""The House page: how much power the house itself uses, from the year's bill."""

from __future__ import annotations

from dataclasses import replace

from PySide6.QtWidgets import QCheckBox, QDoubleSpinBox, QFormLayout, QHBoxLayout, QLabel, QPushButton, QVBoxLayout

from car_charge_planner import colours
from car_charge_planner.i18n import _, number

from .home import MAX_BASELOAD_KW, baseload_from_yearly, car_kwh_per_year, yearly_from_baseload
from .pages import Page
from .solar import note
from .typical import with_typical

KWH = " kWh"


class HousePage(Page):
    id = "house"
    in_sequence = False

    @property
    def title(self) -> str:
        return _("House electricity")

    def __init__(self, wizard) -> None:
        super().__init__(wizard)
        self._filling = False
        layout = QVBoxLayout(self)
        layout.addWidget(note(_("The plan needs to know how much power the house itself uses, to know what is left over for the car. Your year's use is on your energy bill, or in your supplier's app.")))

        form = QFormLayout()
        self.yearly = QDoubleSpinBox()
        self.yearly.setRange(0, 200_000)  # 23 kW on average: beyond what the plan accepts, so the warning can be seen
        self.yearly.setDecimals(0)
        self.yearly.setSingleStep(100)
        self.yearly.setSuffix(KWH)
        self.yearly.setSpecialValueText(_("not entered"))
        form.addRow(_("Electricity used in a year"), self.yearly)
        layout.addLayout(form)
        self.includes_car = QCheckBox(_("My bill already includes charging the car"))
        layout.addWidget(self.includes_car)
        self.result = QLabel()
        self.result.setWordWrap(True)
        self.result.setStyleSheet("font-weight: bold;")
        layout.addWidget(self.result)
        self.warning = QLabel()
        self.warning.setWordWrap(True)
        self.warning.setStyleSheet(f"color: {colours.warning(self)};")
        layout.addWidget(self.warning)
        layout.addWidget(note(_("The year's total is turned into an average power over the year's 8,760 hours. If the car is on the same meter, its share is taken off, using what you said about the car.")))

        row = QHBoxLayout()
        self.dont_know = QPushButton(_("I don't know"))
        self.dont_know.setAutoDefault(False)
        self.dont_know.setToolTip(_("Use a typical value for a house. You can change it later in Settings."))
        row.addWidget(self.dont_know)
        row.addStretch()
        layout.addLayout(row)
        self.current = note("")
        layout.addWidget(self.current)
        layout.addStretch()

        self.yearly.valueChanged.connect(self._edited)
        self.includes_car.toggled.connect(self._edited)
        self.dont_know.clicked.connect(self._use_typical)

    def next_label(self) -> str:
        return _("Done")

    def enter(self) -> None:
        self._filling = True
        self.yearly.setValue(0)  # what the bill says is not known here: the box starts empty
        self.includes_car.setChecked(False)
        self._filling = False
        self._refresh()

    def _car_share(self) -> float:
        car = self.state.config.car
        return car_kwh_per_year(car.km_per_day, car.consumption_kwh_per_100km) if self.includes_car.isChecked() else 0.0

    def _refresh(self) -> None:
        cfg = self.state.config
        yearly = self.yearly.value()
        if yearly > 0:
            kw = baseload_from_yearly(yearly, self._car_share())
            text = _("= an average of {kw} kW for the house").format(kw=number(kw, 2))
            if self.includes_car.isChecked():
                text += " " + _("(about {kwh} kWh of the car taken off)").format(kwh=number(self._car_share()))
            self.result.setText(text)
            self.warning.setText(_("That is more than the plan can use ({max} kW). Check the number.").format(max=MAX_BASELOAD_KW) if kw > MAX_BASELOAD_KW else "")
        else:
            self.result.setText("")
            self.warning.setText("")
        typical = "house.baseload_kw" in cfg.setup.typical
        self.current.setText(
            _("In use now: {kw} kW, a typical value (about {kwh} kWh a year).") .format(kw=number(cfg.house.baseload_kw, 2), kwh=number(yearly_from_baseload(cfg.house.baseload_kw), 0))
            if typical
            else _("In use now: {kw} kW (about {kwh} kWh a year).").format(kw=number(cfg.house.baseload_kw, 2), kwh=number(yearly_from_baseload(cfg.house.baseload_kw), 0))
        )

    def can_continue(self) -> bool:
        return not self.warning.text()  # a number the plan cannot use is said, and Done waits

    def _edited(self, *_args) -> None:
        self._refresh()
        if self._filling or self.yearly.value() <= 0:
            return
        kw = round(baseload_from_yearly(self.yearly.value(), self._car_share()), 4)
        cfg = self.state.config
        self.state.config = replace(cfg, house=replace(cfg.house, baseload_kw=min(kw, MAX_BASELOAD_KW)))
        self.state.give_value("house.baseload_kw")
        self.state.done.add("house")
        self._refresh()
        self.changed.emit()

    def _use_typical(self) -> None:
        """"I don't know": the typical value, remembered as typical. Considering the question is an answer."""
        self.state.config = with_typical(self.state.config, "house.baseload_kw")
        self.state.use_typical("house.baseload_kw")
        self.state.done.add("house")
        self._filling = True
        self.yearly.setValue(0)
        self._filling = False
        self._refresh()
        self.changed.emit()
