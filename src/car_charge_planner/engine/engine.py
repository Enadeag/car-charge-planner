"""The engine's one class: after every change it hands a `ViewUpdate` to the clients that watch it."""

from __future__ import annotations

from collections.abc import Callable
from dataclasses import dataclass
from datetime import date, datetime
from zoneinfo import ZoneInfo

from car_charge_planner.config.model import Config, ConfigError, require_location, validate
from car_charge_planner.forecast.check import Attempt, try_provider
from car_charge_planner.forecast.registry import ProviderRegistry
from car_charge_planner.forecast.registry import registry as builtin
from car_charge_planner.forecast.secret_store import Secrets, SecretsError
from car_charge_planner.forecast.selection import check_selection
from car_charge_planner.forecast.service import ForecastResult, ForecastService, forecast_key
from car_charge_planner.planner.advice import grid_advice
from car_charge_planner.planner.model import AwayPeriod, AwayRule, Window
from car_charge_planner.planner.projection import plan
from car_charge_planner.storage.state import State, with_away, with_battery, with_notified, with_seen, without_away

from ..day_view import DayView, build_days
from ..i18n import _
from ..notifications import candidate_windows, due, message
from ..notifier import Notifying
from ..view_model import ForecastSource, ViewModel, build, needs_setup, no_forecast
from .ports import Clock, Forecasts, Runner, run_here, system_clock
from .store import FileStore, KeyStorage, Store, StoreError


@dataclass(frozen=True)
class ViewUpdate:
    vm: ViewModel
    state: State
    today: date
    rules: tuple[AwayRule, ...]
    tz: ZoneInfo
    days: tuple[DayView, ...] = ()  # one per planned day; empty without a plan


@dataclass(frozen=True)
class SavedKeys:
    values: Secrets
    problem: str = ""  # why they could not be read, for the owner; `values` is empty then


