"""Text colours that stay readable in both light and dark system themes.

Plain widgets follow the system palette by themselves. Only text we colour on purpose
needs care: a fixed red or orange that works on a light window is faint on a dark one.
"""

from __future__ import annotations

from PySide6.QtGui import QPalette
from PySide6.QtWidgets import QWidget

# All four reach a contrast of at least 5:1 on typical window backgrounds of their theme.
ERROR = {"dark": "#f08a74", "light": "#b3402a"}
WARNING = {"dark": "#f0b34a", "light": "#8f5400"}

# Secondary text (hints, footers). palette(mid) is too close to the background in dark themes.
MUTED_STYLE = "color: palette(placeholder-text);"


def is_dark(widget: QWidget) -> bool:
    return widget.palette().color(QPalette.ColorRole.Window).lightness() < 128


def error(widget: QWidget) -> str:
    return ERROR["dark" if is_dark(widget) else "light"]


def warning(widget: QWidget) -> str:
    return WARNING["dark" if is_dark(widget) else "light"]
