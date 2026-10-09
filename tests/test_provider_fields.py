"""The Settings field machinery for provider settings: text and masked secret fields built at run time ."""

import os

os.environ.setdefault("QT_QPA_PLATFORM", "offscreen")


from car_charge_planner.forecast.providers.forecast_solar import ForecastSolar
from car_charge_planner.forecast.providers.open_meteo import OpenMeteo
from car_charge_planner.settings.fields import TabSpec, get_value, set_value
from car_charge_planner.settings.providers import provider_fields
from car_charge_planner.settings.tabs import FieldTab

SECRET = "FIELD-SECRET-77"


def tab_for(provider, cfg, secrets=None):
    return FieldTab(TabSpec(provider.id, provider.name, provider_fields(provider)), cfg, secrets)


def test_a_provider_declares_fields_with_the_right_kinds_and_keys():
    fields = provider_fields(OpenMeteo())
    assert [(f.key, f.kind) for f in fields] == [
        ("forecast.open-meteo.base_url", "text"),
        ("forecast.open-meteo.api_key", "secret"),
    ]
    assert all(f.label and f.hint for f in fields)
    assert [(f.key, f.kind) for f in provider_fields(ForecastSolar())] == [("forecast.forecast-solar.api_key", "secret")]


def test_values_live_in_the_forecast_section_of_the_config(cfg):
    key = "forecast.open-meteo.base_url"
    assert get_value(cfg, key) == ""
    chosen = set_value(cfg, key, "https://customer-api.open-meteo.com")
    assert chosen.forecast.settings == {"open-meteo": {"base_url": "https://customer-api.open-meteo.com"}}
    assert get_value(chosen, key) == "https://customer-api.open-meteo.com"
    assert cfg.forecast.settings == {}


def test_an_empty_value_is_no_setting_and_an_empty_provider_disappears(cfg):
    key = "forecast.open-meteo.base_url"
    assert set_value(set_value(cfg, key, "https://x.test"), key, "") == cfg


def test_the_text_field_is_a_plain_box_filled_from_the_config(qapp, cfg):
    cfg = set_value(cfg, "forecast.open-meteo.base_url", "https://x.test")
    tab = tab_for(OpenMeteo(), cfg)
    box = tab.inputs["forecast.open-meteo.base_url"]
    assert box.text() == "https://x.test"
    box.setText("  https://y.test \n")
    assert tab.apply_to(cfg).forecast.settings["open-meteo"]["base_url"] == "https://y.test"


def test_a_secret_never_goes_into_the_config(qapp, cfg):
    tab = tab_for(OpenMeteo(), cfg, {"forecast.open-meteo.api_key": SECRET})
    applied = tab.apply_to(cfg)
    assert applied == cfg and SECRET not in repr(applied)
    assert tab.collect_secrets() == {"forecast.open-meteo.api_key": SECRET}


def test_a_typed_secret_is_collected_trimmed_and_an_empty_one_is_left_out(qapp, cfg):
    tab = tab_for(OpenMeteo(), cfg)
    assert tab.collect_secrets() == {}
    tab.inputs["forecast.open-meteo.api_key"].setText(f"  {SECRET}\n")
    assert tab.collect_secrets() == {"forecast.open-meteo.api_key": SECRET}
    tab.inputs["forecast.open-meteo.api_key"].setText("")
    assert tab.collect_secrets() == {}


def test_editing_a_field_signals_a_change(qapp, cfg):
    tab = tab_for(OpenMeteo(), cfg)
    seen = []
    tab.changed.connect(lambda: seen.append(1))
    tab.inputs["forecast.open-meteo.api_key"].setText("x")
    tab.inputs["forecast.open-meteo.base_url"].setText("https://x.test")
    assert len(seen) == 2
