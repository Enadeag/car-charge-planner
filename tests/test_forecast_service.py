"""The forecast service: primary, fallback, cache, cool-down (ticket P6). Spec section 5 and the matrix of section 8."""

import json
import logging
import threading
from dataclasses import replace
from datetime import date, timedelta, timezone

import pytest

from fake_provider import FakeProvider, Held, steps_for
from forecast.provider import BadResponse, RateLimited, SettingSpec, Unavailable
from forecast.registry import ProviderRegistry
from forecast.secret_store import SecretsError
from forecast.service import ForecastService, ForecastUnavailable
from planner.model import Forecast

from conftest import at

DAY = date(2026, 7, 3)
T0 = at(DAY, 7)
MIN = timedelta(hours=1)  # the fake providers' minimum refresh
USEFUL = timedelta(hours=6)  # and their useful age
SECRET = "SERVICE-SECRET-31"


def provider(id, **kwargs):
    kwargs.setdefault("name", id.title())
    kwargs.setdefault("attribution", f"Forecast by {id.title()}")
    return FakeProvider(id=id, **kwargs)


class Rig:
    """A service over fake providers named "main" (primary) and "backup" (fallback), with a cache in tmp_path."""

    def __init__(self, tmp_path, cfg, main=None, backup=None, fallback=True, secrets=None):
        self.main = main or provider("main", script=[steps_for(DAY)])
        self.backup = backup or provider("backup", script=[steps_for(DAY, 500)])
        registry = ProviderRegistry()
        registry.register(self.main)
        registry.register(self.backup)
        self.cache = tmp_path / "cache"
        self.registry = registry
        self.secrets = secrets or (lambda: {})
        self.service = ForecastService(registry, self.cache, lambda: self.secrets())
        self.cfg = replace(cfg, forecast=Forecast(primary="main", fallback="backup" if fallback else "", settings={}))

    def get(self, now=T0, force=False, cfg=None):
        return self.service.get(cfg or self.cfg, now, force=force)

    def restart(self):
        self.service = ForecastService(self.registry, self.cache, lambda: self.secrets())


@pytest.fixture
def rig(tmp_path, cfg):
    return Rig(tmp_path, cfg)


def later(minutes=0, hours=0):
    return T0 + timedelta(minutes=minutes, hours=hours)


# ---- the happy path and the cache ---------------------------------------------------------------------------


def test_the_primary_is_asked_and_the_result_says_who_answered(rig):
    result = rig.get()
    assert (result.provider_id, result.provider_name, result.attribution) == ("main", "Main", "Forecast by Main")
    assert not result.from_cache and not result.stale and not result.from_fallback and result.cooldown_until is None
    assert result.steps == steps_for(DAY) and result.fetched_at == T0.astimezone(timezone.utc)
    assert rig.main.call_count == 1 and rig.backup.call_count == 0
    assert (rig.cache / "forecast-main.json").exists()


def test_the_provider_is_given_the_system_the_settings_and_the_time(rig, cfg):
    rig.get()
    system, settings, now = rig.main.calls[0]
    assert (system.latitude, system.kwp, system.timezone) == (cfg.latitude, cfg.panels.kwp, cfg.timezone)
    assert settings == {} and now == T0


def test_within_the_minimum_refresh_the_cache_answers_and_nothing_is_asked(rig):
    rig.get()
    result = rig.get(later(minutes=59))
    assert result.from_cache and not result.stale and rig.main.call_count == 1
    assert result.steps == steps_for(DAY)


def test_the_owner_can_force_a_refresh_inside_the_minimum_interval(rig):
    rig.get()
    assert not rig.get(later(minutes=5), force=True).from_cache
    assert rig.main.call_count == 2


def test_after_the_minimum_refresh_it_is_asked_again(rig):
    rig.get()
    assert not rig.get(later(minutes=61)).from_cache and rig.main.call_count == 2


def test_the_cache_survives_a_restart(rig):
    rig.get()
    rig.restart()
    assert rig.get(later(minutes=30)).from_cache and rig.main.call_count == 1


def test_a_fresh_cache_with_a_failing_provider_is_never_a_reason_to_ask(rig):
    rig.main.script = [steps_for(DAY), Unavailable("down")]
    rig.get()
    assert rig.get(later(minutes=30)).from_cache and rig.main.call_count == 1


