"""Loads the QML dashboard window for a bridge."""

from __future__ import annotations

from pathlib import Path

from PySide6.QtCore import QObject
from PySide6.QtQml import QQmlApplicationEngine
from PySide6.QtQuick import QQuickWindow

from .bridge import DashboardBridge

IMPORT_PATH = Path(__file__).resolve().parent  # holds the QML module CarChargePlanner/
MODULE = "CarChargePlanner"


class QmlError(RuntimeError):
    """A QML file did not load; the message lists Qt's errors."""


def qml_engine(parent: QObject | None = None) -> QQmlApplicationEngine:
    engine = QQmlApplicationEngine(parent)
    engine.addImportPath(str(IMPORT_PATH))
    return engine


def set_dark(engine: QQmlApplicationEngine, dark: bool) -> None:
    """Fix the theme instead of following the system: for the preview, screenshots and tests (the offscreen platform has
    no colour scheme, and a QStyleHints.setColorScheme request is ignored there)."""
    theme = engine.singletonInstance(MODULE, "Theme")
    if theme is None:
        raise QmlError("the Theme singleton did not load")
    theme.setProperty("dark", dark)


def load_dashboard(bridge: DashboardBridge, engine: QQmlApplicationEngine) -> QQuickWindow:
    errors: list[str] = []
    engine.warnings.connect(lambda warnings: errors.extend(w.toString() for w in warnings))
    engine.setInitialProperties({"bridge": bridge})
    engine.loadFromModule(MODULE, "DashboardWindow")
    roots = engine.rootObjects()
    if not roots or not isinstance(roots[0], QQuickWindow):
        raise QmlError("\n".join(errors) or "DashboardWindow did not load")
    return roots[0]
