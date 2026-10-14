"""The QML dashboard, loaded offscreen with a real engine behind the bridge: what a screen reader, the keyboard and the
eye get from it."""

import subprocess
import sys
from datetime import date, timedelta

import pytest
from PySide6.QtCore import Qt
from PySide6.QtGui import QAccessible, QColor
from PySide6.QtQuick import QQuickItem
from PySide6.QtTest import QTest
from shiboken6 import delete

from car_charge_planner.engine import Engine
from car_charge_planner.forecast.service import ForecastResult
from car_charge_planner.quick.bridge import DashboardBridge
from car_charge_planner.quick.window import load_dashboard, qml_engine, set_dark
from conftest import FakeClock, FakeNotifier, at, forecast_from

WEDNESDAY = date(2026, 10, 14)
CONTROLS = {QAccessible.Role.Button, QAccessible.Role.PageTab, QAccessible.Role.CheckBox, QAccessible.Role.RadioButton,
            QAccessible.Role.ComboBox, QAccessible.Role.EditableText, QAccessible.Role.SpinBox, QAccessible.Role.Slider}


class Recording:
    def __init__(self, result):
        self.result = result
        self.forced = []

    def get(self, cfg, now, force=False):
        self.forced.append(force)
        return self.result

    def forget_failures(self):
        pass


class Dashboard:
    def __init__(self, cfg):
        steps = forecast_from([(WEDNESDAY, 700), (WEDNESDAY + timedelta(days=1), 650), (WEDNESDAY + timedelta(days=2), 120)])
        self.forecasts = Recording(ForecastResult(steps, at(WEDNESDAY, 6), True, False))
        self.engine = Engine(cfg, FakeNotifier(), self.forecasts, FakeClock(at(WEDNESDAY, 8, 40)))
        self.engine.set_battery(35)
        self.bridge = DashboardBridge(self.engine)
        self.updates = []
        self.engine.watch(self.updates.append)
        self.engine.watch(self.bridge.show)
        self.engine.refresh()
        self.qml = qml_engine()
        self.warnings = []
        self.qml.warnings.connect(lambda found: self.warnings.extend(w.toString() for w in found))
        self.window = load_dashboard(self.bridge, self.qml)
        self.window.show()
        assert QTest.qWaitForWindowExposed(self.window)

    @property
    def days(self):
        return self.updates[-1].days

    def accessible(self):
        """(role, name) of everything a screen reader is told about, in tree order."""
        found = []

        def walk(node):
            if node.state().invisible:
                return  # a screen reader skips what is hidden
            found.append((node.role(), node.text(QAccessible.Text.Name)))
            for index in range(node.childCount()):
                walk(node.child(index))

        walk(QAccessible.queryAccessibleInterface(self.window))
        return found

    def named(self, role):
        return [name for found, name in self.accessible() if found == role]

    def press(self, key):
        QTest.keyClick(self.window, key)

    def focused(self):
        return QAccessible.queryAccessibleInterface(self.window.activeFocusItem()).text(QAccessible.Text.Name)


@pytest.fixture
def dashboard(cfg, qapp):
    shown = Dashboard(cfg)
    yield shown
    shown.window.close()
    delete(shown.qml)  # before the bridge: its bindings would read a deleted object
    assert shown.warnings == []


def test_the_tabs_name_each_planned_day_for_a_screen_reader(dashboard):
    assert dashboard.named(QAccessible.Role.PageTabList) == ["Days"]
    assert dashboard.named(QAccessible.Role.PageTab) == [day.title for day in dashboard.days]


def test_the_right_arrow_selects_the_next_day_and_its_cards_follow(dashboard):
    dashboard.press(Qt.Key.Key_Tab)
    assert dashboard.focused() == dashboard.days[0].title
    assert dashboard.named(QAccessible.Role.Chart) == [dashboard.days[0].summary]

    dashboard.press(Qt.Key.Key_Right)
    assert dashboard.focused() == dashboard.days[1].title
    assert dashboard.named(QAccessible.Role.Chart) == [dashboard.days[1].summary]
    assert dashboard.days[1].heading in dashboard.named(QAccessible.Role.Grouping)

    dashboard.press(Qt.Key.Key_Left)
    assert dashboard.named(QAccessible.Role.Chart) == [dashboard.days[0].summary]


