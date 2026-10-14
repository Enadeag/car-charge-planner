"""The first page: what the app does, what leaves the computer, and the units."""

from __future__ import annotations

from PySide6.QtWidgets import QComboBox, QHBoxLayout, QLabel, QVBoxLayout

from car_charge_planner import colours
from car_charge_planner.forecast.provider import ForecastProvider
from car_charge_planner.i18n import _
from car_charge_planner.settings.fields import UNITS_CHOICES

from .pages import Page


def privacy_text(provider: ForecastProvider | None) -> str:
    """What leaves the computer, naming the forecast provider and what it is sent."""
    if provider is None:
        return _("Nothing leaves your computer except what the forecast service needs: your location and the angles of your panels. There is no account and no tracking.")
    text = _("Nothing leaves your computer except what is needed for the forecast. {provider} is sent: {sends} There is no account and no tracking.").format(
        provider=provider.name, sends=_(provider.sends)
    )
    if provider.search_places is not None:
        text += " " + _("The place you type in the next step is sent to {provider} to find it.").format(provider=provider.name)
    return text


class WelcomePage(Page):
    id = "welcome"

    @property
    def title(self) -> str:
        return _("Welcome")

    def __init__(self, wizard) -> None:
        super().__init__(wizard)
        layout = QVBoxLayout(self)
        intro = QLabel(
            _(
                "This app tells you when to charge your electric car, so that as much of the power as possible comes from your own solar panels."
                " It needs a few facts about your home and your car, and takes a few minutes."
            )
        )
        intro.setWordWrap(True)
        layout.addWidget(intro)
        self.privacy = QLabel()
        self.privacy.setWordWrap(True)
        self.privacy.setStyleSheet(f"{colours.MUTED_STYLE} font-size: 9pt;")
        layout.addWidget(self.privacy)

        row = QHBoxLayout()
        row.addWidget(QLabel(_("Units")))
        self.units = QComboBox()
        for value, label in UNITS_CHOICES:
            self.units.addItem(_(label), value)
        row.addWidget(self.units)
        row.addStretch()
        layout.addLayout(row)
        hint = QLabel(_("For distances and consumption. You can change it later in Settings."))
        hint.setWordWrap(True)
        hint.setStyleSheet(f"{colours.MUTED_STYLE} font-size: 9pt;")
        layout.addWidget(hint)
        layout.addStretch()
        self.units.currentIndexChanged.connect(lambda _i: self.changed.emit())

    def enter(self) -> None:
        self.units.setCurrentIndex(max(self.units.findData(self.state.units), 0))
        self.privacy.setText(privacy_text(self.wizard.primary_provider()))

    def leave(self) -> None:
        self.state.units = self.units.currentData()