class Engine:
    """Owns the stored data: clients change settings, state and keys only through these methods."""

    def __init__(
        self,
        cfg: Config,
        notifier: Notifying,
        forecasts: Forecasts | None = None,
        clock: Clock = system_clock,
        run_in_background: Runner = run_here,
        store: Store | None = None,
        registry: ProviderRegistry | None = None,
    ) -> None:
        self._cfg = cfg
        self._tz = ZoneInfo(cfg.system.timezone)
        self._notifier = notifier
        self._store: Store = store or FileStore()
        self.registry = registry or builtin
        self._forecasts: Forecasts = forecasts or ForecastService(self.registry, secrets=self._store.load_secrets)
        self._clock = clock
        self._run = run_in_background
        self._result: ForecastResult | None = None
        self._error: str | None = None
        self._vm: ViewModel | None = None
        self._fetching = False
        self._watchers: list[Callable[[ViewUpdate], None]] = []
        self._settings_watchers: list[Callable[[Config, Config], None]] = []

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

    def watch_settings(self, watcher: Callable[[Config, Config], None]) -> None:
        """`watcher(old, new)` is called just before the engine switches to new settings."""
        self._settings_watchers.append(watcher)

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
        """Switch to a new config without a restart (nothing is saved: `save_settings` saves and switches)."""
        for watcher in self._settings_watchers:
            watcher(self._cfg, cfg)
        self._forecasts.forget_failures()  # a corrected key or address deserves a try at once, not after the wait
        if forecast_key(cfg) != forecast_key(self._cfg):
            self._result, self._error = None, None  # a forecast for other panels or place would mislead
        self._cfg = cfg
        self._tz = ZoneInfo(cfg.system.timezone)
        self.rebuild()
        self.refresh()  # reuses the cache when the forecast key is unchanged

    def check_settings(self, cfg: Config, secrets: Secrets) -> list[str]:
        """What stops these settings and keys from being saved, in words for the owner. Empty when they can be."""
        return _invalid(cfg) or check_selection(cfg, secrets, self.registry)

    def save_settings(self, cfg: Config, secrets: Secrets | None = None) -> list[str]:
        """Check, store and switch to the settings, and the keys when given (all of them: a key left out is forgotten).

        Returns what went wrong, in words for the owner; then nothing was switched, and when the keys could not be
        stored the settings were not either."""
        if problems := _invalid(cfg):
            return problems
        try:
            if secrets is not None:
                self._store.save_secrets(secrets)
            self._store.save_config(cfg)
        except (SecretsError, StoreError, ConfigError) as err:
            return [str(err)]
        self.apply_config(cfg)
        return []

    def saved_keys(self) -> SavedKeys:
        try:
            return SavedKeys(self._store.load_secrets())
        except SecretsError as err:
            return SavedKeys({}, str(err))

    def key_storage(self) -> KeyStorage:
        return self._store.key_storage()

    def check_provider(self, provider_id: str, cfg: Config, secrets: Secrets | None, done: Callable[[Attempt], None]) -> None:
        """Ask a provider for one forecast with these settings (and these keys, or the saved ones), through the runner.
        Nothing is cached or saved. The caller has checked that `cfg` has a location."""
        provider = self.registry.get(provider_id) if provider_id in self.registry else None
        if provider is None:
            done(Attempt(False, message=_("The forecast provider {id!r} is not known to this version of the app.").format(id=provider_id)))
            return
        store = self._store

        def work() -> Attempt:
            keys = secrets
            if keys is None:
                try:
                    keys = store.load_secrets()
                except SecretsError:
                    keys = {}  # a provider that needs one says it is missing
            return try_provider(provider, cfg, keys)

        self._run(work, done)

    def mark_seen(self, hint: str) -> bool:
        """Remember that a one-time hint was shown. True the first time only: the client shows the hint then."""
        state = self._store.load_state()
        if hint in state.seen:
            return False
        self._store.save_state(with_seen(state, hint))
        return True

    def set_battery(self, pct: float) -> None:
        self._store.save_state(with_battery(self._store.load_state(), pct, self._now()))
        self.rebuild()

    def add_away_period(self, period: AwayPeriod) -> None:
        self._store.save_state(with_away(self._store.load_state(), period, self._now()))
        self.rebuild()

    def remove_away_period(self, period: AwayPeriod) -> None:
        self._store.save_state(without_away(self._store.load_state(), period))
        self.rebuild()

    def rebuild(self) -> None:
        """Plan again for the current time, announce what is due and send the result to the watchers."""
        now = self._now()
        state = self._store.load_state()
        battery = state.battery
        day_views: tuple[DayView, ...] = ()
        if not self._cfg.is_set_up:
            vm = needs_setup(battery, now)
        elif self._result is None:
            vm = no_forecast(self._error or _("Loading forecast..."), battery, now)
        else:
            result, system, preferences = self._result, self._cfg.system, self._cfg.planning
            days = plan(result.steps, system, preferences, battery.pct, now, list(state.away), list(state.notified))
            advice = grid_advice(days, result.steps, system, preferences, list(state.away), now)
            vm = build(days, now, self._cfg, battery, result.age(now), result.stale, advice, _source(result, now))
            day_views = build_days(days, result.steps, advice, self._cfg, state.away, now)
            self._announce(result, candidate_windows(days, advice), state, now)
        self._vm = vm
        update = ViewUpdate(vm, state, now.date(), self._cfg.system.away_rules, self._tz, day_views)
        for watcher in self._watchers:
            watcher(update)

    def _announce(self, result: ForecastResult, windows: list[tuple[str, Window]], state: State, now: datetime) -> None:
        """Notify once per window, a few minutes ahead."""
        for kind, window in due(windows, state.notified, now, self._cfg.planning.notify_lead_minutes, result.age(now), result.stale):
            title, body = message(kind, window, self._cfg, self._tz)
            self._notifier.send(title, body)
            self._store.save_state(with_notified(self._store.load_state(), window, now))

    def _now(self) -> datetime:
        return self._clock().astimezone(self._tz)


def _invalid(cfg: Config) -> list[str]:
    try:
        require_location(validate(cfg))
    except ConfigError as err:
        return str(err).split("\n")[1:] or [str(err)]  # the first line is a heading over the problems
    except TypeError as err:
        return [_("A value has the wrong type: {error}").format(error=err)]
    return []


def _source(result: ForecastResult, now: datetime) -> ForecastSource:
    return ForecastSource(
        result.attribution,
        result.provider_name,
        result.from_fallback,
        result.primary_name,
        cooling_down=result.cooldown_until is not None and now < result.cooldown_until,
    )
