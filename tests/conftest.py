import gc
import os
import socket
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
    """The profile the tests plan for. Explicit on purpose: the built-in defaults are neutral (no location,
    the system's time zone, no away rule) and no test may depend on them being anything else.

    A neutral city centre, a 4.8 kWp roof facing south-east, a 70 kWh car, a 32 A charger and a weekly Thursday
    06:00-19:00 away rule.
    """
    return Config(
        panels=Panels(kwp=4.8, tilt_deg=35, azimuth_deg=-45, system_losses=0.14),
        house=House(baseload_kw=0.6),
        car=Car(usable_battery_kwh=70, consumption_kwh_per_100km=18, km_per_day=600 / 7, minimum_pct=30, target_pct=80),
        charger=Charger(phases=1, min_amps=6, max_amps=32, volts=230, efficiency=0.90),
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
def no_window_or_worker_left_running():
    """A test must not leave a window open or a worker thread running behind it.

    A window left open (Settings, set-up, a popup) keeps its controller, timers and queued signals alive for the rest
    of the session, and a worker (a forecast fetch, a place search, a connection check) that finishes after its test
    has ended delivers its result to a window that is going away. Both crashed an unrelated test later, as a rare
    segmentation fault in the next event loop. So: close what is left, deliver what is queued, wait for the workers.
    """
    import threading

    yield
    from PySide6.QtCore import QEvent
    from PySide6.QtWidgets import QApplication

    if QApplication.instance() is not None:
        # a window a test opened (Settings, set-up, a popup) must not live on into the next test: it keeps its
        # controller, timers and queued signals alive, and their events are delivered during unrelated tests
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
    """Bell-shaped days given by their peak irradiance (W/m2), turned into the power of the example system."""
    panels = example_config().panels
    steps = []
    for day, peak in days:
        for start, gti in bell_day(day, peak, TZ):
            steps.append(ForecastStep(start, start + timedelta(hours=1), power_kw(gti, panels.kwp, panels.system_losses)))
    return steps


def settle_controller(controller, seconds: float = 5.0) -> None:
    """Wait until the fetch a controller started has been delivered. A test must not end while its worker is running:
    the result would be delivered to a controller that is already gone, during whichever test runs next."""
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
