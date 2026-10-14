"""Clicking the tray icon opens the popup next to where the icon is."""

import os

os.environ.setdefault("QT_QPA_PLATFORM", "offscreen")

import sys

import pytest
from PySide6.QtCore import QPoint, QRect
from PySide6.QtGui import QCursor, QGuiApplication
from PySide6.QtWidgets import QSystemTrayIcon

from car_charge_planner.controller import Controller
from car_charge_planner.engine import FileStore
from car_charge_planner.shells.tray import TrayShell
from conftest import FakeNotifier


@pytest.fixture
def shell(cfg, tmp_path, monkeypatch, qapp):
    monkeypatch.setenv("XDG_STATE_HOME", str(tmp_path))
    monkeypatch.setattr("car_charge_planner.forecast.service.ForecastService.get", lambda *a, **k: None)
    tray_shell = TrayShell(Controller(cfg, FileStore(tmp_path / "config.toml"), FakeNotifier()))
    monkeypatch.setattr(tray_shell.tray, "geometry", lambda: QRect())  # no icon geometry known
    yield tray_shell
    tray_shell.popup.hide()


def click(shell):
    shell._on_activated(QSystemTrayIcon.ActivationReason.Trigger)


@pytest.mark.parametrize("system", ["win32", "darwin"])
def test_an_icon_without_geometry_is_found_where_the_click_was_on_windows_and_macos(system, shell, monkeypatch):
    area = QGuiApplication.primaryScreen().availableGeometry()
    monkeypatch.setattr(sys, "platform", system)
    monkeypatch.setattr(QCursor, "pos", staticmethod(lambda: QPoint(area.center().x(), area.bottom())))  # on a taskbar at the bottom
    click(shell)
    popup = shell.popup
    assert popup.isVisible() and popup.geometry().bottom() > area.center().y()


def test_on_linux_an_icon_without_geometry_does_not_use_the_pointer(shell, monkeypatch):
    area = QGuiApplication.primaryScreen().availableGeometry()
    monkeypatch.setattr(sys, "platform", "linux")  # Wayland does not tell the pointer position to a program
    monkeypatch.setattr(QCursor, "pos", staticmethod(lambda: QPoint(area.center().x(), area.bottom())))
    click(shell)
    assert shell.popup.isVisible() and shell.popup.geometry().top() < area.center().y()  # the top right corner
