"""The engine on its own: no Qt, a clock that stands still, and fetches that finish when the test lets them."""

from dataclasses import dataclass, replace
from datetime import date, timedelta

import pytest

from car_charge_planner.engine import Engine, ViewUpdate, run_here
from car_charge_planner.forecast.service import ForecastResult, ForecastUnavailable
from car_charge_planner.planner.model import AwayPeriod, Config
from car_charge_planner.storage.state import load_state
from conftest import TZ, FakeClock, FakeNotifier, FixedForecast, at, forecast_from

DAY = date(2026, 7, 3)


def sunny(stale=False):
    return ForecastResult(forecast_from([(DAY, 900)]), at(DAY, 6), True, stale)


class HeldRunner:
    """Keeps each job until the test finishes it, as a background thread that is still busy would."""

    def __init__(self):
        self.jobs = []

    def __call__(self, work, done):
        self.jobs.append((work, done))

    def finish_next(self):
        work, done = self.jobs.pop(0)
        done(work())


@dataclass
class Rig:
    engine: Engine
    notifier: FakeNotifier
    forecasts: FixedForecast
    clock: FakeClock
    updates: list[ViewUpdate]

    @property
    def titles(self):
        return [title for title, _ in self.notifier.sent]


def rig(cfg, hour=7, minute=0, result=None, runner=run_here):
    notifier, forecasts, clock = FakeNotifier(), FixedForecast(result or sunny()), FakeClock(at(DAY, hour, minute))
    engine = Engine(cfg, notifier, forecasts, clock, runner)
    updates = []
    engine.watch(updates.append)
    return Rig(engine, notifier, forecasts, clock, updates)


def test_a_refresh_plans_with_the_forecast_and_hands_the_view_to_the_watchers(cfg):
    r = rig(cfg, hour=7)
    r.engine.refresh()
    update = r.updates[-1]
    assert update.vm.state == "upcoming" and "09:00-15:00" in update.vm.headline
    assert (update.today, update.rules, update.tz) == (DAY, cfg.away_rules, TZ)
    assert r.forecasts.calls == [cfg]


def test_the_plan_follows_the_clock(cfg):
    r = rig(cfg, hour=7)
    r.engine.refresh()
    r.clock.now = at(DAY, 10)
    r.engine.tick()
    assert r.updates[-1].vm.state == "active"


def test_it_notifies_once_ahead_of_the_window(cfg):
    r = rig(cfg, 8, 50)
    r.engine.refresh()
    assert len(r.titles) == 1 and r.titles[0].startswith("Charge window 09:00")
    r.clock.now = at(DAY, 8, 52)
    r.engine.tick()
    assert len(r.titles) == 1


def test_it_does_not_notify_again_after_a_restart(cfg):
    rig(cfg, 8, 50).engine.refresh()
    again = rig(cfg, 8, 52)
    again.engine.refresh()
    assert again.titles == []


@pytest.mark.parametrize("minute, stale", [(30, False), (50, True)], ids=["outside the lead time", "refresh failed"])
def test_it_is_silent(cfg, minute, stale):
    r = rig(cfg, 8, minute, result=sunny(stale=stale))
    r.engine.refresh()
    assert r.titles == []


def test_a_new_lead_time_changes_when_the_next_notification_is_due(cfg):
    r = rig(cfg, 8, 30)  # the window starts at 09:00
    r.engine.refresh()
    assert r.titles == []  # the lead time is 15 minutes
    r.engine.apply_config(replace(cfg, notify=replace(cfg.notify, lead_minutes=30)))
    assert len(r.titles) == 1 and r.titles[0].startswith("Charge window 09:00")


def test_a_battery_level_is_stored_and_replans(cfg):
    r = rig(cfg, hour=7)
    r.engine.refresh()
    r.engine.set_battery(40)
    lower = r.updates[-1].vm
    r.engine.set_battery(75)
    higher = r.updates[-1].vm
    assert load_state().battery.pct == 75
    assert (lower.battery_pct, higher.battery_pct) == (40, 75) and lower.projection != higher.projection


def test_an_away_period_is_stored_and_replans(cfg):
    r = rig(cfg, hour=7)
    r.engine.refresh()
    at_home = r.updates[-1].vm.headline
    period = AwayPeriod(at(DAY, 8), at(DAY, 18))
    r.engine.add_away_period(period)
    assert load_state().away == (period,) and r.updates[-1].state.away == (period,)
    assert r.updates[-1].vm.headline != at_home
    r.engine.remove_away_period(period)
    assert load_state().away == () and r.updates[-1].vm.headline == at_home