# ---- the primary fails ----------------------------------------------------------------------------------------


def test_a_failure_keeps_the_primarys_last_good_forecast_while_it_is_still_useful(rig):
    rig.main.script = [steps_for(DAY), Unavailable("no network")]
    first = rig.get()
    result = rig.get(later(hours=2))  # due again, and it fails
    assert result.stale and result.from_cache and not result.from_fallback and result.provider_id == "main"
    assert result.fetched_at == first.fetched_at and rig.backup.call_count == 0


def test_a_cache_too_old_to_be_useful_goes_to_the_fallback(rig):
    rig.main.script = [steps_for(DAY), Unavailable("no network")]
    rig.get()
    result = rig.get(later(hours=7))  # older than the 6 hours the provider calls useful
    assert result.from_fallback and result.provider_id == "backup" and result.primary_name == "Main"
    assert result.attribution == "Forecast by Backup" and not result.stale and not result.from_cache
    assert (rig.cache / "forecast-backup.json").exists()


def test_with_no_cache_at_all_the_fallback_is_used_at_once(rig):
    rig.main.script = [Unavailable("no network")]
    result = rig.get()
    assert result.from_fallback and rig.main.call_count == 1 and rig.backup.call_count == 1


def test_the_fallback_has_its_own_cache_and_rules(rig):
    rig.main.script = [Unavailable("no network")]
    rig.get()
    again = rig.get(later(minutes=10))  # the primary is asked again and fails again; the fallback's cache is fresh
    assert again.from_fallback and again.from_cache and rig.main.call_count == 2 and rig.backup.call_count == 1
    pushed = rig.get(later(minutes=11), force=True)  # the owner pressed refresh: whoever answers is asked again
    assert pushed.from_fallback and not pushed.from_cache and rig.backup.call_count == 2


def test_with_nothing_usable_anywhere_the_reasons_are_given_for_both(rig):
    rig.main.script = [Unavailable("no network")]
    rig.backup.script = [BadResponse("not what it documents")]
    with pytest.raises(ForecastUnavailable) as caught:
        rig.get()
    message = str(caught.value)
    assert "Main: no network" in message and "Backup: not what it documents" in message


def test_without_a_fallback_and_without_a_cache_there_is_no_forecast(tmp_path, cfg):
    rig = Rig(tmp_path, cfg, main=provider("main", script=[Unavailable("no network")]), fallback=False)
    with pytest.raises(ForecastUnavailable, match="Main: no network"):
        rig.get()
    assert rig.backup.call_count == 0


def test_a_recovered_primary_takes_over_at_the_next_refresh(rig):
    rig.main.script = [Unavailable("down"), steps_for(DAY, 800)]
    assert rig.get().from_fallback
    result = rig.get(later(minutes=6))  # past the short retry delay
    assert not result.from_fallback and result.provider_id == "main" and result.primary_name == ""


def test_a_provider_that_could_not_be_reached_is_retried_soon_but_not_every_minute(rig):
    rig.main.script = [Unavailable("down")]
    rig.get()
    rig.get(later(minutes=1))
    rig.get(later(minutes=4))
    assert rig.main.call_count == 1  # waiting out the short delay: the fallback's cache answered
    rig.get(later(minutes=6))
    assert rig.main.call_count == 2
    rig.get(later(minutes=7), force=True)  # the owner may push
    assert rig.main.call_count == 3


def test_a_bad_answer_counts_as_a_request_and_waits_the_full_interval(rig):
    rig.main.script = [BadResponse("odd")]
    rig.get()
    rig.get(later(minutes=30))
    assert rig.main.call_count == 1
    rig.get(later(minutes=61))
    assert rig.main.call_count == 2


def test_a_provider_that_breaks_the_contract_is_treated_as_a_bad_answer(rig):
    from planner.model import ForecastStep

    negative = [ForecastStep(T0, T0 + MIN, -1.0)]
    rig.main.script = [negative]
    result = rig.get()
    assert result.from_fallback  # the primary's answer was refused, not used


