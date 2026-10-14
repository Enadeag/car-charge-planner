"""The popup: a calm Plan page and an Update page for inputs."""

import os

os.environ.setdefault("QT_QPA_PLATFORM", "offscreen")

from datetime import date, time, timedelta

import pytest
from PySide6.QtWidgets import QApplication, QLabel, QPushButton

from car_charge_planner.away_input import away_summary, make_away
from car_charge_planner.planner.advice import grid_advice
from car_charge_planner.planner.model import AwayRule
from car_charge_planner.planner.projection import plan
from car_charge_planner.popup import PLAN, UPDATE, Popup
from car_charge_planner.storage.state import BatteryReading, State
from car_charge_planner.view_model import ForecastSource, build
from conftest import TZ, at, forecast_from

DAY = date(2026, 7, 3)
NOW = at(DAY, 7)


def view(cfg, days, soc=45, battery=None, state=None):
    forecast = forecast_from(list(days))
    plans = plan(forecast, cfg.system, cfg.planning, soc, NOW)
    advice = grid_advice(plans, forecast, cfg.system, cfg.planning, None, NOW)
    battery = battery or BatteryReading(soc, NOW)
    vm = build(plans, NOW, cfg, battery, timedelta(minutes=5), False, advice, ForecastSource("Weather data: Open-Meteo.com (CC BY 4.0)"))
    popup = Popup(TZ)
    popup.update_view(vm, state or State(battery), NOW.date(), cfg.system.away_rules)
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
    assert len(popup.advice_title.text()) < 60


def test_no_advice_block_when_there_is_nothing_to_warn_about(cfg, qapp):
    popup, _ = view(cfg, [(DAY, 900)], soc=70)
    assert popup.advice_box.isHidden()


def test_outlook_is_a_grid_of_columns_and_does_not_repeat_the_headline(cfg, qapp):
    popup, vm = view(cfg, [(DAY + timedelta(days=i), 900) for i in range(3)])
    cells = [popup.outlook_grid.itemAt(i).widget().text() for i in range(popup.outlook_grid.count())]
    assert len(vm.outlook) == 2 and len(cells) == 8
    assert cells[0] == "Tomorrow" and cells[3].endswith("kWh")
    assert "Today" not in cells


def test_the_outlook_hides_when_there_is_nothing_to_list_and_lists_quiet_days(cfg, qapp):
    popup, vm = view(cfg, [(DAY, 900)])
    assert vm.outlook == () and vm.quiet_days == ""
    assert popup.outlook_box.isHidden() and popup.quiet.isHidden()
    popup, vm = view(cfg, [(DAY, 900), (DAY + timedelta(days=1), 120)])
    assert not popup.outlook_box.isHidden() and not popup.quiet.isHidden()


def test_refreshing_does_not_pile_up_outlook_rows(cfg, qapp):
    popup, vm = view(cfg, [(DAY + timedelta(days=i), 900) for i in range(3)])
    popup.update_view(vm, State(BatteryReading(45, NOW)), NOW.date(), cfg.system.away_rules)
    popup.update_view(vm, State(BatteryReading(45, NOW)), NOW.date(), cfg.system.away_rules)
    assert popup.outlook_grid.count() == 8


def test_a_stale_battery_is_flagged_on_the_plan_page_and_the_update_tab(cfg, qapp):
    popup, _ = view(cfg, [(DAY, 900)], battery=BatteryReading(50, None))
    assert "please update" in popup.battery_status.text()
    assert popup.tabs.tabText(UPDATE) == "Update •"
    assert "Please enter it" in popup.battery.text()


def test_the_away_summary_and_edit_buttons_lead_to_the_update_page(cfg, qapp):
    from PySide6.QtWidgets import QPushButton

    popup, _ = view(cfg, [(DAY, 900)])
    assert "Thu" in popup.away_status.text()
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


def _one_off(day, whole_day=True, start=time(), end=time()):
    return make_away(day, whole_day, start, end, TZ)


