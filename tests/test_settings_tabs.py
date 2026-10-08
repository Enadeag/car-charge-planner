import os

os.environ.setdefault("QT_QPA_PLATFORM", "offscreen")

from dataclasses import replace
from datetime import date, time

import pytest
from PySide6.QtCore import QTime
from PySide6.QtWidgets import QApplication, QLabel

from car_charge_planner.settings.tabs import AwayRulesTab
from car_charge_planner.settings.window import SettingsWindow
from car_charge_planner.planner.model import AwayRule, Config, Setup, load_config

from conftest import at, example_config


@pytest.fixture
def qapp():
    return QApplication.instance() or QApplication([])


class Harness:
    def __init__(self, tmp_path, cfg=None):
        self.file = tmp_path / "config.toml"
        self.saved = []
        self.asked = []
        self.window = SettingsWindow(cfg or example_config(), self.file, self.saved.append)
        self.window.confirm_discard = lambda: (self.asked.append(1), True)[1]
        self.window.show()

    def tab(self, title):
        return next(t for t in self.window.all_tabs if t.title == title)

    def box(self, title, key):
        return self.tab(title).inputs[key]

    def show(self, title):
        self.window.tabs.setCurrentWidget(self.tab(title))


@pytest.fixture
def h(tmp_path, qapp):
    return Harness(tmp_path)


def texts(tab):
    return [label.text() for label in tab.findChildren(QLabel)]


# ---- the five tabs ---------------------------------------------------------


def test_the_window_has_the_seven_tabs_in_order(h):
    assert [h.window.tabs.tabText(i) for i in range(h.window.tabs.count())] == [
        "Solar and location",
        "House and car",
        "Charger",
        "Planning and notifications",
        "Forecast",
        "Interface",
        "Away rules",
    ]


# ---- ticket 04: house and car, charger ------------------------------------


def test_house_and_car_shows_values_with_units_and_typical_value_tags(tmp_path, qapp):
    h = Harness(tmp_path, replace(example_config(), setup=Setup(("car.usable_battery_kwh", "car.consumption_kwh_per_100km"))))
    t = "House and car"
    assert h.box(t, "house.baseload_kw").value() == 0.6
    assert h.box(t, "car.usable_battery_kwh").value() == 70
    assert h.box(t, "car.consumption_kwh_per_100km").value() == 18
    assert h.box(t, "car.km_per_day").value() == 85.7
    assert h.box(t, "car.minimum_pct").value() == 30 and h.box(t, "car.target_pct").value() == 80
    assert h.box(t, "car.consumption_kwh_per_100km").suffix() == " kWh/100 km"
    tagged = texts(h.tab(t))
    assert sum("Typical value." in x for x in tagged) == 2  # battery size and consumption, nothing else


def test_charger_shows_values_with_units_and_a_typical_value_tag(tmp_path, qapp):
    h = Harness(tmp_path, replace(example_config(), setup=Setup(("charger.efficiency",))))
    t = "Charger"
    assert h.box(t, "charger.min_amps").value() == 6 and h.box(t, "charger.max_amps").value() == 32
    assert h.box(t, "charger.volts").value() == 230
    assert h.box(t, "charger.efficiency").value() == 90.0  # fraction stored, percent shown
    assert h.box(t, "charger.min_amps").suffix() == " A"
    assert sum("Typical value." in x for x in texts(h.tab(t))) == 1


def test_editing_house_car_and_charger_saves_everything(h):
    h.box("House and car", "house.baseload_kw").setValue(0.8)
    h.box("House and car", "car.minimum_pct").setValue(25)
    h.box("Charger", "charger.max_amps").setValue(25)
    h.box("Charger", "charger.efficiency").setValue(88.5)
    h.window.save_button.click()
    saved = load_config(h.file)
    assert saved.house.baseload_kw == 0.8 and saved.car.minimum_pct == 25
    assert saved.charger.max_amps == 25 and isinstance(saved.charger.max_amps, int)
    assert saved.charger.efficiency == 0.885


def test_untouched_driving_per_day_keeps_its_exact_value(h):
    h.box("Charger", "charger.max_amps").setValue(20)  # edit something else
    h.window.save_button.click()
    assert load_config(h.file).car.km_per_day == 600 / 7  # not the 85.7 shown


