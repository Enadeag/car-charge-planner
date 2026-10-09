"""A scripted forecast provider for the orchestration tests.

    fake = FakeProvider(script=[steps_for(DAY), Unavailable("no network")])   # first call returns, second raises

Each script entry is one answer: a list of steps is returned, an exception raised, a callable called (its result
treated the same way), and `Held(event, then)` waits for the event first so a test can hold a fetch "in flight".
When the script runs out, the last entry repeats (`repeat_last`) or `IndexError` flags an unplanned call.
`per_array` replaces the script with a function from one panel array to its steps, summed like a real provider.
"""

from __future__ import annotations

import threading
from collections.abc import Callable
from dataclasses import dataclass, field
from datetime import date, datetime, timedelta

from car_charge_planner.forecast.provider import PanelArray, SettingSpec, SystemDescription, each_array
from car_charge_planner.planner.model import ForecastStep
from conftest import forecast_from


@dataclass
class Held:
    release: threading.Event
    then: object
    timeout: float = 5.0


Outcome = list | Exception | Callable[[], object] | Held


def steps_for(day: date, peak: float = 900.0) -> list[ForecastStep]:
    return forecast_from([(day, peak)])


@dataclass
class FakeProvider:
    script: list = field(default_factory=list)
    repeat_last: bool = True
    id: str = "fake"
    name: str = "Fake"
    description: str = "A scripted provider for tests."
    terms_url: str = "https://example.test/terms"
    attribution: str = "Fake forecasts"
    sends: str = "Nothing: it is a fake."
    min_refresh: timedelta = timedelta(hours=1)
    horizon: timedelta = timedelta(days=2)
    max_useful_age: timedelta = timedelta(hours=6)
    settings: tuple[SettingSpec, ...] = ()
    search_places: object = None  # an optional place search: a callable (text, language) -> PlaceResults
    calls: list[tuple[SystemDescription, dict, datetime]] = field(default_factory=list)
    per_array: Callable[[PanelArray], list[ForecastStep]] | None = None

    def fetch(self, system: SystemDescription, settings: dict[str, str], now: datetime) -> list[ForecastStep]:
        self.calls.append((system, dict(settings), now))
        if self.per_array is not None:
            return each_array(system, self.per_array)
        index = len(self.calls) - 1
        if not self.script:
            raise IndexError("the fake provider has no script")
        if index >= len(self.script):
            if not self.repeat_last:
                raise IndexError(f"the fake provider was asked {len(self.calls)} times; the script has {len(self.script)}")
            index = len(self.script) - 1
        return self._resolve(self.script[index])

    def _resolve(self, outcome: Outcome) -> list[ForecastStep]:
        if isinstance(outcome, Held):
            outcome.release.wait(outcome.timeout)
            return self._resolve(outcome.then)
        if isinstance(outcome, Exception):
            raise outcome
        if callable(outcome):
            return self._resolve(outcome())
        return list(outcome)

    @property
    def call_count(self) -> int:
        return len(self.calls)
