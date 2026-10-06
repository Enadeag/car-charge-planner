"""A click on one of our notifications shows the plan."""

import os

os.environ.setdefault("QT_QPA_PLATFORM", "offscreen")

import logging
import threading
import time
from datetime import date, timedelta

import pytest
from PySide6.QtCore import QPoint, QRect
from PySide6.QtGui import QGuiApplication
from PySide6.QtTest import QTest
from PySide6.QtWidgets import QApplication

from app.controller import Controller
from app.notification_actions import DEFAULT_ACTION, ActionListener, JeepneySource, action_handler
from app.notifier import Notifier
from app.popup import Popup
from app.shells.tray import TrayShell
from app.shells.window import WindowShell
from forecast.open_meteo import ForecastResult

from conftest import TZ, FakeNotifier, at, forecast_from

DAY = date(2026, 7, 3)


@pytest.fixture
def qapp():
    return QApplication.instance() or QApplication([])


class RecordingBus:
    def __init__(self):
        self.next_id = 100

    def notify(self, *args):
        self.next_id += 1
        return self.next_id


def wait_for(condition, ms=2000):
    end = time.time() + ms / 1000
    while time.time() < end:
        QTest.qWait(20)
        if condition():
            return True
    return False


# ---- which clicks count ---------------------------------------------------------------


def test_the_notifier_knows_which_ids_it_sent():
    notifier = Notifier(RecordingBus(), icon="x")
    first = notifier.send("a", "b")
    assert notifier.owns(first) and not notifier.owns(first + 1) and not notifier.owns(7)


def test_a_click_on_our_notification_shows_the_plan():
    notifier = Notifier(RecordingBus(), icon="x")
    shown = []
    handle = action_handler(notifier, lambda: shown.append(1))
    handle(notifier.send("a", "b"), DEFAULT_ACTION)
    assert shown == [1]


def test_a_click_on_someone_elses_notification_is_ignored():
    notifier = Notifier(RecordingBus(), icon="x")
    notifier.send("a", "b")
    shown = []
    action_handler(notifier, lambda: shown.append(1))(999_999, DEFAULT_ACTION)
    assert shown == []


def test_other_actions_are_ignored():
    notifier = Notifier(RecordingBus(), icon="x")
    shown = []
    handle = action_handler(notifier, lambda: shown.append(1))
    for key in ("dismiss", "snooze", ""):
        handle(notifier.send("a", "b"), key)
    assert shown == []


def test_the_default_action_key_is_the_one_the_notifier_sends():
    from app.notifier import DEFAULT_ACTION as sent

    assert DEFAULT_ACTION == sent[0] == "default"


# ---- the shells show their plan ------------------------------------------------------------


def make_controller(cfg, tmp_path, monkeypatch):
    monkeypatch.setenv("XDG_STATE_HOME", str(tmp_path))
    monkeypatch.setattr("app.controller.get_forecast", lambda *a, **k: None)
    controller = Controller(cfg, tmp_path / "config.toml", FakeNotifier())
    controller.result = ForecastResult(forecast_from([(DAY + timedelta(days=i), 900) for i in range(3)]), at(DAY, 6), True, False)
    return controller


def test_a_click_raises_the_hidden_window(cfg, tmp_path, monkeypatch, qapp):
    controller = make_controller(cfg, tmp_path, monkeypatch)
    shell = WindowShell(controller)
    handle = action_handler(controller.notifier, shell.show_plan)
    controller.notifier.send("Charge window", "body")
    assert not shell.window.isVisible()
    handle(1, DEFAULT_ACTION)
    assert shell.window.isVisible()
    shell.window.hide()


def test_a_click_opens_the_tray_popup(cfg, tmp_path, monkeypatch, qapp):
    controller = make_controller(cfg, tmp_path, monkeypatch)
    shell = TrayShell(controller)
    handle = action_handler(controller.notifier, shell.show_plan)
    controller.notifier.send("Charge window", "body")
    handle(1, DEFAULT_ACTION)
    assert shell.popup.isVisible() and shell.popup.tabs.currentIndex() == 0  # on the plan
    shell.popup.hide()


# ---- where the popup appears with no tray icon to go by ------------------------------------


def area():
    return QGuiApplication.primaryScreen().availableGeometry()


def test_with_no_anchor_and_no_history_it_appears_near_the_top_right(cfg, qapp):
    popup = Popup(TZ)
    popup.show_near(QRect())
    a = area()
    assert popup.x() + popup.width() <= a.right() and popup.x() + popup.width() >= a.right() - 16
    assert popup.y() <= a.top() + 16
    popup.hide()


def test_with_no_anchor_it_returns_to_where_it_was_last_placed(cfg, qapp):
    popup = Popup(TZ)
    popup.show_near(QRect(200, 300, 24, 24))  # a click on the tray icon
    placed = QPoint(popup.x(), popup.y())
    popup.hide()
    popup.show_near(QRect())  # a notification click: no icon
    assert QPoint(popup.x(), popup.y()) == placed and popup.last_position == placed
    popup.hide()


