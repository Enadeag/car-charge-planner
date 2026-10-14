"""Regenerate the screenshots in docs/images (light and dark), offscreen and without a network: the set-up wizard
(`setup-*`) and the app itself (`app-*`: the plan, the update page, the Settings window and the window shell).

    .venv/bin/python docs/screenshots.py

Neutral example answers (central Amsterdam, a 6.4 kWp roof), a recorded place search and a fake connection check.
The look is Qt's default style with a light and a dark palette: a real desktop theme will differ a little.
"""

from __future__ import annotations

import math
import os
import sys
import tempfile
from datetime import date, datetime, timedelta
from pathlib import Path
from zoneinfo import ZoneInfo

os.environ.setdefault("QT_QPA_PLATFORM", "offscreen")
ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT / "src"))

from PySide6.QtCore import QLocale  # noqa: E402
from PySide6.QtGui import QColor, QPalette  # noqa: E402
from PySide6.QtTest import QTest  # noqa: E402
from PySide6.QtWidgets import QApplication  # noqa: E402

from car_charge_planner.forecast.check import Attempt  # noqa: E402
from car_charge_planner.forecast.providers.open_meteo import OpenMeteo  # noqa: E402
from car_charge_planner.forecast.registry import ProviderRegistry  # noqa: E402
from car_charge_planner.forecast.transport import Reply  # noqa: E402
from car_charge_planner.planner.model import Config  # noqa: E402
from car_charge_planner.setup.wizard import SetupWizard  # noqa: E402

OUT = ROOT / "docs" / "images"
PLACES = (ROOT / "tests" / "fixtures" / "open_meteo_geocoding_amsterdam.json").read_bytes()
PAGES = ("welcome", "location", "solar", "car", "next", "house", "away", "finish")  # in the order of the README
DARK = {
    QPalette.ColorRole.Window: "#31363b", QPalette.ColorRole.WindowText: "#eff0f1", QPalette.ColorRole.Base: "#232629",
    QPalette.ColorRole.Text: "#eff0f1", QPalette.ColorRole.Button: "#31363b", QPalette.ColorRole.ButtonText: "#eff0f1",
    QPalette.ColorRole.PlaceholderText: "#a0a4a8",
}


def palette(app: QApplication, dark: bool) -> None:
    pal = QPalette(app.style().standardPalette())
    if dark:
        for role, colour in DARK.items():
            pal.setColor(role, QColor(colour))
    app.setPalette(pal)


def walk(app: QApplication, theme: str) -> None:
    registry = ProviderRegistry()
    registry.register(OpenMeteo(lambda request: Reply(200, PLACES)))
    wizard = SetupWizard(Config(), OUT / "unused.toml", lambda cfg: None, registry=registry,
                         check=lambda cfg: Attempt(True, 4, "OK"), environ={"LANG": "en_IE.UTF-8"}, qt_locale_name="")
    wizard.confirm_leave = lambda: True
    wizard.resize(640, 640)
    wizard.show()

    def shot(page: str) -> None:
        QTest.qWait(80)
        wizard.grab().save(str(OUT / f"setup-{page}-{theme}.png"))

    shot("welcome")
    wizard.next()
    location = wizard.pages["location"]
    location.timer.setInterval(10)
    location.search.setText("Amsterdam")
    QTest.qWait(60)
    for thread in list(location._threads):
        thread.join(10)
    QTest.qWait(60)
    location.results.itemClicked.emit(location.results.item(0))
    shot("location")
    wizard.next()
    solar = wizard.pages["solar"]
    solar.kwp.setValue(6.4)
    solar.compass_buttons["sw"].click()
    shot("solar")
    wizard.next()
    car = wizard.pages["car"]
    car.battery.setValue(64)
    car.consumption.setValue(17.5)
    car.weekly.setValue(300)
    car.max_amps.setValue(20)
    shot("car")
    wizard.next()
    wizard.navigate("house")
    wizard.pages["house"].yearly.setValue(3600)
    shot("house")
    wizard.back()
    wizard.navigate("away")
    away = wizard.pages["away"]
    away.some_days.click()
    away.rows[0].weekday.setCurrentIndex(3)
    shot("away")
    wizard.back()
    shot("next")
    wizard.next()
    for thread in list(wizard.pages["finish"]._threads):
        thread.join(10)
    shot("finish")
    wizard.close()


