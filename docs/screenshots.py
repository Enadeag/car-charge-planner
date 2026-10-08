"""Regenerate the set-up wizard screenshots in docs/images (light and dark), offscreen and without a network.

    .venv/bin/python docs/screenshots.py

Neutral example answers (central Amsterdam, a 6.4 kWp roof), a recorded place search and a fake connection check.
The look is Qt's default style with a light and a dark palette: a real desktop theme will differ a little.
"""

from __future__ import annotations

import os
import sys
from pathlib import Path

os.environ.setdefault("QT_QPA_PLATFORM", "offscreen")
ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT / "src"))

from PySide6.QtCore import QLocale  # noqa: E402
from PySide6.QtGui import QColor, QPalette  # noqa: E402
from PySide6.QtTest import QTest  # noqa: E402
from PySide6.QtWidgets import QApplication  # noqa: E402

from car_charge_planner.setup.wizard import SetupWizard  # noqa: E402
from car_charge_planner.forecast.check import Attempt  # noqa: E402
from car_charge_planner.forecast.providers.open_meteo import OpenMeteo  # noqa: E402
from car_charge_planner.forecast.registry import ProviderRegistry  # noqa: E402
from car_charge_planner.forecast.transport import Reply  # noqa: E402
from car_charge_planner.planner.model import Config  # noqa: E402

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


def main() -> None:
    OUT.mkdir(parents=True, exist_ok=True)
    QLocale.setDefault(QLocale("en_GB"))  # decimal points in the boxes, whatever the computer that makes the pictures uses
    app = QApplication.instance() or QApplication([])
    for theme in ("light", "dark"):
        palette(app, theme == "dark")
        walk(app, theme)
    print(f"{len(PAGES) * 2} screenshots written to {OUT}")


if __name__ == "__main__":
    main()
