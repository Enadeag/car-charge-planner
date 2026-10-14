"""The set-up wizard window: navigation, Welcome, Finish, leaving early, one write."""

import os

os.environ.setdefault("QT_QPA_PLATFORM", "offscreen")

import time
from dataclasses import replace

import pytest
from PySide6.QtTest import QTest
from PySide6.QtWidgets import QLabel

from car_charge_planner.app_settings import AppSettings, DesktopSettings
from car_charge_planner.config.model import Config, Setup, load_config
from car_charge_planner.engine import FileStore
from car_charge_planner.forecast.check import Attempt
from car_charge_planner.setup.finish import FinishPage
from car_charge_planner.setup.pages import Page
from car_charge_planner.setup.welcome import WelcomePage
from car_charge_planner.setup.wizard import PAGES, SetupWizard
from conftest import answers, engine_for, example_config, settle_controller

NL = {"LANG": "nl_NL.UTF-8"}
US = {"LANG": "en_US.UTF-8"}


def sub(tmp_path, name):
    folder = tmp_path / name
    folder.mkdir()
    return folder


class Rig:
    def __init__(self, tmp_path, cfg=None, environ=NL, check=None, registry=None, pages=None, file=None, app=None):
        self.file = file or tmp_path / "config.toml"
        self.app = DesktopSettings(app)
        self.finished = []
        self.checked = []
        self.outcome = check or Attempt(True, 2, "OK: 2 days of forecast")
        self.wizard = SetupWizard(
            engine_for(cfg if cfg is not None else example_config(), self.file, registry=registry),
            self.app,
            self.finished.append,
            check=self._check,
            page_classes=pages,
            environ=environ,
            qt_locale_name="",
        )
        self.asked = []
        self.answer = True
        self.wizard.confirm_leave = lambda: (self.asked.append(1), self.answer)[1]
        self.wizard.show()

    def _check(self, cfg, done):
        self.checked.append(cfg)
        done(self.outcome)

    def settle(self):
        QTest.qWait(40)

    def at_finish(self):
        self.wizard.navigate("finish")
        self.settle()
        return self.wizard.pages["finish"]


@pytest.fixture
def rig(tmp_path, qapp):
    return Rig(tmp_path)


def texts(widget):
    return [l.text() for l in widget.findChildren(QLabel)]


def test_it_opens_on_welcome_with_next_and_no_back(rig):
    w = rig.wizard
    assert w.current == "welcome" and w.heading.text() == "Welcome" and w.step.text() == "Step 1 of 6"
    assert not w.back_button.isEnabled() and w.next_button.isEnabled() and w.next_button.text() == "Next"
    assert [type(p) for p in w.pages.values()] == PAGES and w.sequence == ["welcome", "location", "solar", "car", "next", "finish"]


def test_next_and_back_move_through_the_pages_and_keep_the_answers(rig):
    w = rig.wizard
    w.pages["welcome"].units.setCurrentIndex(w.pages["welcome"].units.findData("imperial"))
    w.next()
    assert w.current == "location" and w.step.text() == "Step 2 of 6"
    w.next()
    w.next()
    w.next()
    w.next()
    assert w.current == "finish" and w.heading.text() == "Finish" and w.step.text() == "Step 6 of 6"
    assert w.back_button.isEnabled() and w.next_button.text() == "Finish"
    rig.settle()
    for _ in range(5):
        w.back()
    assert w.current == "welcome" and w.pages["welcome"].units.currentData() == "imperial"  # what was chosen is still there
    assert not w.back_button.isEnabled()


def test_a_page_can_stop_next(rig):
    class Stubborn(Page):
        id = "welcome"
        title = "Stubborn"
        allowed = False

        def can_continue(self):
            return self.allowed

    w = Rig(sub(rig.file.parent, "two"), pages=[Stubborn, FinishPage]).wizard
    assert not w.next_button.isEnabled()
    w.next()
    assert w.current == "welcome"
    w.pages["welcome"].allowed = True
    w.pages["welcome"].changed.emit()
    assert w.next_button.isEnabled()


