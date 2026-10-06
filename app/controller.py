"""What the app does, apart from how it shows itself.

The controller owns the config, the forecast refresh, planning, grid advice, announcing windows and
the owner's edits (battery level, away days). It never touches a tray icon or a popup: after every
change it emits `view_changed` with everything a shell needs to draw, and a shell (tray or window)
listens. Both shells therefore behave the same, and the logic is tested without either.
"""

from __future__ import annotations

import threading
from dataclasses import dataclass
from datetime import date, datetime
from pathlib import Path
from zoneinfo import ZoneInfo

from PySide6.QtCore import QObject, QTimer, Signal

from forecast.open_meteo import ForecastResult, ForecastUnavailable, forecast_key, get_forecast
from planner.advice import grid_advice
from planner.model import AwayPeriod, AwayRule, Config
from planner.projection import plan
from storage.state import State, add_away, load_state, mark_notified, remove_away, set_battery

from .notifications import candidate_windows, due, message
from .notifier import Notifier
from .settings.window import SettingsWindow
from .view_model import ViewModel, build, needs_setup, no_forecast

TICK_MS = 60_000  # recompute every minute; the forecast itself refreshes at most hourly


@dataclass(frozen=True)
class ViewUpdate:
    """Everything a shell needs to draw the current plan."""

    vm: ViewModel
    state: State
    today: date
    rules: tuple[AwayRule, ...]
    tz: ZoneInfo


class Controller(QObject):
    forecast_loaded = Signal(object, object)  # the Config the fetch ran under, ForecastResult | Exception
    view_changed = Signal(object)  # a ViewUpdate

    def __init__(self, cfg: Config, config_file: Path, notifier: Notifier | None = None) -> None:
        super().__init__()
        self.notifier = notifier or Notifier()
        self.cfg = cfg
        self.config_file = config_file
        self.tz = ZoneInfo(cfg.timezone)
        self.result: ForecastResult | None = None
        self.error: str | None = None
        self.vm: ViewModel | None = None
        self._loading = False
        self.settings_window: SettingsWindow | None = None

        self.forecast_loaded.connect(self._on_forecast)
        self.timer = QTimer(self)
        self.timer.timeout.connect(self.refresh)
        self.timer.start(TICK_MS)

    def start(self) -> None:
        self.refresh()

    # -- the forecast ---------------------------------------------------------

    def refresh(self, force: bool = False) -> None:
        """Fetch (cache-aware) off the UI thread; the result comes back as a signal."""
        if not self.cfg.is_set_up:
            self.rebuild()  # no location yet: there is nothing to ask a forecast service, and nothing leaves the machine
            return
        if self._loading:
            return
        self._loading = True
        cfg, tz = self.cfg, self.tz  # the worker keeps the config it started with

        def work() -> None:
            try:
                outcome: object = get_forecast(cfg, datetime.now(tz), force=force)
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

    # -- settings -------------------------------------------------------------

    def open_settings(self, tab: str | None = None) -> None:
        """One Settings window at a time: opening it again brings the open one forward."""
        if self.settings_window is None or not self.settings_window.isVisible():
            self.settings_window = SettingsWindow(self.cfg, self.config_file, self.apply_config)
        if tab:
            self.settings_window.show_tab(tab)
        self.settings_window.show_centred()

    def apply_config(self, cfg: Config) -> None:
        """Switch to a new config without a restart (settings spec 3.3)."""
        if forecast_key(cfg) != forecast_key(self.cfg):
            self.result, self.error = None, None  # a forecast for other panels or place would mislead
        if cfg.interface.mode != self.cfg.interface.mode:
            # the shell is chosen once at start: say so, since nothing visible happens now
            self.notifier.send("Interface changed", "The new interface applies the next time the app starts.")
        self.cfg = cfg
        self.tz = ZoneInfo(cfg.timezone)
        self.rebuild()
        self.refresh()  # reuses the cache when the forecast key is unchanged

    # -- planning and announcing ---------------------------------------------

    def rebuild(self) -> None:
        now = datetime.now(self.tz)
        state = load_state()
        battery = state.battery
        if not self.cfg.is_set_up:
            vm = needs_setup(battery, now)
        elif self.result is None:
            vm = no_forecast(self.error or "Loading forecast...", battery, now)
        else:
            days = plan(self.result.steps, self.cfg, battery.pct, now, list(state.away), list(state.notified))
            advice = grid_advice(days, self.result.steps, self.cfg, list(state.away), now)
            vm = build(days, now, self.cfg, battery, self.result.age(now), self.result.stale, advice)
            self._announce(candidate_windows(days, advice), state, now)
        self.vm = vm
        self.view_changed.emit(ViewUpdate(vm, state, now.date(), self.cfg.away_rules, self.tz))

    def _announce(self, windows, state: State, now: datetime) -> None:
        """Notify once per window, a few minutes ahead (spec 5)."""
        for kind, window in due(
            windows, state.notified, now, self.cfg.notify.lead_minutes, self.result.age(now), self.result.stale
        ):
            title, body = message(kind, window, self.cfg, self.tz)
            self.notifier.send(title, body)
            mark_notified(window, now)

    # -- the owner's edits ----------------------------------------------------

    def set_battery(self, pct: float) -> None:
        set_battery(pct, datetime.now(self.tz))
        self.rebuild()

    def add_away_period(self, period: AwayPeriod) -> None:
        add_away(period, datetime.now(self.tz))
        self.rebuild()

    def remove_away_period(self, period: AwayPeriod) -> None:
        remove_away(period)
        self.rebuild()