def test_the_grid_advice_shows_on_todays_tab_only(dashboard):
    assert "Grid advice" in dashboard.named(QAccessible.Role.Grouping)
    dashboard.press(Qt.Key.Key_Tab)
    dashboard.press(Qt.Key.Key_Right)
    assert "Grid advice" not in dashboard.named(QAccessible.Role.Grouping)


def test_every_control_has_a_name(dashboard):
    controls = [(role, name) for role, name in dashboard.accessible() if role in CONTROLS]
    assert len(controls) == len(dashboard.days) + 3
    assert all(name for _role, name in controls), controls


def test_tab_reaches_every_control_in_layout_order_and_shows_where_the_focus_is(dashboard):
    order = []
    for _ in range(len(dashboard.days) + 4):
        dashboard.press(Qt.Key.Key_Tab)
        order.append(dashboard.focused())
        rings = [ring for ring in dashboard.window.activeFocusItem().findChildren(QQuickItem, "focusRing") if ring.isVisible()]
        assert len(rings) == 1, order[-1]
    assert order == [*(day.title for day in dashboard.days), "Refresh", "Away…", "Settings", dashboard.days[0].title]


def test_the_footers_buttons_reach_the_engine_and_the_desktop_app(dashboard):
    asked = []
    dashboard.bridge.settingsRequested.connect(lambda: asked.append("settings"))
    dashboard.bridge.awayRequested.connect(lambda: asked.append("away"))
    for _ in range(len(dashboard.days) + 1):
        dashboard.press(Qt.Key.Key_Tab)
    dashboard.press(Qt.Key.Key_Space)  # Refresh
    dashboard.press(Qt.Key.Key_Tab)
    dashboard.press(Qt.Key.Key_Space)  # Away
    dashboard.press(Qt.Key.Key_Tab)
    dashboard.press(Qt.Key.Key_Space)  # Settings
    assert dashboard.forecasts.forced == [False, True]
    assert asked == ["away", "settings"]


@pytest.mark.parametrize("dark, panel", [(False, "#ffffff"), (True, "#161c24")], ids=["light", "dark"])
def test_the_window_is_drawn_in_the_themes_colours(dashboard, dark, panel):
    set_dark(dashboard.qml, dark)
    QTest.qWait(50)
    image = dashboard.window.grabWindow()
    assert QColor(image.pixel(2, image.height() // 2)).name() == panel


SCALED = """
import sys
sys.path[:0] = sys.argv[2:]
from PySide6.QtWidgets import QApplication
app = QApplication([])
from PySide6.QtQuick import QQuickItem
from PySide6.QtTest import QTest
from test_quick_dashboard import Dashboard
from conftest import example_config
shown = Dashboard(example_config())
window = shown.window
image = window.grabWindow()
assert window.devicePixelRatio() == float(sys.argv[1]), window.devicePixelRatio()
assert image.width() == round(window.width() * float(sys.argv[1])), (image.width(), window.width())
outside = []
for item in window.contentItem().findChildren(QQuickItem):
    if item.isVisible() and item.width() > 0:
        right = item.mapToScene(item.boundingRect().topRight()).x()
        if right > window.width() + 0.5:
            outside.append((item.metaObject().className(), right))
assert not outside, outside
assert not shown.warnings, shown.warnings
from shiboken6 import delete
window.close()
delete(shown.qml)
"""


@pytest.mark.parametrize("factor", ["1.5", "2"])
def test_the_dashboard_lays_out_at_150_and_200_percent(factor, tmp_path):
    """Qt scales device-independent pixels for the screen; this checks the layout does not overflow at that scale."""
    import os
    from pathlib import Path

    tests = Path(__file__).resolve().parent
    env = {**os.environ, "QT_SCALE_FACTOR": factor, "XDG_STATE_HOME": str(tmp_path), "XDG_CACHE_HOME": str(tmp_path), "XDG_CONFIG_HOME": str(tmp_path)}
    done = subprocess.run([sys.executable, "-c", SCALED, factor, str(tests)], capture_output=True, text=True, timeout=120, env=env, encoding="utf-8")
    assert done.returncode == 0, done.stderr[-2000:]