def test_a_bug_in_a_provider_does_not_stop_the_app_and_does_not_log_its_message(rig, caplog):
    rig.main.script = [ValueError(f"oops with {SECRET}")]
    with caplog.at_level(logging.DEBUG):
        result = rig.get()
    assert result.from_fallback and SECRET not in caplog.text and "ValueError" in caplog.text
    with pytest.raises(ForecastUnavailable):
        rig_two = Rig(rig.cache.parent / "two", rig.cfg, main=provider("main", script=[ValueError(SECRET)]), fallback=False)
        try:
            rig_two.get()
        except ForecastUnavailable as err:
            assert SECRET not in str(err)
            raise


# ---- rate limits and the cool-down ---------------------------------------------------------------------------


def test_a_rate_limit_starts_a_cool_down_that_even_a_forced_refresh_respects(rig):
    rig.main.script = [steps_for(DAY), RateLimited("busy", timedelta(minutes=30))]
    rig.get()
    limited = rig.get(later(hours=2))
    assert limited.stale and limited.cooldown_until == (later(hours=2) + timedelta(minutes=30)).astimezone(timezone.utc)
    assert rig.main.call_count == 2
    for minutes in (125, 140):
        rig.get(later(minutes=minutes), force=True)
    assert rig.main.call_count == 2  # not asked during the cool-down, forced or not
    rig.main.script = [steps_for(DAY), RateLimited("busy", timedelta(minutes=30)), steps_for(DAY, 700)]
    again = rig.get(later(hours=2, minutes=31))
    assert rig.main.call_count == 3 and not again.stale and again.cooldown_until is None


def test_without_a_retry_time_the_cool_down_is_the_minimum_refresh(rig):
    rig.main.script = [steps_for(DAY), RateLimited("busy")]
    rig.get()
    rig.get(later(hours=2))
    rig.get(later(hours=2, minutes=59))
    assert rig.main.call_count == 2
    rig.get(later(hours=3, minutes=1))
    assert rig.main.call_count == 3


def test_during_a_cool_down_with_no_usable_cache_the_fallback_answers(rig):
    rig.main.script = [RateLimited("busy", timedelta(hours=1))]
    first = rig.get()
    assert first.from_fallback and rig.main.call_count == 1
    second = rig.get(later(minutes=30), force=True)
    assert second.from_fallback and rig.main.call_count == 1  # the primary was left alone


def test_the_cool_down_is_remembered_across_a_restart(rig):
    rig.main.script = [RateLimited("busy", timedelta(hours=1))]
    rig.get()
    rig.restart()
    rig.get(later(minutes=10))
    assert rig.main.call_count == 1


def test_a_cool_down_that_has_passed_is_forgotten(rig):
    rig.main.script = [RateLimited("busy", timedelta(minutes=10)), steps_for(DAY)]
    rig.get()
    rig.restart()
    assert not rig.get(later(minutes=11)).from_fallback and rig.main.call_count == 2


# ---- what a cache is for -----------------------------------------------------------------------------------


def test_a_changed_system_or_setting_makes_the_cache_not_apply(rig, cfg):
    rig.get()
    bigger = replace(rig.cfg, panels=replace(cfg.panels, kwp=9.0))
    assert not rig.get(later(minutes=5), cfg=bigger).from_cache
    moved = replace(rig.cfg, latitude=51.0)
    assert not rig.get(later(minutes=6), cfg=moved).from_cache
    assert rig.main.call_count == 3
    assert rig.get(later(minutes=7), cfg=moved).from_cache  # and the new one is cached in turn


def test_a_corrupt_cache_is_ignored(rig):
    rig.get()
    for junk in ("not json", "[]", '{"key": 1}', '{"key": {}, "fetched_at": "x", "steps": 5}', ""):
        (rig.cache / "forecast-main.json").write_text(junk)
        rig.main.script = [steps_for(DAY)]
        rig.main.calls.clear()
        assert not rig.get(later(minutes=5), force=True).from_cache and rig.main.call_count == 1


def test_a_cache_with_steps_that_break_the_contract_is_ignored(rig):
    rig.get()
    path = rig.cache / "forecast-main.json"
    blob = json.loads(path.read_text())
    blob["steps"][3][2] = -4.0
    path.write_text(json.dumps(blob))
    rig.main.script = [Unavailable("down")]
    rig.backup.script = [steps_for(DAY, 400)]
    assert rig.get(later(hours=1, minutes=1)).from_fallback  # the damaged cache is not worth using


