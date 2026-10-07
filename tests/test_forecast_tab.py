"""The Forecast tab in Settings (ticket P7)."""

import os

os.environ.setdefault("QT_QPA_PLATFORM", "offscreen")

import stat
import threading
from dataclasses import replace
from datetime import datetime, timedelta
from zoneinfo import ZoneInfo

import pytest
from PySide6.QtTest import QTest
from PySide6.QtWidgets import QApplication, QLabel, QLineEdit

from app import colours
from app.settings.window import SettingsWindow
from fake_provider import FakeProvider, Held, steps_for
from forecast.provider import BadResponse, NotConfigured, RateLimited, SettingSpec, Unavailable
from forecast.registry import ProviderRegistry, registry as builtin
from forecast.secret_store import dump_secrets, load_secrets
from planner.model import Forecast, load_config

from conftest import example_config

DAY = datetime.now(ZoneInfo("Europe/Amsterdam")).date()  # the Test button counts days from today
KEY = "TAB-SECRET-KEY-1"


@pytest.fixture
def qapp():
    return QApplication.instance() or QApplication([])


def two_days():
    return steps_for(DAY) + steps_for(DAY + timedelta(days=1))


def keyed(**kwargs):
    settings = (
        SettingSpec("account", "Account", "Your account name."),
        SettingSpec("api_key", "API key", "Your key.", optional=False, secret=True),
    )
    return FakeProvider(id="alpha", name="Alpha", settings=settings, **kwargs)


class Rig:
    def __init__(self, tmp_path, cfg=None, alpha=None, beta=None, secrets=None, registry=None):
        self.alpha = alpha or keyed(script=[two_days()])
        self.beta = beta or FakeProvider(id="beta", name="Beta", script=[two_days()])
        if registry is None:
            registry = ProviderRegistry()
            registry.register(self.alpha)
            registry.register(self.beta)
        self.registry = registry
        self.file = tmp_path / "config.toml"
        self.secrets_file = tmp_path / "secrets.toml"
        if secrets is not None:
            self.secrets_file.write_text(dump_secrets(secrets))
            os.chmod(self.secrets_file, 0o600)
        base = cfg or example_config()
        self.cfg = replace(base, forecast=Forecast("beta", "", {})) if cfg is None else cfg
        self.saved = []
        self.window = SettingsWindow(self.cfg, self.file, self.saved.append, registry)
        self.window.confirm_discard = lambda: True
        self.window.show()
        self.tab = self.window.forecast_tab

    def pick(self, role, provider_id):
        picker = self.tab.pickers[role]
        picker.setCurrentIndex(picker.findData(provider_id))

    def field(self, provider_id, setting):
        return self.tab.cards[provider_id].fields.inputs[f"forecast.{provider_id}.{setting}"]

    def wait_for_test(self, role):
        for thread in list(self.tab._threads):
            thread.join(5)
        QTest.qWait(40)
        return self.tab.results[role].text()


@pytest.fixture
def rig(tmp_path, qapp):
    return Rig(tmp_path)


def labels(widget):
    return [l.text() for l in widget.findChildren(QLabel) if l.isVisibleTo(widget)]


# ---- the choices -----------------------------------------------------------------------------------------------


def test_the_tab_sits_between_planning_and_interface(rig):
    titles = [rig.window.tabs.tabText(i) for i in range(rig.window.tabs.count())]
    assert titles.index("Forecast") == titles.index("Planning and notifications") + 1
    assert rig.tab.key == "forecast"


def test_a_new_install_shows_open_meteo_and_no_fallback(tmp_path, qapp):
    fresh = Rig(tmp_path, cfg=example_config(), registry=builtin)
    assert fresh.tab.pickers["primary"].currentData() == "open-meteo"
    fallback = fresh.tab.pickers["fallback"]
    assert fallback.itemText(0) == "None" and fallback.currentData() == ""
    assert [fallback.itemData(i) for i in range(fallback.count())] == ["", "open-meteo", "forecast-solar"]