@pytest.mark.parametrize(
    "rules, periods, limit, expected",
    [
        ((), (), 3, "No absences planned"),
        ((AwayRule(3, time(6), time(19)),), (_one_off(date(2026, 7, 9)),), 3, "Away: Thu weekly, Thu 9 Jul"),
        ((), (_one_off(date(2026, 7, 5), False, time(10), time(16, 30)),), 3, "Away: Sun 5 Jul, 10:00-16:30"),
        ((AwayRule(3, time(6), time(12)), AwayRule(3, time(14), time(19))), (_one_off(date(2026, 7, 1)),), 3, "Away: Thu weekly"),
        ((), tuple(_one_off(date(2026, 7, d)) for d in (4, 5, 6, 7, 8)), 3, "Away: Sat 4 Jul, Sun 5 Jul, Mon 6 Jul +2 more"),
    ],
    ids=["nothing planned", "weekly then one-off", "time range kept", "no repeated weekday, past day ignored", "cut off with a count"],
)
def test_the_away_summary(rules, periods, limit, expected):
    assert away_summary(rules, periods, NOW, TZ, limit) == expected


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
    _focus(popup, False)
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
    _focus(popup, True)
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
    popup.show()
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


def _tray(cfg, tmp_path, monkeypatch, days=((DAY, 900),)):
    """A tray app at NOW, with a forecast for `days` (peak irradiance per day)."""
    from car_charge_planner.engine import run_here
    from car_charge_planner.forecast.service import ForecastResult
    from conftest import FakeClock, FixedForecast, TrayApp

    monkeypatch.setenv("XDG_STATE_HOME", str(tmp_path))
    forecast = FixedForecast(ForecastResult(forecast_from(list(days)), at(DAY, 6), True, False))
    app = TrayApp(cfg, tmp_path / "config.toml", forecasts=forecast, clock=FakeClock(NOW), run_in_background=run_here)
    app.refresh()
    return app


def test_the_click_that_closed_the_popup_does_not_reopen_it(cfg, tmp_path, monkeypatch, qapp):
    import time as clock

    from PySide6.QtWidgets import QSystemTrayIcon

    app = _tray(cfg, tmp_path, monkeypatch)
    trigger = QSystemTrayIcon.ActivationReason.Trigger
    app.popup.auto_closed_at = clock.monotonic()
    app._on_activated(trigger)
    assert not app.popup.isVisible()
    app.popup.auto_closed_at = clock.monotonic() - 5  # long ago: a fresh click opens it
    app._on_activated(trigger)
    assert app.popup.isVisible()
    app._on_activated(trigger)
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
    assert popup.height() == plan_height
    popup.hide()


def test_the_footer_and_its_tooltip_follow_the_view(cfg, qapp):
    from car_charge_planner.view_model import ForecastSource

    popup, vm = view(cfg, [(DAY, 900)])
    assert popup.footer.text() == vm.footer and popup.footer.toolTip() == vm.footer_tooltip
    forecast = forecast_from([(DAY, 900)])
    plans = plan(forecast, cfg.system, cfg.planning, 45, NOW)
    source = ForecastSource("Solar forecast: Forecast.Solar", "Forecast.Solar", from_fallback=True, primary_name="Open-Meteo")
    popup.update_view(build(plans, NOW, cfg, BatteryReading(45, NOW), timedelta(minutes=5), False, None, source), State(BatteryReading(45, NOW)), NOW.date(), cfg.system.away_rules)
    assert "from Forecast.Solar, primary unavailable" in popup.footer.text()
    assert "Open-Meteo has no forecast" in popup.footer.toolTip()
    popup.update_view(needs_setup_vm(), State(BatteryReading(45, NOW)), NOW.date(), cfg.system.away_rules)
    assert popup.footer.text() == "" and popup.footer.toolTip() == ""


def needs_setup_vm():
    from car_charge_planner.view_model import needs_setup

    return needs_setup(BatteryReading(45, NOW), NOW)


def button(popup, text):
    from PySide6.QtWidgets import QPushButton

    return next(b for b in popup.findChildren(QPushButton) if b.text() == text)


def test_the_set_battery_button_emits_the_entered_value(cfg, qapp):
    popup, _ = view(cfg, [(DAY, 900)])
    seen = []
    popup.battery_submitted.connect(seen.append)
    popup.battery_input.setValue(42)
    button(popup, "Set battery").click()
    assert seen == [42.0]


def test_an_absence_is_added_for_a_whole_day_or_a_time_range(cfg, qapp):
    from PySide6.QtCore import QTime

    from car_charge_planner.away_input import describe

    popup, _ = view(cfg, [(DAY, 900), (DAY + timedelta(days=1), 900)])
    seen = []
    popup.away_added.connect(seen.append)
    popup.away_day.setCurrentIndex(1)
    button(popup, "Add").click()
    popup.away_kind.setCurrentIndex(1)
    popup.away_from.setTime(QTime(10, 0))
    popup.away_to.setTime(QTime(16, 30))
    button(popup, "Add").click()
    assert [describe(period, TZ) for period in seen] == ["Sat 4 Jul, all day", "Sat 4 Jul, 10:00-16:30"]


