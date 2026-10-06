import json
import urllib.error
from datetime import datetime, timedelta, timezone
from pathlib import Path

import pytest

from forecast.open_meteo import ForecastUnavailable, get_forecast, parse, request_url
from planner.model import Config

FIXTURE = json.loads((Path(__file__).parent / "fixtures" / "open_meteo_3days.json").read_text())
NOW = datetime(2026, 10, 6, 7, 0, tzinfo=timezone.utc)


def offline(_url):
    raise urllib.error.URLError("no network")


def test_parse_shifts_to_preceding_hour(cfg):
    steps = parse(FIXTURE, cfg)
    assert len(steps) == 72
    assert all(s.end - s.start == timedelta(hours=1) for s in steps)
    # the value stamped 09:00 local (156.6 W/m2) covers 08:00-09:00
    nine = next(s for s in steps if s.end.hour == 9 and s.end.day == 6)
    assert nine.gti_wm2 == 156.6
    assert nine.start.hour == 8
    assert steps[0].end.utcoffset() == timedelta(hours=2)  # CEST, timezone-aware


def test_parse_treats_null_as_zero(cfg):
    raw = {"hourly": {"time": [1791237600], "global_tilted_irradiance": [None]}}
    assert parse(raw, cfg)[0].gti_wm2 == 0.0


def test_parse_raises_on_api_error(cfg):
    with pytest.raises(ValueError):
        parse({"error": True, "reason": "bad"}, cfg)


def test_request_uses_panel_settings(cfg):
    url = request_url(cfg)
    assert "tilt=35" in url and "azimuth=-45" in url and "timeformat=unixtime" in url
    assert "global_tilted_irradiance" in url


def test_fetch_writes_cache_and_second_call_uses_it(cfg, tmp_path):
    cache = tmp_path / "f.json"
    calls = []
    first = get_forecast(cfg, NOW, cache, lambda u: calls.append(u) or FIXTURE)
    assert not first.from_cache and cache.exists()
    second = get_forecast(cfg, NOW + timedelta(minutes=30), cache, lambda u: calls.append(u) or FIXTURE)
    assert second.from_cache and not second.stale
    assert len(calls) == 1  # at most hourly


def test_refetches_after_an_hour(cfg, tmp_path):
    cache = tmp_path / "f.json"
    calls = []
    fetch = lambda u: calls.append(u) or FIXTURE
    get_forecast(cfg, NOW, cache, fetch)
    get_forecast(cfg, NOW + timedelta(minutes=61), cache, fetch)
    assert len(calls) == 2


def test_network_failure_falls_back_to_stale_cache(cfg, tmp_path):
    cache = tmp_path / "f.json"
    get_forecast(cfg, NOW, cache, lambda u: FIXTURE)
    later = NOW + timedelta(hours=5)
    result = get_forecast(cfg, later, cache, offline)
    assert result.from_cache and result.stale
    assert result.age(later) == timedelta(hours=5)
    assert len(result.steps) == 72


def test_no_cache_and_no_network_raises(cfg, tmp_path):
    with pytest.raises(ForecastUnavailable):
        get_forecast(cfg, NOW, tmp_path / "none.json", offline)


def test_corrupt_cache_is_ignored(cfg, tmp_path):
    cache = tmp_path / "f.json"
    cache.write_text("{not json")
    assert get_forecast(cfg, NOW, cache, lambda u: FIXTURE).steps


def test_cache_is_ignored_when_panel_settings_change(cfg, tmp_path):
    from dataclasses import replace

    cache = tmp_path / "f.json"
    get_forecast(cfg, NOW, cache, lambda u: FIXTURE)
    moved = replace(cfg, panels=replace(cfg.panels, tilt_deg=20))
    calls = []
    get_forecast(moved, NOW, cache, lambda u: calls.append(u) or FIXTURE)
    assert len(calls) == 1


def test_force_bypasses_fresh_cache(cfg, tmp_path):
    cache = tmp_path / "f.json"
    calls = []
    fetch = lambda u: calls.append(u) or FIXTURE
    get_forecast(cfg, NOW, cache, fetch)
    get_forecast(cfg, NOW, cache, fetch, force=True)
    assert len(calls) == 2
