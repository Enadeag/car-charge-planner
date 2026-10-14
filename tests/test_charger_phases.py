"""Charger phases: one or three . Three phases deliver three times the power at the same current."""

import os

os.environ.setdefault("QT_QPA_PLATFORM", "offscreen")

from dataclasses import replace
from datetime import date, timedelta

import pytest

from car_charge_planner.planner.advice import grid_advice
from car_charge_planner.planner.config_io import dump_config, write_config
from car_charge_planner.planner.model import Charger, ConfigError, ForecastStep, load_config, validate
from car_charge_planner.planner.projection import plan
from car_charge_planner.planner.surplus import amps_for, charge_power_kw, usable_kw
from car_charge_planner.settings.window import SettingsWindow
from conftest import at, engine_for, forecast_from

DAY = date(2026, 7, 3)
NOW = at(DAY, 7)
STRONG = 2500  # W/m2 at the peak: about 10 kW from the example system, enough for three phases (an artificial sky)


def with_phases(cfg, phases, **charger):
    return replace(cfg, charger=replace(cfg.charger, phases=phases, **charger))


def test_one_phase_is_the_default_and_an_old_config_means_one_phase(tmp_path):
    assert Charger().phases == 1
    path = tmp_path / "config.toml"
    path.write_text("[charger]\nmin_amps = 6\nmax_amps = 16\n", encoding="utf-8")  # no phases key
    assert load_config(path).charger.phases == 1


def test_the_phases_are_written_and_read_back(tmp_path, cfg):
    path = tmp_path / "config.toml"
    write_config(with_phases(cfg, 3), path)
    assert "phases = 3" in path.read_text(encoding="utf-8") and load_config(path).charger.phases == 3
    assert "phases = 1" in dump_config(cfg)


@pytest.mark.parametrize("phases", [0, 2, 4, -1, True, 1.5])
def test_only_one_or_three_phases_are_accepted(cfg, phases):
    with pytest.raises(ConfigError, match="phases"):
        validate(with_phases(cfg, phases))


@pytest.mark.parametrize(
    "phases, amps, expected_kw",
    [(1, 6, 1.38), (3, 6, 4.14), (1, 16, 3.68), (3, 16, 11.04), (1, 32, 7.36), (3, 32, 22.08)],
)
def test_power_is_phases_times_current_times_voltage(cfg, phases, amps, expected_kw):
    assert charge_power_kw(amps, with_phases(cfg, phases)) == pytest.approx(expected_kw)


@pytest.mark.parametrize("surplus_kw", [0.5, 1.38, 2.0, 4.13, 4.14, 5.0, 8.0, 11.0, 30.0])
@pytest.mark.parametrize("phases", [1, 3])
def test_amps_for_is_the_largest_whole_current_that_fits(cfg, surplus_kw, phases):
    chosen = with_phases(cfg, phases)
    amps = amps_for(surplus_kw, chosen)
    assert amps == min(chosen.charger.max_amps, int(surplus_kw * 1000 / (230 * phases)))
    if 0 < amps < chosen.charger.max_amps:
        assert charge_power_kw(amps, chosen) <= surplus_kw + 1e-9 < charge_power_kw(amps + 1, chosen)


def test_the_same_surplus_gives_a_third_of_the_current_on_three_phases(cfg):
    assert amps_for(6.9, with_phases(cfg, 1)) == 30 and amps_for(6.9, with_phases(cfg, 3)) == 10


def day_windows(cfg, peak, soc=40):
    return plan(forecast_from([(DAY, peak)]), cfg, soc, at(DAY, 0))[0].windows


def test_a_surplus_that_one_phase_can_use_is_too_small_for_three(cfg):
    """About 3 kW of surplus: 13 A on one phase, but under the 4.1 kW that 6 A on three phases already takes."""
    assert day_windows(with_phases(cfg, 1), 1000)
    assert day_windows(with_phases(cfg, 3), 1000) == ()


def test_a_three_phase_plan_never_goes_below_the_three_phase_minimum(cfg):
    chosen = with_phases(cfg, 3)
    windows = day_windows(chosen, STRONG)
    assert windows
    minimum = charge_power_kw(chosen.charger.min_amps, chosen)
    for w in windows:
        assert w.power_kw >= minimum - 1e-9
        assert w.power_kw == pytest.approx(charge_power_kw(w.amps, chosen))
        assert chosen.charger.min_amps <= w.amps <= chosen.charger.max_amps


def test_a_window_never_asks_for_more_than_the_surplus_beyond_the_tolerance(cfg):
    chosen = with_phases(cfg, 3)
    step = ForecastStep(at(DAY, 12), at(DAY, 13), 9.0)
    surplus = usable_kw(step, chosen)
    amps = amps_for(surplus, chosen)
    assert charge_power_kw(amps, chosen) <= surplus + 1e-9


def test_the_grid_advice_uses_three_phase_power(cfg):
    days = [(DAY + timedelta(days=i), 120) for i in range(3)]
    forecast = forecast_from(days)
    for phases in (1, 3):
        chosen = with_phases(cfg, phases)
        plans = plan(forecast, chosen, 35, NOW)
        advice = grid_advice(plans, forecast, chosen, None, NOW)
        assert advice is not None and advice.window is not None
        assert advice.window.power_kw == pytest.approx(charge_power_kw(advice.window.amps, chosen))
        assert advice.window.power_kw >= charge_power_kw(chosen.charger.min_amps, chosen) - 1e-9


def charger_tab(window):
    return next(t for t in window.field_tabs if t.key == "charger")


def test_choosing_three_phases_saves_the_number_three(tmp_path, qapp, cfg):
    file = tmp_path / "config.toml"
    window = SettingsWindow(engine_for(cfg, file))
    box = charger_tab(window).inputs["charger.phases"]
    assert not window.is_dirty()
    box.setCurrentIndex(box.findData(3))
    assert window.is_dirty() and window.cancel_button.text() == "Cancel"
    assert window.save()
    assert load_config(file).charger.phases == 3 and isinstance(load_config(file).charger.phases, int)
    assert not window.is_dirty()


def test_reset_tab_goes_back_to_one_phase(tmp_path, qapp, cfg):
    window = SettingsWindow(engine_for(with_phases(cfg, 3), tmp_path / "config.toml"))
    window.tabs.setCurrentWidget(charger_tab(window))
    assert charger_tab(window).inputs["charger.phases"].currentData() == 3
    window.reset_tab()
    assert charger_tab(window).inputs["charger.phases"].currentData() == 1 and window.is_dirty()