def test_a_range_that_ends_before_it_starts_is_refused(cfg, qapp):
    from PySide6.QtCore import QTime

    popup, _ = view(cfg, [(DAY, 900)])
    seen = []
    popup.away_added.connect(seen.append)
    popup.away_kind.setCurrentIndex(1)
    popup.away_from.setTime(QTime(16, 0))
    popup.away_to.setTime(QTime(10, 0))
    button(popup, "Add").click()
    assert seen == [] and not popup.away_error.isHidden()


def test_a_planned_absence_can_be_removed_and_the_stored_battery_is_shown(cfg, qapp):
    period = make_away(DAY + timedelta(days=1), True, time(), time(), TZ)
    popup, _ = view(cfg, [(DAY, 900)], state=State(BatteryReading(61, at(DAY, 6)), (period,)))
    removed = []
    popup.away_removed.connect(removed.append)
    button(popup, "Remove").click()
    assert removed == [period]
    assert popup.battery_input.value() == 61


def test_weekly_rules_are_listed_read_only_next_to_the_removable_one_off_days(cfg, qapp):
    one_off = make_away(DAY + timedelta(days=1), True, time(), time(), TZ)
    popup, _ = view(cfg, [(DAY, 900)], state=State(BatteryReading(50, NOW), (one_off,)))
    texts = labels(popup)
    assert any("Thursday" in t and "06:00-19:00" in t for t in texts) and any("Sat 4 Jul" in t for t in texts)
    assert len([b for b in popup.findChildren(QPushButton) if b.text() == "Remove"]) == 1


def test_the_popup_says_so_when_there_are_no_absences_at_all(cfg, qapp):
    popup, vm = view(cfg, [(DAY, 900)])
    popup.update_view(vm, State(), DAY, ())
    assert any("No absences planned" in t for t in labels(popup))


def test_the_settings_button_asks_for_the_away_rules_tab(cfg, qapp):
    popup, _ = view(cfg, [(DAY, 900)])
    seen = []
    popup.settings_requested.connect(seen.append)
    button(popup, "Edit weekly rules in Settings...").click()
    assert seen == ["away"]


def test_the_tray_opens_settings_on_the_away_rules_tab_and_hides_the_popup(cfg, tmp_path, monkeypatch, qapp):
    app = _tray(cfg, tmp_path, monkeypatch)
    app.popup.show()
    app.popup.settings_requested.emit("away")
    window = app.shell.windows.settings_window
    assert window.isVisible() and window.tabs.currentWidget() is window.away_tab
    assert not app.popup.isVisible()


def test_a_rule_saved_in_settings_shows_up_in_the_popup(cfg, tmp_path, monkeypatch, qapp):
    from PySide6.QtCore import QTime

    app = _tray(cfg, tmp_path, monkeypatch)
    assert any("Thursday" in t for t in labels(app.popup))
    app.shell.windows.open_settings("away")
    row = app.shell.windows.settings_window.away_tab.rows[0]
    row.weekday.setCurrentIndex(0)
    row.start.setTime(QTime(7, 0))
    row.end.setTime(QTime(12, 30))
    app.shell.windows.settings_window.save_button.click()
    texts = labels(app.popup)
    assert any("Monday" in t and "07:00-12:30" in t for t in texts) and not any("Thursday" in t for t in texts)


def test_the_battery_and_absences_entered_in_the_tray_are_stored_and_replan(cfg, tmp_path, monkeypatch, qapp):
    from car_charge_planner.storage.state import load_state

    app = _tray(cfg, tmp_path, monkeypatch, [(DAY, 900), (DAY + timedelta(days=1), 900)])
    app.set_battery(33)
    assert load_state().battery.pct == 33 and app.vm.projection.startswith("Battery 33%")
    app.add_away_period(make_away(DAY, True, time(), time(), TZ))
    assert len(load_state().away) == 1 and "Today" in app.vm.quiet_days
    app.remove_away_period(load_state().away[0])
    assert load_state().away == () and "Today" not in app.vm.quiet_days
