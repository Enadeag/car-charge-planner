import os

os.environ.setdefault("QT_QPA_PLATFORM", "offscreen")

from dataclasses import replace

import pytest
from PySide6.QtWidgets import QDialog

from car_charge_planner.app_settings import DesktopSettings
from car_charge_planner.config.model import Config, Setup, first_run_config, load_config
from car_charge_planner.planner.model import Panels
from car_charge_planner.settings.window import SettingsWindow
from conftest import engine_for, example_config


class Harness:
    def __init__(self, tmp_path, cfg=None):
        self.file = tmp_path / "config.toml"
        self.saved = []
        self.asked = []
        self.answer = True
        self.window = SettingsWindow(engine_for(cfg or example_config(), self.file, saved=self.saved), DesktopSettings())
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
    assert h.box("panels.system_losses").value() == 14.0
    assert h.box("latitude").value() == 52.37 and h.box("longitude").value() == 4.9
    assert h.box("timezone").currentText() == "Europe/Amsterdam"


def test_units_and_typical_value_tags_are_visible(tmp_path, qapp):
    typical = replace(example_config(), setup=Setup(("panels.kwp", "panels.tilt_deg")))
    h = Harness(tmp_path, typical)
    assert h.box("panels.kwp").suffix() == " kWp" and h.box("panels.system_losses").suffix() == " %"
    texts = [label.text() for label in h.window.field_tabs[0].findChildren(__import__("PySide6.QtWidgets", fromlist=["QLabel"]).QLabel)]
    assert any("Typical value." in t and "Peak power of the panels" in t for t in texts)
    assert any("Typical value." in t and "flat" in t for t in texts)
    assert not any("Typical value." in t and "South-east is -45" in t for t in texts)


def test_save_writes_the_file_applies_and_keeps_the_window_open(h):
    h.box("panels.kwp").setValue(5.2)
    h.window.save_button.click()
    assert load_config(h.file).system.panels[0].kwp == 5.2
    assert [c.system.panels[0].kwp for c in h.saved] == [5.2]
    assert h.window.isVisible()


def test_percent_is_stored_as_a_fraction(h):
    h.box("panels.system_losses").setValue(12.5)
    h.window.save_button.click()
    assert load_config(h.file).system.panels[0].system_losses == 0.125


def test_untouched_fields_keep_their_exact_values(tmp_path, qapp):
    odd = example_config().with_system(latitude=52.123456789, panels=(replace(Config().system.panels[0], system_losses=0.1234567),))
    h = Harness(tmp_path, odd)
    h.box("panels.kwp").setValue(6.0)
    h.window.save_button.click()
    saved = load_config(h.file)
    assert saved.system.latitude == 52.123456789
    assert saved.system.panels[0].system_losses == 0.1234567
    assert saved.system.panels[0].tilt_deg == 35 and isinstance(saved.system.panels[0].tilt_deg, int)


def test_a_changed_field_is_stored_as_shown(h):
    h.box("panels.tilt_deg").setValue(28.5)
    h.window.save_button.click()
    assert load_config(h.file).system.panels[0].tilt_deg == 28.5


def test_invalid_value_lists_the_problem_and_writes_nothing(h):
    h.box("timezone").setCurrentText("Mars/Base")
    h.window.save_button.click()
    assert h.window.isVisible() and not h.window.errors.isHidden()
    assert "Mars/Base" in h.window.errors.text()
    assert not h.file.exists() and h.saved == []


def test_a_failed_write_is_reported_and_keeps_the_window_open(h, monkeypatch):
    def boom(cfg, path):
        raise OSError("disk full")

    monkeypatch.setattr("car_charge_planner.engine.store.write_config", boom)
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
    assert h.box("panels.kwp").value() == 7


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


def test_reset_tab_restores_defaults_without_saving(tmp_path, qapp):
    base = example_config()
    custom = base.with_system(panels=(replace(base.system.panels[0], kwp=9.0, tilt_deg=10),), latitude=51.5)
    h = Harness(tmp_path, custom)
    assert h.box("panels.kwp").value() == 9.0
    h.window.reset_button.click()
    assert h.box("panels.kwp").value() == Config().system.panels[0].kwp and h.box("panels.tilt_deg").value() == 35
    assert h.box("latitude").value() == 0.0 and h.box("longitude").value() == 0.0
    assert not h.file.exists() and h.saved == []
    assert h.window.is_dirty()
    h.box("latitude").setValue(52.37)
    h.box("longitude").setValue(4.9)
    h.window.save_button.click()
    solar_keys = ("panels.kwp", "panels.tilt_deg", "panels.azimuth_deg", "panels.system_losses")
    expected = replace(base.with_system(panels=Config().system.panels, timezone=Config().system.timezone, latitude=52.37, longitude=4.9), setup=Setup(solar_keys))
    assert load_config(h.file) == expected


