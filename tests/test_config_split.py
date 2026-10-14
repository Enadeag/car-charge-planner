"""The settings split: the engine keeps the system and the planning preferences in config.toml, the desktop app its own
settings in app.toml, and a config.toml from before the split is divided between them on the first start."""

import ast
import tomllib
from dataclasses import fields
from pathlib import Path

import pytest

from app_process import RunningApp
from car_charge_planner.app_settings import AppSettings, AppSettingsFile, DesktopSettings, adopt_interface, load_app_settings
from car_charge_planner.config.model import CONFIG_SCHEMA, Config, load_config
from car_charge_planner.engine import FileStore
from car_charge_planner.planner.model import HomeSystem, PlanningPreferences
from car_charge_planner.settings.window import SettingsWindow
from conftest import engine_for, example_config

VERSION_2 = """\
schema = 2
timezone = "Europe/Amsterdam"
latitude = 52.37
longitude = 4.9

[[panels]]
kwp = 4.8
tilt_deg = 35
azimuth_deg = -45
system_losses = 0.14

[house]
baseload_kw = 0.6

[car]
usable_battery_kwh = 70
consumption_kwh_per_100km = 18
km_per_day = 40
minimum_pct = 25
target_pct = 90

[charger]
phases = 1
min_amps = 6
max_amps = 32
volts = 230
efficiency = 0.9

[planning]
margin = 0.8
import_tolerance_kwh = 0.2
min_window_minutes = 45

[notify]
lead_minutes = 20

[interface]
mode = "window"
units = "imperial"
language = "nl"

[forecast]
primary = "open-meteo"
fallback = ""

[setup]
typical = ["house.baseload_kw"]

[away]
recurring = [
  {weekday = 3, start = "06:00", end = "19:00"},
]
"""

VERSION_1 = """\
latitude = 52.37
longitude = 4.9

[panels]
kwp = 4.2

[car]
minimum_pct = 35

[notify]
lead_minutes = 5

[interface]
mode = "tray"
"""


def start(folder: Path) -> tuple[Config, AppSettings]:
    """What the desktop app does with its files at a start, in its order (main.py)."""
    store, app_file = FileStore(folder / "config.toml"), AppSettingsFile(folder / "app.toml")
    adopt_interface(store.config_file, app_file.path)
    return store.load_config(), app_file.load()


def written(folder: Path) -> dict:
    return tomllib.loads((folder / "config.toml").read_text(encoding="utf-8"))


def test_a_version_2_file_is_split_into_config_and_app_settings(tmp_path):
    (tmp_path / "config.toml").write_text(VERSION_2, encoding="utf-8")
    cfg, app = start(tmp_path)
    assert app == AppSettings(mode="window", units="imperial", language="nl")
    assert cfg.planning == PlanningPreferences(minimum_pct=25, target_pct=90, margin=0.8, notify_lead_minutes=20)
    assert cfg.system.car.km_per_day == 40 and cfg.system.latitude == 52.37 and cfg.setup.typical == ("house.baseload_kw",)
    data = written(tmp_path)
    assert data["schema"] == CONFIG_SCHEMA == 3
    assert "interface" not in data and "notify" not in data and "minimum_pct" not in data["car"]
    assert data["planning"]["minimum_pct"] == 25 and data["planning"]["notify_lead_minutes"] == 20
    assert load_config(tmp_path / "config.toml") == cfg


def test_a_second_start_changes_nothing(tmp_path):
    (tmp_path / "config.toml").write_text(VERSION_2, encoding="utf-8")
    first = start(tmp_path)
    files = [(tmp_path / name).read_bytes() for name in ("config.toml", "app.toml")]
    assert start(tmp_path) == first
    assert [(tmp_path / name).read_bytes() for name in ("config.toml", "app.toml")] == files


def test_a_start_stopped_after_writing_app_toml_loses_nothing(tmp_path):
    (tmp_path / "config.toml").write_text(VERSION_2, encoding="utf-8")
    assert adopt_interface(tmp_path / "config.toml", tmp_path / "app.toml")  # and then the app stops
    assert (tmp_path / "config.toml").read_text(encoding="utf-8") == VERSION_2
    cfg, app = start(tmp_path)
    assert app == AppSettings(mode="window", units="imperial", language="nl") and "interface" not in written(tmp_path)
    assert cfg.planning.notify_lead_minutes == 20


