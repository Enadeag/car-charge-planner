"""The bridge between the engine and QML: the engine's updates become a model of days and properties; actions reach the engine."""

from datetime import date, timedelta

import pytest
from PySide6.QtCore import QPointF

from car_charge_planner.engine import Engine
from car_charge_planner.forecast.service import ForecastResult
from car_charge_planner.quick.bridge import DashboardBridge, DaysModel
from conftest import FakeClock, FakeNotifier, at, forecast_from

WEDNESDAY = date(2026, 10, 14)


class RecordingForecast:
    def __init__(self, result):
        self.result = result
        self.forced = []

    def get(self, cfg, now, force=False):
        self.forced.append(force)
        return self.result

    def forget_failures(self):
        pass


@pytest.fixture
def rig(cfg, qapp):
    steps = forecast_from([(WEDNESDAY, 700), (WEDNESDAY + timedelta(days=1), 650), (WEDNESDAY + timedelta(days=2), 120)])
    forecasts = RecordingForecast(ForecastResult(steps, at(WEDNESDAY, 6), True, False))
    clock = FakeClock(at(WEDNESDAY, 8, 40))
    engine = Engine(cfg, FakeNotifier(), forecasts, clock)
    engine.set_battery(35)
    bridge = DashboardBridge(engine)
    updates = []
    engine.watch(updates.append)
    engine.watch(bridge.show)
    engine.refresh()
    return engine, bridge, updates, forecasts, clock


def rows(model: DaysModel):
    return [
        {name: model.data(model.index(row), role) for role, name in ((DaysModel.TAB, "tab"), (DaysModel.KIND, "kind"), (DaysModel.DAY, "day"))}
        for row in range(model.rowCount())
    ]


def test_the_days_model_has_a_row_per_planned_day_with_its_values(rig):
    _engine, bridge, updates, _forecasts, _clock = rig
    days = updates[-1].days
    found = rows(bridge.property("days"))

    assert [(r["tab"], r["kind"]) for r in found] == [("Today", "sun"), ("Thu", "grid"), ("Fri", "none")]
    today = found[0]["day"]
    assert today["hero"] == days[0].hero == days[0].charges[0].text
    assert today["charges"] == [{"start": days[0].charges[0].start_h, "end": days[0].charges[0].end_h, "text": days[0].charges[0].text}]
    assert today["nowHour"] == pytest.approx(8 + 40 / 60, abs=0.001) and found[1]["day"]["nowHour"] == -1
    assert today["solar"] == [QPointF(hour, kw) for hour, kw in days[0].solar]
    assert (today["batteryBefore"], today["batteryAfter"], today["minimum"], today["target"]) == (35, days[0].battery_after_pct, 30, 80)
    assert found[1]["day"]["grid"] == {"start": days[1].grid.start_h, "end": days[1].grid.end_h, "text": days[1].grid.text}


def test_advice_footer_and_state_reach_the_properties(rig):
    _engine, bridge, updates, _forecasts, _clock = rig
    vm = updates[-1].vm
    assert vm.advice is not None
    assert bridge.property("hasAdvice") is True
    assert (bridge.property("adviceTitle"), bridge.property("adviceDetail"), bridge.property("adviceReason")) == (vm.advice.title, vm.advice.detail, vm.advice.reason)
    assert (bridge.property("state"), bridge.property("footer"), bridge.property("headline")) == (vm.state, vm.footer, vm.headline)


def test_the_minutes_update_changes_the_rows_in_place_and_a_new_day_replaces_them(rig):
    engine, bridge, _updates, _forecasts, clock = rig
    model = bridge.property("days")
    resets, changes = [], []
    model.modelReset.connect(lambda: resets.append(1))
    model.dataChanged.connect(lambda first, last, roles: changes.append((first.row(), last.row())))

    clock.now = at(WEDNESDAY, 8, 41)
    engine.tick()
    assert (resets, changes) == ([], [(0, 2)])  # the tab bar keeps its delegates, selection and focus

    clock.now = at(WEDNESDAY + timedelta(days=1), 0, 5)
    engine.tick()
    assert resets == [1] and rows(model)[0]["tab"] == "Today" and rows(model)[0]["day"]["date"] == "2026-10-15"


def test_refresh_asks_the_engine_for_a_new_forecast(rig):
    _engine, bridge, _updates, forecasts, _clock = rig
    bridge.refresh()
    assert forecasts.forced == [False, True]


def test_without_a_plan_there_are_no_days_but_the_headline_says_why(cfg, qapp):
    engine = Engine(cfg.with_system(latitude=0.0, longitude=0.0), FakeNotifier(), RecordingForecast(None), FakeClock(at(WEDNESDAY, 9)))
    bridge = DashboardBridge(engine)
    engine.watch(bridge.show)
    engine.refresh()
    assert bridge.property("days").rowCount() == 0
    assert bridge.property("state") == "needs-setup" and bridge.property("headline") == engine.vm.headline
