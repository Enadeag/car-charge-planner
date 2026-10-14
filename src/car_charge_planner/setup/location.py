"""The Location page: find the place by name, or type its coordinates.

The text typed in the search box goes to the primary forecast provider's place search (the page says so, naming
it); when that provider has none the page asks for coordinates and says why. The time zone comes with a search
result, or defaults to the computer's for coordinates, and can be changed under Advanced.
"""

from __future__ import annotations

import threading
from zoneinfo import available_timezones

from PySide6.QtCore import QObject, Qt, QTimer, Signal
from PySide6.QtWidgets import (
    QDoubleSpinBox,
    QFormLayout,
    QLabel,
    QLineEdit,
    QListWidget,
    QListWidgetItem,
    QVBoxLayout,
    QWidget,
)

from car_charge_planner import colours, i18n
from car_charge_planner.forecast.provider import Place, PlaceResults, Problem
from car_charge_planner.forecast.selection import place_search
from car_charge_planner.i18n import _, ngettext
from car_charge_planner.planner.model import system_timezone
from car_charge_planner.settings.tabs import timezone_box

from .pages import Page, fold

PAUSE_MS = 400  # after the last key: so a search is not sent for every letter


class _Found(QObject):
    done = Signal(int, object)  # which search it answers, a PlaceResults


