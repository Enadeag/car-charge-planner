"""Start the app: pick the shell, load the config, run."""

from __future__ import annotations

import sys

from PySide6.QtWidgets import QApplication, QMessageBox, QSystemTrayIcon

from planner.model import ConfigError, load_config

from . import launcher
from .config_file import ensure_config
from .controller import Controller
from .identity import APP_NAME, DESKTOP_ENTRY
from .instance import Outcome, claim_instance
from .shells.tray import TrayShell


def main(argv: list[str] | None = None) -> int:
    app = QApplication(argv if argv is not None else sys.argv)
    app.setApplicationName("car-charge-planner")
    if launcher.is_installed():  # the portal looks the id up: set it only when there is an entry to find
        app.setDesktopFileName(DESKTOP_ENTRY)  # the Wayland app id: matches the launcher entry
    app.setQuitOnLastWindowClosed(False)

    outcome, instance = claim_instance()
    if outcome is Outcome.SIGNALLED:
        print(f"{APP_NAME} is already running: asked it to show itself.", file=sys.stderr)
        return 0
    if outcome is Outcome.UNAVAILABLE:
        print("Could not set up the single-instance socket; a second copy could start.", file=sys.stderr)

    if not QSystemTrayIcon.isSystemTrayAvailable():
        print("No system tray available on this desktop", file=sys.stderr)
        return 1

    config_file = ensure_config()
    try:
        cfg = load_config(config_file)
    except ConfigError as err:
        # started from autostart there is no terminal: show the problem where the owner will see it
        QMessageBox.critical(None, "Car charge planner", f"{err}\n\nFix the file and start the app again:\n{config_file}")
        print(err, file=sys.stderr)
        return 2
    shell = TrayShell(Controller(cfg, config_file))
    if instance is not None:
        instance.show_requested.connect(shell.show_plan)  # a second start shows the plan of this one
    shell.start()
    return app.exec()
