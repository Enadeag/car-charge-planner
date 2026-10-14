"""`car-charge-planner qml-preview [--light | --dark]`: the QML dashboard in a window of its own, for developers, while the
Widgets screens are still the app. It plans with the owner's settings and forecast but never writes their data."""

from __future__ import annotations

import sys

from PySide6.QtWidgets import QApplication
from shiboken6 import delete

from car_charge_planner.config.model import Config, ConfigError, load_config
from car_charge_planner.forecast.secret_store import Secrets
from car_charge_planner.storage.state import State

from . import i18n
from .controller import Controller
from .engine import FileStore, KeyStorage
from .quick.bridge import DashboardBridge
from .quick.window import load_dashboard, qml_engine, set_dark


class ReadOnlyStore:
    """The owner's files, read but never written: the installed app may be running beside the preview, and the preview's
    own announcements (state.json's `notified`) would keep the app from sending them."""

    def __init__(self) -> None:
        self._files = FileStore()
        self._state = self._files.load_state()

    def load_config(self) -> Config:
        path = self._files.config_file
        return load_config(path if path.exists() else None)  # not FileStore's: that creates or upgrades the file

    def save_config(self, cfg: Config) -> None:
        pass

    def load_state(self) -> State:
        return self._state

    def save_state(self, state: State) -> None:
        self._state = state  # kept in memory, so the preview still behaves as the app would

    def load_secrets(self) -> Secrets:
        return self._files.load_secrets()

    def save_secrets(self, secrets: Secrets) -> None:
        pass

    def key_storage(self) -> KeyStorage:
        return self._files.key_storage()


class Silent:
    def send(self, title: str, body: str, expire_ms: int = 20_000) -> int | None:
        return None

    def owns(self, notification_id: int) -> bool:
        return False


def main(argv: list[str]) -> int:
    app = QApplication(sys.argv[:1])
    app.setApplicationName("car-charge-planner")
    i18n.setup(app)
    store = ReadOnlyStore()
    try:
        cfg = store.load_config()
    except ConfigError as err:
        print(err, file=sys.stderr)
        return 2
    controller = Controller(cfg, store, Silent())
    bridge = DashboardBridge(controller.engine)
    controller.view_changed.connect(bridge.show)
    engine = qml_engine()
    window = load_dashboard(bridge, engine)
    if "--dark" in argv or "--light" in argv:
        set_dark(engine, "--dark" in argv)
    window.show()
    controller.start()
    code = app.exec()
    del window
    delete(engine)  # before the bridge it reads: its bindings would otherwise read a deleted object while closing
    return code
