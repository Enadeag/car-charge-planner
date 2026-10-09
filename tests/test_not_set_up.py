"""Neutral defaults and the "not set up" state: nothing is assumed about the user, nothing is fetched."""

import os

os.environ.setdefault("QT_QPA_PLATFORM", "offscreen")

import socket
import subprocess
import sys
from dataclasses import replace
from datetime import date, timedelta
from pathlib import Path
from zoneinfo import ZoneInfo

import pytest
from PySide6.QtWidgets import QApplication, QPushButton

from car_charge_planner.config_file import ensure_config
from car_charge_planner.controller import Controller
from car_charge_planner.forecast.service import ForecastResult
from car_charge_planner.planner.model import Config, ConfigError, find_timezone, load_config, require_location, system_timezone
from car_charge_planner.settings.window import SettingsWindow
from car_charge_planner.shells.icons import COLORS, make_icon
from car_charge_planner.shells.tray import TrayShell
from car_charge_planner.shells.window import WindowShell
from car_charge_planner.storage.state import BatteryReading
from car_charge_planner.view_model import needs_setup
from conftest import FakeNotifier, NetworkAccessInTests, at, example_config, forecast_from

DAY = date(2026, 7, 3)
PROJECT = Path(__file__).resolve().parent.parent


@pytest.fixture
def qapp():
    return QApplication.instance() or QApplication([])


def test_a_new_config_assumes_nothing_about_the_user():
    cfg = Config()
    assert (cfg.latitude, cfg.longitude) == (0.0, 0.0)
    assert cfg.away_rules == ()  # no weekly rule: the car is home all week until told otherwise
    assert cfg.timezone == system_timezone()
    assert not cfg.is_set_up


@pytest.mark.parametrize("lat, lon, expected", [(0, 0, False), (0.0, 0.0, False), (52.37, 4.9, True), (0, 4.9, True), (-33.9, 0, True)])
def test_a_location_counts_when_either_part_is_not_zero(lat, lon, expected):
    assert replace(Config(), latitude=lat, longitude=lon).is_set_up is expected


def test_defaults_do_not_name_any_particular_place():
    cfg = Config()
    assert abs(cfg.latitude) < 1e-9 and abs(cfg.longitude) < 1e-9


@pytest.mark.parametrize(
    "environ, etc_timezone, localtime, expected",
    [
        ({"TZ": "Europe/Berlin"}, "", "", "Europe/Berlin"),
        ({"TZ": ":Europe/Berlin"}, "", "", "Europe/Berlin"),  # the leading colon form
        ({}, "Asia/Tokyo\n", "", "Asia/Tokyo"),
        ({}, "", "/usr/share/zoneinfo/America/New_York", "America/New_York"),
        ({}, "", "/usr/share/zoneinfo/posix/Europe/Paris", "Europe/Paris"),
        ({"TZ": "Europe/Berlin"}, "Asia/Tokyo", "/usr/share/zoneinfo/America/New_York", "Europe/Berlin"),  # TZ wins
        ({"TZ": "Not/AZone"}, "Asia/Tokyo", "", "Asia/Tokyo"),  # a bad candidate is skipped
        ({}, "", "", "UTC"),
        ({"TZ": "Nonsense"}, "also nonsense", "/etc/localtime", "UTC"),
    ],
)
def test_the_time_zone_comes_from_the_system(environ, etc_timezone, localtime, expected):
    assert find_timezone(environ, etc_timezone, localtime) == expected


def test_the_detected_zone_is_always_a_real_one():
    ZoneInfo(system_timezone())  # must not raise


def test_requiring_a_location_gives_a_plain_message():
    with pytest.raises(ConfigError) as err:
        require_location(Config())
    assert "Enter your location" in str(err.value)
    assert require_location(example_config()).is_set_up


def test_the_first_config_file_loads_as_not_set_up(tmp_path):
    path = ensure_config(tmp_path / "config.toml")
    cfg = load_config(path)
    assert not cfg.is_set_up and cfg.away_rules == () and cfg.timezone == system_timezone()


