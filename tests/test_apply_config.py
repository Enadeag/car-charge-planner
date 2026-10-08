"""apply_config: switch settings while the app runs (settings spec 3.3)."""

import os

os.environ.setdefault("QT_QPA_PLATFORM", "offscreen")

import threading
import time
from dataclasses import replace
from datetime import date

import pytest
from PySide6.QtWidgets import QApplication

from conftest import TrayApp
from car_charge_planner.forecast.service import ForecastResult, forecast_key
from car_charge_planner.storage.state import load_state, mark_notified

from conftest import at, example_config, forecast_from

DAY = date(2026, 7, 3)


@pytest.fixture
def qapp():
    return QApplication.instance() or QApplication([])


def wait_until(condition, qapp, seconds=3.0):
    end = time.time() + seconds
    while time.time() < end:
        qapp.processEvents()
        if condition():
            return
        time.sleep(0.01)
    raise AssertionError("timed out waiting for the app")


class FakeForecast:
    """Stands in for get_forecast: records the config of each call; can hold the first call open."""

    def __init__(self, hold_first=False):
        self.calls = []
        self.release = threading.Event()
        self.hold_first = hold_first
        self.first_started = threading.Event()

    def __call__(self, cfg, now, force=False, **_):
        self.calls.append(cfg)
        if self.hold_first and len(self.calls) == 1:
            self.first_started.set()
            self.release.wait(5)
        steps = forecast_from([(DAY, 900)])
        return ForecastResult(steps, at(DAY, 6), from_cache=False, stale=False)


def make_app(tmp_path, monkeypatch, fake, cfg=None):
    monkeypatch.setenv("XDG_STATE_HOME", str(tmp_path))
    monkeypatch.setattr("car_charge_planner.forecast.service.ForecastService.get", lambda self, cfg, now, force=False: fake(cfg, now, force))
    return TrayApp(cfg or example_config(), tmp_path / "config.toml")


def loaded(app, fake, qapp, calls=1):
    app.refresh()
    wait_until(lambda: app.result is not None and len(fake.calls) >= calls and not app._loading, qapp)


def test_forecast_key_ignores_planning_settings_but_not_the_system_or_the_providers(cfg):
    base = forecast_key(cfg)
    assert forecast_key(replace(cfg, planning=replace(cfg.planning, margin=0.5))) == base
    assert forecast_key(replace(cfg, house=replace(cfg.house, baseload_kw=1.2))) == base
    for changed in (
        replace(cfg, panels=replace(cfg.panels, kwp=9)),  # the provider turns sunlight into power: size and losses matter
        replace(cfg, panels=replace(cfg.panels, system_losses=0.2)),
        replace(cfg, forecast=replace(cfg.forecast, primary="forecast-solar")),
        replace(cfg, forecast=replace(cfg.forecast, fallback="forecast-solar")),
        replace(cfg, forecast=replace(cfg.forecast, settings={"open-meteo": {"base_url": "https://x.test"}})),
        replace(cfg, panels=replace(cfg.panels, tilt_deg=20)),
        replace(cfg, panels=replace(cfg.panels, azimuth_deg=0)),
        replace(cfg, latitude=51.0),
        replace(cfg, longitude=5.0),
        replace(cfg, timezone="Europe/Brussels"),
    ):
        assert forecast_key(changed) != base


def test_applying_a_planning_change_keeps_the_forecast_and_replans(qapp, tmp_path, monkeypatch, cfg):
    fake = FakeForecast()
    app = make_app(tmp_path, monkeypatch, fake)
    loaded(app, fake, qapp)
    kept = app.result
    app.apply_config(replace(cfg, planning=replace(cfg.planning, margin=0.5)))
    assert app.result is kept  # not cleared while it is still valid
    assert app.cfg.planning.margin == 0.5 and app.vm is not None
    wait_until(lambda: len(fake.calls) == 2 and not app._loading, qapp)  # cache-aware refresh, may be a hit
    assert fake.calls[-1].planning.margin == 0.5


