"""The Forecast tab: which providers give the forecast, their settings, and a way to try them (spec section 7).

Two roles, the primary provider and an optional fallback, each picked from the providers the app knows. Every
provider has a card (what it is, what it sends, its limits, its terms, its settings); a card sits under the role
it is chosen for. Plain settings go to config.toml; secret ones are kept apart and saved to secrets.toml by the
window.
"""

from __future__ import annotations

import threading
from collections.abc import Callable
from dataclasses import replace

from PySide6.QtCore import QObject, Signal
from PySide6.QtWidgets import QComboBox, QFrame, QHBoxLayout, QLabel, QPushButton, QScrollArea, QVBoxLayout, QWidget

from car_charge_planner import colours
from car_charge_planner.forecast.check import missing_settings, try_provider
from car_charge_planner.forecast.provider import ForecastProvider
from car_charge_planner.forecast.registry import ProviderRegistry
from car_charge_planner.forecast.registry import registry as builtin
from car_charge_planner.forecast.secret_store import Secrets
from car_charge_planner.i18n import _, ngettext
from car_charge_planner.planner.model import Config

from .fields import TabSpec
from .providers import provider_fields
from .tabs import FieldTab

NONE = ""  # the fallback's "no provider"


class _Outcome(QObject):
    """Carries a test's result from its worker thread to the window."""

    done = Signal(str, bool, str)  # role, worked, message


class ProviderCard(QWidget):
    """One provider: its description, what it sends, its limits, its terms, and its settings."""

    def __init__(self, provider: ForecastProvider, cfg: Config, secrets: Secrets) -> None:
        super().__init__()
        self.provider = provider
        layout = QVBoxLayout(self)
        layout.setContentsMargins(0, 4, 0, 0)

        def note(text: str, *, rich: bool = False) -> QLabel:
            label = QLabel(text)
            label.setWordWrap(True)
            if rich:
                label.setOpenExternalLinks(True)
            label.setStyleSheet(f"{colours.MUTED_STYLE} font-size: 9pt;")
            layout.addWidget(label)
            return label

        note(_(provider.description))
        note(_("What is sent: {sends}").format(sends=_(provider.sends)))
        minutes = round(provider.min_refresh.total_seconds() / 60)
        days = max(1, round(provider.horizon.total_seconds() / 86400))
        every = ngettext("Asks at most once every {n} minute.", "Asks at most once every {n} minutes.", minutes).format(n=minutes)
        ahead = ngettext("Looks {n} day ahead.", "Looks {n} days ahead.", days).format(n=days)
        note(f"{every} {ahead}")
        # the default link blue is unreadable on a dark window, and a style sheet hides the palette's link colour
        colour = colours.link(self)
        self.terms = note(f'<style>a {{ color: {colour}; }}</style><a href="{provider.terms_url}">{_("Terms and limits")}</a>', rich=True)

        flat = {f"forecast.{provider.id}.{key}": value for key, value in secrets.get(provider.id, {}).items()}
        fields = TabSpec(provider.id, provider.name, provider_fields(provider))
        self.fields = FieldTab(fields, cfg, flat)
        layout.addWidget(self.fields)


