"""Translations: choosing a language, looking texts up, the markers, Qt's own texts and the developer tools."""

import os

os.environ.setdefault("QT_QPA_PLATFORM", "offscreen")

from pathlib import Path

import pytest
from PySide6.QtCore import QCoreApplication
from PySide6.QtWidgets import QApplication

from app import i18n, i18n_tools
from app.i18n import (
    DOMAIN,
    NC_,
    N_,
    _,
    current_language,
    init,
    install_qt_translations,
    ngettext,
    pgettext,
    resolve_languages,
    setup,
    translate,
)

babel = pytest.importorskip("babel")
from babel.messages.catalog import Catalog  # noqa: E402
from babel.messages.mofile import write_mo  # noqa: E402
from babel.messages.pofile import read_po, write_po  # noqa: E402


@pytest.fixture(autouse=True)
def english_after_every_test():
    yield
    init(languages=[])


@pytest.fixture
def qapp():
    return QApplication.instance() or QApplication([])


def make_catalogue(localedir: Path, language: str, messages: dict) -> Path:
    """A compiled catalogue for tests: {msgid: translation}, {(singular, plural): (forms...)}, {(context, msgid): text}."""
    catalogue = Catalog(locale=language, domain=DOMAIN)
    for key, value in messages.items():
        if isinstance(key, tuple) and len(key) == 3:  # (context, singular, plural)
            catalogue.add((key[1], key[2]), tuple(value), context=key[0])
        elif isinstance(key, tuple) and isinstance(value, tuple):  # (singular, plural): forms
            catalogue.add(key, value)
        elif isinstance(key, tuple):  # (context, msgid): text
            catalogue.add(key[1], value, context=key[0])
        else:
            catalogue.add(key, value)
    target = localedir / language / "LC_MESSAGES" / f"{DOMAIN}.mo"
    target.parent.mkdir(parents=True, exist_ok=True)
    with open(target, "wb") as handle:
        write_mo(handle, catalogue)
    return target


# ---- choosing the language ---------------------------------------------------------------------------------


@pytest.mark.parametrize(
    "environ, expected",
    [
        ({"LANG": "nl_NL.UTF-8"}, ["nl_NL", "nl"]),
        ({"LC_ALL": "de_DE.UTF-8", "LANG": "nl_NL.UTF-8"}, ["de_DE", "de"]),  # LC_ALL beats LANG
        ({"LC_MESSAGES": "fr_FR", "LANG": "nl_NL"}, ["fr_FR", "fr"]),  # LC_MESSAGES beats LANG
        ({"LANGUAGE": "de:nl", "LANG": "en_US.UTF-8"}, ["de", "nl", "en_US", "en"]),  # LANGUAGE is a preference list
        ({"LANGUAGE": "nl", "LANG": "C"}, []),  # the C locale means English whatever LANGUAGE says
        ({"LANGUAGE": "nl", "LC_ALL": "POSIX"}, []),
        ({"LANG": "nl_NL@euro"}, ["nl_NL", "nl"]),  # a modifier is dropped
        ({"LANG": "pt_BR.ISO-8859-1"}, ["pt_BR", "pt"]),
        ({"LANG": "nl-NL"}, ["nl_NL", "nl"]),
        ({"LANGUAGE": "nl"}, ["nl"]),  # LANGUAGE alone
        ({"LANGUAGE": "", "LANG": ""}, []),  # empty values count as unset
        ({}, []),
    ],
)
def test_the_language_comes_from_the_environment_like_gettext_tools_do(environ, expected):
    assert resolve_languages(environ) == expected


def test_with_nothing_set_qts_system_language_is_used_most_specific_first():
    assert resolve_languages({}, ["en-Latn-GB", "en-GB", "en-Latn", "en"]) == ["en_Latn_GB", "en_GB", "en_Latn", "en"]
    assert resolve_languages({}, ["nl-NL", "nl"]) == ["nl_NL", "nl"]
    assert resolve_languages({}, ["zh-Hant-TW"]) == ["zh_Hant_TW", "zh_Hant", "zh"]


