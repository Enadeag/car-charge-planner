"""apply_config: switch settings while the app runs."""

import os

os.environ.setdefault("QT_QPA_PLATFORM", "offscreen")

import threading
import time
from dataclasses import replace
from datetime import date

from car_charge_planner.forecast.service import ForecastResult, forecast_key
from conftest import TrayApp, at, example_config, forecast_from

DAY = date(2026, 7, 3)


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


def planned(app):
    return app.vm is not None and app.vm.state != "no-forecast"


def loaded(app, fake, qapp, calls=1):
    app.refresh()
    wait_until(lambda: planned(app) and len(fake.calls) >= calls and not app.fetching, qapp)


def test_forecast_key_ignores_planning_settings_but_not_the_system_or_the_providers(cfg):
    base = forecast_key(cfg)
    assert forecast_key(replace(cfg, planning=replace(cfg.planning, margin=0.5))) == base
    assert forecast_key(cfg.with_system(house=replace(cfg.system.house, baseload_kw=1.2))) == base
    for changed in (
        cfg.with_system(panels=(replace(cfg.system.panels[0], kwp=9),)),  # the provider turns sunlight into power: size and losses matter
        cfg.with_system(panels=(replace(cfg.system.panels[0], system_losses=0.2),)),
        replace(cfg, forecast=replace(cfg.forecast, primary="forecast-solar")),
        replace(cfg, forecast=replace(cfg.forecast, fallback="forecast-solar")),
        replace(cfg, forecast=replace(cfg.forecast, settings={"open-meteo": {"base_url": "https://x.test"}})),
        cfg.with_system(panels=(replace(cfg.system.panels[0], tilt_deg=20),)),
        cfg.with_system(panels=(replace(cfg.system.panels[0], azimuth_deg=0),)),
        cfg.with_system(latitude=51.0),
        cfg.with_system(longitude=5.0),
        cfg.with_system(timezone="Europe/Brussels"),
    ):
        assert forecast_key(changed) != base


def test_time_zone_change_updates_the_app_and_popup(qapp, tmp_path, monkeypatch, cfg):
    fake = FakeForecast()
    app = make_app(tmp_path, monkeypatch, fake)
    loaded(app, fake, qapp)
    app.apply_config(cfg.with_system(timezone="Europe/London"))
    assert str(app.tz) == "Europe/London" and str(app.popup.tz) == "Europe/London"
    wait_until(lambda: not app.fetching, qapp)


def test_a_fetch_on_a_thread_started_before_the_change_does_not_overwrite_the_new_settings(qapp, tmp_path, monkeypatch, cfg):
    fake = FakeForecast(hold_first=True)
    app = make_app(tmp_path, monkeypatch, fake)
    app.refresh()
    assert fake.first_started.wait(2)  # the old-config fetch is now in flight
    new = cfg.with_system(panels=(replace(cfg.system.panels[0], tilt_deg=20),))
    app.apply_config(new)
    fake.release.set()  # the old fetch finishes after the change
    wait_until(lambda: len(fake.calls) == 2 and not app.fetching and planned(app), qapp)
    assert fake.calls[0].system.panels[0].tilt_deg == 35 and fake.calls[1].system.panels[0].tilt_deg == 20
    assert app.cfg is new


def test_tray_menu_has_settings_and_no_config_file_entry(qapp, tmp_path, monkeypatch):
    app = make_app(tmp_path, monkeypatch, FakeForecast())
    labels = [a.text() for a in app.menu.actions() if a.text()]
    assert "Settings..." in labels and "Open config file" not in labels


def test_opening_settings_twice_gives_one_window(qapp, tmp_path, monkeypatch):
    app = make_app(tmp_path, monkeypatch, FakeForecast())
    app.shell.windows.open_settings()
    first = app.shell.windows.settings_window
    app.shell.windows.open_settings()
    assert app.shell.windows.settings_window is first and first.isVisible()
    first.confirm_discard = lambda: True
    first.close()
    app.shell.windows.open_settings()
    assert app.shell.windows.settings_window is not first and app.shell.windows.settings_window.isVisible()
    app.shell.windows.settings_window.close()


def test_saving_in_the_window_applies_to_the_running_app(qapp, tmp_path, monkeypatch):
    fake = FakeForecast()
    app = make_app(tmp_path, monkeypatch, fake)
    loaded(app, fake, qapp)
    app.shell.windows.open_settings()
    window = app.shell.windows.settings_window
    window.field_tabs[0].inputs["panels.tilt_deg"].setValue(20)
    window.save_button.click()
    assert app.cfg.system.panels[0].tilt_deg == 20
    assert (tmp_path / "config.toml").exists()
    wait_until(lambda: not app.fetching and planned(app), qapp)
    assert fake.calls[-1].system.panels[0].tilt_deg == 20