def test_pages_outside_the_sequence_are_reached_by_navigating_and_left_with_back(tmp_path, qapp):
    class First(Page):
        id = "first"
        title = "First"

    class Side(Page):
        id = "side"
        title = "Side"
        in_sequence = False

    class Last(Page):
        id = "last"
        title = "Last"

    rig = Rig(tmp_path, pages=[First, Side, Last])
    w = rig.wizard
    assert w.sequence == ["first", "last"] and w.step.text() == "Step 1 of 2"
    w.navigate("side")
    assert w.current == "side" and w.step.text() == "" and w.back_button.isEnabled()
    w.back()
    assert w.current == "first"
    w.navigate("nowhere")
    assert w.current == "first"


def test_welcome_says_what_the_app_does_and_what_leaves_the_computer(rig):
    shown = " ".join(texts(rig.wizard.pages["welcome"]))
    assert "tells you when to charge your electric car" in shown
    assert "Open-Meteo is sent: Your location (latitude and longitude), your time zone and the tilt and direction of your panels." in shown
    assert "The place you type in the next step is sent to Open-Meteo to find it." in shown
    assert "no account and no tracking" in shown


def test_welcome_names_the_provider_and_says_nothing_about_place_search_when_it_has_none(tmp_path, qapp):
    cfg = replace(example_config(), forecast=replace(example_config().forecast, primary="forecast-solar"))
    shown = " ".join(texts(Rig(tmp_path, cfg=cfg).wizard.pages["welcome"]))
    assert "Forecast.Solar is sent: Your location (latitude and longitude), the tilt and direction of your panels and their size in kWp." in shown
    assert "place you type" not in shown
    unknown = replace(example_config(), forecast=replace(example_config().forecast, primary="solcast"))
    assert "forecast service" in " ".join(texts(Rig(sub(tmp_path, "u"), cfg=unknown).wizard.pages["welcome"]))


def test_the_units_are_preselected_from_the_region_on_a_new_install(tmp_path, qapp):
    assert Rig(sub(tmp_path, "a"), cfg=Config(), environ=US).wizard.pages["welcome"].units.currentData() == "imperial"
    assert Rig(sub(tmp_path, "b"), cfg=Config(), environ=NL).wizard.pages["welcome"].units.currentData() == "metric"


def test_running_set_up_again_keeps_the_owners_units(tmp_path, qapp):
    rig = Rig(tmp_path, cfg=example_config(), environ=US, app=AppSettings(units="metric"))
    assert rig.wizard.pages["welcome"].units.currentData() == "metric"


def test_the_choice_of_units_is_kept_in_the_answers_when_the_page_is_left(rig):
    w = rig.wizard
    w.pages["welcome"].units.setCurrentIndex(w.pages["welcome"].units.findData("imperial"))
    assert w.state.units == "metric"  # not yet: the page keeps it until it is left
    w.next()
    assert w.state.units == "imperial"


def test_finish_shows_the_recap_with_typical_values_marked(tmp_path, qapp):
    rig = Rig(tmp_path, cfg=replace(example_config(), setup=Setup(("panels.kwp", "car.km_per_day"))))
    page = rig.at_finish()
    shown = texts(page)
    assert "Solar panels" in shown and "Charger" in shown and "Car away" in shown
    assert "4.80 kWp (typical value)" in shown and "86 km (typical value)" in shown and "South-east (-45°)" in shown
    assert any(t.startswith("Based on typical values for: Panel size, Driving per day.") for t in shown)


def test_a_group_has_a_change_link_only_when_its_page_exists(rig):
    page = rig.at_finish()
    links = [l for l in page.findChildren(QLabel) if "href=" in l.text()]
    assert [l.text().split("href='")[1].split("'")[0] for l in links] == ["location", "solar", "car", "car", "house", "away"]  # the Car and Charger groups both change on the car page


def test_a_change_link_returns_to_that_page_and_back_comes_back(tmp_path, qapp):
    class Location(Page):
        id = "location"
        title = "Location"

    rig = Rig(tmp_path, pages=[WelcomePage, Location, FinishPage])
    rig.wizard.next()
    rig.wizard.next()
    rig.settle()
    links = [l for l in rig.wizard.pages["finish"].findChildren(QLabel) if "href='location'" in l.text()]
    assert len(links) == 1 and "Change" in links[0].text()
    links[0].linkActivated.emit("location")
    assert rig.wizard.current == "location"
    rig.wizard.back()
    assert rig.wizard.current == "finish"


def test_the_connection_check_runs_with_the_chosen_place_and_says_what_it_got(rig):
    page = rig.at_finish()
    assert len(rig.checked) == 1 and rig.checked[0].system.latitude == 52.37
    assert page.check_status.text() == "Forecast received for 52.37, 4.90: 2 days."
    assert page.check_button.isEnabled() and rig.wizard.next_button.isEnabled()


