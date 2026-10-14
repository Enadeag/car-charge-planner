"""PlanView is the content; Popup is the window around it."""

import os

os.environ.setdefault("QT_QPA_PLATFORM", "offscreen")

from datetime import date, timedelta

from car_charge_planner.planner.projection import plan
from car_charge_planner.planview import PLAN, UPDATE, PlanView
from car_charge_planner.popup import Popup
from car_charge_planner.storage.state import BatteryReading, State
from car_charge_planner.view_model import build
from conftest import TZ, at, forecast_from

DAY = date(2026, 7, 3)
NOW = at(DAY, 7)


def vm_for(cfg):
    forecast = forecast_from([(DAY + timedelta(days=i), 900) for i in range(3)])
    return build(plan(forecast, cfg.system, cfg.planning, 45, NOW), NOW, cfg, BatteryReading(45, NOW), timedelta(minutes=5), False)


def test_the_view_works_on_its_own(cfg, qapp):
    view = PlanView(TZ)
    vm = vm_for(cfg)
    view.update_view(vm, State(BatteryReading(45, NOW)), DAY, cfg.system.away_rules)
    assert view.headline.text() == vm.headline and view.tabs.count() == 2
    assert view.tabs.currentIndex() == PLAN
    view.show_update()
    assert view.tabs.currentIndex() == UPDATE


def test_the_view_signals_reach_the_popup_unchanged(cfg, qapp):
    popup = Popup(TZ)
    got = []
    popup.battery_submitted.connect(lambda v: got.append(("battery", v)))
    popup.settings_requested.connect(lambda t: got.append(("settings", t)))
    popup.view.battery_submitted.emit(55.0)
    popup.view.settings_requested.emit("away")
    assert got == [("battery", 55.0), ("settings", "away")]
