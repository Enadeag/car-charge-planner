"""Neutral defaults and the "not set up" state: nothing is assumed about the user, nothing is fetched."""

import os

os.environ.setdefault("QT_QPA_PLATFORM", "offscreen")

import subprocess
import sys
from dataclasses import replace
from datetime import date, timedelta
from pathlib import Path
from zoneinfo import ZoneInfoNotFoundError

import pytest
from PySide6.QtWidgets import QPushButton

from app_process import RunningApp
from car_charge_planner.config_file import ensure_config
from car_charge_planner.controller import Controller
from car_charge_planner.forecast.service import ForecastResult
from car_charge_planner.planner.model import Config, ConfigError, load_config, require_location, system_timezone
from car_charge_planner.settings.window import SettingsWindow
from car_charge_planner.shells.icons import COLORS, make_icon
from car_charge_planner.shells.tray import TrayShell
from car_charge_planner.shells.window import WindowShell
from car_charge_planner.storage.state import BatteryReading
from car_charge_planner.view_model import needs_setup
from conftest import FakeNotifier, at, example_config, forecast_from

DAY = date(2026, 7, 3)
PROJECT = Path(__file__).resolve().parent.parent


def test_a_new_config_assumes_nothing_about_the_user():
    cfg = Config()
    assert (cfg.latitude, cfg.longitude) == (0.0, 0.0)
    assert cfg.away_rules == ()  # no weekly rule: the car is home all week until told otherwise
    assert cfg.timezone == system_timezone()
    assert not cfg.is_set_up


@pytest.mark.parametrize("lat, lon, expected", [(0, 0, False), (0.0, 0.0, False), (52.37, 4.9, True), (0, 4.9, True), (-33.9, 0, True)])
def test_a_location_counts_when_either_part_is_not_zero(lat, lon, expected):
    assert replace(Config(), latitude=lat, longitude=lon).is_set_up is expected


def fake_tzlocal(monkeypatch, answer):
    def get_localzone_name():
        if isinstance(answer, Exception):
            raise answer
        return answer

    monkeypatch.setattr("tzlocal.get_localzone_name", get_localzone_name)


@pytest.mark.parametrize(
    "answer, expected",
    [
        ("Europe/Berlin", "Europe/Berlin"),
        ("Not/AZone", "UTC"),
        ("", "UTC"),
        (ZoneInfoNotFoundError("no zone"), "UTC"),
        (OSError("unreadable"), "UTC"),
    ],
)
def test_the_time_zone_comes_from_tzlocal_and_falls_back_to_utc(monkeypatch, answer, expected):
    fake_tzlocal(monkeypatch, answer)
    assert system_timezone() == expected
    assert Config().timezone == expected


@pytest.fixture
def fresh_tzlocal(monkeypatch):
    """tzlocal remembers its first answer: start without one, and put the memory back afterwards."""
    monkeypatch.setattr("tzlocal.unix._cache_tz_name", None)
    monkeypatch.delenv("TZ", raising=False)


@pytest.mark.linux
def test_a_tz_variable_still_decides(monkeypatch, fresh_tzlocal):
    monkeypatch.setenv("TZ", "Asia/Tokyo")
    assert system_timezone() == "Asia/Tokyo"


@pytest.mark.linux
def test_an_etc_localtime_link_still_decides(monkeypatch, tmp_path, fresh_tzlocal):
    import tzlocal.unix

    zone_file = tmp_path / "usr" / "share" / "zoneinfo" / "Asia" / "Tokyo"
    zone_file.parent.mkdir(parents=True)
    zone_file.touch()
    (tmp_path / "etc").mkdir()
    (tmp_path / "etc" / "localtime").symlink_to(zone_file)
    read_below_the_fake_root = tzlocal.unix._get_localzone_name  # tzlocal can look for /etc/localtime below another root
    monkeypatch.setattr("tzlocal.unix._get_localzone_name", lambda: read_below_the_fake_root(_root=str(tmp_path)))
    assert system_timezone() == "Asia/Tokyo"


def test_requiring_a_location_refuses_a_config_without_one():
    with pytest.raises(ConfigError):
        require_location(Config())
    assert require_location(example_config()).is_set_up


def test_the_first_config_file_loads_as_not_set_up(tmp_path):
    path = ensure_config(tmp_path / "config.toml")
    cfg = load_config(path)
    assert not cfg.is_set_up and cfg.away_rules == () and cfg.timezone == system_timezone()


