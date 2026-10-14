"""What the QML dashboard reads and calls: the engine's updates as properties and a model of days, its actions as slots."""

from __future__ import annotations

import math
from typing import Any

from PySide6.QtCore import Property, QAbstractListModel, QByteArray, QModelIndex, QObject, QPersistentModelIndex, QPointF, Qt, Signal, Slot

from ..day_view import DayView, Span
from ..engine import Engine, ViewUpdate
from ..i18n import _, number

Index = QModelIndex | QPersistentModelIndex


def _span(span: Span) -> dict[str, Any]:
    return {"start": span.start_h, "end": span.end_h, "text": span.text}


def day_data(day: DayView) -> dict[str, Any]:
    """One day as QML reads it: names in camelCase, spans as {start, end, text} in hours since midnight."""
    scale = max(1, math.ceil(max((kw for _hour, kw in day.solar), default=0.0)))  # the chart's top, in whole kW
    return {
        "date": day.date.isoformat(),
        "tab": day.tab,
        "title": day.title,
        "kind": day.kind,
        "heading": day.heading,
        "hero": day.hero,
        "facts": list(day.facts),
        "note": day.note,
        "hours": day.hours,
        "solar": [QPointF(hour, kw) for hour, kw in day.solar],
        "scaleKw": scale,
        "scaleText": _("{power} kW").format(power=number(scale)),
        "nowHour": -1.0 if day.now_h is None else day.now_h,  # QML has no None for a number
        "charges": [_span(span) for span in day.charges],
        "grid": _span(day.grid) if day.grid else None,
        "away": [{**_span(away.span), "origin": away.origin} for away in day.away],
        "batteryBefore": day.battery_before_pct,
        "batteryAfter": day.battery_after_pct,
        "minimum": day.minimum_pct,
        "target": day.target_pct,
        "batteryText": day.battery_text,
        "minimumText": _("min {pct}%").format(pct=number(day.minimum_pct)),
        "targetText": _("target {pct}%").format(pct=number(day.target_pct)),
        "batterySummary": _("Battery {before}% now, {after}% after charging; minimum {minimum}%, target {target}%.").format(
            before=number(day.battery_before_pct), after=number(day.battery_after_pct), minimum=number(day.minimum_pct), target=number(day.target_pct)
        ),
        "summary": day.summary,
    }


class DaysModel(QAbstractListModel):
    """The planned days, one row each: `tab` and `kind` for the tab bar, and `day` with everything else."""

    TAB = Qt.ItemDataRole.UserRole + 1
    KIND = Qt.ItemDataRole.UserRole + 2
    DAY = Qt.ItemDataRole.UserRole + 3

    def __init__(self, parent: QObject | None = None) -> None:
        super().__init__(parent)
        self._days: tuple[DayView, ...] = ()

    def rowCount(self, parent: Index = QModelIndex()) -> int:  # noqa: B008 - Qt's own signature
        return 0 if parent.isValid() else len(self._days)

    def roleNames(self) -> dict[int, QByteArray]:
        return {self.TAB: QByteArray(b"tab"), self.KIND: QByteArray(b"kind"), self.DAY: QByteArray(b"day")}

    def data(self, index: Index, role: int = Qt.ItemDataRole.DisplayRole) -> Any:
        if not index.isValid() or not 0 <= index.row() < len(self._days):
            return None
        day = self._days[index.row()]
        if role in (self.TAB, Qt.ItemDataRole.DisplayRole):
            return day.tab
        if role == self.KIND:
            return day.kind
        if role == self.DAY:
            return day_data(day)
        return None

    def set_days(self, days: tuple[DayView, ...]) -> None:
        """The same days again (the minute's update) change the rows in place, so the tab bar keeps its delegates,
        its selection and the keyboard focus; other days replace them all."""
        if [d.date for d in days] == [d.date for d in self._days]:
            self._days = days
            if days:
                self.dataChanged.emit(self.index(0), self.index(len(days) - 1), [self.TAB, self.KIND, self.DAY])
            return
        self.beginResetModel()
        self._days = days
        self.endResetModel()


class DashboardBridge(QObject):
    """The dashboard's one object: `show` takes the engine's update, QML reads the properties and calls the slots."""

    changed = Signal()
    settingsRequested = Signal()  # the desktop app opens Settings: a window, not the engine's business
    awayRequested = Signal()

    def __init__(self, engine: Engine, parent: QObject | None = None) -> None:
        super().__init__(parent)
        self._engine = engine
        self._update: ViewUpdate | None = None
        self._days = DaysModel(self)
        self._texts = {
            "title": _("Car charge planner"),
            "days": _("Days"),
            "gridAdvice": _("Grid advice"),
            "expectedSun": _("Expected sun"),
            "battery": _("Car battery"),
            "now": _("now"),
            "away": _("Away…"),
            "settings": _("Settings"),
            "refresh": _("Refresh"),
            "refreshHint": _("Fetch a new forecast and plan again"),
        }

    @Slot(object)
    def show(self, update: ViewUpdate) -> None:
        self._update = update
        self._days.set_days(update.days)
        self.changed.emit()

    @Slot()
    def refresh(self) -> None:
        self._engine.refresh(force=True)

    @Slot()
    def requestSettings(self) -> None:
        self.settingsRequested.emit()

    @Slot()
    def requestAway(self) -> None:
        self.awayRequested.emit()

    def _vm_text(self, name: str) -> str:
        return getattr(self._update.vm, name) if self._update else ""

    def _advice_text(self, name: str) -> str:
        advice = self._update.vm.advice if self._update else None
        return getattr(advice, name) if advice else ""

    @Property(QObject, constant=True)
    def days(self) -> DaysModel:
        return self._days

    @Property(dict, constant=True)
    def texts(self) -> dict[str, str]:
        """The fixed texts of the dashboard, translated: QML files hold no text of their own."""
        return self._texts

    @Property(str, notify=changed)
    def state(self) -> str:
        """The view model's state ("upcoming", "grid", "needs-setup" and so on); empty before the first update."""
        return self._vm_text("state")

    @Property(str, notify=changed)
    def headline(self) -> str:
        return self._vm_text("headline")

    @Property(str, notify=changed)
    def detail(self) -> str:
        return self._vm_text("detail")

    @Property(bool, notify=changed)
    def hasAdvice(self) -> bool:
        return bool(self._update and self._update.vm.advice)

    @Property(str, notify=changed)
    def adviceTitle(self) -> str:
        return self._advice_text("title")

    @Property(str, notify=changed)
    def adviceDetail(self) -> str:
        return self._advice_text("detail")

    @Property(str, notify=changed)
    def adviceReason(self) -> str:
        return self._advice_text("reason")

    @Property(str, notify=changed)
    def footer(self) -> str:
        return self._vm_text("footer")

    @Property(str, notify=changed)
    def footerTooltip(self) -> str:
        return self._vm_text("footer_tooltip")
