import os

os.environ.setdefault("QT_QPA_PLATFORM", "offscreen")

from dataclasses import replace

import pytest
from PySide6.QtWidgets import QApplication, QDialog

from app.settings.window import SettingsWindow
from planner.model import Config, load_config


@pytest.fixture
def qapp():
    return QApplication.instance() or QApplication([])


class Harness:
    def __init__(self, tmp_path, cfg=None):
        self.file = tmp_path / "config.toml"
        self.saved = []
        self.asked = []
        self.answer = True
        self.window = SettingsWindow(cfg or Config(), self.file, self.saved.append)
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
    assert h.box("latitude").value() == 0.0 and h.box("longitude").value() == 0.0
    assert h.box("timezone").currentText() == "Europe/Amsterdam"


def test_units_and_assumed_labels_are_visible(h):
    assert h.box("panels.kwp").suffix() == " kWp" and h.box("panels.system_losses").suffix() == " %"
    texts = [label.text() for label in h.window.field_tabs[0].findChildren(__import__("PySide6.QtWidgets", fromlist=["QLabel"]).QLabel)]
    assert any("Assumed." in t and "Total peak power" in t for t in texts)
    assert any("Assumed." in t and "flat" in t for t in texts)
    assert not any("Assumed." in t and "South-east is -45" in t for t in texts)  # direction is not an assumption


def test_save_writes_the_file_applies_and_closes(h):
    h.box("panels.kwp").setValue(5.2)
    h.window.save_button.click()
    assert load_config(h.file).panels.kwp == 5.2
    assert [c.panels.kwp for c in h.saved] == [5.2]
    assert not h.window.isVisible() and h.window.result() == QDialog.DialogCode.Accepted


def test_percent_is_stored_as_a_fraction(h):
    h.box("panels.system_losses").setValue(12.5)
    h.window.save_button.click()
    assert load_config(h.file).panels.system_losses == 0.125


def test_untouched_fields_keep_their_exact_values(tmp_path, qapp):
    odd = replace(Config(), latitude=50.123456789, panels=replace(Config().panels, system_losses=0.1234567))
    h = Harness(tmp_path, odd)
    h.box("panels.kwp").setValue(6.0)  # edit one field only
    h.window.save_button.click()
    saved = load_config(h.file)
    assert saved.latitude == 50.123456789  # the box only shows 4 decimals
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

    monkeypatch.setattr("app.settings.window.write_config", boom)
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
    custom = replace(Config(), panels=replace(Config().panels, kwp=9.0, tilt_deg=10), latitude=51.5)
    h = Harness(tmp_path, custom)
    assert h.box("panels.kwp").value() == 9.0
    h.window.reset_button.click()
    assert h.box("panels.kwp").value() == 4.8 and h.box("panels.tilt_deg").value() == 35
    assert h.box("latitude").value() == 0.0
    assert not h.file.exists() and h.saved == []  # nothing saved yet
    assert h.window.is_dirty()  # it differs from what is stored
    h.window.save_button.click()
    assert load_config(h.file) == Config()


def test_reset_then_cancel_keeps_the_stored_config(tmp_path, qapp):
    custom = replace(Config(), panels=replace(Config().panels, kwp=9.0))
    h = Harness(tmp_path, custom)
    h.window.reset_button.click()
    h.window.cancel_button.click()
    assert h.asked == [1] and h.window.stored.panels.kwp == 9.0 and not h.file.exists()
