"""A click on one of our notifications shows the plan."""

import os

os.environ.setdefault("QT_QPA_PLATFORM", "offscreen")

import logging
import threading
import time
from datetime import date, timedelta

import pytest
from PySide6.QtCore import QPoint, QRect
from PySide6.QtTest import QTest

from car_charge_planner.controller import Controller
from car_charge_planner.forecast.service import ForecastResult
from car_charge_planner.notification_actions import DEFAULT_ACTION, ActionListener, JeepneySource, action_handler
from car_charge_planner.notifier import Notifier
from car_charge_planner.popup import Popup
from car_charge_planner.shells.tray import TrayShell
from car_charge_planner.shells.window import WindowShell
from conftest import TZ, FakeNotifier, at, forecast_from

DAY = date(2026, 7, 3)


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


def make_controller(cfg, tmp_path, monkeypatch):
    monkeypatch.setenv("XDG_STATE_HOME", str(tmp_path))
    monkeypatch.setattr("car_charge_planner.forecast.service.ForecastService.get", lambda *a, **k: None)
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


def test_with_no_anchor_it_returns_to_where_it_was_last_placed(cfg, qapp):
    popup = Popup(TZ)
    popup.show_near(QRect(200, 300, 24, 24))  # a click on the tray icon
    placed = QPoint(popup.x(), popup.y())
    popup.hide()
    popup.show_near(QRect())  # a notification click: no icon
    assert QPoint(popup.x(), popup.y()) == placed
    popup.hide()


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


def test_a_listener_that_cannot_work_is_logged_and_the_app_carries_on(qapp, caplog):
    listener = ActionListener(FakeSource(fail=OSError("no session bus")))
    with caplog.at_level(logging.WARNING):
        listener.start()
        assert wait_for(lambda: not listener.running)
    assert any("Not listening for notification clicks" in r.message for r in caplog.records)
    listener.stop()  # a second stop is safe


@pytest.mark.skipif(not os.environ.get("DBUS_SESSION_BUS_ADDRESS"), reason="needs a session bus")
@pytest.mark.linux
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


class Said(list):
    def __call__(self, text):
        self.append(text)


def test_the_selftest_reports_a_click_that_arrives(qapp):
    from car_charge_planner import selftest

    notifier = Notifier(RecordingBus(), icon="x")
    said = Said()
    source = FakeSource([(101, "default")])  # the id the first notification gets
    code = selftest.run(timeout_s=5, notifier=notifier, listener=ActionListener(source), out=said)
    assert code == 0 and "Click received" in said[-1] and "id 101" in said[0]


def test_the_selftest_ignores_clicks_on_other_notifications(qapp):
    from car_charge_planner import selftest

    said = Said()
    source = FakeSource([(55, "default"), (101, "dismiss")])  # not ours, and not the default action
    code = selftest.run(timeout_s=0.4, notifier=Notifier(RecordingBus(), icon="x"), listener=ActionListener(source), out=said)
    assert code == 1 and "No click received" in said[-1]


def test_the_selftest_says_so_when_the_notification_cannot_be_sent(qapp):
    from car_charge_planner import selftest

    class Broken:
        def notify(self, *args):
            raise OSError("no bus")

    said = Said()
    code = selftest.run(timeout_s=1, notifier=Notifier(Broken(), icon="x"), listener=ActionListener(FakeSource()), out=said)
    assert code == 1 and "could not be sent" in said[0]