def test_qts_language_is_ignored_when_the_environment_speaks():
    assert resolve_languages({"LANG": "de_DE"}, ["nl-NL"]) == ["de_DE", "de"]


# ---- looking texts up ------------------------------------------------------------------------------------------


def test_untranslated_english_is_the_default():
    assert init(languages=[]) == "" and current_language() == ""
    assert _("Charge now") == "Charge now"
    assert ngettext("{n} day", "{n} days", 1) == "{n} day" and ngettext("{n} day", "{n} days", 3) == "{n} days"
    assert pgettext("month", "May") == "May"


def test_the_empty_text_stays_empty_instead_of_returning_the_catalogue_header(tmp_path):
    make_catalogue(tmp_path, "nl", {"Charge now": "Nu laden"})
    init(["nl"], tmp_path)
    assert _("") == ""  # gettext("") would return the header with the plural rule and translator names


def test_a_catalogue_translates_and_unknown_texts_stay_english(tmp_path):
    make_catalogue(tmp_path, "nl", {"Charge now": "Nu laden"})
    assert init(["nl"], tmp_path) == "nl" and current_language() == "nl"
    assert _("Charge now") == "Nu laden"
    assert _("A text nobody translated") == "A text nobody translated"


def test_a_regional_request_falls_back_to_the_general_catalogue(tmp_path):
    make_catalogue(tmp_path, "nl", {"Charge now": "Nu laden"})
    assert init(["nl_BE"], tmp_path) == "nl"  # nl_BE has none, nl does
    assert _("Charge now") == "Nu laden"


def test_a_more_specific_catalogue_wins(tmp_path):
    make_catalogue(tmp_path, "pt", {"Charge now": "Carregar agora"})
    make_catalogue(tmp_path, "pt_BR", {"Charge now": "Carregar já"})
    assert init(["pt_BR"], tmp_path) == "pt_BR" and _("Charge now") == "Carregar já"
    assert init(["pt_PT"], tmp_path) == "pt" and _("Charge now") == "Carregar agora"


def test_plural_forms_follow_the_languages_own_rule(tmp_path):
    make_catalogue(tmp_path, "pl", {("{n} day", "{n} days"): ("jeden", "kilka", "wiele")})  # Polish: three forms
    init(["pl"], tmp_path)
    forms = {n: ngettext("{n} day", "{n} days", n) for n in (1, 2, 3, 5, 12, 22, 25)}
    assert forms == {1: "jeden", 2: "kilka", 3: "kilka", 5: "wiele", 12: "wiele", 22: "kilka", 25: "wiele"}


def test_a_context_tells_the_same_english_text_apart(tmp_path):
    make_catalogue(tmp_path, "nl", {"May": "Mag", ("month", "May"): "mei"})  # the verb and the month
    init(["nl"], tmp_path)
    assert _("May") == "Mag" and pgettext("month", "May") == "mei"
    assert pgettext("weekday", "May") == "May"  # a context nobody translated


def test_a_missing_or_damaged_catalogue_never_stops_the_app(tmp_path):
    assert init(["nl"], tmp_path / "does-not-exist") == ""
    broken = tmp_path / "nl" / "LC_MESSAGES" / f"{DOMAIN}.mo"
    broken.parent.mkdir(parents=True)
    broken.write_bytes(b"this is not a message catalogue")
    assert init(["nl"], tmp_path) == "" and _("Charge now") == "Charge now"


def test_the_default_catalogue_directory_is_inside_the_package():
    assert i18n.LOCALE_DIR == Path(i18n.__file__).parent / "locale" and i18n.LOCALE_DIR.is_dir()


def test_init_reads_the_real_environment_when_told_nothing(tmp_path, monkeypatch):
    make_catalogue(tmp_path, "nl", {"Charge now": "Nu laden"})
    monkeypatch.setenv("LANGUAGE", "nl")
    monkeypatch.setenv("LANG", "en_US.UTF-8")
    assert init(localedir=tmp_path) == "nl"
    monkeypatch.setenv("LANG", "C")
    assert init(localedir=tmp_path) == ""  # C beats LANGUAGE


