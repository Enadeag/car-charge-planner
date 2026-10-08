import os

os.environ.setdefault("QT_QPA_PLATFORM", "offscreen")

from dataclasses import replace

import pytest
from PySide6.QtWidgets import QApplication, QDialog

from car_charge_planner.planner.model import Config, Setup, first_run_config, load_config
from car_charge_planner.settings.window import SettingsWindow
from conftest import example_config


@pytest.fixture
def qapp():
    return QApplication.instance() or QApplication([])


class Harness:
    def __init__(self, tmp_path, cfg=None):
        self.file = tmp_path / "config.toml"
        self.saved = []
        self.asked = []
        self.answer = True
        self.window = SettingsWindow(cfg or example_config(), self.file, self.saved.append)
        self.window.confirm_discard = lambda: (self.asked.append(1), self.answer)[1]
        self.window.show()

    def box(self, key):
        return self.window.field_tabs[0].inputs[key]


@pytest.fixture
def h(tmp_path, qapp):
    return Harness(tmp_path)


def test_shows_the_stored_values(h):
    assert h.box("panels.kwp").value() == 4.8
    assert h.box("panels.tilt_deg").value() == 35
    assert h.box("panels.azimuth_deg").value() == -45
    assert h.box("panels.system_losses").value() == 14.0  # a fraction stored, a percent shown
    assert h.box("latitude").value() == 52.37 and h.box("longitude").value() == 4.9
    assert h.box("timezone").currentText() == "Europe/Amsterdam"


def test_units_and_typical_value_tags_are_visible(tmp_path, qapp):
    typical = replace(example_config(), setup=Setup(("panels.kwp", "panels.tilt_deg")))
    h = Harness(tmp_path, typical)
    assert h.box("panels.kwp").suffix() == " kWp" and h.box("panels.system_losses").suffix() == " %"
    texts = [label.text() for label in h.window.field_tabs[0].findChildren(__import__("PySide6.QtWidgets", fromlist=["QLabel"]).QLabel)]
    assert any("Typical value." in t and "Total peak power" in t for t in texts)
    assert any("Typical value." in t and "flat" in t for t in texts)
    assert not any("Typical value." in t and "South-east is -45" in t for t in texts)  # direction was not left to a typical value


def test_save_writes_the_file_applies_and_keeps_the_window_open(h):
    h.box("panels.kwp").setValue(5.2)
    h.window.save_button.click()
    assert load_config(h.file).panels.kwp == 5.2
    assert [c.panels.kwp for c in h.saved] == [5.2]
    assert h.window.isVisible()


def test_percent_is_stored_as_a_fraction(h):
    h.box("panels.system_losses").setValue(12.5)
    h.window.save_button.click()
    assert load_config(h.file).panels.system_losses == 0.125


def test_untouched_fields_keep_their_exact_values(tmp_path, qapp):
    odd = replace(example_config(), latitude=52.123456789, panels=replace(Config().panels, system_losses=0.1234567))
    h = Harness(tmp_path, odd)
    h.box("panels.kwp").setValue(6.0)  # edit one field only
    h.window.save_button.click()
    saved = load_config(h.file)
    assert saved.latitude == 52.123456789  # the box only shows 4 decimals
    assert saved.panels.system_losses == 0.1234567
    assert saved.panels.tilt_deg == 35 and isinstance(saved.panels.tilt_deg, int)


def test_a_changed_field_is_stored_as_shown(h):
    h.box("panels.tilt_deg").setValue(28.5)
    h.window.save_button.click()
    assert load_config(h.file).panels.tilt_deg == 28.5


def test_invalid_value_lists_the_problem_and_writes_nothing(h):
    h.box("timezone").setCurrentText("Mars/Base")
    h.window.save_button.click()
    assert h.window.isVisible() and not h.window.errors.isHidden()
    assert "Mars/Base" in h.window.errors.text()
    assert not h.file.exists() and h.saved == []


