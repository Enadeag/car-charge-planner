"""Start the app: pick the shell, load the config, run."""

from __future__ import annotations

import sys
from pathlib import Path

from PySide6.QtCore import QLockFile
from PySide6.QtWidgets import QApplication, QMessageBox, QSystemTrayIcon

from planner.model import ConfigError, load_config

from . import launcher
from .config_file import ensure_config
from .controller import Controller
from .identity import DESKTOP_ENTRY
from .shells.tray import TrayShell


def main(argv: list[str] | None = None) -> int:
    app = QApplication(argv if argv is not None else sys.argv)
    app.setApplicationName("car-charge-planner")
    if launcher.is_installed():  # the portal looks the id up: set it only when there is an entry to find
        app.setDesktopFileName(DESKTOP_ENTRY)  # the Wayland app id: matches the launcher entry
    app.setQuitOnLastWindowClosed(False)

    lock_path = Path.home() / ".cache" / "car-charge-planner.lock"
    lock_path.parent.mkdir(parents=True, exist_ok=True)
    lock = QLockFile(str(lock_path))
    if not lock.tryLock(100):
        if lock.error() == QLockFile.LockError.LockFailedError:
            print("car-charge-planner is already running", file=sys.stderr)
        else:
            print(f"Could not create the lock file {lock_path}", file=sys.stderr)
        return 1

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
    shell.start()
    return app.exec()
