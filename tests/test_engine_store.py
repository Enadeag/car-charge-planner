"""The engine keeps the stored data: settings, keys and one-time hints are saved by asking it, without Qt."""

import os
from dataclasses import replace
from datetime import datetime

import pytest

from car_charge_planner.config.model import Forecast, load_config
from car_charge_planner.engine import Engine, FileStore, KeyStorage, run_here
from car_charge_planner.forecast.provider import SettingSpec
from car_charge_planner.forecast.registry import ProviderRegistry
from car_charge_planner.planner.model import Panels
from conftest import TZ, FakeNotifier, NoForecast, example_config
from fake_provider import FakeProvider, steps_for

KEY = "STORE-SECRET-KEY-1"


def keyed():
    return FakeProvider(
        id="keyed",
        name="Keyed",
        settings=(SettingSpec("api_key", "API key", "Your key.", optional=False, secret=True),),
        script=[steps_for(datetime.now(TZ).date())],
    )


def engine(tmp_path, cfg=None, registry=None, config_file=None):
    store = FileStore(config_file or tmp_path / "config.toml")
    return Engine(cfg or example_config(), FakeNotifier(), NoForecast(), run_in_background=run_here, store=store, registry=registry)


def registry_with(*providers):
    registry = ProviderRegistry()
    for provider in providers:
        registry.register(provider)
    return registry


def test_saved_settings_are_written_and_used_at_once(tmp_path):
    e = engine(tmp_path)
    tilted = example_config().with_system(panels=(Panels(kwp=6.0, tilt_deg=20),))
    assert e.save_settings(tilted) == []
    assert load_config(tmp_path / "config.toml") == tilted and e.cfg == tilted


@pytest.mark.parametrize(
    "change, problem",
    [
        (lambda cfg: cfg.with_system(panels=(Panels(kwp=0.0),)), "kwp must be above 0"),
        (lambda cfg: cfg.with_system(latitude=0.0, longitude=0.0), "Enter your location"),
    ],
)
def test_settings_that_do_not_pass_the_checks_are_not_saved_and_say_why(tmp_path, change, problem):
    e = engine(tmp_path)
    problems = e.save_settings(change(example_config()))
    assert len(problems) == 1 and problem in problems[0]
    assert not (tmp_path / "config.toml").exists() and e.cfg == example_config()


def test_a_config_that_cannot_be_written_is_said_and_nothing_changes(tmp_path):
    blocker = tmp_path / "blocked"
    blocker.write_text("a file where the folder should be", encoding="utf-8")
    e = engine(tmp_path, config_file=blocker / "config.toml")
    problems = e.save_settings(example_config().with_system(latitude=51.0))
    assert len(problems) == 1 and "Could not write" in problems[0] and e.cfg == example_config()


def test_keys_go_to_the_keychain_and_never_into_the_config(tmp_path, keychain):
    e = engine(tmp_path)
    assert e.save_settings(example_config(), {"keyed": {"api_key": KEY}}) == []
    assert KEY not in (tmp_path / "config.toml").read_text(encoding="utf-8")
    assert keychain.entries[("car-charge-planner", "forecast/keyed/api_key")] == KEY
    assert e.saved_keys().values == {"keyed": {"api_key": KEY}}
    assert e.key_storage().kind == "keychain"


def test_without_a_keychain_the_keys_are_in_secrets_toml_and_the_engine_says_where(tmp_path, no_keychain):
    e = engine(tmp_path)
    assert e.save_settings(example_config(), {"keyed": {"api_key": KEY}}) == []
    assert KEY in (tmp_path / "secrets.toml").read_text(encoding="utf-8")
    assert e.key_storage() == KeyStorage("file", str(tmp_path / "secrets.toml"))


def test_keys_that_cannot_be_stored_keep_the_settings_from_being_saved_too(tmp_path, no_keychain):
    (tmp_path / "secrets.toml").mkdir()  # a folder where the file should go: writing it fails on every system
    e = engine(tmp_path)
    problems = e.save_settings(example_config().with_system(latitude=51.0), {"keyed": {"api_key": KEY}})
    assert len(problems) == 1 and KEY not in problems[0]
    assert not (tmp_path / "config.toml").exists() and e.cfg == example_config()


def test_settings_saved_without_keys_leave_the_keys_alone(tmp_path, keychain):
    e = engine(tmp_path)
    e.save_settings(example_config(), {"keyed": {"api_key": KEY}})
    assert e.save_settings(example_config().with_system(latitude=51.0)) == []
    assert e.saved_keys().values == {"keyed": {"api_key": KEY}}


def test_keys_that_cannot_be_read_are_said_without_their_values(tmp_path, no_keychain):
    (tmp_path / "secrets.toml").write_text(f'[keyed]\napi_key = "{KEY}"\nthis is [not toml', encoding="utf-8")
    os.chmod(tmp_path / "secrets.toml", 0o600)
    keys = engine(tmp_path).saved_keys()
    assert keys.values == {} and "not valid TOML" in keys.problem and KEY not in keys.problem


def test_the_settings_check_names_a_missing_key_and_an_unknown_provider(tmp_path):
    e = engine(tmp_path, registry=registry_with(keyed()))
    missing = e.check_settings(replace(example_config(), forecast=Forecast("keyed", "", {})), {})
    unknown = e.check_settings(replace(example_config(), forecast=Forecast("solcast", "", {})), {})
    assert any("API key is required" in p for p in missing) and any("'solcast' is not known" in p for p in unknown)
    assert e.check_settings(replace(example_config(), forecast=Forecast("keyed", "", {})), {"keyed": {"api_key": KEY}}) == []


def test_a_provider_check_uses_the_saved_keys_unless_others_are_given(tmp_path, keychain):
    provider = keyed()
    e = engine(tmp_path, registry=registry_with(provider))
    e.save_settings(example_config(), {"keyed": {"api_key": KEY}})
    cfg = replace(example_config(), forecast=Forecast("keyed", "", {}))
    attempts = []
    e.check_provider("keyed", cfg, None, attempts.append)
    e.check_provider("keyed", cfg, {"keyed": {"api_key": "typed"}}, attempts.append)
    assert [a.worked for a in attempts] == [True, True]
    assert [settings for _, settings, _ in provider.calls] == [{"api_key": KEY}, {"api_key": "typed"}]


def test_checking_a_provider_this_version_does_not_know_says_so(tmp_path):
    attempts = []
    engine(tmp_path).check_provider("solcast", example_config(), {}, attempts.append)
    assert not attempts[0].worked and "'solcast' is not known" in attempts[0].message


def test_a_hint_is_seen_once_also_after_a_restart(tmp_path):
    assert engine(tmp_path).mark_seen("background") is True
    again = engine(tmp_path)
    assert again.mark_seen("background") is False and again.mark_seen("no-tray") is True


def test_a_settings_watcher_hears_the_old_and_the_new_settings(tmp_path):
    e = engine(tmp_path)
    heard = []
    e.watch_settings(lambda old, new: heard.append((old.system.latitude, new.system.latitude)))
    e.save_settings(example_config().with_system(latitude=51.0))
    assert heard == [(52.37, 51.0)]