def test_an_existing_app_toml_is_kept_and_the_old_table_only_dropped(tmp_path):
    (tmp_path / "config.toml").write_text(VERSION_2, encoding="utf-8")
    AppSettingsFile(tmp_path / "app.toml").save(AppSettings(mode="tray"))
    _cfg, app = start(tmp_path)
    assert app == AppSettings(mode="tray") and "interface" not in written(tmp_path)


def test_when_app_toml_cannot_be_written_config_toml_is_left_as_it_was(tmp_path):
    (tmp_path / "config.toml").write_text(VERSION_2, encoding="utf-8")
    (tmp_path / "blocked").write_text("a file where a folder should be", encoding="utf-8")
    with pytest.raises(OSError):
        adopt_interface(tmp_path / "config.toml", tmp_path / "blocked" / "app.toml")
    assert (tmp_path / "config.toml").read_text(encoding="utf-8") == VERSION_2


def test_a_version_1_file_goes_through_both_steps(tmp_path):
    (tmp_path / "config.toml").write_text(VERSION_1, encoding="utf-8")
    cfg, app = start(tmp_path)
    assert app == AppSettings(mode="tray")
    assert cfg.system.panels[0].kwp == 4.2 and cfg.planning.minimum_pct == 35 and cfg.planning.notify_lead_minutes == 5
    assert written(tmp_path)["schema"] == 3 and "interface" not in written(tmp_path)


def test_a_new_install_writes_the_new_format_and_no_app_toml(tmp_path):
    cfg, app = start(tmp_path)
    assert app == AppSettings() and written(tmp_path)["schema"] == 3 and not cfg.is_set_up
    assert not (tmp_path / "app.toml").exists()


def test_the_app_splits_an_old_file_when_it_starts(tmp_path):
    folder = tmp_path / "config" / "car-charge-planner"
    folder.mkdir(parents=True)
    (folder / "config.toml").write_text(VERSION_2.replace('language = "nl"', 'language = "auto"'), encoding="utf-8")
    app = RunningApp(tmp_path, "--background")
    try:
        app.wait_until_running()
    finally:
        err = app.stop()
    assert "Traceback" not in err
    assert load_app_settings(folder / "app.toml") == AppSettings(mode="window", units="imperial")
    assert written(folder)["schema"] == 3 and "interface" not in written(folder)


def test_settings_saves_each_part_where_its_owner_keeps_it(tmp_path, qapp):
    saved = []
    app = DesktopSettings(AppSettings(), AppSettingsFile(tmp_path / "app.toml"))
    window = SettingsWindow(engine_for(example_config(), tmp_path / "config.toml", saved=saved), app)
    window.interface_tab.inputs["interface.mode"].setCurrentIndex(window.interface_tab.inputs["interface.mode"].findData("window"))
    planning = next(t for t in window.field_tabs if t.key == "planning")
    planning.inputs["planning.margin"].setValue(70)
    assert window.save()
    assert saved[-1].planning.margin == 0.7 and load_config(tmp_path / "config.toml").planning.margin == 0.7
    assert load_app_settings(tmp_path / "app.toml") == AppSettings(mode="window") and app.current.mode == "window"
    assert "mode" not in (tmp_path / "config.toml").read_text(encoding="utf-8")


PLANNER = Path(__file__).resolve().parent.parent / "src" / "car_charge_planner" / "planner"


@pytest.mark.parametrize("path", sorted(p for p in PLANNER.glob("*.py") if p.name != "cli.py"), ids=lambda p: p.name)
def test_the_planner_imports_nothing_of_the_app_or_the_engine(path):
    """The command-line tool (cli.py) reads a whole config.toml for --live; the planner itself only plans."""
    outside = []
    for node in ast.walk(ast.parse(path.read_text(encoding="utf-8"))):
        if isinstance(node, ast.ImportFrom) and not node.level and (node.module or "").startswith("car_charge_planner."):
            if not node.module.startswith(("car_charge_planner.planner.", "car_charge_planner.i18n")):
                outside.append(node.module)
        elif isinstance(node, ast.ImportFrom) and node.level > 1:
            outside.append("." * node.level + (node.module or ""))
    assert outside == []


def test_what_the_planner_is_given_holds_no_app_forecast_or_set_up_settings():
    given = {f.name for kind in (HomeSystem, PlanningPreferences) for f in fields(kind)}
    assert given.isdisjoint({f.name for f in fields(AppSettings)} | {f.name for f in fields(Config)} - {"planning"})
