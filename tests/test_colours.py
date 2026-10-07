import os

os.environ.setdefault("QT_QPA_PLATFORM", "offscreen")

from dataclasses import replace
from pathlib import Path

import pytest
from PySide6.QtGui import QColor, QPalette
from PySide6.QtWidgets import QApplication, QLabel

from app import colours
from app.settings.window import SettingsWindow
from app.popup import Popup
from planner.model import Config, first_run_config

from conftest import TZ


def luminance(hex_colour):
    r, g, b = (int(hex_colour[i : i + 2], 16) / 255 for i in (1, 3, 5))
    f = lambda v: v / 12.92 if v <= 0.03928 else ((v + 0.055) / 1.055) ** 2.4
    return 0.2126 * f(r) + 0.7152 * f(g) + 0.0722 * f(b)


def contrast(a, b):
    hi, lo = sorted((luminance(a), luminance(b)), reverse=True)
    return (hi + 0.05) / (lo + 0.05)


@pytest.fixture
def qapp():
    return QApplication.instance() or QApplication([])


@pytest.fixture
def dark(qapp):
    original = qapp.palette()
    palette = QPalette(original)
    palette.setColor(QPalette.ColorRole.Window, QColor("#31363b"))
    palette.setColor(QPalette.ColorRole.WindowText, QColor("#eff0f1"))
    qapp.setPalette(palette)
    yield
    qapp.setPalette(original)


@pytest.fixture
def light(qapp):
    original = qapp.palette()
    palette = QPalette(original)
    palette.setColor(QPalette.ColorRole.Window, QColor("#eff0f1"))
    palette.setColor(QPalette.ColorRole.WindowText, QColor("#232629"))
    qapp.setPalette(palette)
    yield
    qapp.setPalette(original)


@pytest.mark.parametrize("name", ["ERROR", "WARNING", "SUCCESS", "LINK"])
def test_accent_colours_are_readable_on_typical_backgrounds(name):
    table = getattr(colours, name)
    for background in ("#31363b", "#1e1e1e"):
        assert contrast(table["dark"], background) >= 4.5
    for background in ("#eff0f1", "#ffffff"):
        assert contrast(table["light"], background) >= 4.5


def test_dark_theme_gets_the_light_accent_shades(dark):
    w = QLabel()
    assert colours.is_dark(w)
    assert colours.error(w) == colours.ERROR["dark"] and colours.warning(w) == colours.WARNING["dark"]


def test_light_theme_gets_the_dark_accent_shades(light):
    w = QLabel()
    assert not colours.is_dark(w)
    assert colours.error(w) == colours.ERROR["light"] and colours.warning(w) == colours.WARNING["light"]


def test_secondary_text_uses_the_placeholder_role_not_mid():
    assert "placeholder-text" in colours.MUTED_STYLE and "mid" not in colours.MUTED_STYLE


def test_settings_hints_use_the_muted_role_and_the_typical_value_tag_matches_the_theme(dark, tmp_path):
    w = SettingsWindow(first_run_config(), tmp_path / "c.toml", lambda c: None)
    labels = w.field_tabs[0].findChildren(QLabel)
    hints = [l for l in labels if "font-size: 9pt" in l.styleSheet()]
    assert hints and all("placeholder-text" in l.styleSheet() for l in hints)
    assert any(colours.WARNING["dark"] in l.text() for l in hints)


def test_settings_errors_use_the_theme_error_colour(dark, tmp_path):
    w = SettingsWindow(Config(), tmp_path / "c.toml", lambda c: None)
    w.show_errors(["something is wrong"])
    assert colours.ERROR["dark"] in w.errors.styleSheet()


def test_popup_text_colours_follow_the_theme(dark):
    from datetime import date

    from app.view_model import build
    from planner.projection import plan
    from storage.state import BatteryReading, State

    from conftest import at, forecast_from

    cfg = Config()
    now = at(date(2026, 7, 3), 7)
    from planner.advice import grid_advice

    forecast = forecast_from([(date(2026, 7, 3 + i), 120) for i in range(3)])  # dull days: advice appears
    days = plan(forecast, cfg, 35, now)
    advice = grid_advice(days, forecast, cfg, None, now)
    vm = build(days, now, cfg, BatteryReading(35, None), None, False, advice)
    assert vm.advice is not None
    popup = Popup(TZ)
    popup.update_view(vm, State(), now.date())
    assert colours.ERROR["dark"] in popup.battery.styleSheet()  # never entered: flagged
    assert colours.ERROR["dark"] in popup.battery_status.styleSheet()
    assert colours.WARNING["dark"] in popup.advice_title.styleSheet()
    assert "placeholder-text" in popup.footer.styleSheet()