def test_a_failed_write_is_reported_and_keeps_the_window_open(h, monkeypatch):
    def boom(cfg, path):
        raise OSError("disk full")

    monkeypatch.setattr("car_charge_planner.settings.window.write_config", boom)
    h.box("panels.kwp").setValue(5.5)
    h.window.save_button.click()
    assert h.window.isVisible() and "disk full" in h.window.errors.text() and h.saved == []


def test_cancel_without_changes_closes_without_asking(h):
    h.window.cancel_button.click()
    assert h.asked == [] and not h.window.isVisible()
    assert not h.file.exists()


def test_cancel_with_changes_asks_and_can_be_declined(h):
    h.box("panels.kwp").setValue(7)
    h.answer = False
    h.window.cancel_button.click()
    assert h.asked == [1] and h.window.isVisible()
    assert h.box("panels.kwp").value() == 7  # the edit is still there


def test_cancel_with_changes_discards_when_confirmed(h):
    h.box("panels.kwp").setValue(7)
    h.window.cancel_button.click()
    assert h.asked == [1] and not h.window.isVisible()
    assert not h.file.exists() and h.saved == []


def test_close_button_and_escape_behave_like_cancel(h, qapp):
    from PySide6.QtCore import Qt
    from PySide6.QtTest import QTest

    h.box("panels.kwp").setValue(7)
    h.answer = False
    h.window.close()
    assert h.asked == [1] and h.window.isVisible()
    QTest.keyClick(h.window, Qt.Key.Key_Escape)
    assert len(h.asked) == 2 and h.window.isVisible()
    h.answer = True
    h.window.close()
    assert not h.window.isVisible()


def test_editing_back_to_the_stored_value_is_not_a_change(h):
    h.box("panels.kwp").setValue(7)
    h.box("panels.kwp").setValue(4.8)
    assert not h.window.is_dirty()
    h.window.cancel_button.click()
    assert h.asked == []


def test_reset_tab_restores_defaults_without_saving(tmp_path, qapp):
    base = example_config()
    custom = replace(base, panels=replace(base.panels, kwp=9.0, tilt_deg=10), latitude=51.5)
    h = Harness(tmp_path, custom)
    assert h.box("panels.kwp").value() == 9.0
    h.window.reset_button.click()
    assert h.box("panels.kwp").value() == Config().panels.kwp and h.box("panels.tilt_deg").value() == 35
    assert h.box("latitude").value() == 0.0 and h.box("longitude").value() == 0.0  # the location is not set
    assert not h.file.exists() and h.saved == []  # nothing saved yet
    assert h.window.is_dirty()  # it differs from what is stored
    h.box("latitude").setValue(52.37)  # a plan needs a location, so saving asks for one
    h.box("longitude").setValue(4.9)
    h.window.save_button.click()
    solar_keys = ("panels.kwp", "panels.tilt_deg", "panels.azimuth_deg", "panels.system_losses")  # typical again after a reset
    expected = replace(base, panels=Config().panels, timezone=Config().timezone, latitude=52.37, longitude=4.9, setup=Setup(solar_keys))  # the other tabs were untouched
    assert load_config(h.file) == expected


def test_reset_then_cancel_keeps_the_stored_config(tmp_path, qapp):
    custom = replace(example_config(), panels=replace(Config().panels, kwp=9.0))
    h = Harness(tmp_path, custom)
    h.window.reset_button.click()
    h.window.cancel_button.click()
    assert h.asked == [1] and h.window.stored.panels.kwp == 9.0 and not h.file.exists()


# ---- the split save button ---------------------------------------------------


def choose(h, action):
    action.trigger()  # what picking an entry in the dropdown does


def test_the_button_opens_in_plain_save_mode(h):
    assert h.window.save_button.text() == "Save" and h.window.closes_after_save is False
    assert h.window.save_action.isChecked() and not h.window.save_close_action.isChecked()


