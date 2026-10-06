"""The status icon: a bolt on a disc whose colour follows the state. Used by every shell."""

from __future__ import annotations

from PySide6.QtCore import QPointF, Qt
from PySide6.QtGui import QColor, QIcon, QPainter, QPixmap, QPolygonF

COLORS = {"active": "#2e9e5b", "upcoming": "#3b82c4", "none": "#7a7f87", "grid": "#d98c1f", "no-forecast": "#c4553b"}


def make_icon(state: str) -> QIcon:
    pix = QPixmap(64, 64)
    pix.fill(Qt.GlobalColor.transparent)
    p = QPainter(pix)
    p.setRenderHint(QPainter.RenderHint.Antialiasing)
    p.setBrush(QColor(COLORS[state]))
    p.setPen(Qt.PenStyle.NoPen)
    p.drawEllipse(4, 4, 56, 56)
    p.setBrush(QColor("white"))
    bolt = [(36, 10), (18, 36), (30, 36), (26, 54), (46, 26), (34, 26)]
    p.drawPolygon(QPolygonF([QPointF(x, y) for x, y in bolt]))
    p.end()
    return QIcon(pix)
