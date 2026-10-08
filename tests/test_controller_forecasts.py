"""The controller on the forecast service, with fake providers (ticket P6)."""

import os

os.environ.setdefault("QT_QPA_PLATFORM", "offscreen")

import time
from dataclasses import replace
from datetime import UTC, datetime, timedelta

import pytest
from PySide6.QtTest import QTest
from PySide6.QtWidgets import QApplication

from car_charge_planner.controller import Controller
from car_charge_planner.forecast.provider import Unavailable
from car_charge_planner.forecast.registry import ProviderRegistry
from car_charge_planner.forecast.service import ForecastService
from car_charge_planner.planner.model import Forecast
from conftest import FakeNotifier
from fake_provider import FakeProvider, steps_for

TODAY = datetime.now().date()


@pytest.fixture
def qapp():
    return QApplication.instance() or QApplication([])


def make(cfg, tmp_path, monkeypatch, main_script, backup_script=None, fallback=True):
    monkeypatch.setenv("XDG_STATE_HOME", str(tmp_path))
    main = FakeProvider(id="main", name="Main", attribution="Forecast by Main", script=main_script)
    backup = FakeProvider(id="backup", name="Backup", attribution="Forecast by Backup", script=backup_script or [steps_for(TODAY)])
    registry = ProviderRegistry()
    registry.register(main)
    registry.register(backup)
    service = ForecastService(registry, tmp_path / "cache", lambda: {})
    cfg = replace(cfg, forecast=Forecast("main", "backup" if fallback else "", {}))
    return Controller(cfg, tmp_path / "config.toml", FakeNotifier(), service), main, backup


def settle(controller, qapp):
    deadline = time.time() + 5
    while controller._loading and time.time() < deadline:
        QTest.qWait(20)
    QTest.qWait(30)
    assert not controller._loading


def steps_today(days=3, peak=900):
    out = []
    for i in range(days):
        out += steps_for(TODAY + timedelta(days=i), peak)
    return out


def test_the_controller_gets_its_forecast_from_the_service(qapp, tmp_path, monkeypatch, cfg):
    controller, main, _ = make(cfg, tmp_path, monkeypatch, [steps_today()])
    controller.refresh()
    settle(controller, qapp)
    assert controller.result.provider_id == "main" and controller.error is None and main.call_count == 1
    assert controller.vm is not None and controller.vm.state != "no-forecast"


def test_a_fallback_forecast_is_what_the_plan_uses(qapp, tmp_path, monkeypatch, cfg):
    controller, main, backup = make(cfg, tmp_path, monkeypatch, [Unavailable("down")], [steps_today()])
    controller.refresh()
    settle(controller, qapp)
    assert controller.result.from_fallback and controller.result.provider_id == "backup"
    assert controller.vm.state != "no-forecast"


def test_when_nothing_answers_the_reasons_reach_the_plan_page(qapp, tmp_path, monkeypatch, cfg):
    controller, *_ = make(cfg, tmp_path, monkeypatch, [Unavailable("no network")], fallback=False)
    controller.refresh()
    settle(controller, qapp)
    assert controller.result is None and "Main: no network" in controller.vm.detail
    assert controller.vm.state == "no-forecast"


def test_a_short_horizon_gives_a_shorter_outlook_without_an_error(qapp, tmp_path, monkeypatch, cfg):
    controller, *_ = make(cfg, tmp_path, monkeypatch, [steps_today(days=2, peak=120)], fallback=False)
    controller.refresh()
    settle(controller, qapp)
    assert controller.error is None and "next 2 days" in controller.vm.detail


def test_switching_the_provider_drops_the_forecast_that_was_shown(qapp, tmp_path, monkeypatch, cfg):
    controller, main, backup = make(cfg, tmp_path, monkeypatch, [steps_today()], [steps_today(peak=500)])
    controller.refresh()
    settle(controller, qapp)
    assert controller.result.provider_id == "main"
    switched = replace(controller.cfg, forecast=Forecast("backup", "", {}))
    controller.apply_config(switched)
    assert controller.result is None  # not the old provider's forecast while the new one loads
    settle(controller, qapp)
    assert controller.result.provider_id == "backup"


def test_a_planning_change_keeps_the_forecast_and_asks_nobody(qapp, tmp_path, monkeypatch, cfg):
    controller, main, _ = make(cfg, tmp_path, monkeypatch, [steps_today()])
    controller.refresh()
    settle(controller, qapp)
    kept = controller.result
    controller.apply_config(replace(controller.cfg, planning=replace(controller.cfg.planning, margin=0.5)))
    settle(controller, qapp)
    assert controller.result.steps == kept.steps and controller.result.from_cache and main.call_count == 1  # the cache answered


def test_saving_settings_lets_a_provider_that_failed_be_asked_again(qapp, tmp_path, monkeypatch, cfg):
    controller, main, backup = make(cfg, tmp_path, monkeypatch, [Unavailable("down"), steps_today()], [steps_today(peak=500)])
    controller.refresh()
    settle(controller, qapp)
    assert controller.result.from_fallback and main.call_count == 1
    controller.apply_config(replace(controller.cfg, planning=replace(controller.cfg.planning, margin=0.9)))
    settle(controller, qapp)
    assert controller.result.provider_id == "main" and main.call_count == 2  # not after the five minutes


# ---- what the footer says (ticket P8) -----------------------------------------------------------------------


def footer_of(controller):
    update = []
    controller.view_changed.connect(update.append)
    controller.rebuild()
    return update[-1].vm.footer, update[-1].vm.footer_tooltip


def test_the_footer_credits_the_provider_that_made_the_forecast(qapp, tmp_path, monkeypatch, cfg):
    controller, *_ = make(cfg, tmp_path, monkeypatch, [steps_today()])
    controller.refresh()
    settle(controller, qapp)
    footer, tooltip = footer_of(controller)
    assert footer.endswith("Forecast by Main") and footer.startswith("Forecast ") and "primary unavailable" not in footer
    assert tooltip == "Forecast by Main"


def test_a_fallback_forecast_says_so_and_credits_the_fallback(qapp, tmp_path, monkeypatch, cfg):
    controller, *_ = make(cfg, tmp_path, monkeypatch, [Unavailable("down")], [steps_today()])
    controller.refresh()
    settle(controller, qapp)
    footer, tooltip = footer_of(controller)
    assert "from Backup, primary unavailable" in footer and footer.endswith("Forecast by Backup")
    assert tooltip.splitlines()[1] == "Backup is used because Main has no forecast that is still usable."


def test_during_a_cool_down_the_footer_says_the_provider_is_busy_and_after_it_does_not(qapp, tmp_path, monkeypatch, cfg):

    from car_charge_planner.forecast.provider import RateLimited

    controller, main, _backup = make(cfg, tmp_path, monkeypatch, [steps_today(), RateLimited("busy", timedelta(hours=1))], fallback=False)
    controller.refresh()
    settle(controller, qapp)
    controller.refresh(force=True)  # the owner pushes; the provider says wait
    settle(controller, qapp)
    assert "provider busy, retrying later" in footer_of(controller)[0]
    controller.result = replace(controller.result, cooldown_until=datetime.now(UTC) - timedelta(minutes=1))
    assert "provider busy" not in footer_of(controller)[0]


def test_before_a_forecast_arrives_the_footer_is_empty(qapp, tmp_path, monkeypatch, cfg):
    controller, *_ = make(cfg, tmp_path, monkeypatch, [Unavailable("down")], fallback=False)
    controller.refresh()
    settle(controller, qapp)
    assert footer_of(controller) == ("", "")