def test_a_config_without_an_away_section_has_no_rules(tmp_path):
    f = tmp_path / "c.toml"
    f.write_text("latitude = 1.0\nlongitude = 2.0\n", encoding="utf-8")
    assert load_config(f).away_rules == ()


def test_the_not_set_up_view_says_where_to_go():
    vm = needs_setup(BatteryReading(50, None), at(DAY, 7))
    assert vm.state == "needs-setup" and vm.headline and vm.detail
    assert vm.outlook == () and vm.advice is None and vm.projection == ""
    assert vm.battery_stale is False  # a battery that was never entered is not nagged about yet


def test_every_state_has_an_icon(qapp):
    for state in COLORS:
        assert not make_icon(state).isNull()


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
    while controller.engine.fetching and time.time() < deadline:
        QTest.qWait(20)
    QTest.qWait(20)
    assert not controller.engine.fetching, "the fetch did not finish"


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
    controller.engine.refresh()
    controller.engine.refresh(force=True)
    assert fetch.calls == [] and not controller.engine.fetching
    assert controller.engine.vm.state == "needs-setup" and seen and seen[-1].vm.headline == "Not set up yet"


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
    assert controller.engine.vm.state != "needs-setup"


def test_the_tray_shell_shows_the_state(tmp_path, monkeypatch, qapp):
    controller, _ = make_controller(Config(), tmp_path, monkeypatch)
    shell = TrayShell(controller)
    controller.engine.rebuild()
    assert controller.engine.vm.state == "needs-setup"
    assert shell.tray.toolTip() == controller.engine.vm.tooltip and shell.popup.headline.text() == controller.engine.vm.headline


def test_the_window_shell_shows_the_state(tmp_path, monkeypatch, qapp):
    controller, _ = make_controller(Config(), tmp_path, monkeypatch)
    shell = WindowShell(controller)
    controller.engine.rebuild()
    assert shell.window.windowTitle() == controller.engine.vm.headline
    assert shell.window.windowIcon().pixmap(32, 32).toImage() == make_icon("needs-setup").pixmap(32, 32).toImage()


def test_the_plan_page_offers_the_set_up_wizard_only_while_not_set_up(tmp_path, monkeypatch, qapp):
    controller, _ = make_controller(Config(), tmp_path, monkeypatch)
    shell = WindowShell(controller)
    shell.show_plan()
    button = next(b for b in shell.view.findChildren(QPushButton) if b.text() == "Set up")
    assert button.isVisible()
    button.click()
    wizard = shell.windows.setup_wizard
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
    assert not (tmp_path / "config.toml").exists() and saved == []


def test_saving_settings_with_a_location_works(tmp_path, qapp):
    window, saved = settings_window(tmp_path, Config())
    window.field_tabs[0].inputs["latitude"].setValue(52.37)
    window.field_tabs[0].inputs["longitude"].setValue(4.9)
    window.save_button.click()
    assert window.errors.isHidden() and len(saved) == 1 and saved[0].is_set_up
    assert load_config(tmp_path / "config.toml").latitude == 52.37


def run_cli(*args, cwd=PROJECT):
    return subprocess.run([sys.executable, "-m", "car_charge_planner.planner.cli", *args], cwd=cwd, capture_output=True, text=True, timeout=60, encoding="utf-8")


def test_the_live_command_refuses_without_a_location_and_fetches_nothing():
    done = run_cli("--live", "--battery", "50")
    assert done.returncode != 0 and "No location is set" in done.stderr


def test_the_demo_command_needs_no_location():
    done = run_cli("--demo", "summer", "--battery", "50", "--now", "2026-07-03T07:00+02:00")
    assert done.returncode == 0 and "Fri 03 Jul" in done.stdout


def test_a_first_start_of_the_whole_app_makes_no_forecast_request(tmp_path):
    app = RunningApp(tmp_path)
    try:
        app.wait_until_running()  # the first refresh is made before the event loop starts
        assert app.still_running_after()
    finally:
        err = app.stop()
    assert "Traceback" not in err
    assert (tmp_path / "config" / "car-charge-planner" / "config.toml").exists()  # the config was created on first start
    assert not (tmp_path / "cache" / "car-charge-planner").exists()  # but no forecast was ever stored


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
