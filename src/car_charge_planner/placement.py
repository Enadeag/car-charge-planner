"""Where the tray popup goes: next to the panel that holds the tray icon, whichever screen edge that panel is on."""

from __future__ import annotations

from PySide6.QtCore import QPoint, QRect, QSize

MARGIN = 8  # logical pixels between the popup and the panel or the screen edge


def panel_edge(anchor: QRect, available: QRect) -> str:
    """The screen edge ("top", "bottom", "left" or "right") of the panel the tray icon sits in.

    A panel that reserves space (the Windows taskbar, the macOS menu bar) lies outside `available`, so an icon outside
    it names the edge. A panel that reserves nothing (an auto-hidden taskbar) leaves the icon inside `available`;
    then the nearest edge wins.
    """
    centre = anchor.center()
    outside = {
        "top": available.top() - centre.y(),
        "bottom": centre.y() - available.bottom(),
        "left": available.left() - centre.x(),
        "right": centre.x() - available.right(),
    }
    if any(depth > 0 for depth in outside.values()):
        return max(outside, key=lambda edge: outside[edge])
    inside = {
        "top": centre.y() - available.top(),
        "bottom": available.bottom() - centre.y(),
        "left": centre.x() - available.left(),
        "right": available.right() - centre.x(),
    }
    return min(inside, key=lambda edge: inside[edge])


def keep_inside(position: QPoint, size: QSize, area: QRect) -> QPoint:
    """Move `position` the least needed to have a rectangle of `size` inside `area`; the top left wins if it cannot fit."""
    x = min(position.x(), area.x() + area.width() - size.width())
    y = min(position.y(), area.y() + area.height() - size.height())
    return QPoint(max(x, area.x()), max(y, area.y()))


def popup_position(anchor: QRect, size: QSize, available: QRect) -> QPoint:
    """The top left of a popup of `size` for a tray icon at `anchor`, inside `available` (the screen minus its panels).

    The popup sits against the panel on the icon's edge and is centred on the icon along it. All in logical pixels:
    Qt reports screen and icon geometry in them on every system, so a 150 % or 200 % scale needs no extra step.
    """
    edge = panel_edge(anchor, available)
    centre = anchor.center()
    wanted = QPoint(centre.x() - size.width() // 2, centre.y() - size.height() // 2)
    if edge == "top":
        wanted.setY(available.top() + MARGIN)
    elif edge == "bottom":
        wanted.setY(available.top() + available.height() - size.height() - MARGIN)
    elif edge == "left":
        wanted.setX(available.left() + MARGIN)
    else:
        wanted.setX(available.left() + available.width() - size.width() - MARGIN)
    return keep_inside(wanted, size, available.adjusted(MARGIN, MARGIN, -MARGIN, -MARGIN))
