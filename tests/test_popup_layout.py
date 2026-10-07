"""The redesigned popup: a calm Plan page, an Update page for inputs."""

import os

os.environ.setdefault("QT_QPA_PLATFORM", "offscreen")

from datetime import date, time, timedelta

import pytest
from PySide6.QtWidgets import QApplication, QLabel

from app.away_input import away_summary, make_away
from app.popup import PLAN, UPDATE, Popup
from app.view_model import build
from planner.advice import grid_advice
from planner.model import AwayRule, Config
from planner.projection import plan
from storage.state import BatteryReading, State

from conftest import TZ, at, forecast_from

DAY = date(2026, 7, 3)
NOW = at(DAY, 7)


@pytest.fixture
def qapp():
    return QApplication.instance() or QApplication([])


def view(cfg, days, soc=45, battery=None, state=None):
    forecast = forecast_from(list(days))
    plans = plan(forecast, cfg, soc, NOW)
    advice = grid_advice(plans, forecast, cfg, None, NOW)
    battery = battery or BatteryReading(soc, NOW)
    vm = build(plans, NOW, cfg, battery, timedelta(minutes=5), False, advice)
    popup = Popup(TZ)
    popup.update_view(vm, state or State(battery), NOW.date(), cfg.away_rules)
    return popup, vm


def labels(widget):
    return [l.text() for l in widget.findChildren(QLabel)]


def test_plan_page_shows_the_headline_facts_and_projection(cfg, qapp):
    popup, vm = view(cfg, [(DAY, 900), (DAY + timedelta(days=1), 900)])
    assert popup.headline.text() == vm.headline
    assert " · " in popup.detail.text() and "kWh" in popup.detail.text()
    assert popup.projection.text().startswith("Battery 45% → ") and not popup.projection.isHidden()


def test_advice_is_split_into_facts_and_reason(cfg, qapp):
    days = [(DAY + timedelta(days=i), 120) for i in range(3)]
    popup, vm = view(cfg, days, soc=35)
    assert not popup.advice_box.isHidden()
    assert popup.advice_title.text().startswith("Grid charge needed · ")
    assert "kW (" in popup.advice_detail.text()
    assert popup.advice_reason.text().startswith("Battery would drop to")
    assert len(popup.advice_title.text()) < 60  # a line, not a paragraph


def test_no_advice_block_when_there_is_nothing_to_warn_about(cfg, qapp):
    popup, _ = view(cfg, [(DAY, 900)], soc=70)
    assert popup.advice_box.isHidden()


def test_outlook_is_a_grid_of_columns_and_does_not_repeat_the_headline(cfg, qapp):
    popup, vm = view(cfg, [(DAY + timedelta(days=i), 900) for i in range(3)])
    cells = [popup.outlook_grid.itemAt(i).widget().text() for i in range(popup.outlook_grid.count())]
    assert len(vm.outlook) == 2 and len(cells) == 8  # four columns per row
    assert cells[0] == "Tomorrow" and cells[3].endswith("kWh")
    assert "Today" not in cells  # today is the headline


def test_quiet_days_are_one_dim_line(cfg, qapp):
    popup, vm = view(cfg, [(DAY, 900), (DAY + timedelta(days=1), 120), (DAY + timedelta(days=2), 120)])
    assert popup.quiet.text() == "Tomorrow, Sun: no good window" and not popup.quiet.isHidden()


def test_the_outlook_section_hides_when_there_is_nothing_to_list(cfg, qapp):
    popup, vm = view(cfg, [(DAY, 900)])
    assert vm.outlook == () and vm.quiet_days == ""
    assert popup.outlook_box.isHidden()


def test_refreshing_does_not_pile_up_outlook_rows(cfg, qapp):
    popup, vm = view(cfg, [(DAY + timedelta(days=i), 900) for i in range(3)])
    popup.update_view(vm, State(BatteryReading(45, NOW)), NOW.date(), cfg.away_rules)
    popup.update_view(vm, State(BatteryReading(45, NOW)), NOW.date(), cfg.away_rules)
    assert popup.outlook_grid.count() == 8


def test_battery_is_a_status_line_with_a_gauge(cfg, qapp):
    popup, _ = view(cfg, [(DAY, 900)], soc=62)
    assert popup.battery_status.text() == "Battery 62%" and popup.gauge.value() == 62
    assert not popup.gauge.isTextVisible()
    assert popup.tabs.tabText(UPDATE) == "Update"


def test_a_stale_battery_is_flagged_on_the_plan_page_and_the_update_tab(cfg, qapp):
    popup, _ = view(cfg, [(DAY, 900)], battery=BatteryReading(50, None))
    assert "please update" in popup.battery_status.text()
    assert popup.tabs.tabText(UPDATE) == "Update •"
    assert "Please enter it" in popup.battery.text()


