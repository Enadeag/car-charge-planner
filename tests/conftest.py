from datetime import date, datetime, timedelta
from zoneinfo import ZoneInfo

import pytest

from planner.demo import bell_day
from planner.model import Config, ForecastStep

TZ = ZoneInfo("Europe/Amsterdam")


def forecast_from(days: list[tuple[date, float]]) -> list[ForecastStep]:
    steps = []
    for day, peak in days:
        for start, gti in bell_day(day, peak, TZ):
            steps.append(ForecastStep(start, start + timedelta(hours=1), gti))
    return steps


def at(day: date, hour: int, minute: int = 0) -> datetime:
    return datetime(day.year, day.month, day.day, hour, minute, tzinfo=TZ)


@pytest.fixture
def cfg() -> Config:
    return Config()
