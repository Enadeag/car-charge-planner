"""The language setting and its picker."""

import os

os.environ.setdefault("QT_QPA_PLATFORM", "offscreen")

from dataclasses import replace

import pytest
from PySide6.QtWidgets import QComboBox

from car_charge_planner import i18n
from car_charge_planner.app_settings import AppSettings, AppSettingsFile, DesktopSettings, dump_app_settings, load_app_settings, problems
from car_charge_planner.engine import FileStore
from car_charge_planner.i18n import available_languages, init, language_name
from car_charge_planner.settings.window import SettingsWindow
from conftest import engine_for
from test_i18n import make_catalogue


@pytest.fixture(autouse=True)
def english_after_every_test():
    yield
    init(languages=[])


@pytest.fixture
def catalogues(tmp_path, monkeypatch):
    """A test-only locale directory with Dutch and German; the app is pointed at it."""
    directory = tmp_path / "locale"
    make_catalogue(directory, "nl", {"Language": "Taal", "Automatic: follow the system": "Automatisch: volg het systeem"})
    make_catalogue(directory, "de", {"Language": "Sprache"})
    monkeypatch.setattr(i18n, "LOCALE_DIR", directory)
    return directory


def interface_tab(window):
    return next(t for t in window.field_tabs if t.key == "interface")


def window_for(cfg, tmp_path, app=None):
    return SettingsWindow(engine_for(cfg, tmp_path / "config.toml"), DesktopSettings(app))


def test_the_default_follows_the_system_and_survives_a_round_trip(tmp_path):
    assert AppSettings().language == "auto"
    path = tmp_path / "app.toml"
    AppSettingsFile(path).save(AppSettings(language="pt_BR"))
    assert load_app_settings(path).language == "pt_BR"
    assert 'language = "pt_BR"' in dump_app_settings(AppSettings(language="pt_BR"))


def test_a_file_without_the_key_still_loads(tmp_path):
    path = tmp_path / "app.toml"
    path.write_text('[interface]\nmode = "window"\n', encoding="utf-8")
    assert load_app_settings(path) == AppSettings(mode="window", language="auto")


@pytest.mark.parametrize("value", ["auto", "en", "nl", "pt_BR", "zh-Hant", "fil"])
def test_language_codes_are_accepted(value):
    assert problems(AppSettings(language=value)) == []


@pytest.mark.parametrize("value", ["", "Dutch", "n", "nl nl", "../etc", "nl/../x"])
def test_anything_else_is_refused(value):
    assert len(problems(AppSettings(language=value))) == 1 and "language" in problems(AppSettings(language=value))[0]


def test_installed_languages_are_the_directories_with_a_compiled_catalogue(tmp_path):
    make_catalogue(tmp_path, "nl", {})
    (tmp_path / "xx" / "LC_MESSAGES").mkdir(parents=True)  # a folder without a catalogue is not a language
    (tmp_path / "fr").mkdir()
    assert available_languages(tmp_path) == ["nl"]
    assert available_languages(tmp_path / "missing") == []


def test_a_language_is_named_in_its_own_language(qapp):
    assert language_name("nl") == "Nederlands" and language_name("de") == "Deutsch"


def test_with_only_english_there_is_no_picker(cfg, qapp, tmp_path, monkeypatch):
    monkeypatch.setattr(i18n, "LOCALE_DIR", tmp_path / "empty")
    window = window_for(cfg, tmp_path)
    tab = interface_tab(window)
    assert "interface.language" not in tab.inputs
    assert [s.key for s in tab.specs] == ["interface.mode", "interface.units", "interface.start_at_login"]


def test_a_stored_language_is_kept_when_the_picker_is_absent(cfg, qapp, tmp_path, monkeypatch):
    monkeypatch.setattr(i18n, "LOCALE_DIR", tmp_path / "empty")
    window = window_for(cfg, tmp_path, AppSettings(language="nl"))
    assert window.current_app_settings().language == "nl"


def test_with_a_second_language_the_picker_lists_the_choices(cfg, qapp, tmp_path, catalogues):
    window = window_for(cfg, tmp_path)
    box = interface_tab(window).inputs["interface.language"]
    assert isinstance(box, QComboBox)
    assert [box.itemData(i) for i in range(box.count())] == ["auto", "de", "en", "nl"]
    assert [box.itemText(i) for i in range(1, box.count())] == ["Deutsch", "English", "Nederlands"]
    assert box.currentData() == "auto"


def test_choosing_a_language_saves_it_and_it_applies_after_a_restart(cfg, qapp, tmp_path, catalogues):
    path = tmp_path / "app.toml"
    window = SettingsWindow(engine_for(cfg, tmp_path / "config.toml"), DesktopSettings(store=AppSettingsFile(path)))
    box = interface_tab(window).inputs["interface.language"]
    box.setCurrentIndex(box.findData("nl"))
    assert window.save()
    assert load_app_settings(path).language == "nl" and "language" not in (tmp_path / "config.toml").read_text(encoding="utf-8")
    assert i18n.current_language() == ""  # nothing changes while the app runs

    init([load_app_settings(path).language], catalogues)
    assert i18n.current_language() == "nl" and i18n._("Language") == "Taal"
    restarted = window_for(cfg, tmp_path, load_app_settings(path))
    assert interface_tab(restarted).inputs["interface.language"].currentData() == "nl"
    assert restarted.tabs.tabText(restarted.tabs.count() - 1)


def test_english_can_be_chosen_on_a_system_that_speaks_another_language(catalogues):
    assert init(["nl"], catalogues) == "nl"
    assert init(["en"], catalogues) == ""  # no English catalogue: the source text


def test_a_changed_language_is_announced_like_a_changed_interface(cfg, qapp, tmp_path, monkeypatch):
    from car_charge_planner.controller import Controller
    from conftest import FakeNotifier

    monkeypatch.setenv("XDG_STATE_HOME", str(tmp_path))
    monkeypatch.setattr("car_charge_planner.forecast.service.ForecastService.get", lambda *a, **k: None)
    notifier = FakeNotifier()
    controller = Controller(cfg, FileStore(tmp_path / "config.toml"), notifier)
    assert controller.app_settings.save(replace(controller.app_settings.current, language="nl")) == []
    assert len(notifier.sent) == 1
