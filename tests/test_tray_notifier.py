"""Notifications through the tray icon, as used on Windows and macOS."""

import logging
import os
import sys

os.environ.setdefault("QT_QPA_PLATFORM", "offscreen")

import pytest
from PySide6.QtCore import QObject, Signal
from PySide6.QtGui import QIcon

from car_charge_planner import selftest
from car_charge_planner.notification_actions import ActionListener, action_handler, click_listener
from car_charge_planner.notifier import Notifier, make_notifier
from car_charge_planner.tray_notifier import TrayNotifier


class FakeTray(QObject):
    messageClicked = Signal()

    def __init__(self):
        super().__init__()
        self.shown = []

    def icon(self):
        return QIcon()

    def showMessage(self, title, body, icon, expire_ms):
        self.shown.append((title, body, expire_ms))


@pytest.fixture
def notifier():
    notifier = TrayNotifier(available=lambda: True)
    notifier.tray = FakeTray()
    notifier.use_tray(notifier.tray)
    return notifier


def test_a_message_is_shown_with_its_text_and_expiry(notifier):
    notifier.send("Charge window 10:00-15:00", "Set the current to 7 A", expire_ms=15000)
    assert notifier.tray.shown == [("Charge window 10:00-15:00", "Set the current to 7 A", 15000)]


def test_each_message_gets_a_new_id(notifier):
    assert [notifier.send("a", "b"), notifier.send("c", "d")] == [1, 2]


def test_a_click_shows_the_plan(notifier, qapp):
    shown = []
    notifier.action_invoked.connect(action_handler(notifier, lambda: shown.append(1)))
    notifier.send("a", "b")
    notifier.tray.messageClicked.emit()
    assert shown == [1]


def test_a_click_with_no_message_outstanding_is_ignored(notifier, qapp):
    shown = []
    notifier.action_invoked.connect(action_handler(notifier, lambda: shown.append(1)))
    notifier.tray.messageClicked.emit()
    notifier.send("a", "b")
    notifier.tray.messageClicked.emit()
    notifier.tray.messageClicked.emit()  # the same message cannot be clicked twice
    assert shown == [1]


def test_only_the_latest_message_is_ours_to_answer(notifier):
    first = notifier.send("a", "b")
    second = notifier.send("c", "d")
    assert not notifier.owns(first) and notifier.owns(second)
    assert not notifier.owns(second + 1)


def test_a_click_for_another_id_does_nothing(notifier):
    shown = []
    notifier.send("a", "b")
    action_handler(notifier, lambda: shown.append(1))(99, "default")
    assert shown == []


@pytest.mark.parametrize("tray_given, available", [(False, True), (True, False)])
def test_without_a_usable_tray_nothing_is_shown_and_the_log_says_so_once(tray_given, available, caplog):
    notifier = TrayNotifier(available=lambda: available)
    tray = FakeTray()
    if tray_given:
        notifier.use_tray(tray)
    with caplog.at_level(logging.WARNING):
        results = [notifier.send("a", "b") for _ in range(3)]
    assert results == [None, None, None] and tray.shown == []
    assert len([r for r in caplog.records if "no tray icon" in r.message]) == 1


@pytest.mark.parametrize("platform, expected", [("win32", TrayNotifier), ("darwin", TrayNotifier), ("linux", Notifier)])
def test_the_notifier_is_chosen_by_system(monkeypatch, qapp, platform, expected):
    monkeypatch.setattr(sys, "platform", platform)
    assert type(make_notifier()) is expected


def test_the_tray_notifier_needs_no_jeepney(monkeypatch, qapp):
    monkeypatch.setitem(sys.modules, "jeepney", None)  # an import of it now fails
    monkeypatch.setattr(sys, "platform", "win32")
    notifier = make_notifier()
    assert isinstance(click_listener(notifier), TrayNotifier)
    assert not isinstance(click_listener(Notifier()), TrayNotifier)
    assert isinstance(click_listener(Notifier()), ActionListener)


def test_the_self_test_fails_when_there_is_no_tray(qapp):
    said = []
    assert selftest.run(timeout_s=1, notifier=TrayNotifier(available=lambda: False), out=said.append) == 1
    assert "could not be sent" in said[0]


def test_in_window_mode_a_tray_icon_is_there_for_notifications_and_opens_the_window(qapp):
    from PySide6.QtWidgets import QSystemTrayIcon

    from car_charge_planner.main import notification_tray

    opened = []
    tray = notification_tray(lambda: opened.append(True))
    try:
        assert tray.isVisible()
        tray.activated.emit(QSystemTrayIcon.ActivationReason.Trigger)
        assert opened == [True]
    finally:
        tray.hide()
        tray.deleteLater()
