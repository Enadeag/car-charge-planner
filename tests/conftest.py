import gc
import socket
from datetime import date, datetime, time, timedelta
from zoneinfo import ZoneInfo

import pytest

from planner.demo import bell_day
from planner.model import AwayRule, Config, ForecastStep

TZ = ZoneInfo("Europe/Amsterdam")  # the tests' time zone: it has the DST dates the tests use (2026-03-29, 2026-10-25)


def example_config() -> Config:
    """The profile the tests plan for. Explicit on purpose: the built-in defaults are neutral (no location,
    the system's time zone, no away rule) and no test may depend on them being anything else.

    A neutral city centre, a typical 4.8 kWp roof facing south-east, and a weekly Thursday 06:00-19:00 away rule.
    """
    return Config(
        latitude=52.37,
        longitude=4.90,
        timezone="Europe/Amsterdam",
        away_rules=(AwayRule(weekday=3, start=time(6, 0), end=time(19, 0)),),
    )


class NetworkAccessInTests(AssertionError):
    pass


@pytest.fixture(autouse=True)
def main_thread_garbage_collection():
    """Collect cyclic garbage on the test's own (main) thread, never from a background thread.

    Qt objects must be destroyed on the thread that made them. A leftover controller or widget in a reference
    cycle, collected by whichever worker thread happened to allocate memory next, crashed unrelated tests
    (and made Qt print "Timers cannot be stopped from another thread"). The app has the same safeguard
    (app/gcguard.py).
    """
    gc.collect()
    gc.disable()
    yield
    gc.collect()
    gc.enable()


@pytest.fixture(autouse=True)
def no_network(monkeypatch):
    """Fail loudly if a test opens an internet connection: tests use recorded responses and fakes.

    Local sockets (the single-instance socket, the session bus) are not affected. Processes started by
    a test are not covered; those tests use a configuration without a location, so nothing is fetched.
    """
    real_connect = socket.socket.connect
    real_getaddrinfo = socket.getaddrinfo

    def guarded_connect(self, address):
        if self.family in (socket.AF_INET, socket.AF_INET6):
            raise NetworkAccessInTests(f"a test tried to connect to {address!r}: use a fake or a recorded response")
        return real_connect(self, address)

    def guarded_getaddrinfo(host, *args, **kwargs):
        if host not in (None, "", "localhost", "127.0.0.1", "::1"):
            raise NetworkAccessInTests(f"a test tried to look up {host!r}: use a fake or a recorded response")
        return real_getaddrinfo(host, *args, **kwargs)

    monkeypatch.setattr(socket.socket, "connect", guarded_connect)
    monkeypatch.setattr(socket, "getaddrinfo", guarded_getaddrinfo)


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
    return example_config()


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