def test_init_falls_back_to_qts_language_when_the_environment_is_empty(tmp_path):
    make_catalogue(tmp_path, "nl", {"Charge now": "Nu laden"})
    assert init(environ={}, qt_languages=["nl-NL", "nl"], localedir=tmp_path) == "nl"
    assert init(environ={}, qt_languages=["en-GB", "en"], localedir=tmp_path) == ""


# ---- the markers for texts defined before a language is chosen --------------------------------------------------------


LABEL = N_("Charge now")  # defined at import, long before any language is chosen
CONTEXT_LABEL = NC_("month", "May")


def test_a_marker_returns_the_text_unchanged_and_translates_when_shown(tmp_path):
    assert LABEL == "Charge now" and CONTEXT_LABEL == ("month", "May")
    assert translate(LABEL) == "Charge now"  # no language yet
    make_catalogue(tmp_path, "nl", {"Charge now": "Nu laden", ("month", "May"): "mei"})
    init(["nl"], tmp_path)
    assert translate(LABEL) == "Nu laden" and translate(CONTEXT_LABEL) == "mei"  # the module was not re-imported
    assert _(LABEL) == "Nu laden"


def test_the_language_can_change_while_the_app_runs(tmp_path):
    make_catalogue(tmp_path, "nl", {"Charge now": "Nu laden"})
    make_catalogue(tmp_path, "de", {"Charge now": "Jetzt laden"})
    results = []
    for language in ("nl", "de", "", "nl"):
        init([language] if language else [], tmp_path)
        results.append(translate(LABEL))
    assert results == ["Nu laden", "Jetzt laden", "Charge now", "Nu laden"]


# ---- Qt's own texts -----------------------------------------------------------------------------------------------------


def test_qts_own_buttons_follow_the_language(qapp, tmp_path):
    assert QCoreApplication.translate("QPlatformTheme", "Cancel") == "Cancel"
    translators = install_qt_translations(qapp, "nl")
    try:
        assert translators, "the PySide6 package ships qtbase_nl.qm"
        assert QCoreApplication.translate("QPlatformTheme", "Cancel") != "Cancel"
    finally:
        for translator in translators:
            qapp.removeTranslator(translator)
    assert QCoreApplication.translate("QPlatformTheme", "Cancel") == "Cancel"


def test_nothing_is_installed_for_english_or_a_language_qt_has_no_file_for(qapp):
    assert install_qt_translations(qapp, "") == []
    assert install_qt_translations(qapp, "xx") == []
    assert QCoreApplication.translate("QPlatformTheme", "Cancel") == "Cancel"


def test_setup_chooses_the_catalogue_and_installs_qts_texts(qapp, tmp_path):
    make_catalogue(tmp_path, "nl", {"Charge now": "Nu laden"})
    try:
        assert setup(qapp, ["nl"], tmp_path) == "nl"
        assert _("Charge now") == "Nu laden"
        assert qapp._qt_translators and QCoreApplication.translate("QPlatformTheme", "Cancel") != "Cancel"
    finally:
        setup(qapp, [], tmp_path)  # leave the application as it was found
    assert QCoreApplication.translate("QPlatformTheme", "Cancel") == "Cancel"


def test_changing_the_language_replaces_qts_texts_instead_of_stacking_them(qapp, tmp_path):
    make_catalogue(tmp_path, "nl", {"Charge now": "Nu laden"})
    make_catalogue(tmp_path, "de", {"Charge now": "Jetzt laden"})
    english = QCoreApplication.translate("QPlatformTheme", "Cancel")
    try:
        setup(qapp, ["nl"], tmp_path)
        dutch = QCoreApplication.translate("QPlatformTheme", "Cancel")
        setup(qapp, ["de"], tmp_path)
        german = QCoreApplication.translate("QPlatformTheme", "Cancel")
        assert len({english, dutch, german}) == 3  # three different words, not Dutch still showing under German
        setup(qapp, ["fr"], tmp_path)  # Qt has French, this app does not: stay consistently English
        assert QCoreApplication.translate("QPlatformTheme", "Cancel") == english
        setup(qapp, ["nl"], tmp_path)
        assert QCoreApplication.translate("QPlatformTheme", "Cancel") == dutch  # and back again
    finally:
        setup(qapp, [], tmp_path)
    assert QCoreApplication.translate("QPlatformTheme", "Cancel") == english


