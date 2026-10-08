"""The [forecast] section of the config, secrets.toml, and the settings a provider declares (ticket P5)."""

import logging
import os
import stat
from dataclasses import replace
from datetime import timedelta

import pytest

from car_charge_planner.forecast.provider import SettingSpec
from car_charge_planner.forecast.registry import ProviderRegistry, registry
from car_charge_planner.forecast.secret_store import SecretsError, dump_secrets, load_secrets, save_secrets
from car_charge_planner.forecast.selection import check_selection, provider_settings, split_settings
from car_charge_planner.planner.config_io import dump_config, write_config
from car_charge_planner.planner.model import Config, ConfigError, Forecast, load_config, validate

SECRET = "SUPER-SECRET-VALUE-42"


def with_forecast(cfg, **changes):
    return replace(cfg, forecast=replace(cfg.forecast, **changes))


# ---- the config section -------------------------------------------------------------------------------------


def test_a_config_without_the_section_means_open_meteo_and_no_fallback(tmp_path):
    path = tmp_path / "config.toml"
    path.write_text('latitude = 52.37\nlongitude = 4.9\n[panels]\nkwp = 5.0\n')  # the file of an older version
    cfg = load_config(path)
    assert cfg.forecast == Forecast("open-meteo", "", {})


def test_the_section_is_written_and_read_back_exactly(tmp_path, cfg):
    chosen = with_forecast(
        cfg, primary="forecast-solar", fallback="open-meteo",
        settings={"open-meteo": {"base_url": "https://customer-api.open-meteo.com"}, "forecast-solar": {}},
    )
    path = tmp_path / "config.toml"
    write_config(chosen, path)
    text = path.read_text()
    assert '[forecast]\nprimary = "forecast-solar"\nfallback = "open-meteo"' in text
    assert '[forecast.open-meteo]\nbase_url = "https://customer-api.open-meteo.com"' in text
    loaded = load_config(path)
    assert loaded.forecast.primary == "forecast-solar" and loaded.forecast.fallback == "open-meteo"
    assert loaded.forecast.settings["open-meteo"] == {"base_url": "https://customer-api.open-meteo.com"}
    assert dump_config(loaded) == text  # the round trip is exact


def test_the_default_config_names_open_meteo():
    assert '[forecast]\nprimary = "open-meteo"\nfallback = ""' in dump_config(Config())


@pytest.mark.parametrize(
    "changes, message",
    [
        ({"primary": ""}, "primary"),
        ({"primary": "Open Meteo"}, "primary"),
        ({"fallback": "x y"}, "fallback"),
        ({"primary": "open-meteo", "fallback": "open-meteo"}, "different"),
        ({"settings": {"Open Meteo": {"a": "b"}}}, "not a provider id"),
        ({"settings": {"open-meteo": {"Base URL": "x"}}}, "must be a text"),
        ({"settings": {"open-meteo": {"base_url": 5}}}, "must be a text"),
    ],
)
def test_a_selection_that_makes_no_sense_is_refused(cfg, changes, message):
    with pytest.raises(ConfigError, match=message):
        validate(with_forecast(cfg, **changes))


def test_an_unknown_key_in_the_section_is_refused_like_everywhere_else(tmp_path):
    path = tmp_path / "config.toml"
    path.write_text('[forecast]\nprimry = "open-meteo"\n')
    with pytest.raises(ConfigError, match="unknown keys in .forecast."):
        load_config(path)


def test_an_unknown_provider_id_is_not_a_config_error(tmp_path, cfg):
    """It may come from a newer version: the check that knows the providers is `check_selection`."""
    path = tmp_path / "config.toml"
    write_config(with_forecast(cfg, primary="solcast"), path)
    assert load_config(path).forecast.primary == "solcast"


def test_nothing_that_looks_like_a_secret_is_written_to_the_config_file(tmp_path, cfg):
    leaky = with_forecast(cfg, settings={"open-meteo": {"api_key": SECRET}})
    with pytest.raises(ConfigError, match="secret"):
        write_config(leaky, tmp_path / "config.toml")
    assert not (tmp_path / "config.toml").exists()
    assert SECRET not in dump_config(with_forecast(cfg, settings={"open-meteo": {"base_url": "https://x.test"}}))


# ---- secrets.toml -------------------------------------------------------------------------------------------