class LocationPage(Page):
    id = "location"

    @property
    def title(self) -> str:
        return _("Where are your solar panels?")

    def __init__(self, wizard) -> None:
        super().__init__(wizard)
        self._found = _Found()
        self._found.done.connect(self._show_results)
        self._serial = 0  # the newest search: an older answer arriving late is ignored
        self._threads: list[threading.Thread] = []
        self._places: list[Place] = []
        self._tz_edited = False
        self._filling = False

        layout = QVBoxLayout(self)
        self.privacy = QLabel()
        self.privacy.setWordWrap(True)
        self.privacy.setStyleSheet(f"{colours.MUTED_STYLE} font-size: 9pt;")
        layout.addWidget(self.privacy)

        self.search_box = QWidget()
        search = QVBoxLayout(self.search_box)
        search.setContentsMargins(0, 0, 0, 0)
        self.search = QLineEdit()
        self.search.setPlaceholderText(_("Town, or postcode"))
        self.search.setClearButtonEnabled(True)
        self.results = QListWidget()
        self.results.setMaximumHeight(150)
        self.status = QLabel()
        self.status.setWordWrap(True)
        search.addWidget(self.search)
        search.addWidget(self.results)
        search.addWidget(self.status)
        layout.addWidget(self.search_box)

        self.chosen = QLabel()
        self.chosen.setWordWrap(True)
        self.chosen.setStyleSheet("font-weight: bold;")
        layout.addWidget(self.chosen)

        self.coordinates_toggle = fold(_("Enter coordinates instead"))
        layout.addWidget(self.coordinates_toggle, 0, Qt.AlignmentFlag.AlignLeft)
        self.coordinates_box = QWidget()
        coords = QFormLayout(self.coordinates_box)
        self.latitude = QDoubleSpinBox()
        self.latitude.setRange(-90, 90)
        self.latitude.setDecimals(4)
        self.longitude = QDoubleSpinBox()
        self.longitude.setRange(-180, 180)
        self.longitude.setDecimals(4)
        coords.addRow(_("Latitude"), self.latitude)
        coords.addRow(_("Longitude"), self.longitude)
        hint = QLabel(
            _(
                "In a map app, press and hold (or right-click) on your roof to read its coordinates, for example 52.3702, 4.8952."
                " Positive latitude is north, positive longitude is east. 0 and 0 together mean not set."
            )
        )
        hint.setWordWrap(True)
        hint.setStyleSheet(f"{colours.MUTED_STYLE} font-size: 9pt;")
        coords.addRow(hint)
        layout.addWidget(self.coordinates_box)

        self.advanced_toggle = fold(_("Advanced"))
        layout.addWidget(self.advanced_toggle, 0, Qt.AlignmentFlag.AlignLeft)
        self.advanced_box = QWidget()
        advanced = QFormLayout(self.advanced_box)
        self.timezone = timezone_box()
        advanced.addRow(_("Time zone"), self.timezone)
        self.zone_error = QLabel()
        self.zone_error.setStyleSheet(f"color: {colours.error(self)};")
        advanced.addRow(self.zone_error)
        layout.addWidget(self.advanced_box)
        layout.addStretch()

        self.credit = QLabel()
        self.credit.setStyleSheet(f"{colours.MUTED_STYLE} font-size: 8pt;")
        layout.addWidget(self.credit)

        self.timer = QTimer(self)
        self.timer.setSingleShot(True)
        self.timer.setInterval(PAUSE_MS)
        self.timer.timeout.connect(self.search_now)
        self.search.textChanged.connect(self._typed)
        self.search.returnPressed.connect(self.search_now)
        self.results.itemActivated.connect(self._picked)
        self.results.itemClicked.connect(self._picked)
        self.coordinates_toggle.toggled.connect(self.coordinates_box.setVisible)
        self.advanced_toggle.toggled.connect(self.advanced_box.setVisible)
        self.latitude.valueChanged.connect(self._coordinates_edited)
        self.longitude.valueChanged.connect(self._coordinates_edited)
        self.timezone.currentTextChanged.connect(self._zone_edited)

    def enter(self) -> None:
        cfg = self.state.config
        found = place_search(cfg, self.wizard.registry)
        provider = found[0] if found else self.wizard.primary_provider()
        name = provider.name if provider else _("the forecast service")
        self.searchable = found is not None
        self.search_box.setVisible(self.searchable)
        if self.searchable:
            self.privacy.setText(_("What you type here is sent to {provider} to find the place.").format(provider=name))
            credit = getattr(provider, "place_attribution", "")
        else:
            self.privacy.setText(_("{provider} has no place search, so enter the coordinates of your panels instead.").format(provider=name))
            credit = ""
        self.credit.setText(credit)
        self.credit.setVisible(bool(credit))
        self._filling = True
        self.latitude.setValue(cfg.system.latitude)
        self.longitude.setValue(cfg.system.longitude)
        self.timezone.setCurrentText(cfg.system.timezone)
        self._filling = False
        self._tz_edited = False
        self.zone_error.clear()
        show_coordinates = (not self.searchable) or (cfg.is_set_up and not self.state.place_label)
        self.coordinates_toggle.setChecked(show_coordinates)
        self.coordinates_box.setVisible(show_coordinates)
        self.coordinates_toggle.setVisible(self.searchable)  # without a search the coordinates are the only way: no toggle
        self.advanced_toggle.setChecked(False)
        self.advanced_box.setVisible(False)
        self._refresh_chosen()

    def can_continue(self) -> bool:
        return self.state.has_location and self._zone_ok()

    def _typed(self, _text: str) -> None:
        self.timer.start()

    def search_now(self) -> None:
        """Ask the place search about what is in the box, on a worker thread. Newest answer wins."""
        self.timer.stop()
        found = place_search(self.state.config, self.wizard.registry)
        text = " ".join(self.search.text().split())
        if found is None:
            return
        self._serial += 1
        serial = self._serial
        if len(text) < 2:
            self.results.clear()
            self._places = []
            self.status.setStyleSheet(colours.MUTED_STYLE)
            self.status.setText(_("Type at least two characters.") if text else "")
            return
        self.status.setStyleSheet(colours.MUTED_STYLE)
        self.status.setText(_("Searching..."))
        search, language = found[1], i18n.current_language() or "en"

        def work() -> None:
            self._found.done.emit(serial, search(text, language))

        thread = threading.Thread(target=work, daemon=True)
        self._threads.append(thread)
        thread.start()

    def _show_results(self, serial: int, results: PlaceResults) -> None:
        if serial != self._serial:
            return  # the person has typed on since
        self.results.clear()
        self._places = list(results.places)
        if results.problem is not None:
            self._show_problem(results)
            return
        if not results.places:
            self.status.setStyleSheet(colours.MUTED_STYLE)
            self.status.setText(_("No place found. Try another spelling, or enter the coordinates instead."))
            return
        labels = [p.label for p in results.places]
        for place, label in zip(results.places, labels, strict=True):
            item = QListWidgetItem(place.detailed_label if labels.count(label) > 1 else label)  # tell two alike apart
            self.results.addItem(item)
        self.status.setStyleSheet(colours.MUTED_STYLE)
        self.status.setText(ngettext("{n} place found. Pick yours.", "{n} places found. Pick yours.", len(results.places)).format(n=len(results.places)))

    def _show_problem(self, results: PlaceResults) -> None:
        self.status.setStyleSheet(f"color: {colours.warning(self)};")
        if results.problem is Problem.RATE_LIMITED:
            self.status.setText(_("The search service is busy. Try again in a minute, or enter the coordinates instead."))
        elif results.problem is Problem.UNAVAILABLE:
            self.status.setText(_("Could not reach the search service. Check your internet connection, or enter the coordinates instead."))
        else:
            self.status.setText(_("The search service gave an answer the app does not understand. Enter the coordinates instead."))
        self.coordinates_toggle.setChecked(True)  # the way out is in sight

    def _picked(self, item: QListWidgetItem) -> None:
        row = self.results.row(item)
        if not 0 <= row < len(self._places):
            return
        place = self._places[row]
        self.results.setCurrentItem(item)  # shown as the one picked
        self.state.place_label = item.text()
        cfg = self.state.config
        self.state.config = cfg.with_system(latitude=place.latitude, longitude=place.longitude, timezone=place.timezone)
        self._filling = True
        self.latitude.setValue(place.latitude)
        self.longitude.setValue(place.longitude)
        self.timezone.setCurrentText(place.timezone)
        self._filling = False
        self._tz_edited = False
        self._refresh_chosen()
        self.changed.emit()

    def _coordinates_edited(self, _value: float) -> None:
        if self._filling:
            return
        cfg = self.state.config
        self.state.place_label = ""  # typed by hand: the recap shows the coordinates
        zone = cfg.system.timezone if self._tz_edited else system_timezone()
        self.state.config = cfg.with_system(latitude=self.latitude.value(), longitude=self.longitude.value(), timezone=zone)
        if not self._tz_edited:
            self._filling = True
            self.timezone.setCurrentText(zone)
            self._filling = False
        self._refresh_chosen()
        self.changed.emit()

    def _zone_edited(self, text: str) -> None:
        if self._filling:
            return
        self._tz_edited = True
        self._zone_ok()
        cfg = self.state.config
        if text in available_timezones():
            self.state.config = cfg.with_system(timezone=text)
            self._refresh_chosen()
        self.changed.emit()

    def _zone_ok(self) -> bool:
        text = self.timezone.currentText().strip()
        good = text in available_timezones()
        self.zone_error.setText("" if good else _("That is not a time zone the app knows. Pick one from the list."))
        if not good:
            self.advanced_toggle.setChecked(True)  # the field that needs fixing is in sight
        return good

    def _refresh_chosen(self) -> None:
        cfg = self.state.config
        if not cfg.is_set_up:
            self.chosen.setText(_("No place chosen yet."))
            self.chosen.setStyleSheet(colours.MUTED_STYLE)
            return
        where = self.state.place_label or _("{latitude}, {longitude}").format(latitude=f"{cfg.system.latitude:.4f}", longitude=f"{cfg.system.longitude:.4f}")
        self.chosen.setStyleSheet("font-weight: bold;")
        self.chosen.setText(_("Chosen: {place} ({zone})").format(place=where, zone=cfg.system.timezone))
