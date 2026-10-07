"""Every page, rendered in the pseudo-language: nothing untranslated, nothing clipped (ticket I3)."""

import os

os.environ.setdefault("QT_QPA_PLATFORM", "offscreen")

from datetime import date, timedelta

import pytest
from PySide6.QtWidgets import QApplication

import pseudo
from app import i18n
from app.popup import PLAN, UPDATE, Popup
from app.settings.window import SettingsWindow
from app.view_model import ForecastSource, build, needs_setup, no_forecast
from planner.advice import grid_advice
from planner.projection import plan
from storage.state import BatteryReading, State

from conftest import TZ, at, forecast_from

DAY = date(2026, 7, 3)
NOW = at(DAY, 7)


def first_run_config_for(cfg):
    from dataclasses import replace

    from planner.model import TYPICAL_KEYS, Setup

    return replace(cfg, setup=Setup(TYPICAL_KEYS))


@pytest.fixture
def qapp():
    return QApplication.instance() or QApplication([])


@pytest.fixture
def pseudo_language(tmp_path):
    pseudo.install(tmp_path)
    yield
    i18n.init(languages=[])


def settle(widget):
    """Show it at the size its content asks for (adjustSize would cap it at two thirds of the small test screen)."""
    widget.show()
    QApplication.processEvents()
    widget.resize(widget.sizeHint().expandedTo(widget.minimumSizeHint()))
    QApplication.processEvents()


OPEN_METEO = ForecastSource("Weather data: Open-Meteo.com (CC BY 4.0)", "Open-Meteo")


def popup_with(cfg, days, soc=45, battery=None, stale=False, kind="plan", source=OPEN_METEO, typical=False):
    if typical:
        cfg = first_run_config_for(cfg)
    forecast = forecast_from(list(days))
    plans = plan(forecast, cfg, soc, NOW)
    advice = grid_advice(plans, forecast, cfg, None, NOW)
    battery = battery or BatteryReading(soc, NOW)
    if kind == "needs-setup":
        vm = needs_setup(battery, NOW)
    elif kind == "no-forecast":
        vm = no_forecast(i18n._("Loading forecast..."), battery, NOW)
    else:
        vm = build(plans, NOW, cfg, battery, timedelta(minutes=5), stale, advice, source)
    popup = Popup(TZ)
    popup.update_view(vm, State(battery), NOW.date(), cfg.away_rules)
    return popup


SITUATIONS = {
    "a window today": dict(days=[(DAY, 900), (DAY + timedelta(days=1), 900), (DAY + timedelta(days=2), 600)]),
    "grid charge needed": dict(days=[(DAY + timedelta(days=i), 120) for i in range(3)], soc=35),
    "an old battery reading": dict(
        days=[(DAY, 900)], battery=BatteryReading(50, NOW - timedelta(days=3))
    ),
    "a stale forecast": dict(days=[(DAY, 900)], stale=True),
    "a fallback forecast": dict(
        days=[(DAY, 900)], source=ForecastSource("Solar forecast: Forecast.Solar", "Forecast.Solar", from_fallback=True, primary_name="Open-Meteo")
    ),
    "a provider that is busy": dict(days=[(DAY, 900)], stale=True, source=ForecastSource("Weather data: Open-Meteo.com (CC BY 4.0)", "Open-Meteo", cooling_down=True)),
    "typical values": dict(days=[(DAY, 900)], typical=True),
    "not set up": dict(days=[(DAY, 900)], kind="needs-setup"),
    "no forecast": dict(days=[(DAY, 900)], kind="no-forecast"),
}


@pytest.mark.parametrize("name", SITUATIONS)
@pytest.mark.parametrize("page", [PLAN, UPDATE])
def test_the_popup_is_fully_translated_and_nothing_is_clipped(cfg, qapp, pseudo_language, name, page):
    popup = popup_with(cfg, **SITUATIONS[name])
    popup.view.tabs.setCurrentIndex(page)
    settle(popup)
    assert pseudo.untranslated(popup) == []
    assert pseudo.clipped(popup) == []
    popup.close()


def test_the_settings_window_is_fully_translated_and_nothing_is_clipped(cfg, qapp, pseudo_language, tmp_path):
    window = SettingsWindow(cfg, tmp_path / "config.toml", lambda c: None)
    settle(window)
    problems = []
    for index in range(window.tabs.count()):
        window.tabs.setCurrentIndex(index)
        settle(window)
        title = window.tabs.tabText(index)
        problems += [f"{title}: {p}" for p in pseudo.untranslated(window) + pseudo.clipped(window)]
    window.close()
    assert problems == []


def test_the_helper_notices_an_untranslated_text(qapp, pseudo_language):
    from PySide6.QtWidgets import QLabel

    label = QLabel("Charge now")
    settle(label)
    assert pseudo.untranslated(label) == ["QLabel:: 'Charge now'"]
    label.setText(i18n._("Charge now"))
    assert pseudo.untranslated(label) == []
    label.close()


def test_the_helper_notices_a_clipped_label(qapp):
    from PySide6.QtWidgets import QLabel

    label = QLabel("a text that is far too long for the box it is given")
    label.setFixedWidth(40)
    settle(label)
    assert pseudo.clipped(label)
    label.close()


def test_numbers_times_and_units_need_no_translation():
    for text in ("62%", "09:00-15:00", "1.6 kW (7 A)", "7.9 kWh", "5 min", "99% · 5.8 kWh"):
        assert not pseudo.needs_translation(text), text
    assert pseudo.needs_translation("Charge now")