def test_a_config_with_a_location_behaves_as_before(tmp_path):
    from car_charge_planner.planner.config_io import write_config

    write_config(example_config(), tmp_path / "c.toml")
    loaded = load_config(tmp_path / "c.toml")
    assert loaded == example_config() and loaded.is_set_up and len(loaded.away_rules) == 1


def test_a_config_without_an_away_section_has_no_rules(tmp_path):
    f = tmp_path / "c.toml"
    f.write_text("latitude = 1.0\nlongitude = 2.0\n")
    assert load_config(f).away_rules == ()


def test_the_not_set_up_view_says_where_to_go():
    now = at(DAY, 7)
    vm = needs_setup(BatteryReading(50, None), now)
    assert vm.state == "needs-setup" and vm.headline == "Not set up yet" and vm.tooltip == "Not set up yet"
    assert vm.detail == "Set up the app to get a plan. It takes a few minutes."
    assert vm.outlook == () and vm.advice is None and vm.projection == ""


def test_the_not_set_up_state_has_its_own_icon_colour():
    assert "needs-setup" in COLORS
    assert COLORS["needs-setup"] not in {v for k, v in COLORS.items() if k != "needs-setup"}


def test_the_icon_exists(qapp):
    assert not make_icon("needs-setup").isNull()


class CountingFetch:
    def __init__(self):
        self.calls = []

    def __call__(self, cfg, now, force=False, **_):
        self.calls.append(cfg)
        steps = forecast_from([(DAY + timedelta(days=i), 900) for i in range(3)])
        return ForecastResult(steps, at(DAY, 6), from_cache=False, stale=False)


def settle(controller, seconds=3.0):
    """Wait until a fetch started by the controller has been delivered, so it is not delivered during the next test."""
    import time

    from PySide6.QtTest import QTest

    deadline = time.time() + seconds
    while controller._loading and time.time() < deadline:
        QTest.qWait(20)
    QTest.qWait(20)
    assert not controller._loading, "the fetch did not finish"


def make_controller(cfg, tmp_path, monkeypatch, fetch=None):
    monkeypatch.setenv("XDG_STATE_HOME", str(tmp_path))
    fetch = fetch or CountingFetch()
    monkeypatch.setattr("car_charge_planner.forecast.service.ForecastService.get", lambda self, cfg, now, force=False: fetch(cfg, now, force))
    return Controller(cfg, tmp_path / "config.toml", FakeNotifier()), fetch


def test_without_a_location_nothing_is_fetched_and_the_view_says_so(tmp_path, monkeypatch, qapp):
    controller, fetch = make_controller(Config(), tmp_path, monkeypatch)
    seen = []
    controller.view_changed.connect(seen.append)
    controller.start()
    controller.refresh()
    controller.refresh(force=True)
    assert fetch.calls == [] and controller._loading is False
    assert controller.vm.state == "needs-setup" and seen and seen[-1].vm.headline == "Not set up yet"


def test_the_minute_tick_does_not_fetch_either(tmp_path, monkeypatch, qapp):
    controller, fetch = make_controller(Config(), tmp_path, monkeypatch)
    for _ in range(3):
        controller.timer.timeout.emit()
    assert fetch.calls == []


def test_giving_a_location_starts_the_first_fetch(tmp_path, monkeypatch, qapp):
    controller, fetch = make_controller(Config(), tmp_path, monkeypatch)
    controller.start()
    assert fetch.calls == []
    controller.apply_config(example_config())  # what saving Settings does
    settle(controller)
    assert len(fetch.calls) == 1 and fetch.calls[0].latitude == 52.37
    assert controller.vm.state != "needs-setup"


def test_nothing_is_announced_while_not_set_up(tmp_path, monkeypatch, qapp):
    controller, _ = make_controller(Config(), tmp_path, monkeypatch)
    controller.result = ForecastResult(forecast_from([(DAY, 900)]), at(DAY, 6), True, False)  # stale leftovers
    controller.rebuild()
    assert controller.notifier.sent == [] and controller.vm.state == "needs-setup"


