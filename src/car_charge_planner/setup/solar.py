"""The Solar panels page: size, direction and tilt, with "I don't know" for typical values."""

from __future__ import annotations

from dataclasses import replace

from PySide6.QtWidgets import (
    QButtonGroup,
    QDoubleSpinBox,
    QGridLayout,
    QHBoxLayout,
    QLabel,
    QPushButton,
    QRadioButton,
    QSpinBox,
    QVBoxLayout,
)

from car_charge_planner import colours
from car_charge_planner.i18n import _, number, pgettext, translate
from car_charge_planner.planner.model import Panels

from .pages import CHOICE_BUTTON_STYLE, Page
from .panels import COMPASS, TILT_PRESETS, azimuth_for, compass_key_for, panels_to_kwp, tilt_preset_for
from .recap import compass_label
from .typical import with_typical

KWP = " kWp"  # a unit symbol: the same in every language, so not translated
PANEL_KEYS = ("panels.kwp", "panels.azimuth_deg", "panels.tilt_deg")  # what this page asks (the losses stay typical)


def note(text: str) -> QLabel:
    label = QLabel(text)
    label.setWordWrap(True)
    label.setStyleSheet(f"{colours.MUTED_STYLE} font-size: 9pt;")
    return label


def heading(text: str) -> QLabel:
    label = QLabel(text)
    label.setStyleSheet("font-weight: bold;")
    return label