def test_reset_then_cancel_keeps_the_stored_config(tmp_path, qapp):
    custom = example_config().with_system(panels=(replace(Config().system.panels[0], kwp=9.0),))
    h = Harness(tmp_path, custom)
    h.window.reset_button.click()
    h.window.cancel_button.click()
    assert h.asked == [1] and h.window.stored.system.panels[0].kwp == 9.0 and not h.file.exists()


def choose(h, action):
    action.trigger()


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
    assert not h.file.exists() and h.saved == [] and h.window.isVisible()


def test_save_and_close_saves_then_closes(h):
    h.box("panels.kwp").setValue(5.2)
    choose(h, h.window.save_close_action)
    h.window.save_button.click()
    assert load_config(h.file).system.panels[0].kwp == 5.2
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
    h.window.cancel_button.click()
    assert h.asked == [] and not h.window.isVisible()


def test_editing_after_a_save_makes_it_dirty_again_and_cancel_asks(h):
    h.box("panels.kwp").setValue(5.2)
    h.window.save_button.click()
    h.box("panels.kwp").setValue(6.0)
    assert h.window.is_dirty()
    h.window.cancel_button.click()
    assert h.asked == [1]
    assert load_config(h.file).system.panels[0].kwp == 5.2


def test_saving_twice_keeps_both_edits_and_exact_values(h):
    h.box("panels.kwp").setValue(5.2)
    h.window.save_button.click()
    h.window.field_tabs[1].inputs["planning.minimum_pct"].setValue(25)
    h.window.save_button.click()
    saved = load_config(h.file)
    assert saved.system.panels[0].kwp == 5.2 and saved.planning.minimum_pct == 25
    assert saved.system.car.km_per_day == 600 / 7


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
    assert load_config(h.file).system.panels[0].kwp == 5.2 and h.window.isVisible()
    choose(h, h.window.save_close_action)
    h.box("panels.kwp").setValue(5.4)
    QTest.keyClick(h.window, Qt.Key.Key_Return)
    assert load_config(h.file).system.panels[0].kwp == 5.4 and not h.window.isVisible()


def test_enter_does_not_press_reset_or_cancel(h):
    from PySide6.QtCore import Qt
    from PySide6.QtTest import QTest

    h.box("panels.kwp").setValue(5.2)
    assert not h.window.reset_button.autoDefault() and not h.window.cancel_button.autoDefault()
    h.window.reset_button.setFocus()
    QTest.keyClick(h.window.reset_button, Qt.Key.Key_Return)
    assert h.window.isVisible() and h.asked == []
    assert load_config(h.file).system.panels[0].kwp == 5.2


def edit_spin(h):
    box = h.box("panels.kwp")
    return lambda: box.setValue(5.2), lambda: box.setValue(4.8)


def edit_other_tab(h):
    box = h.window.field_tabs[2].inputs["charger.max_amps"]
    return lambda: box.setValue(20), lambda: box.setValue(32)


def edit_dropdown(h):
    box = h.box("timezone")
    return lambda: box.setCurrentText("Europe/London"), lambda: box.setCurrentText("Europe/Amsterdam")


def edit_away_time(h):
    from PySide6.QtCore import QTime

    row = h.window.away_tab.rows[0]
    return lambda: row.end.setTime(QTime(18, 0)), lambda: row.end.setTime(QTime(19, 0))


def edit_away_rows(h):
    tab = h.window.away_tab
    return tab.add_button.click, lambda: tab.rows[1].remove_button.click()


@pytest.mark.parametrize("edit_and_undo", [edit_spin, edit_other_tab, edit_dropdown, edit_away_time, edit_away_rows])
def test_the_window_is_dirty_only_while_something_differs_from_what_is_stored(h, edit_and_undo):
    edit, undo = edit_and_undo(h)
    assert not h.window.is_dirty() and h.window.cancel_button.text() == "Close"
    edit()
    assert h.window.is_dirty() and h.window.cancel_button.text() == "Cancel"
    undo()
    assert not h.window.is_dirty() and h.window.cancel_button.text() == "Close"
    h.window.cancel_button.click()
    assert h.asked == []


def test_reset_tab_changes_the_label_only_when_it_changes_something(tmp_path, qapp):
    h = Harness(tmp_path, first_run_config())
    h.window.reset_button.click()
    assert h.window.cancel_button.text() == "Close"
    custom = Config().with_system(panels=(replace(Config().system.panels[0], kwp=9.0),))
    h2 = Harness(tmp_path, custom)
    h2.window.reset_button.click()
    assert h2.window.cancel_button.text() == "Cancel"


