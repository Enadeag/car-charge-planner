import gc
import os
import socket
import sys
import uuid
from dataclasses import replace
from datetime import UTC, date, datetime, time, timedelta
from pathlib import Path
from zoneinfo import ZoneInfo

import keyring
import keyring.core
import pytest
from keyring.backend import KeyringBackend
from keyring.backends.fail import Keyring as FailKeyring
from keyring.errors import PasswordDeleteError

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


@pytest.fixture(autouse=True)
def files_stay_in_a_temporary_folder(tmp_path_factory, monkeypatch):
    """Keep the app's config, state, cache and data out of the real user folders on every system.

    A test that needs a folder of its own sets the XDG_*_HOME variable, as on Linux; `paths` follows it on every system.
    """
    import xdg_paths
    from car_charge_planner import paths

    for name, function in xdg_paths.locations(tmp_path_factory.mktemp("home")).items():
        monkeypatch.setattr(paths, name, function)


class MemoryKeyring(KeyringBackend):
    """A keychain that lives in a dict: no test may touch the real one."""

    priority = 1  # type: ignore[assignment]

    def __init__(self):
        super().__init__()
        self.entries: dict[tuple[str, str], str] = {}

    def get_password(self, service, username):
        return self.entries.get((service, username))

    def set_password(self, service, username, password):
        self.entries[(service, username)] = password

    def delete_password(self, service, username):
        if (service, username) not in self.entries:
            raise PasswordDeleteError("not found")
        del self.entries[(service, username)]


REAL_SESSION_BUS = os.environ.get("DBUS_SESSION_BUS_ADDRESS", "")


@pytest.fixture(autouse=True)
def no_desktop_notifications(monkeypatch):
    """Point the session bus nowhere, for the test and every app it starts: otherwise each start of the whole app sends
    a real "set-up needed" notification to the developer's desktop."""
    monkeypatch.setenv("DBUS_SESSION_BUS_ADDRESS", "unix:path=/nonexistent/car-charge-planner-tests")


@pytest.fixture(autouse=True)
def keychain(monkeypatch):
    """An empty in-memory keychain instead of the user's real one, here and in any program a test starts."""
    monkeypatch.setenv("PYTHON_KEYRING_BACKEND", "keyring.backends.null.Keyring")  # for child processes
    monkeypatch.setattr(keyring.core, "_keyring_backend", None)  # restored afterwards, without initialising the real backend
    backend = MemoryKeyring()
    keyring.set_keyring(backend)
    return backend


@pytest.fixture
def no_keychain(keychain):
    """A system without a usable keychain: the keys fall back to secrets.toml."""
    keyring.set_keyring(FailKeyring())


@pytest.fixture
def socket_name(tmp_path):
    """A local socket for a test's own instance server: a file in the temp folder, or a pipe name on Windows."""
    if sys.platform == "win32":
        return f"car-charge-planner-test-{uuid.uuid4().hex}"
    return str(tmp_path / "instance.sock")


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


def golden_config() -> Config:
    """The example profile driving 70 km a day: 12.6 kWh, a round 18% of the 70 kWh battery."""
    cfg = example_config()
    return replace(cfg, car=replace(cfg.car, km_per_day=70))


def hourly(day: date, power_kw: dict[int, float]) -> list[ForecastStep]:
    """One local day of hourly steps, 0 kW unless `power_kw` gives a value. The keys count real hours from local
    midnight, so on a DST day key 3 is 04:00 (23-hour day) or the second 02:00 (25-hour day)."""
    start = datetime.combine(day, time(), TZ).astimezone(UTC)
    end = datetime.combine(day + timedelta(days=1), time(), TZ).astimezone(UTC)
    steps = []
    for index in range(round((end - start) / timedelta(hours=1))):
        moment = start + timedelta(hours=index)
        steps.append(ForecastStep(moment.astimezone(TZ), (moment + timedelta(hours=1)).astimezone(TZ), power_kw.get(index, 0.0)))
    return steps


def settle_controller(controller, seconds: float = 5.0) -> None:
    """Wait until the fetch a controller started has been delivered, so it is not delivered during the next test."""
    import time

    from PySide6.QtTest import QTest

    deadline = time.time() + seconds
    while controller.engine.fetching and time.time() < deadline:
        QTest.qWait(20)
    QTest.qWait(30)  # the queued result is handled on the next pass of the event loop
    assert not controller.engine.fetching, "the fetch did not finish"


def at(day: date, hour: int, minute: int = 0) -> datetime:
    return datetime(day.year, day.month, day.day, hour, minute, tzinfo=TZ)


class FakeClock:
    """A clock that stands still until a test moves it."""

    def __init__(self, now: datetime):
        self.now = now

    def __call__(self) -> datetime:
        return self.now


class FixedForecast:
    """A forecast service that answers every request with the same result, and keeps the config of each request."""

    def __init__(self, result):
        self.result = result
        self.calls = []

    def get(self, cfg, now, force=False):
        self.calls.append(cfg)
        return self.result

    def forget_failures(self):
        pass


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
    """The controller and the tray shell wired as main() wires them: logic in the engine, drawing on the shell.

    The keyword arguments go to the controller (a forecast service, a clock, a runner).
    """

    def __init__(self, cfg, config_file, notifier=None, **controller_args):
        from car_charge_planner.controller import Controller
        from car_charge_planner.shells.tray import TrayShell

        object.__setattr__(self, "controller", Controller(cfg, config_file, notifier, **controller_args))
        object.__setattr__(self, "shell", TrayShell(self.controller))

    def _owner(self, name):
        for part in (self.controller, self.controller.engine, self.shell, self.shell.windows):
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
