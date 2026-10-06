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


class FakeNotifier:
    """Stands in for Notifier: records what would have been shown."""

    def __init__(self):
        self.sent = []

    def send(self, title, body, expire_ms=20_000):
        self.sent.append((title, body))
        return len(self.sent)

    def owns(self, notification_id):
        return 1 <= notification_id <= len(self.sent)  # ids are 1, 2, 3 ... in the order sent


@pytest.fixture
def cfg() -> Config:
    return Config()


class TrayApp:
    """The controller and the tray shell wired as main() wires them, under the attribute names the
    older tests use. The logic (cfg, result, rebuild, apply_config, settings_window ...) lives on the
    controller; the drawing (popup, tray, menu, tray clicks) on the shell."""

    def __init__(self, cfg, config_file, notifier=None):
        from app.controller import Controller
        from app.shells.tray import TrayShell

        object.__setattr__(self, "controller", Controller(cfg, config_file, notifier))
        object.__setattr__(self, "shell", TrayShell(self.controller))

    def _owner(self, name):
        for part in (self.controller, self.shell):
            if hasattr(part, name):
                return part
        return None

    def __getattr__(self, name):
        part = self._owner(name)
        if part is None:
            raise AttributeError(name)
        return getattr(part, name)

    def __setattr__(self, name, value):
        setattr(self._owner(name) or self.controller, name, value)
