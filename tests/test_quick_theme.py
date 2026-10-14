"""The design tokens in Theme.qml reach WCAG AA contrast in the light and the dark theme."""

import pytest
from PySide6.QtGui import QColor

from car_charge_planner.quick.window import MODULE, qml_engine, set_dark

SURFACES = ("ground", "panel", "raised")
SOURCES = ("sun", "grid", "car", "act")
TEXT = 4.5  # normal-size text
MARK = 3.0  # graphical objects: chart lines, dots, bars, the focus ring


def luminance(colour: QColor) -> float:
    def channel(value: float) -> float:
        return value / 12.92 if value <= 0.04045 else ((value + 0.055) / 1.055) ** 2.4

    return 0.2126 * channel(colour.redF()) + 0.7152 * channel(colour.greenF()) + 0.0722 * channel(colour.blueF())


def contrast(a: QColor, b: QColor) -> float:
    light, dark = sorted((luminance(a), luminance(b)), reverse=True)
    return (light + 0.05) / (dark + 0.05)


def pairs():
    for surface in SURFACES:
        for text in ("fg", "muted", *(f"{source}Text" for source in SOURCES)):
            yield text, surface, TEXT
        for mark in (*SOURCES, "faint", "focus"):
            yield mark, surface, MARK
    for source in SOURCES:
        soft = f"{source}Soft"
        for text in ("fg", "muted", f"{source}Text"):  # a chip or a card on the soft background
            yield text, soft, TEXT


@pytest.fixture(scope="module")
def themes():
    from PySide6.QtWidgets import QApplication

    _app = QApplication.instance() or QApplication([])
    engine = qml_engine()
    theme = engine.singletonInstance(MODULE, "Theme")
    colours = {}
    for name, dark in (("light", False), ("dark", True)):
        set_dark(engine, dark)
        colours[name] = {key: QColor(theme.property(key)) for key in {*SURFACES, *(f for p in pairs() for f in p[:2])}}
    return colours


@pytest.mark.parametrize("theme", ["light", "dark"])
@pytest.mark.parametrize("foreground, background, needed", list(pairs()), ids=lambda v: str(v))
def test_the_token_pair_has_enough_contrast(themes, theme, foreground, background, needed):
    colours = themes[theme]
    assert contrast(colours[foreground], colours[background]) >= needed