def test_the_away_summary_and_edit_buttons_lead_to_the_update_page(cfg, qapp):
    from PySide6.QtWidgets import QPushButton

    popup, _ = view(cfg, [(DAY, 900)])
    assert popup.away_status.text() == "Away: Thu weekly"
    popup.show()
    assert popup.tabs.currentIndex() == PLAN
    next(b for b in popup.findChildren(QPushButton) if b.text() == "Manage").click()
    assert popup.tabs.currentIndex() == UPDATE
    popup.tabs.setCurrentIndex(PLAN)
    next(b for b in popup.findChildren(QPushButton) if b.text() == "Edit").click()
    assert popup.tabs.currentIndex() == UPDATE


def test_clicking_the_tray_always_opens_on_the_plan(cfg, qapp):
    from PySide6.QtCore import QRect

    popup, _ = view(cfg, [(DAY, 900)])
    popup.show_update()
    popup.show_near(QRect(100, 100, 20, 20))
    assert popup.tabs.currentIndex() == PLAN
    popup.hide()


def test_each_page_is_only_as_tall_as_its_content(cfg, qapp):
    popup, _ = view(cfg, [(DAY + timedelta(days=i), 900) for i in range(3)])
    popup.show()
    plan_height = popup.height()
    popup.tabs.setCurrentIndex(UPDATE)
    assert popup.height() != plan_height  # the popup follows the page instead of the tallest one
    page = popup.stack.currentWidget()
    assert popup.stack.height() >= page.sizeHint().height()
    popup.hide()


def test_footer_is_short_and_keeps_the_attribution(cfg, qapp):
    popup, _ = view(cfg, [(DAY, 900)])
    assert popup.footer.text() == "Forecast 5 min old · Weather data: Open-Meteo.com"
    assert "CC BY 4.0" in popup.footer.toolTip()


def test_there_is_a_close_button_but_no_bottom_close_button(cfg, qapp):
    from PySide6.QtWidgets import QPushButton

    popup, _ = view(cfg, [(DAY, 900)])
    texts = [b.text() for b in popup.findChildren(QPushButton)]
    assert "×" in texts and "Close" not in texts


def test_the_popup_has_no_horizontal_rules(cfg, qapp):
    from PySide6.QtWidgets import QFrame

    popup, _ = view(cfg, [(DAY, 900)])
    assert not [f for f in popup.findChildren(QFrame) if f.frameShape() == QFrame.Shape.HLine]


# ---- the away summary line ---------------------------------------------------


def summary(rules=(), periods=(), limit=3):
    return away_summary(tuple(rules), tuple(periods), NOW, TZ, limit)


def test_summary_with_nothing_planned():
    assert summary() == "No absences planned"


def test_summary_lists_weekly_rules_by_weekday_then_one_off_days():
    one = make_away(date(2026, 7, 9), True, time(), time(), TZ)
    assert summary([AwayRule(3, time(6), time(19))], [one]) == "Away: Thu weekly, Thu 9 Jul"


def test_summary_keeps_the_time_range_of_a_one_off():
    ranged = make_away(date(2026, 7, 5), False, time(10), time(16, 30), TZ)
    assert summary(periods=[ranged]) == "Away: Sun 5 Jul, 10:00-16:30"


def test_summary_does_not_repeat_a_weekday_and_ignores_past_days():
    rules = [AwayRule(3, time(6), time(12)), AwayRule(3, time(14), time(19))]
    past = make_away(date(2026, 7, 1), True, time(), time(), TZ)
    assert summary(rules, [past]) == "Away: Thu weekly"


def test_summary_is_cut_off_with_a_count():
    days = [make_away(date(2026, 7, d), True, time(), time(), TZ) for d in (4, 5, 6, 7, 8)]
    text = summary(periods=days, limit=3)
    assert text.endswith("+2 more") and text.count(",") == 2


# ---- the header: tabs on the left, the close button at the right edge ---------


def _right_edge_in_popup(widget, popup):
    return widget.mapTo(popup, widget.rect().topRight()).x()


def test_the_close_button_sits_at_the_right_edge_of_the_content(cfg, qapp):
    from PySide6.QtWidgets import QPushButton

    popup, _ = view(cfg, [(DAY, 900), (DAY + timedelta(days=1), 900)])
    popup.show()
    qapp.processEvents()
    close = next(b for b in popup.findChildren(QPushButton) if b.text() == "×")
    manage = next(b for b in popup.findChildren(QPushButton) if b.text() == "Manage")
    content_right = _right_edge_in_popup(manage, popup)  # a control that ends the content column
    assert abs(_right_edge_in_popup(close, popup) - content_right) <= 8, "the x is not aligned with the content"
    assert _right_edge_in_popup(close, popup) >= popup.width() - 24
    popup.hide()