def test_the_chosen_provider_is_described_with_what_it_sends_its_limits_and_terms(tmp_path, qapp):
    fresh = Rig(tmp_path, cfg=example_config(), registry=builtin)
    shown = labels(fresh.tab)
    assert any("free weather service" in t for t in shown)
    assert any(t.startswith("What is sent:") and "latitude and longitude" in t for t in shown)
    assert "Asks at most once every 60 minutes. Looks 4 days ahead." in shown
    card = fresh.tab.cards["open-meteo"]
    assert "https://open-meteo.com/en/terms" in card.terms.text() and card.terms.openExternalLinks()


def test_a_provider_card_follows_its_role(rig):
    assert rig.tab.slots["primary"].itemAt(0).widget() is rig.tab.cards["beta"]
    rig.pick("fallback", "alpha")
    assert rig.tab.slots["fallback"].itemAt(0).widget() is rig.tab.cards["alpha"]
    rig.pick("primary", "alpha")  # the primary is now the fallback's provider: the card moves, the fallback says so
    assert rig.tab.slots["primary"].itemAt(0).widget() is rig.tab.cards["alpha"]
    assert rig.tab.slots["fallback"].count() == 0
    assert "different provider" in rig.tab.results["fallback"].text() and not rig.tab.test_buttons["fallback"].isEnabled()


def test_the_same_provider_twice_cannot_be_saved(rig):
    rig.pick("fallback", "beta")
    assert not rig.window.save()
    assert "different provider" in rig.window.errors.text() and not rig.file.exists()


def test_required_settings_are_marked_and_secret_ones_masked(rig):
    rig.pick("primary", "alpha")
    assert rig.field("alpha", "api_key").line.echoMode().name == "Password"
    assert any("Required." in t for t in labels(rig.tab.cards["alpha"].fields))
    assert not any("Required." in t and "account" in t.lower() for t in labels(rig.tab.cards["alpha"].fields))


# ---- saving, dirty tracking, reset ---------------------------------------------------------------------------


def test_editing_a_choice_or_a_setting_marks_the_window_changed(rig):
    assert not rig.window.is_dirty() and rig.window.cancel_button.text() == "Close"
    rig.pick("fallback", "alpha")
    assert rig.window.is_dirty() and rig.window.cancel_button.text() == "Cancel"
    rig.pick("fallback", "")
    assert not rig.window.is_dirty()
    rig.pick("primary", "alpha")
    rig.field("alpha", "account").setText("me")
    assert rig.window.is_dirty()


def test_changing_only_a_secret_counts_as_a_change(tmp_path, qapp):
    rig = Rig(tmp_path, cfg=replace(example_config(), forecast=Forecast("alpha", "", {})))
    assert not rig.window.is_dirty()
    base = rig.window.current_config()
    rig.field("alpha", "api_key").setText(KEY)
    assert rig.window.current_config() == base  # the key is not in the config...
    assert rig.window.is_dirty() and rig.window.cancel_button.text() == "Cancel"  # ...and still it is a change
    rig.field("alpha", "api_key").setText("")
    assert not rig.window.is_dirty()


def test_saving_writes_plain_settings_to_the_config_and_secrets_to_their_own_file(rig):
    rig.pick("primary", "alpha")
    rig.pick("fallback", "beta")
    rig.field("alpha", "account").setText("me")
    rig.field("alpha", "api_key").setText(f"  {KEY}\n")
    assert rig.window.save()
    config_text = rig.file.read_text()
    assert KEY not in config_text and 'primary = "alpha"' in config_text and 'fallback = "beta"' in config_text
    assert '[forecast.alpha]\naccount = "me"' in config_text
    assert load_secrets(rig.secrets_file) == {"alpha": {"api_key": KEY}}
    assert stat.S_IMODE(rig.secrets_file.stat().st_mode) == 0o600
    assert load_config(rig.file).forecast.primary == "alpha" and rig.saved[-1].forecast.fallback == "beta"
    assert not rig.window.is_dirty() and rig.window.cancel_button.text() == "Close"


