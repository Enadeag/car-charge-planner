"""The engine on the forecast service, with fake providers."""

from dataclasses import replace
from datetime import UTC, datetime, timedelta

from car_charge_planner.engine import Engine
from car_charge_planner.forecast.provider import RateLimited, Unavailable
from car_charge_planner.forecast.registry import ProviderRegistry
from car_charge_planner.forecast.service import ForecastService
from car_charge_planner.planner.model import Forecast
from conftest import FakeClock, FakeNotifier
from fake_provider import FakeProvider, steps_for

TODAY = datetime.now().date()


def make(cfg, tmp_path, monkeypatch, main_script, backup_script=None, fallback=True, clock=None):
    monkeypatch.setenv("XDG_STATE_HOME", str(tmp_path))
    main = FakeProvider(id="main", name="Main", attribution="Forecast by Main", script=main_script)
    backup = FakeProvider(id="backup", name="Backup", attribution="Forecast by Backup", script=backup_script or [steps_for(TODAY)])
    registry = ProviderRegistry()
    registry.register(main)
    registry.register(backup)
    service = ForecastService(registry, tmp_path / "cache", lambda: {})
    cfg = replace(cfg, forecast=Forecast("main", "backup" if fallback else "", {}))
    return Engine(cfg, FakeNotifier(), service, clock or FakeClock(datetime.now(UTC))), main, backup


def make_with_clock(cfg, tmp_path, monkeypatch, main_script):
    clock = FakeClock(datetime.now(UTC))
    engine, *_ = make(cfg, tmp_path, monkeypatch, main_script, fallback=False, clock=clock)
    return engine, clock


def steps_today(days=3, peak=900):
    out = []
    for i in range(days):
        out += steps_for(TODAY + timedelta(days=i), peak)
    return out


def credit(engine):
    return engine.vm.footer_tooltip.splitlines()[0] if engine.vm.footer_tooltip else ""


def watched(engine):
    updates = []
    engine.watch(updates.append)
    return updates


def test_the_engine_gets_its_forecast_from_the_service(tmp_path, monkeypatch, cfg):
    engine, main, _ = make(cfg, tmp_path, monkeypatch, [steps_today()])
    engine.refresh()
    assert credit(engine) == "Forecast by Main" and main.call_count == 1 and engine.vm.state != "no-forecast"


def test_a_fallback_forecast_is_what_the_plan_uses(tmp_path, monkeypatch, cfg):
    engine, main, backup = make(cfg, tmp_path, monkeypatch, [Unavailable("down")], [steps_today()])
    engine.refresh()
    assert credit(engine) == "Forecast by Backup" and engine.vm.state != "no-forecast"


def test_when_nothing_answers_the_reasons_reach_the_plan_page(tmp_path, monkeypatch, cfg):
    engine, *_ = make(cfg, tmp_path, monkeypatch, [Unavailable("no network")], fallback=False)
    engine.refresh()
    assert engine.vm.state == "no-forecast" and "Main: no network" in engine.vm.detail


def test_a_short_horizon_gives_a_shorter_outlook_without_an_error(tmp_path, monkeypatch, cfg):
    engine, *_ = make(cfg, tmp_path, monkeypatch, [steps_today(days=2, peak=120)], fallback=False)
    engine.refresh()
    assert engine.vm.state != "no-forecast" and "next 2 days" in engine.vm.detail


def test_switching_the_provider_drops_the_forecast_that_was_shown(tmp_path, monkeypatch, cfg):
    engine, main, backup = make(cfg, tmp_path, monkeypatch, [steps_today()], [steps_today(peak=500)])
    engine.refresh()
    assert credit(engine) == "Forecast by Main"
    updates = watched(engine)
    engine.apply_config(replace(engine.cfg, forecast=Forecast("backup", "", {})))
    assert updates[0].vm.state == "no-forecast"  # not the old provider's forecast while the new one loads
    assert credit(engine) == "Forecast by Backup"


def test_a_planning_change_keeps_the_forecast_and_asks_nobody(tmp_path, monkeypatch, cfg):
    engine, main, _ = make(cfg, tmp_path, monkeypatch, [steps_today()])
    engine.refresh()
    updates = watched(engine)
    engine.apply_config(replace(engine.cfg, planning=replace(engine.cfg.planning, margin=0.5)))
    assert all(u.vm.state != "no-forecast" for u in updates) and credit(engine) == "Forecast by Main"
    assert main.call_count == 1


def test_saving_settings_lets_a_provider_that_failed_be_asked_again(tmp_path, monkeypatch, cfg):
    engine, main, backup = make(cfg, tmp_path, monkeypatch, [Unavailable("down"), steps_today()], [steps_today(peak=500)])
    engine.refresh()
    assert credit(engine) == "Forecast by Backup" and main.call_count == 1
    engine.apply_config(replace(engine.cfg, planning=replace(engine.cfg.planning, margin=0.9)))
    assert credit(engine) == "Forecast by Main" and main.call_count == 2  # not after the five minutes


def footer_of(engine):
    return engine.vm.footer, engine.vm.footer_tooltip


def test_the_footer_credits_the_provider_that_made_the_forecast(tmp_path, monkeypatch, cfg):
    engine, *_ = make(cfg, tmp_path, monkeypatch, [steps_today()])
    engine.refresh()
    footer, tooltip = footer_of(engine)
    assert footer.endswith("Forecast by Main") and footer.startswith("Forecast ") and "primary unavailable" not in footer
    assert tooltip == "Forecast by Main"


def test_a_fallback_forecast_says_so_and_credits_the_fallback(tmp_path, monkeypatch, cfg):
    engine, *_ = make(cfg, tmp_path, monkeypatch, [Unavailable("down")], [steps_today()])
    engine.refresh()
    footer, tooltip = footer_of(engine)
    assert "from Backup, primary unavailable" in footer and footer.endswith("Forecast by Backup")
    assert tooltip.splitlines()[1] == "Backup is used because Main has no forecast that is still usable."


def test_during_a_cool_down_the_footer_says_the_provider_is_busy_and_after_it_does_not(tmp_path, monkeypatch, cfg):
    engine, clock = make_with_clock(cfg, tmp_path, monkeypatch, [steps_today(), RateLimited("busy", timedelta(hours=1))])
    engine.refresh()
    engine.refresh(force=True)  # the owner pushes; the provider says wait
    assert "provider busy, retrying later" in footer_of(engine)[0]
    clock.now += timedelta(hours=1, minutes=1)
    engine.rebuild()
    assert "provider busy" not in footer_of(engine)[0]


def test_before_a_forecast_arrives_the_footer_is_empty(tmp_path, monkeypatch, cfg):
    engine, *_ = make(cfg, tmp_path, monkeypatch, [Unavailable("down")], fallback=False)
    engine.refresh()
    assert footer_of(engine) == ("", "")