TZ = ZoneInfo("Europe/Amsterdam")
DAY = date(2026, 7, 3)
NOW = datetime(2026, 7, 3, 7, 30, tzinfo=TZ)  # a summer morning, the plan is for later today


def example_config() -> Config:
    """The same neutral example as the wizard's: central Amsterdam, a 6.4 kWp roof facing south-west."""
    from car_charge_planner.planner.model import Car, Charger, House, Panels

    return Config(
        panels=(Panels(kwp=6.4, tilt_deg=35, azimuth_deg=45, system_losses=0.14),),
        house=House(baseload_kw=0.4),
        car=Car(usable_battery_kwh=64, consumption_kwh_per_100km=17.5, km_per_day=300 / 7, minimum_pct=30, target_pct=80),
        charger=Charger(phases=1, min_amps=6, max_amps=20, volts=230, efficiency=0.9),
        latitude=52.37,
        longitude=4.90,
        timezone="Europe/Amsterdam",
    )


def example_forecast(cfg: Config) -> list:
    """Four bell-shaped days: a bright one, a mixed one, an overcast one and a bright one."""
    from car_charge_planner.forecast.provider import ForecastStep

    steps = []
    for index, strength in enumerate((0.85, 0.55, 0.2, 0.8)):
        day = DAY + timedelta(days=index)
        for hour in range(5, 21):
            start = datetime(day.year, day.month, day.day, hour, tzinfo=TZ)
            sun = max(0.0, math.sin(math.pi * (hour + 0.5 - 5) / 16)) ** 1.5
            steps.append(ForecastStep(start, start + timedelta(hours=1), sum(p.kwp * (1 - p.system_losses) for p in cfg.panels) * strength * sun))
    return steps


def app_shots(app: QApplication, theme: str) -> None:
    """The plan, the update page, the Settings window and the window shell, for an example home at a fixed time."""
    from car_charge_planner.controller import Controller
    from car_charge_planner.engine import run_here
    from car_charge_planner.forecast.service import ForecastResult
    from car_charge_planner.shells.tray import TrayShell
    from car_charge_planner.shells.window import WindowShell
    from car_charge_planner.storage.state import set_battery

    class Silent:
        def send(self, *args, **kwargs):
            return 0

        def owns(self, _id):
            return False

    class Example:
        """The example forecast instead of a fetch."""

        def __init__(self, cfg):
            self.result = ForecastResult(example_forecast(cfg), NOW - timedelta(minutes=12), False, False, "open-meteo", "Open-Meteo", "Weather data: Open-Meteo.com (CC BY 4.0)")

        def get(self, cfg, now, force=False):
            return self.result

        def forget_failures(self):
            pass

    with tempfile.TemporaryDirectory() as folder:
        os.environ["XDG_STATE_HOME"] = folder
        set_battery(45, NOW)
        cfg = example_config()
        controller = Controller(cfg, Path(folder) / "config.toml", Silent(), Example(cfg), lambda: NOW, run_here)
        tray = TrayShell(controller)
        controller.start()
        QTest.qWait(80)
        popup = tray.popup
        popup.show()
        popup.view.fit_to_page()
        QTest.qWait(80)
        popup.grab().save(str(OUT / f"app-plan-{theme}.png"))
        popup.view.tabs.setCurrentIndex(1)
        popup.view.fit_to_page()
        QTest.qWait(80)
        popup.grab().save(str(OUT / f"app-update-{theme}.png"))
        popup.hide()
        tray.windows.open_settings()
        settings = tray.windows.settings_window
        settings.resize(760, 600)
        QTest.qWait(120)
        settings.grab().save(str(OUT / f"app-settings-{theme}.png"))
        settings.close()
        window = WindowShell(controller)
        controller.engine.rebuild()  # a shell made later has not been told the plan yet
        window.window.resize(window.window.sizeHint())
        window.window.show()
        QTest.qWait(120)
        window.window.grab().save(str(OUT / f"app-window-{theme}.png"))
        window.window.hide()


def main() -> None:
    OUT.mkdir(parents=True, exist_ok=True)
    QLocale.setDefault(QLocale("en_GB"))  # decimal points in the boxes, whatever the computer that makes the pictures uses
    app = QApplication.instance() or QApplication([])
    for theme in ("light", "dark"):
        palette(app, theme == "dark")
        walk(app, theme)
        app_shots(app, theme)
    print(f"{len(PAGES) * 2 + 8} screenshots written to {OUT}")


if __name__ == "__main__":
    main()
