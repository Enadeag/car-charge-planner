"""Tray icon, popup and refresh loop (PySide6)."""

from __future__ import annotations

import sys
import threading
from datetime import date, datetime, time, timedelta
from pathlib import Path
from zoneinfo import ZoneInfo

from PySide6.QtCore import QLockFile, QObject, QPointF, Qt, QTime, QTimer, QUrl, Signal
from PySide6.QtGui import QColor, QDesktopServices, QGuiApplication, QIcon, QPainter, QPixmap, QPolygonF
from PySide6.QtWidgets import (
    QApplication,
    QMessageBox,
    QComboBox,
    QDoubleSpinBox,
    QFrame,
    QHBoxLayout,
    QLabel,
    QMenu,
    QPushButton,
    QSystemTrayIcon,
    QTimeEdit,
    QVBoxLayout,
    QWidget,
)

from forecast.open_meteo import ForecastResult, ForecastUnavailable, get_forecast
from planner.advice import grid_advice
from planner.model import AwayPeriod, Config, ConfigError, load_config
from planner.projection import plan
from storage.state import State, add_away, load_state, mark_notified, remove_away, set_battery

from .away_input import describe, make_away
from .config_file import ensure_config
from .notifications import candidate_windows, due, message
from .view_model import ViewModel, build, day_label, no_forecast

TICK_MS = 60_000  # recompute every minute; the forecast itself refreshes at most hourly
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