def test_the_old_single_cache_file_is_ignored(rig):
    (rig.cache).mkdir(parents=True)
    (rig.cache / "forecast.json").write_text(json.dumps({"key": {}, "fetched_at": 1, "response": {}}))
    assert not rig.get().from_cache


def test_an_unwritable_cache_directory_does_not_break_planning(tmp_path, cfg):
    blocker = tmp_path / "cache"
    blocker.write_text("a file where the directory should be")
    rig = Rig(tmp_path, cfg)
    assert rig.get().steps  # the fetch worked; nothing could be saved


# ---- settings and secrets -----------------------------------------------------------------------------------


def needy(**kwargs):
    return provider("main", settings=(SettingSpec("api_key", "API key", "Your key.", optional=False, secret=True),), **kwargs)


def test_a_missing_required_setting_means_nothing_is_sent(tmp_path, cfg):
    rig = Rig(tmp_path, cfg, main=needy(script=[steps_for(DAY)]))
    result = rig.get()
    assert result.from_fallback and rig.main.call_count == 0
    solo = Rig(tmp_path / "solo", cfg, main=needy(script=[steps_for(DAY)]), fallback=False)
    with pytest.raises(ForecastUnavailable, match="Main: API key is required"):
        solo.get()


def test_secrets_come_from_the_secrets_file_and_plain_settings_from_the_config(tmp_path, cfg):
    rig = Rig(tmp_path, cfg, main=needy(script=[steps_for(DAY)]), secrets=lambda: {"main": {"api_key": SECRET}})
    rig.cfg = replace(rig.cfg, forecast=replace(rig.cfg.forecast, settings={"main": {"colour": "ignored"}}))
    rig.get()
    assert rig.main.calls[0][1] == {"api_key": SECRET}


def test_a_changed_secret_does_not_throw_the_cached_forecast_away(tmp_path, cfg):
    holder = {"main": {"api_key": "first"}}
    rig = Rig(tmp_path, cfg, main=needy(script=[steps_for(DAY)]), secrets=lambda: holder)
    rig.get()
    holder["main"]["api_key"] = "second"
    assert rig.get(later(minutes=5)).from_cache


def test_an_unreadable_secrets_file_does_not_stop_providers_that_need_no_key(tmp_path, cfg):
    def broken():
        raise SecretsError("secrets.toml is not valid TOML")

    rig = Rig(tmp_path, cfg, secrets=broken)
    assert rig.get().provider_id == "main"
    keyed = Rig(tmp_path / "k", cfg, main=needy(script=[steps_for(DAY)]), backup=provider("backup", script=[Unavailable("down")]), secrets=broken)
    with pytest.raises(ForecastUnavailable, match="not valid TOML"):
        keyed.get()


def test_an_unknown_provider_is_a_reason_not_a_crash(tmp_path, cfg):
    rig = Rig(tmp_path, cfg)
    unknown = replace(rig.cfg, forecast=replace(rig.cfg.forecast, primary="solcast"))
    result = rig.get(cfg=unknown)
    assert result.from_fallback and result.primary_name == ""  # the primary is not known: no name to give
    only = replace(unknown, forecast=replace(unknown.forecast, fallback=""))
    with pytest.raises(ForecastUnavailable, match="'solcast' is not known"):
        rig.get(cfg=only)


# ---- threads and the controller ------------------------------------------------------------------------------


def test_two_fetches_at_once_ask_the_provider_once(tmp_path, cfg):
    release = threading.Event()
    rig = Rig(tmp_path, cfg, main=provider("main", script=[Held(release, steps_for(DAY))]))
    results = []
    workers = [threading.Thread(target=lambda: results.append(rig.get())) for _ in range(2)]
    for worker in workers:
        worker.start()
    release.set()
    for worker in workers:
        worker.join(5)
    assert len(results) == 2 and rig.main.call_count == 1
    assert sorted(r.from_cache for r in results) == [False, True]


def test_a_two_day_forecast_plans_without_trouble(rig, cfg):
    from planner.projection import plan

    rig.main.script = [steps_for(DAY) + steps_for(DAY + timedelta(days=1))]
    result = rig.get()
    days = plan(result.steps, rig.cfg, 45, T0)
    assert len(days) == 2 and all(d.windows for d in days)