def test_a_failed_fetch_shows_why(cfg):
    class Down(FixedForecast):
        def get(self, cfg, now, force=False):
            raise ForecastUnavailable("Main: no network")

    engine = Engine(cfg, FakeNotifier(), Down(None), FakeClock(at(DAY, 7)))
    engine.refresh()
    assert engine.vm.state == "no-forecast" and "Main: no network" in engine.vm.detail


def test_while_a_fetch_runs_no_second_one_starts_and_its_result_comes_back_through_the_runner(cfg):
    runner = HeldRunner()
    r = rig(cfg, hour=7, runner=runner)
    r.engine.refresh()
    r.engine.tick()
    assert len(runner.jobs) == 1 and r.engine.fetching and r.updates == []
    runner.finish_next()
    assert not r.engine.fetching and r.updates[-1].vm.state == "upcoming"


def test_a_planning_change_keeps_the_forecast_shown_while_it_asks_again(cfg):
    runner = HeldRunner()
    r = rig(cfg, hour=7, runner=runner)
    r.engine.refresh()
    runner.finish_next()
    r.engine.apply_config(replace(cfg, planning=replace(cfg.planning, margin=0.5)))
    assert r.updates[-1].vm.state == "upcoming"  # not cleared while it is still valid
    runner.finish_next()
    assert r.forecasts.calls[-1].planning.margin == 0.5  # cache-aware: the service decides whether to fetch


def test_a_place_or_angle_change_drops_the_old_forecast_until_the_new_one_arrives(cfg):
    runner = HeldRunner()
    r = rig(cfg, hour=7, runner=runner)
    r.engine.refresh()
    runner.finish_next()
    tilted = replace(cfg, panels=(replace(cfg.panels[0], tilt_deg=20),))
    r.engine.apply_config(tilted)
    assert r.updates[-1].vm.state == "no-forecast"
    runner.finish_next()
    assert r.updates[-1].vm.state == "upcoming" and r.forecasts.calls[-1] is tilted


def test_a_fetch_started_before_a_change_does_not_overwrite_the_new_config(cfg):
    runner = HeldRunner()
    r = rig(cfg, hour=7, runner=runner)
    r.engine.refresh()  # in flight under the old config
    tilted = replace(cfg, panels=(replace(cfg.panels[0], tilt_deg=20),))
    r.engine.apply_config(tilted)
    runner.finish_next()  # the old fetch finishes after the change
    assert r.updates[-1].vm.state == "no-forecast" and len(runner.jobs) == 1  # asked again, for the new config
    runner.finish_next()
    assert [c.panels[0].tilt_deg for c in r.forecasts.calls] == [35, 20] and r.updates[-1].vm.state == "upcoming"


def test_a_new_time_zone_reaches_the_watchers(cfg):
    r = rig(cfg, hour=7)
    r.engine.apply_config(replace(cfg, timezone="Europe/London"))
    assert str(r.updates[-1].tz) == "Europe/London" and str(r.engine.tz) == "Europe/London"


def test_nothing_is_fetched_or_announced_once_the_location_is_gone(cfg):
    r = rig(cfg, 8, 30)
    r.engine.refresh()
    r.clock.now = at(DAY, 8, 50)  # the window would be due now
    r.engine.apply_config(Config())
    assert r.titles == [] and r.updates[-1].vm.state == "needs-setup" and len(r.forecasts.calls) == 1


def test_announced_windows_survive_a_planning_change(cfg):
    r = rig(cfg, 8, 50)
    r.engine.refresh()
    before = load_state().notified
    r.clock.now = at(DAY, 8, 52)
    r.engine.apply_config(replace(cfg, planning=replace(cfg.planning, margin=0.5)))
    assert load_state().notified == before and len(r.titles) == 1


def test_the_next_fetch_follows_the_clock(cfg):
    r = rig(cfg, hour=7)
    seen = []
    r.forecasts.get = lambda cfg, now, force=False: seen.append((now, force)) or sunny()
    r.engine.refresh()
    r.clock.now += timedelta(minutes=1)
    r.engine.refresh(force=True)
    assert seen == [(at(DAY, 7), False), (at(DAY, 7, 1), True)]