def test_the_tray_shell_shows_the_state(tmp_path, monkeypatch, qapp):
    controller, _ = make_controller(Config(), tmp_path, monkeypatch)
    shell = TrayShell(controller)
    controller.rebuild()
    assert shell.tray.toolTip() == "Not set up yet"
    assert shell.popup.headline.text() == "Not set up yet"


def test_the_window_shell_shows_the_state(tmp_path, monkeypatch, qapp):
    controller, _ = make_controller(Config(), tmp_path, monkeypatch)
    shell = WindowShell(controller)
    controller.rebuild()
    assert shell.window.windowTitle() == "Not set up yet"
    assert shell.window.windowIcon().pixmap(32, 32).toImage() == make_icon("needs-setup").pixmap(32, 32).toImage()


def test_the_plan_page_offers_the_set_up_wizard_only_while_not_set_up(tmp_path, monkeypatch, qapp):
    controller, _ = make_controller(Config(), tmp_path, monkeypatch)
    shell = WindowShell(controller)
    shell.show_plan()
    button = next(b for b in shell.view.findChildren(QPushButton) if b.text() == "Set up")
    assert button.isVisible()
    button.click()
    wizard = controller.setup_wizard
    assert wizard is not None and wizard.isVisible() and wizard.current == "welcome"
    wizard.confirm_leave = lambda: True
    wizard.close()
    controller.apply_config(example_config())  # a location entered some other way: set up
    settle(controller)
    assert not button.isVisible()
    shell.window.hide()


def settings_window(tmp_path, cfg):
    saved = []
    window = SettingsWindow(cfg, tmp_path / "config.toml", saved.append)
    window.confirm_discard = lambda: True
    window.show()
    return window, saved


def test_saving_settings_without_a_location_is_refused(tmp_path, qapp):
    window, saved = settings_window(tmp_path, Config())
    window.field_tabs[0].inputs["panels.kwp"].setValue(5.0)  # another change, still no location
    window.save_button.click()
    assert window.isVisible() and not window.errors.isHidden()
    assert "Enter your location" in window.errors.text()
    assert not (tmp_path / "config.toml").exists() and saved == []


def test_saving_settings_with_a_location_works(tmp_path, qapp):
    window, saved = settings_window(tmp_path, Config())
    window.field_tabs[0].inputs["latitude"].setValue(52.37)
    window.field_tabs[0].inputs["longitude"].setValue(4.9)
    window.save_button.click()
    assert window.errors.isHidden() and len(saved) == 1 and saved[0].is_set_up
    assert load_config(tmp_path / "config.toml").latitude == 52.37


def test_the_hint_says_what_zero_zero_means(tmp_path, qapp):
    from PySide6.QtWidgets import QLabel

    window, _ = settings_window(tmp_path, Config())
    hints = [l.text() for l in window.field_tabs[0].findChildren(QLabel)]
    assert any("0 and 0 together mean not set" in t for t in hints)


def run_cli(*args, cwd=PROJECT):
    return subprocess.run([sys.executable, "-m", "car_charge_planner.planner.cli", *args], cwd=cwd, capture_output=True, text=True, timeout=60)


def test_the_live_command_refuses_without_a_location_and_fetches_nothing():
    done = run_cli("--live", "--battery", "50")
    assert done.returncode != 0 and "No location is set" in done.stderr


def test_the_demo_command_needs_no_location():
    done = run_cli("--demo", "summer", "--battery", "50", "--now", "2026-07-03T07:00+02:00")
    assert done.returncode == 0 and "Fri 03 Jul" in done.stdout