def test_setup_called_twice_does_not_leave_translators_behind(qapp, tmp_path):
    make_catalogue(tmp_path, "nl", {"Charge now": "Nu laden"})
    try:
        setup(qapp, ["nl"], tmp_path)
        first = list(qapp._qt_translators)
        setup(qapp, ["nl"], tmp_path)
        assert len(qapp._qt_translators) == len(first)
        for translator in first:  # the old ones were removed from the application, not just forgotten
            assert not qapp.removeTranslator(translator)
    finally:
        setup(qapp, [], tmp_path)


# ---- the developer tools --------------------------------------------------------------------------------------------------


SAMPLE = '''from app.i18n import _, ngettext, pgettext, N_, NC_

# i18n: {name} is the place the user chose
greeting = _("Welcome to {name}")
days = ngettext("{n} day", "{n} days", 2)
month = pgettext("month", "May")
LABEL = N_("Panel size")
SHORT = NC_("weekday, short", "Mon")
not_marked = "a plain string"
'''


@pytest.fixture
def sample_package(tmp_path):
    package = tmp_path / "pkg"
    package.mkdir()
    (package / "sample.py").write_text(SAMPLE)
    return package


def test_extraction_finds_every_kind_of_marker(sample_package, tmp_path):
    pot = tmp_path / "out" / f"{DOMAIN}.pot"
    assert i18n_tools.extract(sample_package, pot) == 5
    catalogue = read_po(open(pot, "rb"))
    found = {(m.context, m.id if isinstance(m.id, str) else m.id) for m in catalogue if m.id}
    assert found == {
        (None, "Welcome to {name}"),
        (None, ("{n} day", "{n} days")),
        ("month", "May"),
        (None, "Panel size"),
        ("weekday, short", "Mon"),
    }
    assert "a plain string" not in pot.read_text()


def test_extraction_keeps_the_note_for_the_translator_and_the_file_but_no_line_numbers(sample_package, tmp_path):
    pot = tmp_path / "t.pot"
    i18n_tools.extract(sample_package, pot, prefix="app")
    text = pot.read_text()
    assert "#. {name} is the place the user chose" in text
    assert "#: app/sample.py\n" in text and "sample.py:" not in text  # a file, never a line number
    assert "python-brace-format" in text  # translators are warned that {name} must stay


def test_the_template_is_the_same_every_time_it_is_made(sample_package, tmp_path):
    first, second = tmp_path / "a.pot", tmp_path / "b.pot"
    i18n_tools.extract(sample_package, first)
    i18n_tools.extract(sample_package, second)
    assert first.read_bytes() == second.read_bytes()
    assert "POT-Creation-Date: 2026-01-01 00:00+0000" in first.read_text()


def test_the_template_header_has_no_placeholder_author_or_version(sample_package, tmp_path):
    pot = tmp_path / "t.pot"
    i18n_tools.extract(sample_package, pot)
    text = pot.read_text()
    assert "FIRST AUTHOR" not in text and "EMAIL@ADDRESS" not in text.replace("FULL NAME <EMAIL@ADDRESS>", "")
    assert "VERSION" not in text and "Kelvin Vroegop" in text and i18n_tools.ISSUES_URL in text


def test_the_committed_template_is_up_to_date_with_the_code(tmp_path):
    fresh = tmp_path / "fresh.pot"
    i18n_tools.extract(i18n_tools.PACKAGE_DIR, fresh)
    assert fresh.read_bytes() == i18n_tools.POT.read_bytes(), "run: python -m app.i18n_tools extract"