def test_the_saved_key_is_there_again_when_the_window_is_reopened(tmp_path, qapp):
    first = Rig(tmp_path)
    first.pick("primary", "alpha")
    first.field("alpha", "api_key").setText(KEY)
    first.window.save()
    second = Rig(tmp_path, cfg=load_config(first.file), alpha=first.alpha, beta=first.beta, registry=first.registry)
    assert second.field("alpha", "api_key").text() == KEY
    assert not second.window.is_dirty()


def test_a_secret_is_removed_from_the_file_when_it_is_cleared(tmp_path, qapp):
    rig = Rig(tmp_path, secrets={"alpha": {"api_key": KEY}}, cfg=replace(example_config(), forecast=Forecast("alpha", "", {})))
    assert rig.field("alpha", "api_key").text() == KEY
    rig.field("alpha", "api_key").setText("")
    rig.pick("primary", "beta")  # a provider with nothing required
    assert rig.window.save()
    assert load_secrets(rig.secrets_file) == {}


def test_reset_puts_the_tab_back_to_the_defaults_without_saving(tmp_path, qapp):
    rig = Rig(tmp_path, secrets={"alpha": {"api_key": KEY}}, cfg=replace(example_config(), forecast=Forecast("alpha", "beta", {"alpha": {"account": "me"}})))
    rig.window.tabs.setCurrentWidget(rig.tab)
    rig.window.reset_tab()
    assert rig.tab.pickers["primary"].currentData() == "open-meteo"  # the built-in choice (not in this test's registry)
    assert rig.tab.pickers["fallback"].currentData() == ""
    assert rig.field("alpha", "account").text() == "" and rig.field("alpha", "api_key").text() == ""
    assert rig.window.is_dirty()
    assert load_secrets(rig.secrets_file) == {"alpha": {"api_key": KEY}}  # nothing is saved until Save


# ---- validation ---------------------------------------------------------------------------------------------


def test_a_missing_required_setting_stops_the_save_and_names_it(rig):
    rig.pick("primary", "alpha")
    assert not rig.window.save()
    assert "Alpha: API key is required" in rig.window.errors.text()
    assert not rig.file.exists() and not rig.secrets_file.exists()


def test_a_provider_from_a_newer_version_is_shown_and_must_be_replaced_to_save(tmp_path, qapp):
    rig = Rig(tmp_path, cfg=replace(example_config(), forecast=Forecast("solcast", "", {})))
    picker = rig.tab.pickers["primary"]
    assert picker.currentData() == "solcast" and "not available" in picker.currentText()
    assert not rig.window.is_dirty()
    assert not rig.window.save() and "'solcast' is not known" in rig.window.errors.text()
    rig.pick("primary", "beta")
    assert rig.window.save()


def test_a_file_that_cannot_be_written_stops_everything_else(rig, monkeypatch):
    rig.pick("primary", "alpha")
    rig.field("alpha", "api_key").setText(KEY)

    def refuse(secrets, path):
        from forecast.secret_store import SecretsError

        raise SecretsError("Could not write secrets.toml: Permission denied")

    monkeypatch.setattr("app.settings.window.save_secrets", refuse)
    assert not rig.window.save()
    assert "Permission denied" in rig.window.errors.text() and not rig.file.exists()


def test_an_unreadable_secrets_file_is_said_on_the_tab_and_does_not_block_other_saves(tmp_path, qapp):
    (tmp_path / "secrets.toml").write_text("this is [not toml")
    os.chmod(tmp_path / "secrets.toml", 0o600)
    rig = Rig(tmp_path)
    assert any("not valid TOML" in t for t in labels(rig.tab))
    rig.pick("primary", "beta")
    rig.field("alpha", "account").setText("me")  # a plain setting changes; no secret is typed
    assert rig.window.save()
    assert (tmp_path / "secrets.toml").read_text() == "this is [not toml"  # left as it was


# ---- the Test button ----------------------------------------------------------------------------------------


