"""The desktop app's hold on the engine: Qt's timer and threads for it, and its updates as the `view_changed` signal."""

from __future__ import annotations

import threading
from collections.abc import Callable
from typing import Any

from PySide6.QtCore import QObject, QTimer, Signal

from car_charge_planner.app_settings import AppSettings, DesktopSettings
from car_charge_planner.config.model import Config
from car_charge_planner.forecast.registry import ProviderRegistry

from .engine import Clock, Engine, Forecasts, Runner, Store, system_clock
from .i18n import _
from .notifier import Notifying, make_notifier

TICK_MS = 60_000  # the engine plans again every minute; the forecast itself refreshes at most hourly


class Controller(QObject):
    """Shells change things through `engine` (and the app's own settings through `app_settings`) and draw what
    `view_changed` brings, on the Qt thread."""

    view_changed = Signal(object)  # ViewUpdate
    _finished = Signal(object, object)  # a background job's `done` and its result, carried to the Qt thread

    def __init__(
        self,
        cfg: Config,
        store: Store,
        notifier: Notifying | None = None,
        forecasts: Forecasts | None = None,
        clock: Clock = system_clock,
        run_in_background: Runner | None = None,
        registry: ProviderRegistry | None = None,
        app_settings: DesktopSettings | None = None,
    ) -> None:
        super().__init__()
        self.notifier = notifier or make_notifier()
        self._finished.connect(self._deliver)
        runner = run_in_background or self._run_in_thread
        self.engine = Engine(cfg, self.notifier, forecasts, clock, runner, store, registry)
        self.engine.watch(self.view_changed.emit)
        self.app_settings = app_settings or DesktopSettings()
        self.app_settings.watch(self._app_settings_changed)
        self.timer = QTimer(self)
        self.timer.timeout.connect(self.engine.tick)
        self.timer.start(TICK_MS)

    def start(self) -> None:
        self.engine.refresh()

    def _app_settings_changed(self, old: AppSettings, new: AppSettings) -> None:
        if new.mode != old.mode or new.language != old.language:
            # the shell and the language are chosen once at start: say so, since nothing visible happens now
            self.notifier.send(_("Interface changed"), _("The new interface applies the next time the app starts."))

    def _run_in_thread(self, work: Callable[[], Any], done: Callable[[Any], None]) -> None:
        threading.Thread(target=lambda: self._finished.emit(done, work()), daemon=True).start()

    def _deliver(self, done: Callable[[Any], None], result: object) -> None:
        done(result)
