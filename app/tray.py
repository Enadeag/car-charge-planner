"""Tray icon, popup and refresh loop (PySide6)."""

from __future__ import annotations

import sys
import threading
import time
from datetime import datetime
from pathlib import Path
from zoneinfo import ZoneInfo

from PySide6.QtCore import QLockFile, QObject, QPointF, Qt, QTimer, Signal
from PySide6.QtGui import QColor, QIcon, QPainter, QPixmap, QPolygonF
from PySide6.QtWidgets import QApplication, QMenu, QMessageBox, QSystemTrayIcon

from forecast.open_meteo import ForecastResult, ForecastUnavailable, forecast_key, get_forecast
from planner.advice import grid_advice
from planner.model import AwayPeriod, Config, ConfigError, load_config
from planner.projection import plan
from storage.state import State, add_away, load_state, mark_notified, remove_away, set_battery

from .config_file import ensure_config
from .notifications import candidate_windows, due, message
from .popup import Popup
from .settings.window import SettingsWindow
from .view_model import ViewModel, build, no_forecast

CLICK_CLOSE_GRACE_S = 0.5
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


class TrayApp(QObject):
    forecast_loaded = Signal(object, object)  # the Config the fetch ran under, ForecastResult | Exception

    def __init__(self, cfg: Config, config_file: Path) -> None:
        super().__init__()
        self.cfg = cfg
        self.config_file = config_file
        self.tz = ZoneInfo(cfg.timezone)
        self.result: ForecastResult | None = None
        self.error: str | None = None
        self.vm: ViewModel | None = None
        self._loading = False
        self.settings_window: SettingsWindow | None = None

        self.popup = Popup(self.tz)
        self.popup.battery_submitted.connect(self._on_battery)
        self.popup.away_added.connect(self._on_away_added)
        self.popup.away_removed.connect(self._on_away_removed)
        self.popup.settings_requested.connect(self._on_settings_requested)
        self.tray = QSystemTrayIcon(make_icon("none"))
        menu = QMenu()
        menu.addAction("Refresh forecast", lambda: self.refresh(force=True))
        menu.addAction("Settings...", lambda: self.open_settings())
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
        cfg, tz = self.cfg, self.tz  # the worker keeps the config it started with

        def work() -> None:
            try:
                outcome: object = get_forecast(cfg, datetime.now(tz), force=force)
            except ForecastUnavailable as err:
                outcome = err
            except Exception as err:  # never let a worker crash the tray silently
                outcome = err
            self.forecast_loaded.emit(cfg, outcome)

        threading.Thread(target=work, daemon=True).start()

    def open_settings(self, tab: str | None = None) -> None:
        """One Settings window at a time: opening it again brings the open one forward."""
        if self.settings_window is None or not self.settings_window.isVisible():
            self.settings_window = SettingsWindow(self.cfg, self.config_file, self.apply_config)
        if tab:
            self.settings_window.show_tab(tab)
        self.settings_window.show_centred()

    def _on_settings_requested(self, tab: str) -> None:
        self.popup.hide()
        self.open_settings(tab)

    def apply_config(self, cfg: Config) -> None:
        """Switch to a new config without a restart (settings spec 3.3)."""
        if forecast_key(cfg) != forecast_key(self.cfg):
            self.result, self.error = None, None  # a forecast for other panels or place would mislead
        self.cfg = cfg
        self.tz = ZoneInfo(cfg.timezone)
        self.popup.tz = self.tz
        self.rebuild()
        self.refresh()  # reuses the cache when the forecast key is unchanged

    def _on_forecast(self, cfg: Config, outcome: object) -> None:
        self._loading = False
        if cfg is not self.cfg:
            self.refresh()  # started under an older config: its result must not replace the new one
            return
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
        self.popup.update_view(vm, state, now.date(), self.cfg.away_rules)

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
            elif time.monotonic() - self.popup.auto_closed_at < CLICK_CLOSE_GRACE_S:
                pass  # this click is what just closed it (it took the focus): do not reopen
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