def test_a_first_start_of_the_whole_app_makes_no_forecast_request(tmp_path):
    import time

    for name in ("config", "state", "cache", "run", "data"):
        (tmp_path / name).mkdir(mode=0o700, exist_ok=True)
    env = {
        **os.environ,
        "QT_QPA_PLATFORM": "offscreen",
        "HOME": str(tmp_path),
        "XDG_CONFIG_HOME": str(tmp_path / "config"),
        "XDG_STATE_HOME": str(tmp_path / "state"),
        "XDG_CACHE_HOME": str(tmp_path / "cache"),
        "XDG_RUNTIME_DIR": str(tmp_path / "run"),
        "XDG_DATA_HOME": str(tmp_path / "data"),
    }
    process = subprocess.Popen([sys.executable, "-m", "car_charge_planner"], cwd=PROJECT, env=env, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
    try:
        time.sleep(5)  # the first refresh and several ticks' worth of startup
        assert process.poll() is None, process.stderr.read()
    finally:
        process.terminate()
        try:
            _, err = process.communicate(timeout=10)
        except subprocess.TimeoutExpired:
            process.kill()
            _, err = process.communicate()
    assert "Traceback" not in err
    created = (tmp_path / "config" / "car-charge-planner" / "config.toml").exists()
    assert created  # the config was created on first start
    assert not (tmp_path / "cache" / "car-charge-planner").exists()  # but no forecast was ever stored


def test_the_suite_refuses_internet_connections():
    with pytest.raises(NetworkAccessInTests):
        socket.socket(socket.AF_INET).connect(("example.com", 80))
    with pytest.raises(NetworkAccessInTests):
        socket.getaddrinfo("api.open-meteo.com", 443)


@pytest.mark.linux
@pytest.mark.macos
def test_local_connections_are_not_affected_by_the_guard(tmp_path):
    server_path = str(tmp_path / "s.sock")
    server = socket.socket(socket.AF_UNIX)
    server.bind(server_path)
    server.listen(1)
    client = socket.socket(socket.AF_UNIX)
    client.connect(server_path)  # must not raise
    client.close()
    server.close()


def test_the_not_set_up_view_does_not_nag_about_the_battery():
    vm = needs_setup(BatteryReading(50, None), at(DAY, 7))  # a battery that was never entered
    assert vm.battery_stale is False


def test_the_plan_page_hides_the_status_lines_while_not_set_up(tmp_path, monkeypatch, qapp):
    controller, _ = make_controller(Config(), tmp_path, monkeypatch)
    shell = WindowShell(controller)
    shell.show_plan()
    assert shell.view.status_box.isHidden() and shell.view.tabs.tabText(1) == "Update"  # no dot, no red prompt
    controller.apply_config(example_config())
    settle(controller)
    assert not shell.view.status_box.isHidden()
    shell.window.hide()


def test_the_collector_is_kept_on_the_main_thread(qapp):
    """A cycle holding a Qt object must never be destroyed by a background thread's allocations."""
    import gc
    import threading

    from PySide6.QtCore import QObject, QTimer

    from car_charge_planner import gcguard

    destroyed_on = []

    class Leftover(QObject):
        def __init__(self):
            super().__init__()
            self.timer = QTimer(self)
            self.timer.start(60_000)
            self.me = self  # a cycle: only the cycle collector can free it

        def __del__(self):
            destroyed_on.append(threading.current_thread().name)

    was_enabled = gc.isenabled()
    timer = gcguard.install(qapp, interval_ms=60_000)
    try:
        assert not gc.isenabled() and timer.isActive()  # automatic collection is off
        Leftover()

        def busy_background_thread():
            junk = [[object()] for _ in range(200_000)]  # plenty of allocations: would trigger collections
            del junk

        worker = threading.Thread(target=busy_background_thread, name="worker")
        worker.start()
        worker.join()
        assert destroyed_on == []  # the worker did not collect it
        timer.timeout.emit()
        assert destroyed_on == [threading.main_thread().name]
    finally:
        timer.stop()
        if was_enabled:
            gc.enable()


def test_every_test_runs_with_automatic_collection_off():
    import gc

    assert not gc.isenabled()  # the suite's own safeguard (tests/conftest.py)