def mode(path):
    return stat.S_IMODE(path.stat().st_mode)


def test_secrets_are_written_private_and_read_back(tmp_path):
    path = tmp_path / "secrets.toml"
    save_secrets({"open-meteo": {"api_key": SECRET}, "forecast-solar": {"api_key": "other"}}, path)
    assert mode(path) == 0o600
    assert load_secrets(path) == {"open-meteo": {"api_key": SECRET}, "forecast-solar": {"api_key": "other"}}
    assert not list(tmp_path.glob("*.tmp"))  # the atomic write left nothing behind


def test_the_file_is_never_open_to_others_not_even_with_a_permissive_umask(tmp_path):
    previous = os.umask(0)
    try:
        path = tmp_path / "secrets.toml"
        (tmp_path / "secrets.toml.tmp").write_text("old leftover")
        os.chmod(tmp_path / "secrets.toml.tmp", 0o666)
        save_secrets({"open-meteo": {"api_key": SECRET}}, path)
    finally:
        os.umask(previous)
    assert mode(path) == 0o600


def test_empty_values_are_not_stored(tmp_path):
    path = tmp_path / "secrets.toml"
    save_secrets({"open-meteo": {"api_key": ""}, "forecast-solar": {}}, path)
    assert load_secrets(path) == {}
    assert "api_key" not in path.read_text()


def test_no_file_means_no_secrets(tmp_path):
    assert load_secrets(tmp_path / "missing.toml") == {}


def test_a_file_open_to_others_is_corrected_when_read(tmp_path):
    path = tmp_path / "secrets.toml"
    path.write_text(dump_secrets({"open-meteo": {"api_key": SECRET}}))
    os.chmod(path, 0o644)
    assert load_secrets(path)["open-meteo"]["api_key"] == SECRET
    assert mode(path) == 0o600


def test_a_file_open_to_others_that_cannot_be_fixed_is_refused_without_the_secret(tmp_path, monkeypatch):
    path = tmp_path / "secrets.toml"
    path.write_text(dump_secrets({"open-meteo": {"api_key": SECRET}}))
    os.chmod(path, 0o644)
    monkeypatch.setattr(os, "chmod", lambda *a, **k: (_ for _ in ()).throw(PermissionError(1, "Operation not permitted")))
    with pytest.raises(SecretsError, match="readable by other users") as caught:
        load_secrets(path)
    assert SECRET not in str(caught.value)


def test_where_there_are_no_file_modes_nothing_is_changed(tmp_path, monkeypatch):
    monkeypatch.setattr("car_charge_planner.forecast.secret_store._has_file_modes", lambda: False)
    path = tmp_path / "secrets.toml"
    path.write_text(dump_secrets({"open-meteo": {"api_key": SECRET}}))
    os.chmod(path, 0o644)
    assert load_secrets(path)  # no complaint, no chmod
    assert mode(path) == 0o644


def test_a_broken_file_is_reported_without_quoting_it(tmp_path):
    path = tmp_path / "secrets.toml"
    path.write_text(f'[open-meteo]\napi_key = "{SECRET}" oops\n')
    with pytest.raises(SecretsError, match="not valid TOML") as caught:
        load_secrets(path)
    assert SECRET not in str(caught.value)


@pytest.mark.parametrize("text", ["api_key = 5\n", "[open-meteo]\napi_key = 5\n"])
def test_a_file_of_the_wrong_shape_is_refused(tmp_path, text):
    path = tmp_path / "secrets.toml"
    path.write_text(text)
    with pytest.raises(SecretsError, match="texts only"):
        load_secrets(path)


def test_ids_and_names_with_odd_characters_are_not_written():
    with pytest.raises(SecretsError):
        dump_secrets({"open meteo": {"api_key": "x"}})
    with pytest.raises(SecretsError):
        dump_secrets({"open-meteo": {"api key\n[evil]": "x"}})


def test_a_value_with_quotes_and_newlines_survives(tmp_path):
    awkward = 'a"b\\c\nd'
    path = tmp_path / "secrets.toml"
    save_secrets({"open-meteo": {"api_key": awkward}}, path)
    assert load_secrets(path)["open-meteo"]["api_key"] == awkward