def test_the_place_by_name_is_what_the_check_reports(rig):
    rig.wizard.state.place_label = "Utrecht, Utrecht, The Netherlands"
    rig.at_finish()
    assert "Forecast received for Utrecht, Utrecht, The Netherlands: 2 days." == rig.wizard.pages["finish"].check_status.text()


def test_one_day_is_said_in_the_singular(tmp_path, qapp):
    rig = Rig(tmp_path, check=Attempt(True, 1, "OK: 1 day of forecast"))
    assert rig.at_finish().check_status.text().endswith(": 1 day.")


def test_a_failed_check_is_said_but_does_not_stop_finish(tmp_path, qapp):
    rig = Rig(tmp_path, check=Attempt(False, message="Could not get a forecast from Open-Meteo: no network"))
    page = rig.at_finish()
    assert "Could not get a forecast from Open-Meteo: no network" in page.check_status.text()
    assert "You can still finish" in page.check_status.text() and rig.wizard.next_button.isEnabled()
    assert rig.wizard.finish() and rig.file.exists()


def test_check_again_asks_again(rig):
    page = rig.at_finish()
    page.check_button.click()
    rig.settle()
    assert len(rig.checked) == 2


def test_nothing_is_asked_of_the_provider_before_a_place_is_chosen(tmp_path, qapp):
    rig = Rig(tmp_path, cfg=Config())
    page = rig.at_finish()
    assert rig.checked == [] and not page.check_button.isEnabled()
    assert "Choose a place first" in page.check_status.text()
    assert not rig.wizard.next_button.isEnabled() and "not chosen yet" in texts(page)


def test_the_check_asks_the_chosen_provider_off_the_window_thread_with_the_saved_key(tmp_path, qapp):
    import threading
    from datetime import datetime

    from car_charge_planner.config.model import Forecast
    from car_charge_planner.controller import Controller
    from car_charge_planner.forecast.provider import SettingSpec
    from car_charge_planner.forecast.registry import ProviderRegistry
    from conftest import TZ, FakeNotifier, NoForecast
    from fake_provider import FakeProvider, steps_for

    threads = []
    key = SettingSpec("api_key", "API key", "Your key.", optional=False, secret=True)
    today = datetime.now(TZ).date()
    provider = FakeProvider(id="keyed", settings=(key,), script=[lambda: (threads.append(threading.current_thread()), steps_for(today))[1]])
    registry = ProviderRegistry()
    registry.register(provider)
    store = FileStore(tmp_path / "config.toml")
    store.save_secrets({"keyed": {"api_key": "K-1"}})
    controller = Controller(replace(example_config(), forecast=Forecast("keyed", "", {})), store, FakeNotifier(), NoForecast(), registry=registry)
    wizard = SetupWizard(controller.engine, controller.app_settings, lambda c: None, environ=NL, qt_locale_name="")
    wizard.navigate("finish")
    page = wizard.pages["finish"]
    deadline = time.monotonic() + 5
    while not page.check_button.isEnabled() and time.monotonic() < deadline:
        QTest.qWait(20)
    assert "Forecast received" in page.check_status.text()
    assert threads and threads[0] is not threading.main_thread() and provider.calls[0][1] == {"api_key": "K-1"}


def test_finish_writes_the_config_once_hands_it_over_and_closes(rig):
    rig.wizard.pages["welcome"].units.setCurrentIndex(rig.wizard.pages["welcome"].units.findData("imperial"))
    rig.at_finish()
    assert not rig.file.exists() and rig.finished == []
    rig.wizard.next()
    assert rig.wizard.finished_ok and not rig.wizard.isVisible()
    written = load_config(rig.file)
    assert written.system.latitude == 52.37 and rig.app.current.units == "imperial"
    assert rig.finished == [written] and rig.asked == []


def test_a_re_run_writes_back_what_it_was_given_untouched(tmp_path, qapp):
    cfg = replace(example_config(), setup=Setup(("panels.kwp",)))
    rig = Rig(tmp_path, cfg=cfg)
    rig.at_finish()
    rig.wizard.next()
    assert load_config(rig.file) == cfg  # the typical list and every value as they were