def test_the_test_button_reports_the_days_it_got_and_saves_nothing(rig):
    rig.tab.test_buttons["primary"].click()
    assert rig.wait_for_test("primary") == "OK: 2 days of forecast"
    assert rig.beta.call_count == 1 and not rig.file.exists() and not rig.secrets_file.exists()
    assert colours.SUCCESS["light"] in rig.tab.results["primary"].styleSheet() or colours.SUCCESS["dark"] in rig.tab.results["primary"].styleSheet()


def test_one_day_is_said_in_the_singular(rig):
    rig.beta.script = [steps_for(DAY)]
    rig.tab.test_buttons["primary"].click()
    assert rig.wait_for_test("primary") == "OK: 1 day of forecast"


def test_the_test_uses_what_is_typed_not_what_is_saved(rig):
    rig.pick("primary", "alpha")
    rig.field("alpha", "account").setText("typed-account")
    rig.field("alpha", "api_key").setText(KEY)
    rig.tab.test_buttons["primary"].click()
    rig.wait_for_test("primary")
    assert rig.alpha.calls[0][1] == {"account": "typed-account", "api_key": KEY}
    assert not rig.file.exists() and not rig.secrets_file.exists()


def test_the_test_uses_the_location_as_typed_on_the_other_tabs(rig):
    rig.window.field_tabs[0].inputs["latitude"].setValue(48.85)
    rig.tab.test_buttons["primary"].click()
    rig.wait_for_test("primary")
    assert rig.beta.calls[0][0].latitude == 48.85


def test_the_fallback_has_its_own_test_button(rig):
    rig.pick("fallback", "alpha")
    rig.field("alpha", "api_key").setText(KEY)
    rig.tab.test_buttons["fallback"].click()
    assert rig.wait_for_test("fallback") == "OK: 2 days of forecast" and rig.alpha.call_count == 1 and rig.beta.call_count == 0


@pytest.mark.parametrize(
    "error, expected",
    [
        (RateLimited("busy", timedelta(minutes=5)), "Beta says there were too many requests. Try again in 5 minutes."),
        (RateLimited("busy", timedelta(seconds=20)), "Try again in 1 minute."),
        (RateLimited("busy"), "Beta says there were too many requests. Try again later."),
        (Unavailable("no network"), "Could not get a forecast from Beta: no network"),
        (NotConfigured("the address is wrong"), "Beta is not set up completely: the address is wrong"),
        (BadResponse("not JSON"), "Beta gave an answer the app does not understand: not JSON"),
        (ValueError(f"bug with {KEY}"), "Beta: unexpected error (ValueError)"),
    ],
)
def test_failures_are_told_in_plain_words(rig, error, expected):
    rig.beta.script = [error]
    rig.tab.test_buttons["primary"].click()
    message = rig.wait_for_test("primary")
    assert expected in message and KEY not in message
    assert colours.ERROR["light"] in rig.tab.results["primary"].styleSheet() or colours.ERROR["dark"] in rig.tab.results["primary"].styleSheet()
    assert rig.tab.test_buttons["primary"].isEnabled()


def test_a_missing_required_setting_is_said_without_asking_the_provider(rig):
    rig.pick("primary", "alpha")
    rig.tab.test_buttons["primary"].click()
    assert rig.tab.results["primary"].text() == "API key is required." and rig.alpha.call_count == 0


def test_without_a_location_the_test_says_where_to_enter_it(tmp_path, qapp):
    rig = Rig(tmp_path, cfg=replace(example_config(), latitude=0.0, longitude=0.0, forecast=Forecast("beta", "", {})))
    rig.tab.test_buttons["primary"].click()
    assert "Enter your location first" in rig.tab.results["primary"].text() and rig.beta.call_count == 0