def test_a_failed_write_leaves_the_old_file_and_no_temporary_one(tmp_path, monkeypatch):
    path = tmp_path / "secrets.toml"
    save_secrets({"open-meteo": {"api_key": "old"}}, path)
    monkeypatch.setattr(os, "replace", lambda *a, **k: (_ for _ in ()).throw(OSError(28, "No space left on device")))
    with pytest.raises(SecretsError) as caught:
        save_secrets({"open-meteo": {"api_key": SECRET}}, path)
    assert SECRET not in str(caught.value)
    monkeypatch.undo()
    assert load_secrets(path)["open-meteo"]["api_key"] == "old" and not list(tmp_path.glob("*.tmp"))


def test_secrets_never_reach_the_log(tmp_path, caplog):
    path = tmp_path / "secrets.toml"
    with caplog.at_level(logging.DEBUG):
        save_secrets({"open-meteo": {"api_key": SECRET}}, path)
        load_secrets(path)
    assert SECRET not in caplog.text


# ---- which settings go where -----------------------------------------------------------------------------------


class Needy:
    id = "needy"
    name = "Needy"
    description = terms_url = attribution = sends = "x"
    min_refresh, horizon, max_useful_age = timedelta(hours=1), timedelta(days=1), timedelta(hours=2)
    settings = (
        SettingSpec("account", "Account", "Your account name.", optional=False),
        SettingSpec("token", "Token", "Your token.", optional=False, secret=True),
        SettingSpec("note", "Note", "Anything.", optional=True),
    )
    search_places = None

    def fetch(self, system, settings, now):
        return []


def custom_registry():
    reg = ProviderRegistry()
    reg.register(Needy())
    reg.register(registry.get("open-meteo").__class__())
    return reg


def test_a_provider_gets_its_declared_settings_with_secrets_from_the_secrets_file(cfg):
    chosen = with_forecast(cfg, settings={"needy": {"account": "me", "note": "", "stray": "x"}})
    got = provider_settings(chosen, {"needy": {"token": "t", "account": "ignored"}}, Needy())
    assert got == {"account": "me", "token": "t"}  # a secret is read only from the secrets file; undeclared keys are dropped


def test_a_secret_setting_found_in_the_config_file_is_not_used(cfg):
    chosen = with_forecast(cfg, settings={"needy": {"account": "me", "token": "from-the-config-file"}})
    assert provider_settings(chosen, {}, Needy()) == {"account": "me"}
    assert any("Token is required" in p for p in check_selection(with_forecast(chosen, primary="needy"), {}, custom_registry()))


def test_settings_are_split_by_where_they_are_kept():
    plain, secret = split_settings(Needy(), {"account": "me", "token": "t", "note": ""})
    assert plain == {"account": "me"} and secret == {"token": "t"}


def test_a_usable_selection_has_no_problems(cfg):
    chosen = with_forecast(cfg, primary="needy", fallback="open-meteo", settings={"needy": {"account": "me"}})
    assert check_selection(chosen, {"needy": {"token": "t"}}, custom_registry()) == []
    assert check_selection(cfg, {}, custom_registry()) == []  # the default: open-meteo, nothing required


def test_missing_required_settings_are_named(cfg):
    chosen = with_forecast(cfg, primary="needy")
    problems = check_selection(chosen, {}, custom_registry())
    assert any("Account is required" in p for p in problems) and any("Token is required" in p for p in problems)
    assert check_selection(chosen, {"needy": {"token": "t"}}, custom_registry()) == ["Needy: Account is required"]


def test_unknown_providers_and_unknown_settings_are_named(cfg):
    chosen = with_forecast(cfg, primary="solcast", fallback="open-meteo", settings={"open-meteo": {"colour": "red"}})
    problems = check_selection(chosen, {}, custom_registry())
    assert any("primary forecast provider 'solcast' is not known" in p for p in problems)
    assert any("unknown setting 'colour'" in p for p in problems)


def test_settings_of_a_provider_that_is_not_selected_are_left_alone(cfg):
    assert check_selection(with_forecast(cfg, settings={"needy": {"anything": "x"}}), {}, custom_registry()) == []


def test_provider_ids_follow_the_same_rule_as_the_config():
    class Dotted(Needy):
        id = "a.b"

    with pytest.raises(ValueError):
        ProviderRegistry().register(Dotted())