def test_the_dropdown_offers_save_and_save_and_close(h):
    menu = h.window.save_button.menu()
    assert [a.text() for a in menu.actions()] == ["Save", "Save and close"]
    assert all(a.isCheckable() for a in menu.actions())
    assert h.window.save_button.popupMode().name == "MenuButtonPopup"


def test_choosing_save_and_close_only_switches_the_mode(h):
    h.box("panels.kwp").setValue(5.2)
    choose(h, h.window.save_close_action)
    assert h.window.save_button.text() == "Save and close"
    assert h.window.save_close_action.isChecked() and not h.window.save_action.isChecked()
    assert not h.file.exists() and h.saved == [] and h.window.isVisible()  # nothing saved yet


def test_save_and_close_saves_then_closes(h):
    h.box("panels.kwp").setValue(5.2)
    choose(h, h.window.save_close_action)
    h.window.save_button.click()
    assert load_config(h.file).panels.kwp == 5.2
    assert not h.window.isVisible() and h.window.result() == QDialog.DialogCode.Accepted


def test_switching_back_to_save_keeps_the_window_open_again(h):
    choose(h, h.window.save_close_action)
    choose(h, h.window.save_action)
    h.box("panels.kwp").setValue(5.2)
    h.window.save_button.click()
    assert h.window.isVisible() and h.window.save_button.text() == "Save"


def test_a_new_window_starts_in_save_mode_again(tmp_path, qapp):
    first = Harness(tmp_path)
    choose(first, first.window.save_close_action)
    second = Harness(tmp_path)
    assert second.window.save_button.text() == "Save" and second.window.closes_after_save is False


def test_save_and_close_stays_open_when_a_value_is_invalid(h):
    choose(h, h.window.save_close_action)
    h.box("timezone").setCurrentText("Mars/Base")
    h.window.save_button.click()
    assert h.window.isVisible() and not h.window.errors.isHidden() and not h.file.exists()


def test_after_saving_with_the_window_open_nothing_counts_as_changed(h):
    h.box("panels.kwp").setValue(5.2)
    assert h.window.is_dirty()
    h.window.save_button.click()
    assert not h.window.is_dirty()
    h.window.cancel_button.click()  # nothing to discard: no question, window closes
    assert h.asked == [] and not h.window.isVisible()


def test_editing_after_a_save_makes_it_dirty_again_and_cancel_asks(h):
    h.box("panels.kwp").setValue(5.2)
    h.window.save_button.click()
    h.box("panels.kwp").setValue(6.0)
    assert h.window.is_dirty()
    h.window.cancel_button.click()
    assert h.asked == [1]
    assert load_config(h.file).panels.kwp == 5.2  # the unsaved 6.0 never reached the file


def test_saving_twice_keeps_both_edits_and_exact_values(h):
    h.box("panels.kwp").setValue(5.2)
    h.window.save_button.click()
    h.window.field_tabs[1].inputs["car.minimum_pct"].setValue(25)
    h.window.save_button.click()
    saved = load_config(h.file)
    assert saved.panels.kwp == 5.2 and saved.car.minimum_pct == 25
    assert saved.car.km_per_day == 600 / 7  # still exact after two saves


def test_away_rules_survive_a_save_with_the_window_open(h):
    h.window.away_tab.add_button.click()
    rows = h.window.away_tab.rows
    rows[1].weekday.setCurrentIndex(2)
    h.window.save_button.click()
    after = h.window.away_tab.rows
    assert [r.weekday.currentIndex() for r in after] == [3, 2] and h.window.isVisible()
    assert not h.window.is_dirty()


def test_enter_runs_the_current_mode(h):
    from PySide6.QtCore import Qt
    from PySide6.QtTest import QTest

    h.box("panels.kwp").setValue(5.2)
    QTest.keyClick(h.window, Qt.Key.Key_Return)
    assert load_config(h.file).panels.kwp == 5.2 and h.window.isVisible()  # Save mode: stays open
    choose(h, h.window.save_close_action)
    h.box("panels.kwp").setValue(5.4)
    QTest.keyClick(h.window, Qt.Key.Key_Return)
    assert load_config(h.file).panels.kwp == 5.4 and not h.window.isVisible()