def test_while_a_test_runs_the_button_waits_and_the_window_stays_usable(rig):
    release = threading.Event()
    rig.beta.script = [Held(release, two_days())]
    rig.tab.test_buttons["primary"].click()
    assert not rig.tab.test_buttons["primary"].isEnabled() and "Asking Beta" in rig.tab.results["primary"].text()
    rig.pick("fallback", "alpha")  # the window answers meanwhile
    assert rig.window.is_dirty()
    release.set()
    assert rig.wait_for_test("primary") == "OK: 2 days of forecast"
    assert rig.tab.test_buttons["primary"].isEnabled()


def test_the_result_is_cleared_when_the_tab_is_loaded_again(rig):
    rig.tab.test_buttons["primary"].click()
    rig.wait_for_test("primary")
    rig.tab.load(rig.window.stored)
    assert rig.tab.results["primary"].text() == ""


# ---- the tab itself ---------------------------------------------------------------------------------------


def test_the_tab_scrolls_so_two_providers_are_not_squeezed(rig):
    from PySide6.QtWidgets import QScrollArea

    assert rig.tab.findChild(QScrollArea) is not None
    for label in rig.tab.findChildren(QLabel):
        if label.wordWrap() and label.text() and label.isVisibleTo(rig.tab):
            assert label.height() >= label.heightForWidth(label.width())


def test_the_secret_box_is_not_a_plain_text_box(rig):
    rig.pick("primary", "alpha")
    plain = [w for w in rig.tab.cards["alpha"].findChildren(QLineEdit) if w.echoMode().name == "Normal"]
    secret = rig.field("alpha", "api_key").line
    assert secret not in plain and secret.echoMode().name == "Password"


def test_results_use_the_accent_colours_of_the_theme(tmp_path, qapp):
    from PySide6.QtGui import QColor, QPalette

    original = qapp.palette()
    palette = QPalette(original)
    palette.setColor(QPalette.ColorRole.Window, QColor("#31363b"))
    palette.setColor(QPalette.ColorRole.WindowText, QColor("#eff0f1"))
    qapp.setPalette(palette)
    try:
        rig = Rig(tmp_path)
        rig.tab.test_buttons["primary"].click()
        rig.wait_for_test("primary")
        assert colours.SUCCESS["dark"] in rig.tab.results["primary"].styleSheet()
        rig.beta.script = [Unavailable("down")]
        rig.tab.test_buttons["primary"].click()
        rig.wait_for_test("primary")
        assert colours.ERROR["dark"] in rig.tab.results["primary"].styleSheet()
    finally:
        qapp.setPalette(original)


def test_the_days_counted_are_today_onwards_even_when_the_first_step_belongs_to_yesterday():
    """Found in the live check: Open-Meteo's first step is the hour before midnight, so four days of data
    touch five dates, and the Test button said "5 days"."""
    from app.settings.forecast_tab import forecast_days
    from planner.model import ForecastStep

    tz = ZoneInfo("Europe/Amsterdam")
    start = datetime(2026, 7, 2, 23, tzinfo=tz)  # yesterday 23:00
    steps = [ForecastStep(start + timedelta(hours=h), start + timedelta(hours=h + 1), 1.0) for h in range(96)]
    assert forecast_days(steps, datetime(2026, 7, 3, 7, tzinfo=tz), tz) == 4
    assert forecast_days(steps[:25], datetime(2026, 7, 3, 7, tzinfo=tz), tz) == 1
    assert forecast_days([], datetime(2026, 7, 3, 7, tzinfo=tz), tz) == 0


def test_the_terms_link_is_readable_on_a_dark_window(tmp_path, qapp):
    """Found by looking at the rendered tab: the default link blue was nearly invisible on a dark window."""
    from PySide6.QtGui import QColor, QPalette

    original = qapp.palette()
    palette = QPalette(original)
    palette.setColor(QPalette.ColorRole.Window, QColor("#31363b"))
    palette.setColor(QPalette.ColorRole.WindowText, QColor("#eff0f1"))
    qapp.setPalette(palette)
    try:
        rig = Rig(tmp_path)
        link = rig.tab.cards["beta"].terms
        assert colours.LINK["dark"] in link.text() and "https://" in link.text()
    finally:
        qapp.setPalette(original)
