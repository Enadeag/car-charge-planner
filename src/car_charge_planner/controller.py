"""The desktop app's hold on the engine: Qt's timer and threads for it, and its updates as the `view_changed` signal."""

from __future__ import annotations

import threading
from collections.abc import Callable
from pathlib import Path
from typing import Any

from PySide6.QtCore import QObject, QTimer, Signal

from car_charge_planner.planner.model import Config

from .engine import Clock, Engine, Forecasts, Runner, system_clock
from .i18n import _
from .notifier import Notifying, make_notifier

TICK_MS = 60_000  # the engine plans again every minute; the forecast itself refreshes at most hourly


class Controller(QObject):
    """Shells change things through `engine` and draw what `view_changed` brings, on the Qt thread.

    A new config goes through `apply_config` here instead: its interface settings are the desktop app's, not the engine's.
    """

    view_changed = Signal(object)  # ViewUpdate
    _finished = Signal(object, object)  # a background job's `done` and its result, carried to the Qt thread

    def __init__(
        self,
        cfg: Config,
        config_file: Path,
        notifier: Notifying | None = None,
        forecasts: Forecasts | None = None,
        clock: Clock = system_clock,
        run_in_background: Runner | None = None,
    ) -> None:
        super().__init__()
        self.notifier = notifier or make_notifier()
        self.config_file = config_file
        self._finished.connect(self._deliver)
        self.engine = Engine(cfg, self.notifier, forecasts, clock, run_in_background or self._run_in_thread)
        self.engine.watch(self.view_changed.emit)
        self.timer = QTimer(self)
        self.timer.timeout.connect(self.engine.tick)
        self.timer.start(TICK_MS)

    def start(self) -> None:
        self.engine.refresh()

    def apply_config(self, cfg: Config) -> None:
        current = self.engine.cfg.interface
        if cfg.interface.mode != current.mode or cfg.interface.language != current.language:
            # the shell and the language are chosen once at start: say so, since nothing visible happens now
            self.notifier.send(_("Interface changed"), _("The new interface applies the next time the app starts."))
        self.engine.apply_config(cfg)

    def _run_in_thread(self, work: Callable[[], Any], done: Callable[[Any], None]) -> None:
        threading.Thread(target=lambda: self._finished.emit(done, work()), daemon=True).start()

    def _deliver(self, done: Callable[[Any], None], result: object) -> None:
        done(result)