def test_both_cross_field_problems_are_listed_together(h):
    h.box("House and car", "car.minimum_pct").setValue(90)  # above the 80 target
    h.box("Charger", "charger.min_amps").setValue(40)  # above the 32 maximum
    h.window.save_button.click()
    message = h.window.errors.text()
    assert not h.window.errors.isHidden() and h.window.isVisible()
    assert "minimum_pct" in message and "min_amps" in message
    assert message.count("•") == 2
    assert not h.file.exists() and h.saved == []


def test_reset_tab_works_on_house_car_and_on_charger(tmp_path, qapp):
    custom = replace(
        example_config(),
        car=replace(Config().car, usable_battery_kwh=75, target_pct=90),
        charger=replace(Config().charger, volts=240, max_amps=25),
    )
    h = Harness(tmp_path, custom)
    h.show("House and car")
    h.window.reset_button.click()
    assert h.box("House and car", "car.usable_battery_kwh").value() == Config().car.usable_battery_kwh
    assert h.box("Charger", "charger.volts").value() == 240  # other tab untouched
    h.show("Charger")
    h.window.reset_button.click()
    assert h.box("Charger", "charger.volts").value() == 230 and h.box("Charger", "charger.max_amps").value() == Config().charger.max_amps
    assert not h.file.exists()  # reset alone saves nothing


# ---- ticket 05: planning and notifications ---------------------------------


def test_planning_tab_shows_values_and_units(h):
    t = "Planning and notifications"
    assert h.box(t, "planning.margin").value() == 85  # percent
    assert h.box(t, "planning.import_tolerance_kwh").value() == 0.2
    assert h.box(t, "planning.min_window_minutes").value() == 45
    assert h.box(t, "notify.lead_minutes").value() == 15
    assert h.box(t, "planning.margin").suffix() == " %" and h.box(t, "notify.lead_minutes").suffix() == " minutes"


def test_planning_edit_saves_as_stored_types(h):
    t = "Planning and notifications"
    h.box(t, "planning.margin").setValue(70)
    h.box(t, "notify.lead_minutes").setValue(30)
    h.window.save_button.click()
    saved = load_config(h.file)
    assert saved.planning.margin == 0.7 and saved.notify.lead_minutes == 30
    assert isinstance(saved.notify.lead_minutes, int)


def test_planning_reset_tab(tmp_path, qapp):
    h = Harness(tmp_path, replace(example_config(), notify=replace(Config().notify, lead_minutes=45)))
    h.show("Planning and notifications")
    h.window.reset_button.click()
    assert h.box("Planning and notifications", "notify.lead_minutes").value() == 15


def test_a_saved_lead_time_changes_when_the_next_notification_is_due(tmp_path, monkeypatch, qapp):
    from car_charge_planner.forecast.service import ForecastResult
    from conftest import TrayApp

    from conftest import forecast_from

    day = date(2026, 7, 3)
    monkeypatch.setenv("XDG_STATE_HOME", str(tmp_path))
    monkeypatch.setattr("car_charge_planner.forecast.service.ForecastService.get", lambda self, cfg, now, force=False: None)
    app = TrayApp(example_config(), tmp_path / "config.toml")
    app.result = ForecastResult(forecast_from([(day, 900)]), at(day, 6), True, False)
    from conftest import FakeNotifier

    app.notifier = FakeNotifier()

    class Frozen:
        @staticmethod
        def now(tz=None):
            return at(day, 8, 30)  # the window starts at 09:00

    monkeypatch.setattr("car_charge_planner.controller.datetime", Frozen)
    app.rebuild()
    assert app.notifier.sent == []  # 30 minutes ahead, lead time is 15
    app.cfg = replace(app.cfg, notify=replace(app.cfg.notify, lead_minutes=30))
    app.rebuild()
    titles = [title for title, _ in app.notifier.sent]
    assert len(titles) == 1 and titles[0].startswith("Charge window 09:00")


# ---- ticket 06: away rules --------------------------------------------------


def rows(h):
    return h.tab("Away rules").rows


