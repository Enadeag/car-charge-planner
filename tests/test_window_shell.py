"""The window shell: the plan in an ordinary window, for desktops without a tray."""

import os

os.environ.setdefault("QT_QPA_PLATFORM", "offscreen")

from datetime import date, timedelta

import pytest
from PySide6.QtCore import QEvent
from PySide6.QtGui import QCloseEvent, QKeySequence
from PySide6.QtWidgets import QApplication, QPushButton

from car_charge_planner.controller import Controller
from car_charge_planner.forecast.service import ForecastResult
from car_charge_planner.shells.icons import make_icon
from car_charge_planner.shells.window import BACKGROUND_HINT, WindowShell
from car_charge_planner.storage.state import load_state
from conftest import FakeNotifier, at, forecast_from

DAY = date(2026, 7, 3)


class Setup:
    def __init__(self, cfg, tmp_path, monkeypatch, hour=7, minute=0):
        monkeypatch.setenv("XDG_STATE_HOME", str(tmp_path))
        monkeypatch.setattr("car_charge_planner.forecast.service.ForecastService.get", lambda *a, **k: None)
        self.notifier = FakeNotifier()
        self.controller = Controller(cfg, tmp_path / "config.toml", self.notifier)
        self.controller.result = ForecastResult(
            forecast_from([(DAY + timedelta(days=i), 900) for i in range(3)]), at(DAY, 6), True, False
        )
        self.shell = WindowShell(self.controller)
        self.window = self.shell.window
        self.clock(hour, minute)

    def clock(self, hour, minute=0):
        class Frozen:
            @staticmethod
            def now(tz=None):
                return at(DAY, hour, minute)

        self.monkeypatch_target = Frozen
        import car_charge_planner.controller as controller_module

        controller_module.datetime = Frozen


@pytest.fixture
def s(cfg, tmp_path, monkeypatch, qapp):
    import car_charge_planner.controller as controller_module

    real = controller_module.datetime
    setup = Setup(cfg, tmp_path, monkeypatch)
    yield setup
    controller_module.datetime = real
    setup.window.hide()


def actions(menu):
    return [a.text() for a in menu.actions() if a.text()]


def test_the_window_shows_the_plan_in_the_shared_view(s):
    s.shell.show_plan()
    vm = s.controller.vm
    assert s.window.isVisible() and s.shell.view.headline.text() == vm.headline


def test_the_title_and_icon_follow_the_state(s):
    s.shell.show_plan()
    upcoming = s.window.windowTitle()
    assert upcoming == s.controller.vm.headline
    s.clock(11)
    s.controller.rebuild()
    assert s.window.windowTitle() == "Charge now" and s.window.windowTitle() != upcoming
    assert not s.window.windowIcon().isNull()
    assert s.window.windowIcon().pixmap(32, 32).toImage() == make_icon("active").pixmap(32, 32).toImage()


def test_with_no_forecast_the_title_says_so(cfg, tmp_path, monkeypatch, qapp):
    monkeypatch.setenv("XDG_STATE_HOME", str(tmp_path))
    controller = Controller(cfg, tmp_path / "config.toml", FakeNotifier())
    shell = WindowShell(controller)
    controller.rebuild()
    assert shell.window.windowTitle() == "No forecast available"


def test_losing_the_focus_does_not_close_the_window(s):
    s.shell.show_plan()
    s.window.isActiveWindow = lambda: True
    QApplication.sendEvent(s.window, QEvent(QEvent.Type.ActivationChange))
    s.window.isActiveWindow = lambda: False
    QApplication.sendEvent(s.window, QEvent(QEvent.Type.ActivationChange))
    from PySide6.QtTest import QTest

    QTest.qWait(300)
    assert s.window.isVisible()  # no click-away: it is a window, not a popup


def test_the_window_follows_the_height_of_the_page_shown(s, qapp):
    s.shell.show_plan()
    qapp.processEvents()
    plan_height = s.window.height()
    s.shell.view.show_update()
    qapp.processEvents()  # the resize is applied by the event loop
    assert s.window.height() != plan_height
    s.shell.view.tabs.setCurrentIndex(0)
    qapp.processEvents()
    assert s.window.height() == plan_height


def test_closing_hides_the_window_and_the_app_keeps_running(s):
    s.shell.show_plan()
    event = QCloseEvent()
    QApplication.sendEvent(s.window, event)
    assert not event.isAccepted() and not s.window.isVisible()
    s.controller.rebuild()  # still alive: it keeps planning and announcing
    assert s.controller.vm is not None