def solar(h):
    return h.window.field_tabs[0]


def test_one_array_has_no_heading_name_or_remove(h):
    tab = solar(h)
    assert len(tab.boxes) == 1 and tab.boxes[0].heading.isHidden() and tab.boxes[0].remove_button.isHidden()
    assert "panels.name" not in tab.inputs and tab.add_button.isVisible()
    assert tab.location_heading.text() == "Location" and tab.location_heading.isVisible()
    assert tab.location_heading.styleSheet().startswith(tab.boxes[0].heading.styleSheet().rstrip(";"))
    assert "of this array" not in tab.hints["panels.kwp"].text()
    assert [s.key for s in tab.specs][:4] == ["panels.kwp", "panels.tilt_deg", "panels.azimuth_deg", "panels.system_losses"]


def test_an_added_array_is_tagged_typical_and_saved_with_its_name(h):
    tab = solar(h)
    tab.add_button.click()
    assert [box.heading.text() for box in tab.boxes] == ["Array 1", "Array 2"]
    assert all(box.heading.isVisible() and box.remove_button.isVisible() for box in tab.boxes)
    assert h.box("panels.kwp").value() == 4.8 and h.box("panels.2.kwp").value() == 5.0
    assert all("Typical value." in tab.hints[k].text() for k in ("panels.2.kwp", "panels.2.tilt_deg", "panels.2.azimuth_deg"))
    assert h.window.is_dirty()
    h.box("panels.2.kwp").setValue(2.4)
    h.box("panels.2.azimuth_deg").setValue(90)
    h.box("panels.2.name").setText("Garage")
    assert tab.boxes[1].heading.text() == "Array 2: Garage"
    h.window.save_button.click()
    saved = load_config(h.file)
    assert saved.system.panels == (example_config().system.panels[0], Panels(kwp=2.4, tilt_deg=35, azimuth_deg=90, system_losses=0.14, name="Garage"))
    assert saved.setup.typical == ("panels.2.tilt_deg", "panels.2.system_losses")
    assert len(solar(h).boxes) == 2


def test_the_add_button_sits_under_the_arrays_and_the_location_has_its_own_heading(h):
    tab = solar(h)
    tab.add_button.click()
    y = lambda widget: widget.mapTo(tab, widget.rect().topLeft()).y()  # noqa: E731
    last = tab.boxes[-1]
    assert y(last) + last.height() <= y(tab.add_button) < y(tab.location_heading) < y(tab.location)
    assert tab.location_heading.text() == "Location"
    for key in ("panels.kwp", "panels.2.kwp"):
        assert "Peak power of the panels in this array, as stated" in tab.hints[key].text()


def test_an_edit_made_before_adding_an_array_is_kept(h):
    h.box("panels.kwp").setValue(6.1)
    solar(h).add_button.click()
    assert h.box("panels.kwp").value() == 6.1 and h.window.current_config().system.panels[0].kwp == 6.1


def test_removing_an_array_moves_the_later_ones_up(tmp_path, qapp):
    arrays = (Panels(kwp=3.0, azimuth_deg=-90, name="East"), Panels(kwp=2.0, azimuth_deg=90, name="West"))
    h = Harness(tmp_path, replace(example_config().with_system(panels=arrays), setup=Setup(("panels.2.tilt_deg",))))
    tab = solar(h)
    assert [box.heading.text() for box in tab.boxes] == ["Array 1: East", "Array 2: West"]
    tab.boxes[0].remove_button.click()
    assert len(tab.boxes) == 1 and tab.boxes[0].heading.isHidden() and h.box("panels.kwp").value() == 2.0
    assert "Typical value." in tab.hints["panels.tilt_deg"].text()
    h.window.save_button.click()
    saved = load_config(h.file)
    assert saved.system.panels == (arrays[1],) and saved.setup.typical == ("panels.tilt_deg",)


def test_reset_brings_back_one_typical_array(tmp_path, qapp):
    arrays = (Panels(kwp=3.0, name="East"), Panels(kwp=2.0, name="West"))
    h = Harness(tmp_path, example_config().with_system(panels=arrays))
    h.window.reset_button.click()
    assert len(solar(h).boxes) == 1 and h.window.current_config().system.panels == (Panels(),)
    assert h.window.current_config().setup.typical[:4] == ("panels.kwp", "panels.tilt_deg", "panels.azimuth_deg", "panels.system_losses")
