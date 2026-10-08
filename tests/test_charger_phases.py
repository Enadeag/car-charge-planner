"""Charger phases: one or three (ticket S1). Three phases deliver three times the power at the same current."""

import os

os.environ.setdefault("QT_QPA_PLATFORM", "offscreen")

from dataclasses import replace
from datetime import date, timedelta

import pytest
from PySide6.QtWidgets import QApplication, QLabel

from car_charge_planner.notifications import message
from car_charge_planner.settings.window import SettingsWindow
from car_charge_planner.view_model import ForecastSource, build
from car_charge_planner.planner.advice import grid_advice
from car_charge_planner.planner.config_io import dump_config, write_config
from car_charge_planner.planner.model import Charger, ConfigError, ForecastStep, load_config, validate
from car_charge_planner.planner.projection import plan
from car_charge_planner.planner.surplus import amps_for, charge_power_kw, usable_kw
from car_charge_planner.storage.state import BatteryReading

from conftest import TZ, at, forecast_from

DAY = date(2026, 7, 3)
NOW = at(DAY, 7)
STRONG = 2500  # W/m2 at the peak: about 10 kW from the example system, enough for three phases (an artificial sky)


def with_phases(cfg, phases, **charger):
    return replace(cfg, charger=replace(cfg.charger, phases=phases, **charger))


@pytest.fixture
def qapp():
    return QApplication.instance() or QApplication([])


# ---- the setting -------------------------------------------------------------------------------------------


def test_one_phase_is_the_default_and_an_old_config_means_one_phase(tmp_path):
    assert Charger().phases == 1
    path = tmp_path / "config.toml"
    path.write_text("[charger]\nmin_amps = 6\nmax_amps = 16\n")  # the file of an older version
    assert load_config(path).charger.phases == 1


def test_the_phases_are_written_and_read_back(tmp_path, cfg):
    path = tmp_path / "config.toml"
    write_config(with_phases(cfg, 3), path)
    assert "phases = 3" in path.read_text() and load_config(path).charger.phases == 3
    assert "phases = 1" in dump_config(cfg)


@pytest.mark.parametrize("phases", [0, 2, 4, -1, True, 1.5])
def test_only_one_or_three_phases_are_accepted(cfg, phases):
    with pytest.raises(ConfigError, match="phases"):
        validate(with_phases(cfg, phases))


# ---- the power ---------------------------------------------------------------------------------------------


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


def test_the_minimum_current_is_a_higher_power_on_three_phases(cfg):
    assert charge_power_kw(cfg.charger.min_amps, with_phases(cfg, 1)) == pytest.approx(1.38)
    assert charge_power_kw(cfg.charger.min_amps, with_phases(cfg, 3)) == pytest.approx(4.14)


# ---- the plan ----------------------------------------------------------------------------------------------


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


# ---- what the texts say ------------------------------------------------------------------------------------


def view_for(cfg, peak=STRONG):
    forecast = forecast_from([(DAY, peak), (DAY + timedelta(days=1), peak)])
    plans = plan(forecast, cfg, 45, NOW)
    return build(plans, NOW, cfg, BatteryReading(45, NOW), timedelta(minutes=5), False, None, ForecastSource("Credit"))


def test_texts_say_per_phase_only_with_three_phases(cfg):
    one, three = view_for(with_phases(cfg, 1)), view_for(with_phases(cfg, 3))
    assert "per phase" not in (one.detail + one.tooltip + one.headline) and " A)" in one.detail
    assert "A per phase)" in three.detail and "A per phase" in three.tooltip
    assert all("A per phase)" in row.power for row in three.outlook)


def test_the_grid_advice_text_says_per_phase_with_three_phases(cfg):
    forecast = forecast_from([(DAY + timedelta(days=i), 120) for i in range(3)])
    chosen = with_phases(cfg, 3)
    plans = plan(forecast, chosen, 35, NOW)
    vm = build(plans, NOW, chosen, BatteryReading(35, NOW), None, False, grid_advice(plans, forecast, chosen, None, NOW))
    assert "A per phase)" in vm.advice.detail


def test_the_notification_says_the_current_is_per_phase_with_three_phases(cfg):
    for phases, wanted in ((1, "Set the current to {a} A ("), (3, "Set the current to {a} A per phase (")):
        chosen = with_phases(cfg, phases)
        window = day_windows(chosen, STRONG)[0]
        title, body = message("solar", window, chosen, TZ)
        assert wanted.format(a=window.amps) in body


# ---- Settings ----------------------------------------------------------------------------------------------


def charger_tab(window):
    return next(t for t in window.field_tabs if t.key == "charger")


def test_the_charger_tab_asks_for_the_phases_first(tmp_path, qapp, cfg):
    window = SettingsWindow(cfg, tmp_path / "config.toml", lambda c: None)
    tab = charger_tab(window)
    assert tab.specs[0].key == "charger.phases"
    box = tab.inputs["charger.phases"]
    assert [box.itemText(i) for i in range(box.count())] == ["One phase", "Three phases"] and box.currentData() == 1
    assert any("per phase" in label.text() for label in tab.findChildren(QLabel))


def test_choosing_three_phases_saves_the_number_three(tmp_path, qapp, cfg):
    file = tmp_path / "config.toml"
    window = SettingsWindow(cfg, file, lambda c: None)
    box = charger_tab(window).inputs["charger.phases"]
    assert not window.is_dirty()
    box.setCurrentIndex(box.findData(3))
    assert window.is_dirty() and window.cancel_button.text() == "Cancel"
    assert window.save()
    assert load_config(file).charger.phases == 3 and isinstance(load_config(file).charger.phases, int)
    assert not window.is_dirty()


def test_reset_tab_goes_back_to_one_phase(tmp_path, qapp, cfg):
    window = SettingsWindow(with_phases(cfg, 3), tmp_path / "config.toml", lambda c: None)
    window.tabs.setCurrentWidget(charger_tab(window))
    assert charger_tab(window).inputs["charger.phases"].currentData() == 3
    window.reset_tab()
    assert charger_tab(window).inputs["charger.phases"].currentData() == 1 and window.is_dirty()
