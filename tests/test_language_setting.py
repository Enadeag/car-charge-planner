"""The language setting and its picker (ticket I4)."""

import os

os.environ.setdefault("QT_QPA_PLATFORM", "offscreen")

from dataclasses import replace

import pytest
from PySide6.QtWidgets import QApplication, QComboBox

from app import i18n
from app.i18n import available_languages, init, language_name
from app.settings.window import SettingsWindow
from planner.config_io import dump_config, write_config
from planner.model import Config, ConfigError, Interface, load_config, validate

from test_i18n import make_catalogue


@pytest.fixture(autouse=True)
def english_after_every_test():
    yield
    init(languages=[])


@pytest.fixture
def qapp():
    return QApplication.instance() or QApplication([])


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


def window_for(cfg, tmp_path):
    return SettingsWindow(cfg, tmp_path / "config.toml", lambda c: None)


# ---- the setting ---------------------------------------------------------------------------------------------


def test_the_default_follows_the_system_and_survives_a_round_trip(tmp_path):
    assert Config().interface.language == "auto"
    cfg = replace(Config(), interface=Interface(language="pt_BR"))
    path = tmp_path / "config.toml"
    write_config(cfg, path)
    assert load_config(path).interface.language == "pt_BR"
    assert 'language = "pt_BR"' in dump_config(cfg)


def test_an_old_config_without_the_key_still_loads(tmp_path):
    path = tmp_path / "config.toml"
    path.write_text('[interface]\nmode = "window"\n')
    cfg = load_config(path)
    assert cfg.interface.mode == "window" and cfg.interface.language == "auto"


@pytest.mark.parametrize("value", ["auto", "en", "nl", "pt_BR", "zh-Hant", "fil"])
def test_language_codes_are_accepted(value):
    validate(replace(Config(), interface=Interface(language=value)))


@pytest.mark.parametrize("value", ["", "Dutch", "n", "nl nl", "../etc", "nl/../x"])
def test_anything_else_is_refused(value):
    with pytest.raises(ConfigError, match="language"):
        validate(replace(Config(), interface=Interface(language=value)))


# ---- which languages are installed ----------------------------------------------------------------------------


def test_installed_languages_are_the_directories_with_a_compiled_catalogue(tmp_path):
    make_catalogue(tmp_path, "nl", {})
    (tmp_path / "xx" / "LC_MESSAGES").mkdir(parents=True)  # a folder without a catalogue is not a language
    (tmp_path / "fr").mkdir()
    assert available_languages(tmp_path) == ["nl"]
    assert available_languages(tmp_path / "missing") == []


def test_a_language_is_named_in_its_own_language(qapp):
    assert language_name("nl") == "Nederlands" and language_name("de") == "Deutsch"


# ---- the picker ----------------------------------------------------------------------------------------------


def test_with_only_english_there_is_no_picker(cfg, qapp, tmp_path, monkeypatch):
    monkeypatch.setattr(i18n, "LOCALE_DIR", tmp_path / "empty")
    window = window_for(cfg, tmp_path)
    tab = interface_tab(window)
    assert "interface.language" not in tab.inputs
    assert [s.key for s in tab.specs] == ["interface.mode", "interface.units"]


def test_a_stored_language_is_kept_when_the_picker_is_absent(cfg, qapp, tmp_path, monkeypatch):
    monkeypatch.setattr(i18n, "LOCALE_DIR", tmp_path / "empty")
    stored = replace(cfg, interface=Interface(language="nl"))
    window = window_for(stored, tmp_path)
    assert window.current_config().interface.language == "nl"


def test_with_a_second_language_the_picker_lists_the_choices(cfg, qapp, tmp_path, catalogues):
    window = window_for(cfg, tmp_path)
    box = interface_tab(window).inputs["interface.language"]
    assert isinstance(box, QComboBox)
    assert [box.itemData(i) for i in range(box.count())] == ["auto", "de", "en", "nl"]
    assert [box.itemText(i) for i in range(1, box.count())] == ["Deutsch", "English", "Nederlands"]
    assert box.currentData() == "auto"


def test_choosing_a_language_saves_it_and_it_applies_after_a_restart(cfg, qapp, tmp_path, catalogues):
    saved = []
    path = tmp_path / "config.toml"
    window = SettingsWindow(cfg, path, saved.append)
    box = interface_tab(window).inputs["interface.language"]
    box.setCurrentIndex(box.findData("nl"))
    assert window.save()
    assert saved[-1].interface.language == "nl" and load_config(path).interface.language == "nl"
    assert i18n.current_language() == ""  # nothing changes while the app runs

    # the next start reads the file and applies the choice
    init([load_config(path).interface.language], catalogues)
    assert i18n.current_language() == "nl" and i18n._("Language") == "Taal"
    restarted = window_for(load_config(path), tmp_path)
    assert interface_tab(restarted).inputs["interface.language"].currentData() == "nl"
    assert restarted.tabs.tabText(restarted.tabs.count() - 1)  # the window builds in the new language


def test_english_can_be_chosen_on_a_system_that_speaks_another_language(catalogues):
    assert init(["nl"], catalogues) == "nl"
    assert init(["en"], catalogues) == ""  # no English catalogue: the source text


def test_a_changed_language_is_announced_like_a_changed_interface(cfg, qapp, tmp_path, monkeypatch):
    from app.controller import Controller
    from conftest import FakeNotifier

    monkeypatch.setenv("XDG_STATE_HOME", str(tmp_path))
    monkeypatch.setattr("forecast.service.ForecastService.get", lambda *a, **k: None)
    notifier = FakeNotifier()
    controller = Controller(cfg, tmp_path / "config.toml", notifier)
    controller.apply_config(replace(cfg, interface=Interface(language="nl")))
    assert len(notifier.sent) == 1
