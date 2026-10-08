"""The forecast provider contract and the planner's use of power steps (ticket P1)."""

import json
import math
from dataclasses import replace
from datetime import datetime, timedelta, timezone

import pytest

from car_charge_planner.forecast.provider import (
    BadResponse,
    ForecastProvider,
    NotConfigured,
    ProviderError,
    RateLimited,
    SettingSpec,
    SystemDescription,
    Unavailable,
    check_steps,
    require_valid_steps,
)
from car_charge_planner.forecast.registry import ProviderRegistry
from car_charge_planner.planner.cli import demo_forecast, load_forecast
from car_charge_planner.planner.model import ForecastStep, Panels
from car_charge_planner.planner.projection import plan
from car_charge_planner.planner.surplus import usable_kw

from conftest import TZ, at, forecast_from
from datetime import date

DAY = date(2026, 7, 3)
UTC = timezone.utc


def step(hour, power=1.0, length=1):
    start = datetime(2026, 7, 3, hour, tzinfo=UTC)
    return ForecastStep(start, start + timedelta(hours=length), power)


# ---- what a step must be ----------------------------------------------------------------------------------


def test_good_steps_pass():
    assert check_steps([step(8), step(9, 0.0), step(10, 2.5)]) == []
    assert check_steps([]) == []


def test_steps_must_carry_a_time_zone():
    naive = ForecastStep(datetime(2026, 7, 3, 8), datetime(2026, 7, 3, 9), 1.0)
    assert any("time zone" in p for p in check_steps([naive]))


def test_a_step_must_end_after_it_starts():
    flat = ForecastStep(step(8).start, step(8).start, 1.0)
    backwards = ForecastStep(step(9).start, step(8).start, 1.0)
    assert check_steps([flat]) and check_steps([backwards])


def test_steps_must_be_in_order_without_overlap():
    assert check_steps([step(8), step(9)]) == []  # touching is fine
    assert any("overlaps" in p for p in check_steps([step(8, length=2), step(9)]))
    assert any("overlaps" in p for p in check_steps([step(9), step(8)]))


@pytest.mark.parametrize("power", [-0.1, math.nan, math.inf, -math.inf])
def test_power_must_be_finite_and_not_negative(power):
    assert check_steps([step(8, power)])


def test_fifteen_thirty_and_sixty_minute_steps_are_all_fine():
    start = datetime(2026, 7, 3, 8, tzinfo=UTC)
    steps = [ForecastStep(start + timedelta(minutes=m), start + timedelta(minutes=m + 30), 1.0) for m in (0, 30, 60)]
    assert check_steps(steps) == []


def test_require_valid_steps_raises_bad_response_with_the_reason():
    assert require_valid_steps([step(8)]) == [step(8)]
    with pytest.raises(BadResponse, match="negative"):
        require_valid_steps([step(8, -1)])


# ---- the other types --------------------------------------------------------------------------------------


def test_the_errors_are_provider_errors_the_service_can_tell_apart():
    for kind in (Unavailable, RateLimited, BadResponse, NotConfigured):
        assert issubclass(kind, ProviderError)
    assert RateLimited().retry_after is None
    assert RateLimited("busy", timedelta(minutes=5)).retry_after == timedelta(minutes=5)
    assert str(RateLimited("busy")) == "busy"


def test_a_system_description_is_built_from_the_config(cfg):
    system = SystemDescription.from_config(cfg)
    assert (system.latitude, system.longitude, system.timezone) == (52.37, 4.90, "Europe/Amsterdam")
    assert (system.kwp, system.tilt_deg, system.azimuth_deg, system.system_losses) == (4.8, 35, -45, 0.14)
    with pytest.raises(Exception):
        system.kwp = 1  # frozen


def test_a_setting_is_optional_and_plain_by_default():
    spec = SettingSpec("api_key", "API key", "Only for paid plans.", secret=True)
    assert spec.optional and spec.secret


# ---- the registry -----------------------------------------------------------------------------------------


class Tiny:
    name = "Tiny"
    description = ""
    terms_url = ""
    attribution = ""
    sends = ""
    min_refresh = timedelta(hours=1)
    horizon = timedelta(days=2)
    max_useful_age = timedelta(hours=6)
    settings = ()
    search_places = None

    def __init__(self, id="tiny"):
        self.id = id

    def fetch(self, system, settings, now):
        return [step(8)]


def test_providers_are_registered_and_found_by_id():
    registry = ProviderRegistry()
    provider: ForecastProvider = Tiny()
    registry.register(provider)
    assert registry.get("tiny") is provider and "tiny" in registry and registry.ids() == ["tiny"]
    registry.unregister("tiny")
    assert "tiny" not in registry and registry.all() == []


def test_a_second_provider_with_the_same_id_is_refused():
    registry = ProviderRegistry()
    registry.register(Tiny())
    with pytest.raises(ValueError, match="already"):
        registry.register(Tiny())


@pytest.mark.parametrize("bad", ["", "Open Meteo", "UPPER", " x"])
def test_ids_are_short_lower_case_words(bad):
    with pytest.raises(ValueError):
        ProviderRegistry().register(Tiny(bad))


def test_an_unknown_id_says_so():
    with pytest.raises(KeyError, match="unknown forecast provider"):
        ProviderRegistry().get("nope")


# ---- the planner takes power as it is ---------------------------------------------------------------------


def test_the_planner_no_longer_applies_panel_size_or_losses(cfg):
    """The same power steps give the same plan for any panel settings: the provider did that work already."""
    steps = forecast_from([(DAY, 900)])
    bigger = replace(cfg, panels=Panels(kwp=20, system_losses=0.0))
    assert plan(steps, cfg, 40, at(DAY, 0)) == plan(steps, bigger, 40, at(DAY, 0))


def test_usable_power_is_power_minus_baseload_times_margin(cfg):
    s = step(12, power=3.0)
    assert usable_kw(s, cfg) == pytest.approx((3.0 - cfg.house.baseload_kw) * cfg.planning.margin)
    assert usable_kw(step(12, power=0.2), cfg) == 0.0  # below the baseload: nothing left over


def test_the_test_helper_gives_the_power_of_the_example_system():
    steps = forecast_from([(DAY, 1000)])
    peak = max(s.power_kw for s in steps)
    assert peak == pytest.approx(4.8 * 1.0 * 0.86, rel=0.01)  # about 4.1 kW at a sine peak of 1000 W/m2
    assert check_steps(steps) == []


# ---- the command line -------------------------------------------------------------------------------------


def test_the_demo_forecast_is_power_for_the_configured_panels(cfg):
    small = demo_forecast("summer", DAY, TZ, cfg)
    big = demo_forecast("summer", DAY, TZ, replace(cfg, panels=Panels(kwp=9.6)))
    assert check_steps(small) == []
    assert max(s.power_kw for s in big) == pytest.approx(2 * max(s.power_kw for s in small))


def test_a_forecast_file_gives_power_in_kw(tmp_path):
    path = tmp_path / "forecast.json"
    path.write_text(json.dumps([{"start": "2026-07-03T12:00:00+02:00", "end": "2026-07-03T13:00:00+02:00", "power_kw": 3.5}]))
    assert load_forecast(path)[0].power_kw == 3.5


def test_an_old_forecast_file_is_refused_with_a_clear_message(tmp_path):
    path = tmp_path / "old.json"
    path.write_text(json.dumps([{"start": "2026-07-03T12:00:00+02:00", "end": "2026-07-03T13:00:00+02:00", "gti": 800}]))
    with pytest.raises(SystemExit, match="old format"):
        load_forecast(path)