def test_a_language_goes_from_template_to_a_working_translation(sample_package, tmp_path):
    pot = tmp_path / "po" / f"{DOMAIN}.pot"
    locale_dir = tmp_path / "locale"
    i18n_tools.extract(sample_package, pot)
    i18n_tools.init("nl", pot, locale_dir)
    po = locale_dir / "nl" / "LC_MESSAGES" / f"{DOMAIN}.po"
    assert po.exists()
    catalogue = read_po(open(po, "rb"), locale="nl")
    for message in catalogue:
        if message.id == "Panel size":
            message.string = "Paneelgrootte"
        elif message.id == ("{n} day", "{n} days"):
            message.string = ("{n} dag", "{n} dagen")
    with open(po, "wb") as handle:
        write_po(handle, catalogue)
    i18n_tools.compile_catalogues(locale_dir)
    assert (locale_dir / "nl" / "LC_MESSAGES" / f"{DOMAIN}.mo").exists()
    assert init(["nl"], locale_dir) == "nl"
    assert translate(N_("Panel size")) == "Paneelgrootte"
    assert ngettext("{n} day", "{n} days", 1) == "{n} dag" and ngettext("{n} day", "{n} days", 4) == "{n} dagen"


def test_updating_adds_new_texts_and_keeps_existing_translations(sample_package, tmp_path):
    pot = tmp_path / "t.pot"
    locale_dir = tmp_path / "locale"
    i18n_tools.extract(sample_package, pot)
    i18n_tools.init("nl", pot, locale_dir)
    po = locale_dir / "nl" / "LC_MESSAGES" / f"{DOMAIN}.po"
    catalogue = read_po(open(po, "rb"), locale="nl")
    catalogue.get("Panel size").string = "Paneelgrootte"
    with open(po, "wb") as handle:
        write_po(handle, catalogue)
    (sample_package / "sample.py").write_text(SAMPLE + '\nextra = _("A brand new text")\n')
    i18n_tools.extract(sample_package, pot)
    i18n_tools.update(pot, locale_dir)
    updated = read_po(open(po, "rb"), locale="nl")
    assert updated.get("A brand new text") is not None and updated.get("A brand new text").string == ""
    assert updated.get("Panel size").string == "Paneelgrootte"


def test_the_command_line_wrapper_reports_usage_for_an_unknown_command(capsys):
    assert i18n_tools.main(["frobnicate"]) == 2
    assert "extract" in capsys.readouterr().err


# ---- the whole app -------------------------------------------------------------------------------------------------------


@pytest.mark.parametrize("environment", [{"LANGUAGE": "nl", "LANG": "nl_NL.UTF-8"}, {"LC_ALL": "C"}, {"LANG": "de_DE.UTF-8", "LANGUAGE": ""}])
def test_the_whole_app_starts_under_other_languages_and_locales(tmp_path, environment):
    import subprocess
    import sys
    import time

    project = Path(__file__).resolve().parent.parent
    for name in ("config", "state", "cache", "run", "data"):
        (tmp_path / name).mkdir(mode=0o700, exist_ok=True)
    env = {k: v for k, v in os.environ.items() if k not in ("LANG", "LANGUAGE", "LC_ALL", "LC_MESSAGES")}
    env.update(
        environment,
        QT_QPA_PLATFORM="offscreen",
        HOME=str(tmp_path),
        XDG_CONFIG_HOME=str(tmp_path / "config"),
        XDG_STATE_HOME=str(tmp_path / "state"),
        XDG_CACHE_HOME=str(tmp_path / "cache"),
        XDG_RUNTIME_DIR=str(tmp_path / "run"),
        XDG_DATA_HOME=str(tmp_path / "data"),
    )
    process = subprocess.Popen([sys.executable, "-m", "app"], cwd=project, env=env, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
    try:
        time.sleep(4)
        assert process.poll() is None, process.stderr.read()
    finally:
        process.terminate()
        try:
            _, err = process.communicate(timeout=10)
        except subprocess.TimeoutExpired:
            process.kill()
            _, err = process.communicate()
    assert "Traceback" not in err
