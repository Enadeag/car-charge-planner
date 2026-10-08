"""The "What next?" page: two optional things that make the plan better, each a card that opens its own page."""

from __future__ import annotations

from PySide6.QtWidgets import QFrame, QHBoxLayout, QLabel, QPushButton, QVBoxLayout

from app import colours
from app.i18n import _, number, pgettext
from app.names import weekday_short

from .pages import Page
from .solar import note


class Card(QFrame):
    def __init__(self, title: str, what: str, skipped: str, button: str, extra: str = "") -> None:
        super().__init__()
        self.setFrameShape(QFrame.Shape.StyledPanel)
        layout = QVBoxLayout(self)
        head = QHBoxLayout()
        self.title = QLabel(title)
        self.title.setStyleSheet("font-weight: bold;")
        self.tick = QLabel()
        head.addWidget(self.title)
        head.addStretch()
        head.addWidget(self.tick)
        layout.addLayout(head)
        what_label = QLabel(what)
        what_label.setWordWrap(True)
        layout.addWidget(what_label)
        layout.addWidget(note(skipped))
        if extra:
            commuters = QLabel(extra)
            commuters.setWordWrap(True)
            commuters.setStyleSheet("font-weight: bold;")
            layout.addWidget(commuters)
        self.status = note("")
        layout.addWidget(self.status)
        row = QHBoxLayout()
        self.add_text = button
        self.button = QPushButton(button)
        self.button.setAutoDefault(False)
        row.addWidget(self.button)
        row.addStretch()
        layout.addLayout(row)

    def show_done(self, done: bool, status: str, colour: str) -> None:
        self.tick.setText(_("✓ Done") if done else "")
        self.tick.setStyleSheet(f"color: {colour}; font-weight: bold;")
        self.status.setText(status)


class HubPage(Page):
    id = "next"

    @property
    def title(self) -> str:
        return _("What next?")

    def __init__(self, wizard) -> None:
        super().__init__(wizard)
        layout = QVBoxLayout(self)
        layout.addWidget(QLabel(_("You can finish now. These two make the plan better, and you can do them later in Settings.")))
        self.house = Card(
            _("House electricity"),
            _("Tells the plan how much power the house itself uses, so it knows what is left for the car."),
            _("If skipped: a typical value is used."),
            _("Add house electricity"),
        )
        self.away = Card(
            _("When is the car away?"),
            _("Stops the plan recommending charging while the car is out, for example at work."),
            _("If skipped: the car is treated as home all day."),
            _("Set the car's away times"),
            extra=_("If you drive to work, set this."),
        )
        layout.addWidget(self.house)
        layout.addWidget(self.away)
        layout.addStretch()
        self.house.button.clicked.connect(lambda: self.wizard.navigate("house"))
        self.away.button.clicked.connect(lambda: self.wizard.navigate("away"))

    def next_label(self) -> str:
        return _("Review and finish")

    def enter(self) -> None:
        cfg = self.state.config
        done, colour = self.state.done, colours.success(self)
        typical = "house.baseload_kw" in cfg.setup.typical
        self.house.show_done(
            "house" in done,
            (_("{kw} kW, a typical value") if typical else _("{kw} kW")).format(kw=number(cfg.house.baseload_kw, 2)) if "house" in done else "",
            colour,
        )
        if cfg.away_rules:
            times = pgettext("list separator", ", ").join(
                _("{weekday} {start}-{end}").format(weekday=weekday_short(r.weekday), start=f"{r.start:%H:%M}", end=f"{r.end:%H:%M}") for r in cfg.away_rules
            )
        else:
            times = _("usually home")
        self.away.show_done("away" in done, times if "away" in done else "", colour)
