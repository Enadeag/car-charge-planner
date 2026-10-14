"""What the engine is given from outside: a clock, a way to run slow work, the forecasts and the notifier."""

from __future__ import annotations

from collections.abc import Callable
from datetime import UTC, datetime
from typing import Protocol

from car_charge_planner.forecast.service import ForecastResult
from car_charge_planner.planner.model import Config

Clock = Callable[[], datetime]  # an aware datetime, in any time zone


def system_clock() -> datetime:
    return datetime.now(UTC)


class Runner(Protocol):
    """Runs `work` away from the caller, then calls `done` with its result on the engine's own thread.

    `work` never raises. The engine is not thread-safe: `done` must not run while another engine call does.
    """

    def __call__[T](self, work: Callable[[], T], done: Callable[[T], None]) -> None: ...


def run_here[T](work: Callable[[], T], done: Callable[[T], None]) -> None:
    """The runner that runs at once, on the caller's thread: for a program without an event loop, and for tests."""
    done(work())


class Forecasts(Protocol):
    def get(self, cfg: Config, now: datetime, force: bool = False) -> ForecastResult: ...

    def forget_failures(self) -> None: ...
