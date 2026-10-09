"""What the app does, apart from how it shows itself: after every change it emits `view_changed` for a shell to draw."""

from __future__ import annotations

import threading
from collections.abc import Callable
from dataclasses import dataclass
from datetime import date, datetime
from pathlib import Path
from zoneinfo import ZoneInfo

from PySide6.QtCore import QObject, QTimer, Signal

from car_charge_planner.forecast.service import ForecastResult, ForecastService, ForecastUnavailable, forecast_key
from car_charge_planner.planner.advice import grid_advice
from car_charge_planner.planner.model import AwayPeriod, AwayRule, Config
from car_charge_planner.planner.projection import plan
from car_charge_planner.storage.state import State, add_away, load_state, mark_notified, mark_seen, remove_away, set_battery

from .i18n import _
from .notifications import candidate_windows, due, message
from .notifier import Notifier
from .settings.window import SettingsWindow
from .setup.wizard import SetupWizard
from .view_model import ForecastSource, ViewModel, build, needs_setup, no_forecast

SETUP_OFFER = "set-up-offered"  # the one-time notification offering set-up to a hidden start
TICK_MS = 60_000  # recompute every minute; the forecast itself refreshes at most hourly


@dataclass(frozen=True)
class ViewUpdate:
    vm: ViewModel
    state: State
    today: date
    rules: tuple[AwayRule, ...]
    tz: ZoneInfo


