import gc
import os
import socket
import sys
from datetime import date, datetime, time, timedelta
from pathlib import Path
from zoneinfo import ZoneInfo

import pytest

from car_charge_planner.forecast.providers.open_meteo import power_kw
from car_charge_planner.planner.demo import bell_day
from car_charge_planner.planner.model import AwayRule, Car, Charger, Config, ForecastStep, House, Panels

# the package lives in src/: tests that start the app as a program find it without an install
os.environ["PYTHONPATH"] = str(Path(__file__).resolve().parent.parent / "src") + os.pathsep + os.environ.get("PYTHONPATH", "")

TZ = ZoneInfo("Europe/Amsterdam")  # the tests' time zone: it has the DST dates the tests use (2026-03-29, 2026-10-25)


def example_config() -> Config:
    """The profile the tests plan for, explicit because the built-in defaults are neutral (no location, no away rule).

    A 4.8 kWp roof facing south-east, a 70 kWh car, a 32 A charger and a weekly Thursday 06:00-19:00 away rule.
    """
    return Config(
        panels=(Panels(kwp=4.8, tilt_deg=35, azimuth_deg=-45, system_losses=0.14),),
        house=House(baseload_kw=0.6),
        car=Car(usable_battery_kwh=70, consumption_kwh_per_100km=18, km_per_day=600 / 7, minimum_pct=30, target_pct=80),
        charger=Charger(phases=1, min_amps=6, max_amps=32, volts=230, efficiency=0.90),
        latitude=52.37,
        longitude=4.90,
        timezone="Europe/Amsterdam",
        away_rules=(AwayRule(weekday=3, start=time(6, 0), end=time(19, 0)),),
    )


_PLATFORM_MARKERS = {"linux": "linux", "windows": "win32", "macos": "darwin"}


def pytest_collection_modifyitems(items):
    """A test marked `linux`, `windows` or `macos` runs only on that system (several marks: on any of them)."""
    for item in items:
        wanted = [name for name in _PLATFORM_MARKERS if item.get_closest_marker(name)]
        if wanted and not any(sys.platform.startswith(_PLATFORM_MARKERS[name]) for name in wanted):
            item.add_marker(pytest.mark.skip(reason=f"only runs on {' or '.join(wanted)} (this is {sys.platform})"))


class NetworkAccessInTests(AssertionError):
    pass


@pytest.fixture(autouse=True)
def main_thread_garbage_collection():
    """Collect cyclic garbage on the main thread only (as car_charge_planner.gcguard does in the app).

    Qt objects must be destroyed on the thread that made them: a leftover controller or widget in a reference
    cycle, collected by whichever worker thread allocated next, crashed unrelated tests ("Timers cannot be stopped
    from another thread").
    """
    gc.collect()
    gc.disable()
    yield
    gc.collect()
    gc.enable()


@pytest.fixture(autouse=True)
def no_window_or_worker_left_running():
    """Close leftover windows, deliver queued events and wait for worker threads after each test.

    A window left open keeps its controller, timers and queued signals alive, and a worker that finishes after its
    test delivers into a window that is going away; both caused rare segfaults in a later, unrelated test.
    """
    import threading

    yield
    from PySide6.QtCore import QEvent
    from PySide6.QtWidgets import QApplication

    if QApplication.instance() is not None:
        # a window left open would deliver its events during an unrelated test
        for widget in QApplication.topLevelWidgets():
            widget.hide()
            widget.deleteLater()
        QApplication.sendPostedEvents(None, QEvent.Type.DeferredDelete)

    for _ in range(8):  # delivering an event can start another worker (a refresh), so go round until it is quiet
        if QApplication.instance() is not None:
            QApplication.processEvents()
        workers = [t for t in threading.enumerate() if t.daemon and t is not threading.main_thread() and t.is_alive()]
        if not workers:
            return
        for thread in workers:
            thread.join(5)
    raise AssertionError("worker threads keep starting after the test")


@pytest.fixture(autouse=True)
def no_network(monkeypatch):
    """Fail if a test opens an internet connection; local sockets (single-instance, session bus) are allowed.

    Processes a test starts are not covered; those use a configuration without a location, so nothing is fetched.
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
    """Bell-shaped days given by their peak irradiance (W/m2), turned into the power of the example system."""
    panels = example_config().panels[0]
    steps = []
    for day, peak in days:
        for start, gti in bell_day(day, peak, TZ):
            steps.append(ForecastStep(start, start + timedelta(hours=1), power_kw(gti, panels.kwp, panels.system_losses)))
    return steps


def settle_controller(controller, seconds: float = 5.0) -> None:
    """Wait until the fetch a controller started has been delivered, so it is not delivered during the next test."""
    import time

    from PySide6.QtTest import QTest

    deadline = time.time() + seconds
    while controller._loading and time.time() < deadline:
        QTest.qWait(20)
    QTest.qWait(30)  # the queued result is handled on the next pass of the event loop
    assert not controller._loading, "the fetch did not finish"


def at(day: date, hour: int, minute: int = 0) -> datetime:
    return datetime(day.year, day.month, day.day, hour, minute, tzinfo=TZ)


class FakeNotifier:
    def __init__(self):
        self.sent = []

    def send(self, title, body, expire_ms=20_000):
        self.sent.append((title, body))
        return len(self.sent)

    def owns(self, notification_id):
        return 1 <= notification_id <= len(self.sent)  # ids are 1, 2, 3 ... in the order sent


@pytest.fixture
def qapp():
    from PySide6.QtWidgets import QApplication

    return QApplication.instance() or QApplication([])


@pytest.fixture
def cfg() -> Config:
    return example_config()


class TrayApp:
    """The controller and the tray shell wired as main() wires them: logic on the controller, drawing on the shell."""

    def __init__(self, cfg, config_file, notifier=None):
        from car_charge_planner.controller import Controller
        from car_charge_planner.shells.tray import TrayShell

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
