import os
from dataclasses import replace
from datetime import time

import pytest

from car_charge_planner.config.io import HEADER, dump_config, write_config
from car_charge_planner.config.model import Config, ConfigError, load_config
from car_charge_planner.planner.model import AwayRule


def round_trip(cfg, tmp_path):
    f = tmp_path / "config.toml"
    write_config(cfg, f)
    return load_config(f)


def test_defaults_round_trip(tmp_path):
    assert round_trip(Config(), tmp_path) == Config()


def test_a_changed_config_round_trips(tmp_path):
    cfg = Config()
    cfg = cfg.with_system(
        panels=(replace(cfg.system.panels[0], kwp=5.2, tilt_deg=28, azimuth_deg=-30.5),),
        car=replace(cfg.system.car, km_per_day=92.5),
        charger=replace(cfg.system.charger, max_amps=25),
        timezone="Europe/Brussels",
        latitude=51.2,
        longitude=4.4,
    ).with_planning(minimum_pct=25, margin=0.9, min_window_minutes=30, notify_lead_minutes=10)
    assert round_trip(cfg, tmp_path) == cfg


def test_floats_keep_full_precision(tmp_path):
    cfg = Config()  # km_per_day is 600 / 7, which has no short decimal form
    assert round_trip(cfg, tmp_path).system.car.km_per_day == cfg.system.car.km_per_day


def test_several_away_rules_round_trip(tmp_path):
    rules = (
        AwayRule(0, time(7, 30), time(17, 45)),
        AwayRule(3, time(6, 0), time(19, 0)),
        AwayRule(6, time(0, 0), time(12, 15)),
    )
    assert round_trip(Config().with_system(away_rules=rules), tmp_path).system.away_rules == rules


def test_no_away_rules_stays_empty_instead_of_returning_the_default(tmp_path):
    loaded = round_trip(Config().with_system(away_rules=()), tmp_path)
    assert loaded.system.away_rules == ()
    assert "recurring = []" in dump_config(Config().with_system(away_rules=()))


def test_file_starts_with_the_header_and_has_only_values(tmp_path):
    text = dump_config(Config())
    assert text.startswith(HEADER)
    body = [line for line in text.splitlines() if line.startswith("#")]
    assert body == HEADER.splitlines()  # no other comments


def test_written_file_has_the_same_tables_the_loader_reads(tmp_path):
    text = dump_config(Config())
    for table in ("[[panels]]", "[house]", "[car]", "[charger]", "[planning]", "[forecast]", "[setup]", "[away]"):
        assert table in text


def test_invalid_config_is_refused_and_nothing_is_written(tmp_path):
    f = tmp_path / "config.toml"
    bad = Config().with_system(charger=replace(Config().system.charger, volts=0))
    with pytest.raises(ConfigError):
        write_config(bad, f)
    assert not f.exists() and list(tmp_path.iterdir()) == []


def test_a_failed_write_keeps_the_old_file_and_removes_the_temp_file(tmp_path, monkeypatch):
    f = tmp_path / "config.toml"
    write_config(Config(), f)
    before = f.read_text(encoding="utf-8")

    def boom(src, dst):
        raise OSError("disk full")

    monkeypatch.setattr(os, "replace", boom)
    changed = Config().with_system(panels=(replace(Config().system.panels[0], kwp=9.9),))
    with pytest.raises(OSError):
        write_config(changed, f)
    assert f.read_text(encoding="utf-8") == before
    assert [p.name for p in tmp_path.iterdir()] == ["config.toml"]


def test_creates_the_folder_when_missing(tmp_path):
    f = tmp_path / "a" / "b" / "config.toml"
    write_config(Config(), f)
    assert load_config(f) == Config()


def test_overwrites_an_existing_file(tmp_path):
    f = tmp_path / "config.toml"
    f.write_text("[panels]\nkwp = 1\n# my own comment\n", encoding="utf-8")
    write_config(Config(), f)
    assert "my own comment" not in f.read_text(encoding="utf-8") and load_config(f) == Config()


def test_values_are_written_as_toml_types(tmp_path):
    text = dump_config(Config().with_system(timezone="Europe/Amsterdam"))  # not the default: that is this computer's own zone
    assert 'timezone = "Europe/Amsterdam"' in text
    assert "min_amps = 6\n" in text  # an int stays an int
    assert "kwp = 5.0\n" in text  # a float stays a float


def test_file_carries_the_schema_version_first(tmp_path):
    from car_charge_planner.config.model import CONFIG_SCHEMA

    lines = [line for line in dump_config(Config()).splitlines() if line and not line.startswith("#")]
    assert lines[0] == f"schema = {CONFIG_SCHEMA}" and CONFIG_SCHEMA == 3
    assert round_trip(Config(), tmp_path) == Config()  # the key is not an unknown key, nor a Config field


def test_a_file_without_a_schema_key_loads_as_version_1(tmp_path):
    f = tmp_path / "config.toml"
    f.write_text("[panels]\nkwp = 4.5\n", encoding="utf-8")
    assert load_config(f).system.panels[0].kwp == 4.5


def test_a_file_from_a_newer_version_gets_the_newer_version_message(tmp_path):
    f = tmp_path / "config.toml"
    f.write_text("schema = 99\n[panels]\nsome_future_key = 1\n", encoding="utf-8")
    with pytest.raises(ConfigError, match="written by a newer version of the app") as caught:
        load_config(f)
    assert "unknown keys" not in str(caught.value)


@pytest.mark.parametrize("bad", ['"two"', "0", "-1", "1.5", "true"])
def test_a_schema_that_is_not_a_positive_whole_number_is_refused(tmp_path, bad):
    f = tmp_path / "config.toml"
    f.write_text(f"schema = {bad}\n", encoding="utf-8")
    with pytest.raises(ConfigError, match="schema"):
        load_config(f)


def test_migrations_run_in_order_before_the_values_are_read(tmp_path, monkeypatch):
    from car_charge_planner.config import model

    seen = []

    def three_to_four(data):  # pretend version 3 called the panel size "peak_kw"
        seen.append(3)
        for table in data["panels"]:
            table["kwp"] = table.pop("peak_kw")
        return data

    def four_to_five(data):
        seen.append(4)
        data["planning"] = {"minimum_pct": 33}
        return data

    monkeypatch.setattr(model, "CONFIG_SCHEMA", 5)
    monkeypatch.setitem(model.CONFIG_MIGRATIONS, 3, three_to_four)
    monkeypatch.setitem(model.CONFIG_MIGRATIONS, 4, four_to_five)
    f = tmp_path / "config.toml"
    f.write_text("[panels]\npeak_kw = 6.5\n", encoding="utf-8")  # no schema key: version 1, which has one [panels] table
    cfg = load_config(f)
    assert seen == [3, 4] and cfg.system.panels[0].kwp == 6.5 and cfg.planning.minimum_pct == 33
    f.write_text("schema = 4\n[[panels]]\nkwp = 3.0\n", encoding="utf-8")  # starts at the right step
    seen.clear()
    assert load_config(f).planning.minimum_pct == 33 and seen == [4]
    f.write_text("schema = 6\n", encoding="utf-8")
    with pytest.raises(ConfigError, match="newer version"):
        load_config(f)


def test_a_missing_migration_is_an_error_not_a_silent_skip(tmp_path, monkeypatch):
    from car_charge_planner.config import model

    monkeypatch.setattr(model, "CONFIG_SCHEMA", 4)
    f = tmp_path / "config.toml"
    f.write_text("schema = 3\n", encoding="utf-8")
    with pytest.raises(ConfigError, match="cannot be upgraded"):
        load_config(f)
