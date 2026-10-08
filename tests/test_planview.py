"""PlanView is the content; Popup is the window around it."""

import os

os.environ.setdefault("QT_QPA_PLATFORM", "offscreen")

from datetime import date, timedelta

import pytest
from PySide6.QtCore import Qt
from PySide6.QtWidgets import QApplication, QLabel, QPushButton

from car_charge_planner.planner.projection import plan
from car_charge_planner.planview import PLAN, UPDATE, PlanView
from car_charge_planner.popup import Popup
from car_charge_planner.storage.state import BatteryReading, State
from car_charge_planner.view_model import build
from conftest import TZ, at, forecast_from

DAY = date(2026, 7, 3)
NOW = at(DAY, 7)


@pytest.fixture
def qapp():
    return QApplication.instance() or QApplication([])


def vm_for(cfg):
    forecast = forecast_from([(DAY + timedelta(days=i), 900) for i in range(3)])
    return build(plan(forecast, cfg, 45, NOW), NOW, cfg, BatteryReading(45, NOW), timedelta(minutes=5), False)


def test_the_view_is_a_plain_widget_with_no_window_behaviour(cfg, qapp):
    view = PlanView(TZ)
    assert not view.windowFlags() & Qt.WindowType.FramelessWindowHint
    assert view.windowType() == Qt.WindowType.Window  # an ordinary widget, not a tool window or popup
    assert not hasattr(view, "show_near") and not hasattr(view, "auto_closed_at")
    assert "×" not in [b.text() for b in view.findChildren(QPushButton)]  # no close button of its own


def test_the_view_works_on_its_own(cfg, qapp):
    view = PlanView(TZ)
    vm = vm_for(cfg)
    view.update_view(vm, State(BatteryReading(45, NOW)), DAY, cfg.away_rules)
    assert view.headline.text() == vm.headline and view.tabs.count() == 2
    assert view.tabs.currentIndex() == PLAN
    view.show_update()
    assert view.tabs.currentIndex() == UPDATE and view.stack.currentWidget() is view.update_page


def test_a_control_can_be_added_at_the_right_end_of_the_header(cfg, qapp):
    view = PlanView(TZ)
    extra = QPushButton("menu")
    view.add_to_header(extra)
    view.show()
    qapp.processEvents()
    assert extra.mapTo(view, extra.rect().topRight()).x() >= view.width() - 2  # at the right edge
    assert view.header.width() == view.width()
    view.hide()


def test_the_view_announces_when_it_resized_itself_to_a_page(cfg, qapp):
    view = PlanView(TZ)
    view.update_view(vm_for(cfg), State(BatteryReading(45, NOW)), DAY, cfg.away_rules)
    seen = []
    view.layout_changed.connect(lambda: seen.append(1))
    view.show_update()
    assert seen  # a wrapping window follows the page height


def test_the_view_signals_reach_the_popup_unchanged(cfg, qapp):
    popup = Popup(TZ)
    got = []
    popup.battery_submitted.connect(lambda v: got.append(("battery", v)))
    popup.settings_requested.connect(lambda t: got.append(("settings", t)))
    popup.view.battery_submitted.emit(55.0)
    popup.view.settings_requested.emit("away")
    assert got == [("battery", 55.0), ("settings", "away")]


def test_the_popup_wraps_one_view_and_adds_the_window_behaviour(cfg, qapp):
    popup = Popup(TZ)
    assert isinstance(popup.view, PlanView) and popup.view.parent() is popup
    assert popup.windowFlags() & Qt.WindowType.FramelessWindowHint
    assert popup.windowType() == Qt.WindowType.Tool
    assert "×" in [b.text() for b in popup.findChildren(QPushButton)]
    assert hasattr(popup, "show_near") and hasattr(popup, "auto_closed_at")


def test_the_popup_reaches_the_views_widgets_and_time_zone(cfg, qapp):
    popup = Popup(TZ)
    assert popup.headline is popup.view.headline and popup.battery_input is popup.view.battery_input
    assert popup.tz is TZ
    from zoneinfo import ZoneInfo

    london = ZoneInfo("Europe/London")
    popup.tz = london
    assert popup.view.tz is london and popup.tz is london
    with pytest.raises(AttributeError):
        _ = popup.no_such_widget


def test_the_popup_follows_the_views_height_when_the_page_changes(cfg, qapp):
    popup = Popup(TZ)
    popup.update_view(vm_for(cfg), State(BatteryReading(45, NOW)), DAY, cfg.away_rules)
    popup.show()
    plan_height = popup.height()
    popup.show_update()
    assert popup.height() != plan_height
    popup.hide()


def test_labels_and_inputs_live_in_the_view_not_the_popup_itself(cfg, qapp):
    popup = Popup(TZ)
    own = [c for c in popup.children() if isinstance(c, (QLabel, QPushButton)) and c.parent() is popup]
    assert own == []  # everything is inside the PlanView