def test_the_first_close_says_the_app_keeps_running_and_only_once(s):
    s.shell.show_plan()
    s.window.close()
    assert len(s.notifier.sent) == 1
    title, body = s.notifier.sent[0]
    assert "background" in title.lower() and "Ctrl+Q" in body
    s.shell.show_plan()
    s.window.close()
    assert len(s.notifier.sent) == 1


def test_the_note_is_not_repeated_after_a_restart(cfg, tmp_path, monkeypatch, qapp):
    first = Setup(cfg, tmp_path, monkeypatch)
    first.shell.show_plan()
    first.window.close()
    assert BACKGROUND_HINT in load_state().seen
    second = Setup(cfg, tmp_path, monkeypatch)
    second.shell.show_plan()
    second.window.close()
    assert second.notifier.sent == []


def test_quit_has_the_standard_shortcut_and_ends_the_app(s, monkeypatch):
    assert s.shell.quit_action.shortcut() == QKeySequence(QKeySequence.StandardKey.Quit)
    quit_calls = []
    monkeypatch.setattr(QApplication, "quit", staticmethod(lambda: quit_calls.append(1)))
    s.shell.quit_action.trigger()
    assert quit_calls == [1]


def test_refresh_forces_a_new_forecast(s, monkeypatch):
    calls = []
    monkeypatch.setattr(s.controller, "refresh", lambda force=False: calls.append(force))
    s.shell.refresh_action.trigger()
    assert calls == [True]


def test_settings_from_the_menu_opens_the_settings_window(s):
    s.shell.settings_action.trigger()
    assert s.controller.settings_window is not None and s.controller.settings_window.isVisible()
    s.controller.settings_window.confirm_discard = lambda: True
    s.controller.settings_window.close()


def test_the_edit_weekly_rules_button_opens_settings_on_the_away_rules_tab(s):
    s.shell.show_plan()
    next(b for b in s.shell.view.findChildren(QPushButton) if b.text() == "Edit weekly rules in Settings...").click()
    window = s.controller.settings_window
    assert window.isVisible() and window.tabs.tabText(window.tabs.currentIndex()) == "Away rules"
    window.confirm_discard = lambda: True
    window.close()


def test_start_shows_the_window(s, monkeypatch):
    monkeypatch.setattr(s.controller, "refresh", lambda force=False: None)
    s.shell.start()
    assert s.window.isVisible()


def test_start_in_the_background_keeps_the_window_hidden_but_runs_the_controller(s, monkeypatch):
    started = []
    monkeypatch.setattr(s.controller, "refresh", lambda force=False: started.append(1))
    s.shell.start(background=True)
    assert not s.window.isVisible() and started == [1]


def test_a_second_launch_shows_the_hidden_window(s):
    from car_charge_planner.instance import InstanceServer, request_show

    server = InstanceServer(os.path.join(os.environ["XDG_STATE_HOME"], "i.sock"))
    assert server.listen()
    server.show_requested.connect(s.shell.show_plan)  # as main() connects it
    assert not s.window.isVisible()
    assert request_show(server.path)
    from PySide6.QtTest import QTest

    QTest.qWait(250)
    assert s.window.isVisible()
    server.close()


def test_show_plan_brings_a_minimised_window_back(s):
    s.shell.show_plan()
    s.window.showMinimized()
    s.shell.show_plan()
    assert not s.window.isMinimized() and s.window.isVisible()


def test_setting_the_battery_in_the_window_reaches_the_state_and_the_view(s):
    s.shell.show_plan()
    s.shell.view.battery_submitted.emit(33.0)
    assert load_state().battery.pct == 33
    assert s.shell.view.battery_status.text().startswith("Battery 33%")


def test_the_window_is_centred_the_first_time_it_is_shown(s):
    from PySide6.QtGui import QGuiApplication

    s.shell.show_plan()
    area = QGuiApplication.primaryScreen().availableGeometry()
    assert abs(s.window.geometry().center().x() - area.center().x()) <= 2
    assert abs(s.window.geometry().center().y() - area.center().y()) <= 2


def test_it_is_not_moved_again_after_the_owner_has_placed_it(s):
    s.shell.show_plan()
    s.window.move(37, 53)
    s.window.hide()
    s.shell.show_plan()
    assert (s.window.x(), s.window.y()) == (37, 53)