class Controller(QObject):
    setup_completed = Signal()
    forecast_loaded = Signal(object, object)  # the Config the fetch ran under, ForecastResult | Exception
    view_changed = Signal(object)

    def __init__(
        self, cfg: Config, config_file: Path, notifier: Notifier | None = None, forecasts: ForecastService | None = None
    ) -> None:
        super().__init__()
        self.notifier = notifier or Notifier()
        self.forecasts = forecasts or ForecastService()
        self.cfg = cfg
        self.config_file = config_file
        self.tz = ZoneInfo(cfg.timezone)
        self.result: ForecastResult | None = None
        self.error: str | None = None
        self.vm: ViewModel | None = None
        self._loading = False
        self.settings_window: SettingsWindow | None = None
        self.setup_wizard: SetupWizard | None = None

        self.forecast_loaded.connect(self._on_forecast)
        self.timer = QTimer(self)
        self.timer.timeout.connect(self.refresh)
        self.timer.start(TICK_MS)

    def start(self) -> None:
        self.refresh()

    def refresh(self, force: bool = False) -> None:
        """Fetch off the UI thread (cache-aware); the result comes back as a signal."""
        if not self.cfg.is_set_up:
            self.rebuild()  # no location yet: there is nothing to ask a forecast service, and nothing leaves the machine
            return
        if self._loading:
            return
        self._loading = True
        cfg, tz = self.cfg, self.tz  # the worker keeps the config it started with

        def work() -> None:
            try:
                outcome: object = self.forecasts.get(cfg, datetime.now(tz), force=force)
            except ForecastUnavailable as err:
                outcome = err
            except Exception as err:  # never let a worker crash the app silently
                outcome = err
            self.forecast_loaded.emit(cfg, outcome)

        threading.Thread(target=work, daemon=True).start()

    def _on_forecast(self, cfg: Config, outcome: object) -> None:
        self._loading = False
        if cfg is not self.cfg:
            self.refresh()  # started under an older config: its result must not replace the new one
            return
        if isinstance(outcome, ForecastResult):
            self.result, self.error = outcome, None
        else:
            self.error = str(outcome)
        self.rebuild()

    def open_settings(self, tab: str | None = None) -> None:
        """One Settings window at a time: opening it again brings the open one forward."""
        if self.settings_window is None or not self.settings_window.isVisible():
            self.settings_window = SettingsWindow(self.cfg, self.config_file, self.apply_config, on_setup=self.open_setup)
        if tab:
            self.settings_window.show_tab(tab)
        self.settings_window.show_centred()

    def open_setup(self) -> None:
        """One set-up wizard at a time, started from the current config."""
        if self.setup_wizard is None or not self.setup_wizard.isVisible():
            self.setup_wizard = SetupWizard(self.cfg, self.config_file, self._setup_finished)
        self.setup_wizard.show()
        self.setup_wizard.raise_()
        self.setup_wizard.activateWindow()

    def _setup_finished(self, cfg: Config) -> None:
        self.apply_config(cfg)
        self.setup_completed.emit()

    def offer_setup(self, background: bool) -> None:
        """At a start that is not set up: open the wizard, or, when started hidden (at login), only offer it."""
        if self.cfg.is_set_up:
            return
        if not background:
            self.open_setup()
        elif mark_seen(SETUP_OFFER):  # once: a notification at every login would nag
            self.notifier.send(_("Set-up needed"), _("Click here to describe your solar panels and car. It takes a few minutes."))

    def notification_clicked(self, show_plan: Callable[[], None]) -> None:
        """A click on a notification opens the wizard while not set up (the offer asked for it), else the plan."""
        if self.cfg.is_set_up:
            show_plan()
        else:
            self.open_setup()

    def apply_config(self, cfg: Config) -> None:
        """Switch to a new config without a restart."""
        self.forecasts.forget_failures()  # a corrected key or address deserves a try at once, not after the wait
        if forecast_key(cfg) != forecast_key(self.cfg):
            self.result, self.error = None, None  # a forecast for other panels or place would mislead
        if cfg.interface.mode != self.cfg.interface.mode or cfg.interface.language != self.cfg.interface.language:
            # the shell and the language are chosen once at start: say so, since nothing visible happens now
            self.notifier.send(_("Interface changed"), _("The new interface applies the next time the app starts."))
        self.cfg = cfg
        self.tz = ZoneInfo(cfg.timezone)
        self.rebuild()
        self.refresh()  # reuses the cache when the forecast key is unchanged

    def rebuild(self) -> None:
        now = datetime.now(self.tz)
        state = load_state()
        battery = state.battery
        if not self.cfg.is_set_up:
            vm = needs_setup(battery, now)
        elif self.result is None:
            vm = no_forecast(self.error or _("Loading forecast..."), battery, now)
        else:
            days = plan(self.result.steps, self.cfg, battery.pct, now, list(state.away), list(state.notified))
            advice = grid_advice(days, self.result.steps, self.cfg, list(state.away), now)
            vm = build(days, now, self.cfg, battery, self.result.age(now), self.result.stale, advice, self._source(now))
            self._announce(candidate_windows(days, advice), state, now)
        self.vm = vm
        self.view_changed.emit(ViewUpdate(vm, state, now.date(), self.cfg.away_rules, self.tz))

    def _source(self, now: datetime) -> ForecastSource:
        result = self.result
        assert result is not None
        return ForecastSource(
            result.attribution,
            result.provider_name,
            result.from_fallback,
            result.primary_name,
            cooling_down=result.cooldown_until is not None and now < result.cooldown_until,
        )

    def _announce(self, windows, state: State, now: datetime) -> None:
        """Notify once per window, a few minutes ahead."""
        assert self.result is not None
        for kind, window in due(
            windows, state.notified, now, self.cfg.notify.lead_minutes, self.result.age(now), self.result.stale
        ):
            title, body = message(kind, window, self.cfg, self.tz)
            self.notifier.send(title, body)
            mark_notified(window, now)

    def set_battery(self, pct: float) -> None:
        set_battery(pct, datetime.now(self.tz))
        self.rebuild()

    def add_away_period(self, period: AwayPeriod) -> None:
        add_away(period, datetime.now(self.tz))
        self.rebuild()

    def remove_away_period(self, period: AwayPeriod) -> None:
        remove_away(period)
        self.rebuild()