def test_existing_rule_is_shown(h):
    (row,) = rows(h)
    assert row.weekday.currentText() == "Thursday"
    assert row.start.time() == QTime(6, 0) and row.end.time() == QTime(19, 0)
    assert h.tab("Away rules").empty_note.isHidden()


def test_add_edit_and_save_a_rule(h):
    tab = h.tab("Away rules")
    tab.add_button.click()
    assert len(rows(h)) == 2
    new = rows(h)[1]
    new.weekday.setCurrentIndex(1)  # Tuesday
    new.start.setTime(QTime(7, 30))
    new.end.setTime(QTime(12, 45))
    h.window.save_button.click()
    assert load_config(h.file).away_rules == (
        AwayRule(3, time(6, 0), time(19, 0)),
        AwayRule(1, time(7, 30), time(12, 45)),
    )


def test_remove_a_rule_and_save(h):
    h.tab("Away rules").add_button.click()
    rows(h)[0].remove_button.click()
    assert len(rows(h)) == 1
    h.window.save_button.click()
    saved = load_config(h.file).away_rules
    assert len(saved) == 1 and saved[0] != AwayRule(3, time(6, 0), time(19, 0))


def test_an_empty_list_round_trips_and_does_not_bring_back_the_default(h):
    rows(h)[0].remove_button.click()
    assert rows(h) == [] and not h.tab("Away rules").empty_note.isHidden()
    h.window.save_button.click()
    assert load_config(h.file).away_rules == ()
    assert "recurring = []" in h.file.read_text()


def test_a_rule_that_ends_before_it_starts_is_reported_and_nothing_is_written(h):
    row = rows(h)[0]
    row.start.setTime(QTime(20, 0))
    row.end.setTime(QTime(6, 0))
    h.window.save_button.click()
    assert "overnight" in h.window.errors.text() and h.window.isVisible()
    assert not h.file.exists()


def test_reset_empties_the_rules_because_there_is_no_default_rule(tmp_path, qapp):
    cfg = replace(example_config(), away_rules=(AwayRule(0, time(8, 0), time(9, 0)), AwayRule(5, time(1, 0), time(2, 0))))
    h = Harness(tmp_path, cfg)
    assert len(rows(h)) == 2
    h.show("Away rules")
    h.window.reset_button.click()
    assert rows(h) == [] and not h.tab("Away rules").empty_note.isHidden()  # the car is treated as home all week
    assert not h.file.exists()  # reset alone saves nothing


def test_rules_count_as_changes_for_the_discard_question(h):
    assert not h.window.is_dirty()
    rows(h)[0].remove_button.click()
    assert h.window.is_dirty()
    h.window.cancel_button.click()
    assert h.asked == [1] and not h.window.isVisible()


def test_putting_a_rule_back_is_not_a_change(h):
    rows(h)[0].end.setTime(QTime(18, 0))
    assert h.window.is_dirty()
    rows(h)[0].end.setTime(QTime(19, 0))
    assert not h.window.is_dirty()


def test_rule_order_is_kept(tmp_path, qapp):
    cfg = replace(example_config(), away_rules=(AwayRule(4, time(8, 0), time(9, 0)), AwayRule(1, time(8, 0), time(9, 0))))
    h = Harness(tmp_path, cfg)
    assert [r.weekday.currentText() for r in rows(h)] == ["Friday", "Tuesday"]
    rows(h)[0].end.setTime(QTime(10, 0))
    h.window.save_button.click()
    assert [r.weekday for r in load_config(h.file).away_rules] == [4, 1]


def test_no_hint_text_is_clipped_on_any_tab(tmp_path, qapp):
    from PySide6.QtWidgets import QLabel as Label

    window = SettingsWindow(example_config(), tmp_path / "c.toml", lambda c: None)
    window.show_centred()  # the same path the tray uses
    for index in range(window.tabs.count()):
        window.tabs.setCurrentIndex(index)
        for _ in range(3):
            qapp.processEvents()
        tab = window.tabs.widget(index)
        for label in tab.findChildren(Label):
            if label.wordWrap() and label.text():
                needed = label.heightForWidth(label.width())
                assert label.height() >= needed, f"{window.tabs.tabText(index)}: {label.text()[:40]!r} is clipped ({label.height()} < {needed})"
    window.close()