def test_the_header_spans_the_full_width_so_its_line_reaches_the_edge(cfg, qapp):
    popup, _ = view(cfg, [(DAY, 900)])
    popup.show()
    qapp.processEvents()
    page = popup.stack.currentWidget()
    assert abs(popup.header.width() - page.width()) <= 2
    popup.hide()


# ---- closing: the x, Escape, the tray icon, or a click outside -----------------


def _focus(popup, active):
    """Pretend the window manager gave or took the focus."""
    from PySide6.QtCore import QEvent

    popup.isActiveWindow = lambda: active
    QApplication.sendEvent(popup, QEvent(QEvent.Type.ActivationChange))


def _wait(ms=350):
    from PySide6.QtTest import QTest

    QTest.qWait(ms)


def test_losing_the_focus_after_having_it_closes_the_popup(cfg, qapp):
    popup, _ = view(cfg, [(DAY, 900)])
    popup.show()
    _focus(popup, True)
    _focus(popup, False)  # a click somewhere else
    _wait()
    assert not popup.isVisible() and popup.auto_closed_at > 0


def test_a_popup_that_never_got_the_focus_is_not_closed_by_a_click_elsewhere(cfg, qapp):
    popup, _ = view(cfg, [(DAY, 900)])
    popup.show()  # on Wayland a new window may not be activated at once
    _focus(popup, False)
    _wait()
    assert popup.isVisible()
    popup.hide()


def test_getting_the_focus_back_quickly_keeps_it_open(cfg, qapp):
    popup, _ = view(cfg, [(DAY, 900)])
    popup.show()
    _focus(popup, True)
    _focus(popup, False)
    _focus(popup, True)  # e.g. one of our dropdowns closed again
    _wait()
    assert popup.isVisible()
    popup.hide()


def test_an_open_dropdown_of_our_own_does_not_close_the_popup(cfg, qapp, monkeypatch):
    popup, _ = view(cfg, [(DAY, 900)])
    popup.show()
    _focus(popup, True)
    monkeypatch.setattr(QApplication, "activePopupWidget", staticmethod(lambda: object()))
    _focus(popup, False)
    _wait()
    assert popup.isVisible()
    popup.hide()


def test_hiding_resets_the_have_had_focus_flag(cfg, qapp):
    popup, _ = view(cfg, [(DAY, 900)])
    popup.show()
    _focus(popup, True)
    popup.hide()
    popup.show()  # shown again, not yet focused
    _focus(popup, False)
    _wait()
    assert popup.isVisible()
    popup.hide()


def test_escape_and_the_x_still_close_it(cfg, qapp):
    from PySide6.QtCore import Qt
    from PySide6.QtTest import QTest
    from PySide6.QtWidgets import QPushButton

    popup, _ = view(cfg, [(DAY, 900)])
    popup.show()
    QTest.keyClick(popup, Qt.Key.Key_Escape)
    assert not popup.isVisible()
    popup.show()
    next(b for b in popup.findChildren(QPushButton) if b.text() == "×").click()
    assert not popup.isVisible()


def _tray(cfg, tmp_path, monkeypatch):
    from conftest import TrayApp

    monkeypatch.setenv("XDG_STATE_HOME", str(tmp_path))
    monkeypatch.setattr("forecast.service.ForecastService.get", lambda *a, **k: None)
    return TrayApp(cfg, tmp_path / "config.toml")


def test_the_click_that_closed_the_popup_does_not_reopen_it(cfg, tmp_path, monkeypatch, qapp):
    import time as clock

    from PySide6.QtWidgets import QSystemTrayIcon

    app = _tray(cfg, tmp_path, monkeypatch)
    trigger = QSystemTrayIcon.ActivationReason.Trigger
    app.popup.auto_closed_at = clock.monotonic()  # focus loss closed it a moment ago
    app._on_activated(trigger)
    assert not app.popup.isVisible()
    app.popup.auto_closed_at = clock.monotonic() - 5  # long ago: a fresh click opens it
    app._on_activated(trigger)
    assert app.popup.isVisible()
    app._on_activated(trigger)  # and a click while it is open closes it
    assert not app.popup.isVisible()


def test_the_popup_grows_for_the_taller_page_and_shrinks_back(cfg, qapp):
    popup, _ = view(cfg, [(DAY, 900)], soc=70)  # one sunny day: a short Plan page, a taller Update page
    popup.show()
    qapp.processEvents()
    plan_height = popup.height()
    popup.show_update()
    qapp.processEvents()
    update_height = popup.height()
    popup.tabs.setCurrentIndex(PLAN)
    qapp.processEvents()
    assert update_height > plan_height
    assert popup.height() == plan_height  # no stale minimum left behind by the taller page
    popup.hide()
