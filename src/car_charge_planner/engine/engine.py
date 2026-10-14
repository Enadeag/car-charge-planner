"""The engine's one class: after every change it hands a `ViewUpdate` to the clients that watch it."""

from __future__ import annotations

from collections.abc import Callable
from dataclasses import dataclass
from datetime import date, datetime
from zoneinfo import ZoneInfo

from car_charge_planner.forecast.service import ForecastResult, ForecastService, forecast_key
from car_charge_planner.planner.advice import grid_advice
from car_charge_planner.planner.model import AwayPeriod, AwayRule, Config, Window
from car_charge_planner.planner.projection import plan
from car_charge_planner.storage.state import State, add_away, load_state, mark_notified, remove_away, set_battery

from ..i18n import _
from ..notifications import candidate_windows, due, message
from ..notifier import Notifying
from ..view_model import ForecastSource, ViewModel, build, needs_setup, no_forecast
from .ports import Clock, Forecasts, Runner, run_here, system_clock


@dataclass(frozen=True)
class ViewUpdate:
    vm: ViewModel
    state: State
    today: date
    rules: tuple[AwayRule, ...]
    tz: ZoneInfo


class Engine:
    def __init__(
        self,
        cfg: Config,
        notifier: Notifying,
        forecasts: Forecasts | None = None,
        clock: Clock = system_clock,
        run_in_background: Runner = run_here,
    ) -> None:
        self._cfg = cfg
        self._tz = ZoneInfo(cfg.timezone)
        self._notifier = notifier
        self._forecasts: Forecasts = forecasts or ForecastService()
        self._clock = clock
        self._run = run_in_background
        self._result: ForecastResult | None = None
        self._error: str | None = None
        self._vm: ViewModel | None = None
        self._fetching = False
        self._watchers: list[Callable[[ViewUpdate], None]] = []

    @property
    def cfg(self) -> Config:
        return self._cfg

    @property
    def tz(self) -> ZoneInfo:
        return self._tz

    @property
    def vm(self) -> ViewModel | None:
        """What the last update showed; None before the first."""
        return self._vm

    @property
    def fetching(self) -> bool:
        return self._fetching

    def watch(self, watcher: Callable[[ViewUpdate], None]) -> None:
        self._watchers.append(watcher)

    def tick(self) -> None:
        """Time has passed (the desktop app calls this every minute): fetch if a fetch is due, and plan again."""
        self.refresh()

    def refresh(self, force: bool = False) -> None:
        """Get the forecast through the runner (cache-aware unless `force`), then plan again."""
        if not self._cfg.is_set_up:
            self.rebuild()  # no location yet: there is nothing to ask a forecast service, and nothing leaves the machine
            return
        if self._fetching:
            return
        self._fetching = True
        cfg, now, forecasts = self._cfg, self._now(), self._forecasts  # the fetch keeps the config it started with

        def fetch() -> ForecastResult | Exception:
            try:
                return forecasts.get(cfg, now, force=force)
            except Exception as err:  # never let a fetch crash the app silently
                return err

        self._run(fetch, lambda outcome: self._fetched(cfg, outcome))

    def _fetched(self, cfg: Config, outcome: ForecastResult | Exception) -> None:
        self._fetching = False
        if cfg is not self._cfg:
            self.refresh()  # started under an older config: its result must not replace the new one
            return
        if isinstance(outcome, ForecastResult):
            self._result, self._error = outcome, None
        else:
            self._error = str(outcome)
        self.rebuild()

    def apply_config(self, cfg: Config) -> None:
        """Switch to a new config without a restart."""
        self._forecasts.forget_failures()  # a corrected key or address deserves a try at once, not after the wait
        if forecast_key(cfg) != forecast_key(self._cfg):
            self._result, self._error = None, None  # a forecast for other panels or place would mislead
        self._cfg = cfg
        self._tz = ZoneInfo(cfg.timezone)
        self.rebuild()
        self.refresh()  # reuses the cache when the forecast key is unchanged

    def set_battery(self, pct: float) -> None:
        set_battery(pct, self._now())
        self.rebuild()

    def add_away_period(self, period: AwayPeriod) -> None:
        add_away(period, self._now())
        self.rebuild()

    def remove_away_period(self, period: AwayPeriod) -> None:
        remove_away(period)
        self.rebuild()

    def rebuild(self) -> None:
        """Plan again for the current time, announce what is due and send the result to the watchers."""
        now = self._now()
        state = load_state()
        battery = state.battery
        if not self._cfg.is_set_up:
            vm = needs_setup(battery, now)
        elif self._result is None:
            vm = no_forecast(self._error or _("Loading forecast..."), battery, now)
        else:
            result = self._result
            days = plan(result.steps, self._cfg, battery.pct, now, list(state.away), list(state.notified))
            advice = grid_advice(days, result.steps, self._cfg, list(state.away), now)
            vm = build(days, now, self._cfg, battery, result.age(now), result.stale, advice, _source(result, now))
            self._announce(result, candidate_windows(days, advice), state, now)
        self._vm = vm
        update = ViewUpdate(vm, state, now.date(), self._cfg.away_rules, self._tz)
        for watcher in self._watchers:
            watcher(update)

    def _announce(self, result: ForecastResult, windows: list[tuple[str, Window]], state: State, now: datetime) -> None:
        """Notify once per window, a few minutes ahead."""
        for kind, window in due(windows, state.notified, now, self._cfg.notify.lead_minutes, result.age(now), result.stale):
            title, body = message(kind, window, self._cfg, self._tz)
            self._notifier.send(title, body)
            mark_notified(window, now)

    def _now(self) -> datetime:
        return self._clock().astimezone(self._tz)


def _source(result: ForecastResult, now: datetime) -> ForecastSource:
    return ForecastSource(
        result.attribution,
        result.provider_name,
        result.from_fallback,
        result.primary_name,
        cooling_down=result.cooldown_until is not None and now < result.cooldown_until,
    )