class SolarPage(Page):
    id = "solar"

    @property
    def title(self) -> str:
        return _("Your solar panels")

    def __init__(self, wizard) -> None:
        super().__init__(wizard)
        self._filling = False
        layout = QVBoxLayout(self)

        top = QHBoxLayout()
        self.dont_know = QPushButton(_("I don't know these"))
        self.dont_know.setAutoDefault(False)
        self.dont_know.setToolTip(_("Use typical values for the size, direction and tilt. You can change them later in Settings."))
        top.addWidget(self.dont_know)
        self.typical_note = note("")
        top.addWidget(self.typical_note, 1)
        layout.addLayout(top)

        # -- size
        layout.addWidget(heading(_("Size")))
        row = QHBoxLayout()
        self.by_total = QRadioButton(_("Total size"))
        self.by_total.setChecked(True)
        self.kwp = QDoubleSpinBox()
        self.kwp.setRange(0.1, 100)
        self.kwp.setDecimals(2)
        self.kwp.setSingleStep(0.1)
        self.kwp.setSuffix(KWP)
        row.addWidget(self.by_total)
        row.addWidget(self.kwp)
        row.addStretch()
        layout.addLayout(row)
        row = QHBoxLayout()
        self.by_count = QRadioButton(_("Or the number of panels"))
        self.count = QSpinBox()
        self.count.setRange(1, 500)
        self.count.setValue(12)
        self.watts = QSpinBox()
        self.watts.setRange(50, 1000)
        self.watts.setSingleStep(10)
        self.watts.setValue(400)
        self.watts.setSuffix(" " + _("W each"))
        self.product = QLabel()
        row.addWidget(self.by_count)
        row.addWidget(self.count)
        row.addWidget(QLabel("×"))
        row.addWidget(self.watts)
        row.addWidget(self.product, 1)
        layout.addLayout(row)
        modes = QButtonGroup(self)
        modes.addButton(self.by_total)
        modes.addButton(self.by_count)
        layout.addWidget(note(_("The total is on the installer's datasheet or invoice, and in the inverter's app. Otherwise count the panels on the roof and read the watts on the label of one.")))

        # -- direction
        layout.addWidget(heading(_("Direction the panels face")))
        row = QHBoxLayout()
        compass = QGridLayout()
        compass.setSpacing(2)
        self.compass_buttons: dict[str, QPushButton] = {}
        group = QButtonGroup(self)
        group.setExclusive(True)
        positions = {"nw": (0, 0), "n": (0, 1), "ne": (0, 2), "w": (1, 0), "e": (1, 2), "sw": (2, 0), "s": (2, 1), "se": (2, 2)}
        for key, short, long_name, _azimuth in COMPASS:
            button = QPushButton(translate(short))
            button.setCheckable(True)
            button.setAutoDefault(False)
            button.setToolTip(translate(long_name))
            button.setMinimumWidth(46)  # a longer abbreviation in another language widens it
            button.setStyleSheet(CHOICE_BUTTON_STYLE)
            group.addButton(button)
            compass.addWidget(button, *positions[key])
            self.compass_buttons[key] = button
            button.clicked.connect(lambda _checked=False, k=key: self._compass_clicked(k))
        row.addLayout(compass)
        right = QVBoxLayout()
        self.degrees = QSpinBox()
        self.degrees.setRange(-180, 180)
        self.degrees.setSuffix("°")
        right.addWidget(self.degrees)
        self.direction_name = note("")
        right.addWidget(self.direction_name)
        right.addStretch()
        row.addLayout(right, 1)
        layout.addLayout(row)
        layout.addWidget(note(_("The direction the panels face, not the way the roof runs. A satellite view in a map app shows it. 0 is south, -90 east, 90 west, 180 north.")))

        # -- tilt
        layout.addWidget(heading(_("Tilt")))
        row = QHBoxLayout()
        self.tilt_buttons: dict[str, QPushButton] = {}
        tilt_group = QButtonGroup(self)
        for key, name, degrees in TILT_PRESETS:
            button = QPushButton(_("{name} ({degrees}°)").format(name=translate(name), degrees=degrees))
            button.setCheckable(True)
            button.setAutoDefault(False)
            tilt_group.addButton(button)
            button.setStyleSheet(CHOICE_BUTTON_STYLE)
            row.addWidget(button)
            self.tilt_buttons[key] = button
            button.clicked.connect(lambda _checked=False, d=degrees: self._tilt_preset(d))
        self.tilt = QSpinBox()
        self.tilt.setRange(0, 90)
        self.tilt.setSuffix("°")
        row.addWidget(self.tilt)
        layout.addLayout(row)
        layout.addWidget(note(_("The angle of the panels from flat. Most pitched roofs are 30 to 40 degrees; the installer or the roof's papers know.")))

        layout.addWidget(note(_("Panels on both an east and a west roof? Enter the total size, face them south and choose a flat tilt (about 10 degrees). It is only an approximation, but it gives a similar curve through the day.")))
        layout.addStretch()

        self.dont_know.clicked.connect(self._use_typical)
        self.kwp.valueChanged.connect(self._kwp_edited)
        self.by_total.toggled.connect(self._mode_changed)
        self.count.valueChanged.connect(self._count_edited)
        self.watts.valueChanged.connect(self._count_edited)
        self.degrees.valueChanged.connect(self._degrees_edited)
        self.tilt.valueChanged.connect(self._tilt_edited)

    # -- showing what the answers say ----------------------------------------------------------------------------

    def enter(self) -> None:
        self._show(self.state.config.panels)

    def _show(self, panels: Panels) -> None:
        self._filling = True
        self.kwp.setValue(panels.kwp)
        self.by_total.setChecked(True)
        self.degrees.setValue(round(panels.azimuth_deg))
        self.tilt.setValue(round(panels.tilt_deg))
        self._filling = False
        self._sync_buttons()
        self._refresh_note()
        self._refresh_product()

    def _sync_buttons(self) -> None:
        key = compass_key_for(self.degrees.value())
        for k, button in self.compass_buttons.items():
            button.setChecked(k == key)
        if key is None:  # between two buttons: none is down, but exclusive groups cannot be all-up by clicking
            for button in self.compass_buttons.values():
                button.group().setExclusive(False)
                button.setChecked(False)
                button.group().setExclusive(True)
        preset = tilt_preset_for(self.tilt.value())
        for k, button in self.tilt_buttons.items():
            button.group().setExclusive(False)
            button.setChecked(k == preset)
            button.group().setExclusive(True)
        self.direction_name.setText(compass_label(self.degrees.value()))

    def _refresh_product(self) -> None:
        self.product.setText(_("= {kwp} kWp").format(kwp=number(panels_to_kwp(self.count.value(), self.watts.value()), 2)))

    def _refresh_note(self) -> None:
        panels = self.state.config.panels
        parts = {
            "panels.kwp": lambda: _("{kwp} kWp").format(kwp=number(panels.kwp, 1)),
            "panels.azimuth_deg": lambda: _("facing {direction}").format(direction=compass_label(panels.azimuth_deg)),
            "panels.tilt_deg": lambda: _("tilt {tilt}\u00b0").format(tilt=number(panels.tilt_deg, 0)),
        }
        typical = [parts[key]() for key in PANEL_KEYS if key in self.state.config.setup.typical]
        if typical:
            self.typical_note.setText(
                _("Typical values in use: {values}. Enter your own, or leave them.").format(values=pgettext("list separator", ", ").join(typical))
            )
        else:
            self.typical_note.setText("")

    # -- what the person does ------------------------------------------------------------------------------------

    def _set_panels(self, key: str, **values) -> None:
        """The person gave this value: it goes into the answers and is no longer a typical one."""
        cfg = self.state.config
        self.state.config = replace(cfg, panels=replace(cfg.panels, **values))
        self.state.give_value(key)
        self._refresh_note()
        self.changed.emit()

    def _kwp_edited(self, value: float) -> None:
        if not self._filling:
            self._set_panels("panels.kwp", kwp=value)
            self._refresh_product()

    def _mode_changed(self, by_total: bool) -> None:
        if not by_total and not self._filling:
            self._count_edited(0)

    def _count_edited(self, _value: int) -> None:
        self._refresh_product()
        if self._filling or self.by_total.isChecked():
            return
        kwp = panels_to_kwp(self.count.value(), self.watts.value())
        self._filling = True
        self.kwp.setValue(kwp)
        self._filling = False
        self._set_panels("panels.kwp", kwp=kwp)

    def _compass_clicked(self, key: str) -> None:
        azimuth = azimuth_for(key)
        self.degrees.setValue(azimuth)
        self._set_panels("panels.azimuth_deg", azimuth_deg=azimuth)  # clicking the direction it already shows is an answer too
        self._sync_buttons()

    def _degrees_edited(self, value: int) -> None:
        if self._filling:
            return
        self._set_panels("panels.azimuth_deg", azimuth_deg=value)
        self._sync_buttons()

    def _tilt_preset(self, degrees: int) -> None:
        self.tilt.setValue(degrees)
        self._set_panels("panels.tilt_deg", tilt_deg=degrees)
        self._sync_buttons()

    def _tilt_edited(self, value: int) -> None:
        if self._filling:
            return
        self._set_panels("panels.tilt_deg", tilt_deg=value)
        self._sync_buttons()

    def _use_typical(self) -> None:
        """"I don't know": the typical size, direction and tilt, remembered as typical."""
        self.state.config = with_typical(self.state.config, *PANEL_KEYS)
        self.state.use_typical(*PANEL_KEYS)
        self._show(self.state.config.panels)
        self.changed.emit()