def test_an_anchored_placement_still_wins_over_the_remembered_one(cfg, qapp):
    popup = Popup(TZ)
    popup.show_near(QRect(200, 300, 24, 24))
    popup.hide()
    popup.last_position = QPoint(5, 5)
    popup.show_near(QRect(400, 100, 24, 24))
    assert popup.last_position == QPoint(popup.x(), popup.y()) and popup.last_position != QPoint(5, 5)
    popup.hide()


# ---- the listener ------------------------------------------------------------------------------


class FakeSource:
    """Plays a script of clicks, then waits until told to stop."""

    def __init__(self, clicks=(), fail=None):
        self.clicks, self.fail, self.stopped = list(clicks), fail, threading.Event()

    def run(self, on_action, should_stop):
        if self.fail:
            raise self.fail
        for click in self.clicks:
            on_action(*click)
        while not should_stop():
            time.sleep(0.01)
        self.stopped.set()


def test_clicks_reach_the_qt_side(qapp):
    listener = ActionListener(FakeSource([(7, "default"), (8, "dismiss")]))
    seen = []
    listener.action_invoked.connect(lambda i, k: seen.append((i, k)))
    listener.start()
    assert wait_for(lambda: len(seen) == 2)
    assert seen == [(7, "default"), (8, "dismiss")]
    listener.stop()


def test_stopping_ends_the_thread(qapp):
    source = FakeSource()
    listener = ActionListener(source)
    listener.start()
    assert listener.running
    listener.stop()
    assert source.stopped.is_set() and not listener.running


def test_starting_twice_does_not_start_a_second_thread(qapp):
    source = FakeSource([(1, "default")])
    listener = ActionListener(source)
    seen = []
    listener.action_invoked.connect(lambda i, k: seen.append(i))
    listener.start()
    listener.start()
    assert wait_for(lambda: seen == [1])
    QTest.qWait(100)
    assert seen == [1]
    listener.stop()


def test_a_listener_that_cannot_work_is_logged_and_the_app_carries_on(qapp, caplog):
    listener = ActionListener(FakeSource(fail=OSError("no session bus")))
    with caplog.at_level(logging.WARNING):
        listener.start()
        assert wait_for(lambda: not listener.running)
    assert any("Not listening for notification clicks" in r.message for r in caplog.records)
    listener.stop()  # still safe


def test_the_thread_does_not_keep_the_app_alive(qapp):
    listener = ActionListener(FakeSource())
    listener.start()
    assert listener._thread.daemon
    listener.stop()


# ---- for real, over the session bus ----------------------------------------------------------------


@pytest.mark.skipif(not os.environ.get("DBUS_SESSION_BUS_ADDRESS"), reason="needs a session bus")
def test_a_real_action_signal_on_the_session_bus_is_heard(qapp):
    """Emits a fake ActionInvoked for an id nobody owns, so no notification is involved."""
    from jeepney import DBusAddress, new_signal
    from jeepney.io.blocking import open_dbus_connection

    fake_id = 2_000_000_011
    listener = ActionListener(JeepneySource())
    seen = []
    listener.action_invoked.connect(lambda i, k: seen.append((i, k)))
    listener.start()
    try:
        address = DBusAddress("/org/freedesktop/Notifications", interface="org.freedesktop.Notifications")
        with open_dbus_connection(bus="SESSION") as sender:
            for _ in range(10):  # the listener needs a moment to register its match rule
                QTest.qWait(150)
                sender.send(new_signal(address, "ActionInvoked", "us", (fake_id, "default")))
                if (fake_id, "default") in seen:
                    break
        assert wait_for(lambda: (fake_id, "default") in seen)
    finally:
        started = time.time()
        listener.stop()
    assert time.time() - started < 3  # it stops promptly instead of hanging on a blocked read


# ---- python -m app test-notification ------------------------------------------------------------


class Said(list):
    def __call__(self, text):
        self.append(text)


def test_the_selftest_reports_a_click_that_arrives(qapp):
    from app import selftest

    notifier = Notifier(RecordingBus(), icon="x")
    said = Said()
    source = FakeSource([(101, "default")])  # the id the first notification gets
    code = selftest.run(timeout_s=5, notifier=notifier, listener=ActionListener(source), out=said)
    assert code == 0 and "Click received" in said[-1] and "id 101" in said[0]


def test_the_selftest_ignores_clicks_on_other_notifications(qapp):
    from app import selftest

    said = Said()
    source = FakeSource([(55, "default"), (101, "dismiss")])  # not ours, and not the default action
    code = selftest.run(timeout_s=0.4, notifier=Notifier(RecordingBus(), icon="x"), listener=ActionListener(source), out=said)
    assert code == 1 and "No click received" in said[-1]


def test_the_selftest_says_so_when_the_notification_cannot_be_sent(qapp):
    from app import selftest

    class Broken:
        def notify(self, *args):
            raise OSError("no bus")

    said = Said()
    code = selftest.run(timeout_s=1, notifier=Notifier(Broken(), icon="x"), listener=ActionListener(FakeSource()), out=said)
    assert code == 1 and "could not be sent" in said[0]


def test_the_command_is_listed_and_wired():
    import subprocess
    import sys
    from pathlib import Path

    project = Path(__file__).resolve().parent.parent
    done = subprocess.run([sys.executable, "-m", "app", "frobnicate"], cwd=project, capture_output=True, text=True, timeout=30)
    assert "test-notification" in done.stderr