class Popup(QWidget):
    """Window with the plan and the inputs. Closes with the buttons, Escape, or a click on the tray icon."""

    battery_submitted = Signal(float)
    away_added = Signal(object)  # AwayPeriod
    away_removed = Signal(object)  # AwayPeriod

    def __init__(self, tz: ZoneInfo | None = None) -> None:
        super().__init__(None, Qt.WindowType.Tool | Qt.WindowType.FramelessWindowHint)
        self.tz = tz or ZoneInfo("Europe/Amsterdam")
        self.setMinimumWidth(400)
        layout = QVBoxLayout(self)
        layout.setContentsMargins(16, 14, 16, 12)
        layout.setSpacing(6)

        self.headline = QLabel()
        font = self.headline.font()
        font.setPointSizeF(font.pointSizeF() * 1.4)
        font.setBold(True)
        self.headline.setFont(font)
        self.detail = QLabel()
        self.detail.setWordWrap(True)
        self.projection = QLabel()
        self.advice = QLabel()
        self.advice.setWordWrap(True)
        self.advice.setStyleSheet("color: #d98c1f; font-weight: bold;")

        self.battery = QLabel()
        self.battery.setWordWrap(True)
        self.battery_input = QDoubleSpinBox()
        self.battery_input.setRange(0, 100)
        self.battery_input.setDecimals(0)
        self.battery_input.setSuffix(" %")
        self.battery_input.setValue(50)
        set_button = QPushButton("Set battery")
        set_button.setToolTip("Saves immediately")
        set_button.clicked.connect(lambda: self.battery_submitted.emit(self.battery_input.value()))
        self.battery_input.lineEdit().returnPressed.connect(set_button.click)
        battery_row = QHBoxLayout()
        battery_row.addWidget(self.battery_input)
        battery_row.addWidget(set_button)
        battery_row.addStretch()

        self.outlook_title = QLabel("Outlook")
        self.outlook_title.setStyleSheet("font-weight: bold;")
        self.outlook = QLabel()
        self.outlook.setTextFormat(Qt.TextFormat.PlainText)

        away_title = QLabel("Car away (saved as you add or remove)")
        away_title.setStyleSheet("font-weight: bold;")
        self.away_list = QVBoxLayout()
        self.away_list.setSpacing(2)
        self.away_day = QComboBox()
        self.away_kind = QComboBox()
        self.away_kind.addItems(["All day", "Time range"])
        self.away_from = QTimeEdit(QTime(9, 0))
        self.away_to = QTimeEdit(QTime(17, 0))
        for edit in (self.away_from, self.away_to):
            edit.setDisplayFormat("HH:mm")
            edit.setVisible(False)
        self.away_kind.currentIndexChanged.connect(self._on_kind_changed)
        add_button = QPushButton("Add")
        add_button.setToolTip("Saves immediately")
        add_button.clicked.connect(self._on_add_away)
        add_row = QHBoxLayout()
        for widget in (self.away_day, self.away_kind, self.away_from, self.away_to, add_button):
            add_row.addWidget(widget)
        add_row.addStretch()
        self.away_error = QLabel()
        self.away_error.setStyleSheet("color: #c4553b;")
        self.away_error.setVisible(False)

        self.footer = QLabel()
        self.footer.setWordWrap(True)
        self.footer.setStyleSheet("color: palette(mid); font-size: 9pt;")

        def rule() -> QFrame:
            line = QFrame()
            line.setFrameShape(QFrame.Shape.HLine)
            line.setFrameShadow(QFrame.Shadow.Sunken)
            return line

        close_x = QPushButton("\u00d7")
        close_x.setFlat(True)
        close_x.setToolTip("Close")
        close_x.setFixedSize(28, 28)
        close_x.clicked.connect(self.hide)
        header = QHBoxLayout()
        header.addWidget(self.headline)
        header.addStretch()
        header.addWidget(close_x)
        layout.addLayout(header)
        for widget in (self.detail, self.projection, self.advice):
            layout.addWidget(widget)
        layout.addWidget(rule())
        layout.addWidget(self.battery)
        layout.addLayout(battery_row)
        layout.addWidget(rule())
        layout.addWidget(self.outlook_title)
        layout.addWidget(self.outlook)
        layout.addWidget(rule())
        layout.addWidget(away_title)
        layout.addLayout(self.away_list)
        layout.addLayout(add_row)
        layout.addWidget(self.away_error)
        layout.addWidget(self.footer)
        close_button = QPushButton("Close")
        close_button.clicked.connect(self.hide)
        close_row = QHBoxLayout()
        close_row.addStretch()
        close_row.addWidget(close_button)
        layout.addLayout(close_row)
        self.setObjectName("popup")
        self.setStyleSheet("#popup { border: 1px solid palette(mid); background: palette(window); }")

    def update_view(self, vm: ViewModel, state: State | None = None, today: date | None = None) -> None:
        self.headline.setText(vm.headline)
        self.detail.setText(vm.detail)
        self.projection.setText(vm.projection)
        self.projection.setVisible(bool(vm.projection))
        self.advice.setText(vm.advice)
        self.advice.setVisible(bool(vm.advice))
        self.battery.setText(vm.battery_line)
        self.battery.setStyleSheet("color: #c4553b;" if vm.battery_stale else "")
        self.outlook.setText("\n".join(vm.outlook))
        self.outlook_title.setVisible(bool(vm.outlook))
        self.outlook.setVisible(bool(vm.outlook))
        self.footer.setText(vm.footer)
        if state is not None:
            if not self.battery_input.hasFocus():
                self.battery_input.setValue(round(state.battery.pct))
            self._fill_away(state.away)
        if today is not None:
            self._fill_days(today)
        self.adjustSize()

    def _fill_days(self, today: date) -> None:
        chosen = self.away_day.currentData()
        self.away_day.clear()
        for offset in range(8):
            day = today + timedelta(days=offset)
            self.away_day.addItem(day_label(day, today), day)
        index = self.away_day.findData(chosen)
        self.away_day.setCurrentIndex(max(index, 0))

    def _fill_away(self, periods: tuple[AwayPeriod, ...]) -> None:
        while self.away_list.count():
            item = self.away_list.takeAt(0)
            if item.layout():
                while item.layout().count():
                    child = item.layout().takeAt(0)
                    if child.widget():
                        child.widget().deleteLater()
            if item.widget():
                item.widget().deleteLater()
        if not periods:
            none = QLabel("Home all week apart from the weekly rule in the config.")
            none.setStyleSheet("color: palette(mid);")
            self.away_list.addWidget(none)
        for period in periods:
            row = QHBoxLayout()
            row.addWidget(QLabel(describe(period, self.tz)))
            row.addStretch()
            remove = QPushButton("Remove")
            remove.setFlat(True)
            remove.clicked.connect(lambda _=False, p=period: self.away_removed.emit(p))
            row.addWidget(remove)
            self.away_list.addLayout(row)

    def _on_kind_changed(self) -> None:
        ranged = self.away_kind.currentIndex() == 1
        self.away_from.setVisible(ranged)
        self.away_to.setVisible(ranged)
        self.adjustSize()

    def _on_add_away(self) -> None:
        try:
            period = make_away(
                self.away_day.currentData(),
                self.away_kind.currentIndex() == 0,
                self.away_from.time().toPython(),
                self.away_to.time().toPython(),
                self.tz,
            )
        except ValueError as err:
            self.away_error.setText(str(err))
            self.away_error.setVisible(True)
            return
        self.away_error.setVisible(False)
        self.away_added.emit(period)

    def keyPressEvent(self, event) -> None:
        if event.key() == Qt.Key.Key_Escape:
            self.hide()
        else:
            super().keyPressEvent(event)

    def show_near(self, anchor) -> None:
        screen = QGuiApplication.screenAt(anchor.center()) or QGuiApplication.primaryScreen()
        area = screen.availableGeometry()
        self.adjustSize()
        x = min(max(area.left() + 8, anchor.center().x() - self.width() // 2), area.right() - self.width() - 8)
        top_panel = anchor.isValid() and anchor.center().y() < area.center().y()
        y = area.top() + 8 if top_panel else area.bottom() - self.height() - 8
        self.move(x, y)
        self.show()
        self.activateWindow()


class TrayApp(QObject):
    forecast_loaded = Signal(object)  # ForecastResult | Exception

    def __init__(self, cfg: Config, config_file: Path) -> None:
        super().__init__()
        self.cfg = cfg
        self.config_file = config_file
        self.tz = ZoneInfo(cfg.timezone)
        self.result: ForecastResult | None = None
        self.error: str | None = None
        self.vm: ViewModel | None = None
        self._loading = False

        self.popup = Popup(self.tz)
        self.popup.battery_submitted.connect(self._on_battery)
        self.popup.away_added.connect(self._on_away_added)
        self.popup.away_removed.connect(self._on_away_removed)
        self.tray = QSystemTrayIcon(make_icon("none"))
        menu = QMenu()
        menu.addAction("Refresh forecast", lambda: self.refresh(force=True))
        menu.addAction("Open config file", lambda: QDesktopServices.openUrl(QUrl.fromLocalFile(str(config_file))))
        menu.addSeparator()
        menu.addAction("Quit", QApplication.quit)
        self.menu = menu
        self.tray.setContextMenu(menu)
        self.tray.activated.connect(self._on_activated)
        self.forecast_loaded.connect(self._on_forecast)

        self.timer = QTimer(self)
        self.timer.timeout.connect(self.refresh)
        self.timer.start(TICK_MS)

    def start(self) -> None:
        self.tray.show()
        self.refresh()

    def refresh(self, force: bool = False) -> None:
        """Fetch (cache-aware) off the UI thread; the result comes back as a signal."""
        if self._loading:
            return
        self._loading = True

        def work() -> None:
            try:
                outcome: object = get_forecast(self.cfg, datetime.now(self.tz), force=force)
            except ForecastUnavailable as err:
                outcome = err
            except Exception as err:  # never let a worker crash the tray silently
                outcome = err
            self.forecast_loaded.emit(outcome)

        threading.Thread(target=work, daemon=True).start()

    def _on_forecast(self, outcome: object) -> None:
        self._loading = False
        if isinstance(outcome, ForecastResult):
            self.result, self.error = outcome, None
        else:
            self.error = str(outcome)
        self.rebuild()

    def rebuild(self) -> None:
        now = datetime.now(self.tz)
        state = load_state()
        battery = state.battery
        if self.result is None:
            vm = no_forecast(self.error or "Loading forecast...", battery, now)
        else:
            days = plan(self.result.steps, self.cfg, battery.pct, now, list(state.away), list(state.notified))
            advice = grid_advice(days, self.result.steps, self.cfg, list(state.away), now)
            vm = build(days, now, self.cfg, battery, self.result.age(now), self.result.stale, advice)
            self._announce(candidate_windows(days, advice), state, now)
        self.vm = vm
        self.tray.setIcon(make_icon(vm.state))
        self.tray.setToolTip(vm.tooltip)
        self.popup.update_view(vm, state, now.date())

    def _announce(self, windows, state: State, now: datetime) -> None:
        """Notify once per window, a few minutes ahead (spec 5)."""
        for kind, window in due(
            windows, state.notified, now, self.cfg.notify.lead_minutes, self.result.age(now), self.result.stale
        ):
            title, body = message(kind, window, self.cfg, self.tz)
            self.tray.showMessage(title, body, QSystemTrayIcon.MessageIcon.Information, 20_000)
            mark_notified(window, now)

    def _on_battery(self, pct: float) -> None:
        set_battery(pct, datetime.now(self.tz))
        self.rebuild()

    def _on_away_added(self, period: AwayPeriod) -> None:
        add_away(period, datetime.now(self.tz))
        self.rebuild()

    def _on_away_removed(self, period: AwayPeriod) -> None:
        remove_away(period)
        self.rebuild()

    def _on_activated(self, reason: QSystemTrayIcon.ActivationReason) -> None:
        if reason in (QSystemTrayIcon.ActivationReason.Trigger, QSystemTrayIcon.ActivationReason.MiddleClick):
            if self.popup.isVisible():
                self.popup.hide()
            else:
                self.rebuild()
                self.popup.show_near(self.tray.geometry())


def main(argv: list[str] | None = None) -> int:
    app = QApplication(argv if argv is not None else sys.argv)
    app.setApplicationName("car-charge-planner")
    app.setQuitOnLastWindowClosed(False)

    lock_path = Path.home() / ".cache" / "car-charge-planner.lock"
    lock_path.parent.mkdir(parents=True, exist_ok=True)
    lock = QLockFile(str(lock_path))
    if not lock.tryLock(100):
        if lock.error() == QLockFile.LockError.LockFailedError:
            print("car-charge-planner is already running", file=sys.stderr)
        else:
            print(f"Could not create the lock file {lock_path}", file=sys.stderr)
        return 1

    if not QSystemTrayIcon.isSystemTrayAvailable():
        print("No system tray available on this desktop", file=sys.stderr)
        return 1

    config_file = ensure_config()
    try:
        cfg = load_config(config_file)
    except ConfigError as err:
        # started from autostart there is no terminal: show the problem where the owner will see it
        QMessageBox.critical(None, "Car charge planner", f"{err}\n\nFix the file and start the app again:\n{config_file}")
        print(err, file=sys.stderr)
        return 2
    tray = TrayApp(cfg, config_file)
    tray.start()
    return app.exec()