def test_applying_a_place_or_angle_change_drops_the_old_forecast_until_the_new_one_arrives(qapp, tmp_path, monkeypatch, cfg):
    fake = FakeForecast(hold_first=False)
    app = make_app(tmp_path, monkeypatch, fake)
    loaded(app, fake, qapp)
    fake.hold_first = True
    fake.calls.clear()
    fake.release.clear()
    app.apply_config(replace(cfg, panels=replace(cfg.panels, tilt_deg=20)))
    assert app.result is None and app.vm.state == "no-forecast"
    fake.release.set()
    wait_until(lambda: app.result is not None, qapp)
    assert app.vm.state != "no-forecast" and fake.calls[0].panels.tilt_deg == 20


def test_time_zone_change_updates_the_app_and_popup(qapp, tmp_path, monkeypatch, cfg):
    fake = FakeForecast()
    app = make_app(tmp_path, monkeypatch, fake)
    loaded(app, fake, qapp)
    app.apply_config(replace(cfg, timezone="Europe/London"))
    assert str(app.tz) == "Europe/London" and str(app.popup.tz) == "Europe/London"
    wait_until(lambda: not app._loading, qapp)


def test_a_fetch_started_before_the_change_does_not_overwrite_the_new_settings(qapp, tmp_path, monkeypatch, cfg):
    fake = FakeForecast(hold_first=True)
    app = make_app(tmp_path, monkeypatch, fake)
    app.refresh()
    assert fake.first_started.wait(2)  # the old-config fetch is now in flight
    new = replace(cfg, panels=replace(cfg.panels, tilt_deg=20))
    app.apply_config(new)
    fake.release.set()  # the old fetch finishes after the change
    wait_until(lambda: len(fake.calls) == 2 and not app._loading and app.result is not None, qapp)
    assert fake.calls[0].panels.tilt_deg == 35 and fake.calls[1].panels.tilt_deg == 20
    assert app.cfg is new and app.result is not None
    assert app.vm.state != "no-forecast"


def test_announced_windows_are_unaffected(qapp, tmp_path, monkeypatch, cfg):
    fake = FakeForecast()
    app = make_app(tmp_path, monkeypatch, fake)
    loaded(app, fake, qapp)
    day = plan_window(cfg)
    mark_notified(day, at(DAY, 8, 50))
    before = load_state().notified
    app.apply_config(replace(cfg, planning=replace(cfg.planning, margin=0.5)))
    wait_until(lambda: not app._loading, qapp)
    assert load_state().notified == before


def plan_window(cfg):
    from car_charge_planner.planner.projection import plan

    return plan(forecast_from([(DAY, 900)]), cfg, 40, at(DAY, 7))[0].windows[0]


def test_tray_menu_has_settings_and_no_config_file_entry(qapp, tmp_path, monkeypatch):
    app = make_app(tmp_path, monkeypatch, FakeForecast())
    labels = [a.text() for a in app.menu.actions() if a.text()]
    assert "Settings..." in labels and "Open config file" not in labels


def test_opening_settings_twice_gives_one_window(qapp, tmp_path, monkeypatch):
    app = make_app(tmp_path, monkeypatch, FakeForecast())
    app.open_settings()
    first = app.settings_window
    app.open_settings()
    assert app.settings_window is first and first.isVisible()
    first.confirm_discard = lambda: True
    first.close()
    app.open_settings()  # after closing, a fresh window opens
    assert app.settings_window is not first and app.settings_window.isVisible()
    app.settings_window.close()


def test_saving_in_the_window_applies_to_the_running_app(qapp, tmp_path, monkeypatch):
    fake = FakeForecast()
    app = make_app(tmp_path, monkeypatch, fake)
    loaded(app, fake, qapp)
    app.config_file = tmp_path / "config.toml"
    app.open_settings()
    window = app.settings_window
    window.field_tabs[0].inputs["panels.tilt_deg"].setValue(20)
    window.save_button.click()
    assert app.cfg.panels.tilt_deg == 20
    assert (tmp_path / "config.toml").exists()
    wait_until(lambda: not app._loading and app.result is not None, qapp)
    assert fake.calls[-1].panels.tilt_deg == 20  # a new forecast was fetched for the new angle