def test_a_draft_that_does_not_pass_the_checks_writes_nothing_and_says_why(tmp_path, qapp):
    rig = Rig(tmp_path)
    rig.at_finish()
    rig.wizard.state.config = rig.wizard.state.config.with_system(charger=replace(rig.wizard.state.config.system.charger, phases=2))
    assert not rig.wizard.finish()
    assert rig.wizard.errors.isVisible() and "phases" in rig.wizard.errors.text()
    assert not rig.file.exists() and rig.finished == [] and rig.wizard.isVisible()


def test_a_file_that_cannot_be_written_is_said_and_the_wizard_stays(tmp_path, qapp):
    blocker = tmp_path / "blocked"
    blocker.write_text("a file where the directory should be", encoding="utf-8")
    rig = Rig(tmp_path, file=blocker / "config.toml")
    rig.at_finish()
    assert not rig.wizard.finish() and "Could not write" in rig.wizard.errors.text() and rig.finished == []


def test_closing_a_wizard_nobody_touched_just_closes(rig):
    rig.wizard.reject()
    assert rig.asked == [] and not rig.wizard.isVisible() and not rig.file.exists()


def test_closing_after_a_change_asks_and_staying_keeps_everything(rig):
    rig.wizard.pages["welcome"].units.setCurrentIndex(rig.wizard.pages["welcome"].units.findData("imperial"))
    rig.wizard.next()
    rig.answer = False
    rig.wizard.reject()
    assert rig.asked == [1] and rig.wizard.isVisible() and rig.wizard.current == "location"


def test_leaving_discards_everything_and_writes_nothing(rig):
    rig.wizard.state.config = rig.wizard.state.config.with_system(latitude=10.0)
    rig.at_finish()
    rig.wizard.reject()
    assert rig.asked == [1] and not rig.wizard.isVisible()
    assert not rig.file.exists() and rig.finished == [] and not rig.wizard.finished_ok


def test_having_moved_on_counts_as_something_done(rig):
    rig.wizard.next()  # no answer changed, but the owner went on
    rig.wizard.reject()
    assert rig.asked == [1]


def test_escape_is_the_same_as_closing(rig):
    from PySide6.QtCore import Qt

    rig.wizard.state.place_label = "Somewhere"
    QTest.keyClick(rig.wizard, Qt.Key.Key_Escape)
    assert rig.asked == [1] and not rig.wizard.isVisible()


def test_a_finished_wizard_does_not_ask_when_it_closes(rig):
    rig.at_finish()
    rig.wizard.next()
    rig.wizard.reject()
    assert rig.asked == []


def make_controller(cfg, tmp_path, monkeypatch):
    from car_charge_planner.controller import Controller
    from conftest import FakeNotifier

    monkeypatch.setenv("XDG_STATE_HOME", str(tmp_path))
    monkeypatch.setattr("car_charge_planner.forecast.service.ForecastService.get", lambda *a, **k: None)
    return Controller(cfg, FileStore(tmp_path / "config.toml"), FakeNotifier())


def make_windows(controller, shown=None):
    from car_charge_planner.app_windows import AppWindows

    return AppWindows(controller, (lambda: shown.append(1)) if shown is not None else (lambda: None))


def test_the_shell_opens_one_wizard_at_a_time(tmp_path, qapp, monkeypatch):
    controller = make_controller(example_config(), tmp_path, monkeypatch)
    windows = make_windows(controller)
    windows.open_setup()
    first = windows.setup_wizard
    assert first.isVisible() and first.state.config == controller.engine.cfg
    windows.open_setup()
    assert windows.setup_wizard is first
    first.reject()  # untouched: closes without asking
    windows.open_setup()
    assert windows.setup_wizard is not first and windows.setup_wizard.isVisible()
    windows.setup_wizard.close()


def test_finishing_applies_the_new_config_in_the_running_app(tmp_path, qapp, monkeypatch):
    controller = make_controller(example_config(), tmp_path, monkeypatch)
    shown = []
    windows = make_windows(controller, shown)
    windows.open_setup()
    wizard = windows.setup_wizard
    wizard.check = answers(Attempt(True, 2, "OK: 2 days of forecast"))
    wizard.pages["welcome"].units.setCurrentIndex(wizard.pages["welcome"].units.findData("imperial"))
    wizard.next()
    wizard.next()
    wizard.next()
    wizard.next()
    wizard.next()
    wizard.next()
    settle_controller(controller)  # applying the config starts a fetch
    assert controller.app_settings.current.units == "imperial"
    assert shown == [1]