class ForecastTab(QWidget):
    changed = Signal()  # any choice or setting edited, or the tab (re)loaded

    def __init__(
        self,
        cfg: Config,
        secrets: Secrets | None = None,
        registry: ProviderRegistry | None = None,
        current_config: Callable[[], Config] | None = None,
        secrets_problem: str = "",
    ) -> None:
        super().__init__()
        self.key = "forecast"
        self.title = _("Forecast")
        self.registry = registry or builtin
        self.saved_secrets: Secrets = {k: dict(v) for k, v in (secrets or {}).items()}
        self.current_config = current_config or (lambda: self.stored)
        self.stored = cfg
        self._outcome = _Outcome()
        self._outcome.done.connect(self._show_result)
        self._threads: list[threading.Thread] = []

        # Two providers with their settings are taller than the other tabs: the content scrolls instead of being squeezed.
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
        if secrets_problem:
            problem = QLabel(secrets_problem)
            problem.setWordWrap(True)
            problem.setStyleSheet(f"color: {colours.error(self)};")
            layout.addWidget(problem)
        self.cards: dict[str, ProviderCard] = {}
        for provider in self.registry.all():
            card = ProviderCard(provider, cfg, self.saved_secrets)
            card.fields.changed.connect(self.changed)
            self.cards[provider.id] = card

        self.slots: dict[str, QVBoxLayout] = {}
        self.pickers: dict[str, QComboBox] = {}
        self.test_buttons: dict[str, QPushButton] = {}
        self.results: dict[str, QLabel] = {}
        for role, heading, hint in (
            ("primary", _("Forecast provider"), ""),
            ("fallback", _("Fallback provider"), _("Asked only when the first provider has no forecast that is still usable.")),
        ):
            if role == "fallback":
                layout.addSpacing(14)
            title = QLabel(heading)
            title.setStyleSheet("font-weight: bold;")
            layout.addWidget(title)
            picker = QComboBox()
            self.pickers[role] = picker
            layout.addWidget(picker)
            if hint:
                hint_label = QLabel(hint)
                hint_label.setWordWrap(True)
                hint_label.setStyleSheet(f"{colours.MUTED_STYLE} font-size: 9pt;")
                layout.addWidget(hint_label)
            slot = QVBoxLayout()
            self.slots[role] = slot
            layout.addLayout(slot)
            row = QHBoxLayout()
            button = QPushButton(_("Test"))
            button.setToolTip(_("Ask this provider for a forecast now, with what is entered here. Nothing is saved."))
            button.setAutoDefault(False)
            button.clicked.connect(lambda _checked=False, r=role: self.test(r))
            self.test_buttons[role] = button
            result = QLabel()
            result.setWordWrap(True)
            self.results[role] = result
            row.addWidget(button)
            row.addWidget(result, 1)
            layout.addLayout(row)
        layout.addStretch()

        for role, picker in self.pickers.items():
            picker.currentIndexChanged.connect(lambda _i, r=role: self._picked(r))
        self.load(cfg)

    # -- the choices --------------------------------------------------------------------------------------------

    def _fill(self, role: str, chosen: str) -> None:
        picker = self.pickers[role]
        picker.blockSignals(True)
        picker.clear()
        if role == "fallback":
            picker.addItem(_("None"), NONE)
        for provider in self.registry.all():
            picker.addItem(provider.name, provider.id)
        if chosen and chosen not in self.registry:  # chosen by a newer version: keep it until the owner picks another
            picker.addItem(_("{id} (not available)").format(id=chosen), chosen)
        picker.setCurrentIndex(max(picker.findData(chosen), 0))
        picker.blockSignals(False)

    def load(self, cfg: Config) -> None:
        self.stored = cfg
        for card in self.cards.values():
            card.fields.load(cfg)
        self._fill("primary", cfg.forecast.primary)
        self._fill("fallback", cfg.forecast.fallback)
        self._place_cards()
        for result in self.results.values():
            result.clear()

    def reset(self) -> None:
        """Back to the built-in choice: Open-Meteo, no fallback, no settings, no keys."""
        self.saved_secrets = {}
        for card in self.cards.values():
            card.fields._secrets = {}
        self.load(Config())

    def remember_secrets(self, secrets: Secrets) -> None:
        """What was saved; a reload (after Save) starts from it."""
        self.saved_secrets = {k: dict(v) for k, v in secrets.items()}
        for provider_id, card in self.cards.items():
            card.fields._secrets = {f"forecast.{provider_id}.{k}": v for k, v in self.saved_secrets.get(provider_id, {}).items()}

    def _chosen(self, role: str) -> str:
        return self.pickers[role].currentData() or ""

    def _picked(self, role: str) -> None:
        self._place_cards()
        self.changed.emit()

    def _place_cards(self) -> None:
        """Each card sits under the role it is chosen for. One provider cannot be both: the second role then says so."""
        for slot in self.slots.values():
            while slot.count():
                item = slot.takeAt(0)
                widget = item.widget()  # pyright: ignore[reportOptionalMemberAccess]  # takeAt(0) of a non-empty layout
                if widget is not None:
                    widget.hide()
                    widget.setParent(self)
        primary, fallback = self._chosen("primary"), self._chosen("fallback")
        for role, provider_id in (("primary", primary), ("fallback", fallback)):
            card = self.cards.get(provider_id)
            clash = role == "fallback" and provider_id and provider_id == primary
            if card is not None and not clash:
                self.slots[role].addWidget(card)
                card.show()
            self.test_buttons[role].setEnabled(card is not None and not clash)
            if clash:
                self.results[role].setText(_("Choose a different provider than the first one."))
                self.results[role].setStyleSheet(f"color: {colours.error(self)};")
            elif self.results[role].styleSheet():
                self.results[role].clear()
                self.results[role].setStyleSheet("")

    # -- reading the boxes --------------------------------------------------------------------------------------

    def apply_to(self, cfg: Config) -> Config:
        for card in self.cards.values():
            cfg = card.fields.apply_to(cfg)
        return replace(cfg, forecast=replace(cfg.forecast, primary=self._chosen("primary"), fallback=self._chosen("fallback")))

    def collect_secrets(self) -> Secrets:
        """The secrets as they stand: the saved ones, with what the boxes hold laid over them."""
        secrets: Secrets = {k: dict(v) for k, v in self.saved_secrets.items()}
        for provider_id, card in self.cards.items():
            held = {key.split(".", 2)[2]: value for key, value in card.fields.collect_secrets().items()}
            declared = {s.key for s in card.provider.settings if s.secret}
            values = {k: v for k, v in secrets.get(provider_id, {}).items() if k not in declared}  # undeclared ones stay
            values.update(held)
            if values:
                secrets[provider_id] = values
            else:
                secrets.pop(provider_id, None)
        return secrets

    # -- the Test button ----------------------------------------------------------------------------------------

    def test(self, role: str) -> None:
        """One fetch on a worker thread, with what is entered now (nothing is saved)."""
        provider = self.registry.get(self._chosen(role)) if self._chosen(role) in self.registry else None
        if provider is None:
            return
        cfg = self.apply_to(self.current_config())
        if not cfg.is_set_up:
            self._show_result(role, False, _("Enter your location first, on the Solar and location tab."))
            return
        secrets = self.collect_secrets()
        missing = missing_settings(provider, cfg, secrets)
        if missing:
            self._show_result(role, False, _("{setting} is required.").format(setting=", ".join(missing)))
            return
        self.test_buttons[role].setEnabled(False)
        self.results[role].setStyleSheet(f"{colours.MUTED_STYLE}")
        self.results[role].setText(_("Asking {provider}...").format(provider=provider.name))

        def work() -> None:
            attempt = try_provider(provider, cfg, secrets)
            self._outcome.done.emit(role, attempt.worked, attempt.message)

        thread = threading.Thread(target=work, daemon=True)
        self._threads.append(thread)
        thread.start()

    def _show_result(self, role: str, worked: bool, message: str) -> None:
        colour = colours.success(self) if worked else colours.error(self)
        self.results[role].setStyleSheet(f"color: {colour};")
        self.results[role].setText(message)
        card = self.cards.get(self._chosen(role))
        clash = role == "fallback" and self._chosen(role) == self._chosen("primary")
        self.test_buttons[role].setEnabled(card is not None and not clash)
