"""Texts built by the app follow the language: word order, plurals, numbers, names, tab keys."""

from datetime import date, datetime, time, timedelta

import pytest

from car_charge_planner.app_settings import DesktopSettings
from car_charge_planner.away_input import away_summary, describe_rule
from car_charge_planner.i18n import init, number
from car_charge_planner.names import month_short, short_date, weekday_long
from car_charge_planner.planner.model import AwayRule
from car_charge_planner.storage.state import BatteryReading
from car_charge_planner.view_model import ForecastSource, battery_status, day_label, footer_text
from conftest import TZ, at, engine_for
from test_i18n import make_catalogue

DAY = date(2026, 7, 3)  # a Friday


@pytest.fixture(autouse=True)
def english_after_every_test():
    yield
    init(languages=[])


def test_english_is_unchanged_without_a_catalogue():
    assert number(1.55, 1) == "1.6" and number(30.0) == "30"
    assert short_date(date(2026, 10, 8)) == "Thu 8 Oct"
    assert weekday_long(0) == "Monday" and month_short(4) == "May"


def test_names_and_the_order_of_a_date_are_the_languages(tmp_path):
    make_catalogue(
        tmp_path,
        "xx",
        {
            ("weekday, short", "Thu"): "do",
            ("month, short", "Oct"): "okt",
            ("weekday", "Monday"): "maandag",
            "{weekday} {day} {month}": "{day} {month} ({weekday})",
        },
    )
    init(["xx"], tmp_path)
    assert short_date(date(2026, 10, 8)) == "8 okt (do)"
    assert weekday_long(0) == "maandag"


def test_a_comma_can_replace_the_decimal_point(tmp_path):
    make_catalogue(tmp_path, "xx", {("decimal separator", "."): ","})
    init(["xx"], tmp_path)
    assert number(1.55, 1) == "1,6" and number(7) == "7"
    assert footer_text(None, False, ForecastSource("Credit"))  # texts that carry no number are untouched


def test_the_battery_line_has_a_plural_and_a_word_order_of_its_own(tmp_path):
    make_catalogue(
        tmp_path,
        "xx",
        {
            ("Battery {pct}%, entered {n} day ago. Please update.", "Battery {pct}%, entered {n} days ago. Please update."): (
                "{n} dag geleden: accu {pct}%",
                "{n} dagen geleden: accu {pct}%",
            )
        },
    )
    init(["xx"], tmp_path)
    now = at(DAY, 12)
    one = battery_status(BatteryReading(40, now - timedelta(days=1)), now)
    many = battery_status(BatteryReading(40, now - timedelta(days=3)), now)
    assert one == ("1 dag geleden: accu 40%", True)
    assert many == ("3 dagen geleden: accu 40%", True)


def test_today_and_tomorrow_are_translated_but_other_days_use_the_names(tmp_path):
    make_catalogue(tmp_path, "xx", {"Today": "Vandaag", "Tomorrow": "Morgen", ("weekday, short", "Sat"): "za"})
    init(["xx"], tmp_path)
    assert day_label(DAY, DAY) == "Vandaag"
    assert day_label(DAY + timedelta(days=1), DAY) == "Morgen"
    assert day_label(DAY + timedelta(days=1), DAY - timedelta(days=3)).startswith("za ")


def test_the_advice_reason_is_a_whole_phrase_for_today_tomorrow_and_a_named_day(tmp_path, cfg):
    from car_charge_planner.planner.advice import GridAdvice
    from car_charge_planner.view_model import advice_view

    make_catalogue(
        tmp_path,
        "xx",
        {
            "Battery would drop to {level}% (minimum {minimum}%) by the end of today.": "vandaag: {level}% (min {minimum}%)",
            "Battery would drop to {level}% (minimum {minimum}%) by the end of tomorrow.": "morgen: {level}% (min {minimum}%)",
            "Battery would drop to {level}% (minimum {minimum}%) by the end of {day}.": "op {day}: {level}% (min {minimum}%)",
        },
    )
    init(["xx"], tmp_path)
    now = at(DAY, 7)
    reasons = [advice_view(GridAdvice(d, 8.0, 22.0, 17.0, None), now, cfg, TZ).reason for d in
               (DAY, DAY + timedelta(days=1), date(2026, 7, 8))]
    assert reasons == ["vandaag: 8% (min 30%)", "morgen: 8% (min 30%)", "op Wed 8 Jul: 8% (min 30%)"]


def test_away_texts_follow_the_language(tmp_path):
    make_catalogue(
        tmp_path,
        "xx",
        {"Every {weekday}, {start}-{end}": "elke {weekday} van {start} tot {end}", ("weekday", "Thursday"): "donderdag"},
    )
    init(["xx"], tmp_path)
    assert describe_rule(AwayRule(3, time(6, 0), time(19, 0))) == "elke donderdag van 06:00 tot 19:00"
    assert away_summary((), (), datetime(2026, 7, 3, tzinfo=TZ), TZ) == "No absences planned"


def test_a_tab_is_found_by_its_key_in_any_language(tmp_path):
    from PySide6.QtWidgets import QApplication

    from car_charge_planner.config.model import Config
    from car_charge_planner.settings.window import SettingsWindow

    QApplication.instance() or QApplication([])
    make_catalogue(tmp_path, "xx", {"Away rules": "Afwezigheid", "Solar and location": "Zon en locatie"})
    init(["xx"], tmp_path)
    window = SettingsWindow(engine_for(Config(), tmp_path / "config.toml"), DesktopSettings())
    titles = [window.tabs.tabText(i) for i in range(window.tabs.count())]
    assert "Afwezigheid" in titles and "Zon en locatie" in titles
    window.show_tab("away")
    assert window.tabs.tabText(window.tabs.currentIndex()) == "Afwezigheid"
    window.show_tab("solar")
    assert window.tabs.tabText(window.tabs.currentIndex()) == "Zon en locatie"


def test_validation_messages_reach_the_user_in_their_language(tmp_path):

    from car_charge_planner.config.model import Config, ConfigError, validate
    from car_charge_planner.planner.model import Panels

    make_catalogue(tmp_path, "xx", {"Invalid config:": "Ongeldig:", "{panels} kwp must be above 0": "{panels} kwp moet boven 0 zijn"})
    init(["xx"], tmp_path)
    with pytest.raises(ConfigError) as err:
        validate(Config().with_system(panels=(Panels(kwp=0),)))
    assert str(err.value).startswith("Ongeldig:") and "kwp moet boven 0 zijn" in str(err.value)