def test_enter_does_not_press_reset_or_cancel(h):
    from PySide6.QtCore import Qt
    from PySide6.QtTest import QTest

    h.box("panels.kwp").setValue(5.2)
    assert not h.window.reset_button.autoDefault() and not h.window.cancel_button.autoDefault()
    h.window.reset_button.setFocus()
    QTest.keyClick(h.window.reset_button, Qt.Key.Key_Return)
    assert h.window.isVisible() and h.asked == []
    assert load_config(h.file).panels.kwp == 5.2  # it saved; it did not reset or cancel


# ---- Close / Cancel label ------------------------------------------------------


def test_the_button_reads_close_when_nothing_is_unsaved(h):
    assert h.window.cancel_button.text() == "Close"


def test_it_reads_cancel_once_something_is_edited(h):
    h.box("panels.kwp").setValue(5.2)
    assert h.window.cancel_button.text() == "Cancel"
    assert "Discard" in h.window.cancel_button.toolTip()


def test_it_goes_back_to_close_when_the_edit_is_undone(h):
    h.box("panels.kwp").setValue(5.2)
    h.box("panels.kwp").setValue(4.8)
    assert h.window.cancel_button.text() == "Close"


def test_it_reads_close_again_after_a_save_with_the_window_open(h):
    h.box("panels.kwp").setValue(5.2)
    h.window.save_button.click()
    assert h.window.cancel_button.text() == "Close"
    h.box("panels.kwp").setValue(6.0)
    assert h.window.cancel_button.text() == "Cancel"


def test_it_follows_edits_on_every_kind_of_input(h):
    t = h.window
    t.field_tabs[2].inputs["charger.max_amps"].setValue(20)  # a whole-number box on another tab
    assert t.cancel_button.text() == "Cancel"
    t.field_tabs[2].inputs["charger.max_amps"].setValue(32)
    assert t.cancel_button.text() == "Close"
    h.box("timezone").setCurrentText("Europe/London")  # the time zone dropdown
    assert t.cancel_button.text() == "Cancel"
    h.box("timezone").setCurrentText("Europe/Amsterdam")
    assert t.cancel_button.text() == "Close"


def test_it_follows_the_away_rule_rows(h):
    from PySide6.QtCore import QTime

    tab = h.window.away_tab
    tab.rows[0].end.setTime(QTime(18, 0))
    assert h.window.cancel_button.text() == "Cancel"
    tab.rows[0].end.setTime(QTime(19, 0))
    assert h.window.cancel_button.text() == "Close"
    tab.add_button.click()
    assert h.window.cancel_button.text() == "Cancel"
    tab.rows[1].remove_button.click()
    assert h.window.cancel_button.text() == "Close"
    tab.rows[0].remove_button.click()
    assert h.window.cancel_button.text() == "Cancel"


def test_reset_tab_changes_the_label_only_when_it_changes_something(tmp_path, qapp):
    h = Harness(tmp_path, first_run_config())
    h.window.reset_button.click()  # stored values already are the built-in defaults, and all typical
    assert h.window.cancel_button.text() == "Close"
    custom = replace(Config(), panels=replace(Config().panels, kwp=9.0))
    h2 = Harness(tmp_path, custom)
    h2.window.reset_button.click()
    assert h2.window.cancel_button.text() == "Cancel"


def test_close_with_nothing_unsaved_closes_without_asking(h):
    h.box("panels.kwp").setValue(5.2)
    h.window.save_button.click()
    h.window.cancel_button.click()  # reads "Close"
    assert h.asked == [] and not h.window.isVisible()


def test_cancel_with_unsaved_edits_still_asks(h):
    h.box("panels.kwp").setValue(5.2)
    assert h.window.cancel_button.text() == "Cancel"
    h.window.cancel_button.click()
    assert h.asked == [1]
