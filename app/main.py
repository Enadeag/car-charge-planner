"""Start the app: pick the shell, load the config, run."""

from __future__ import annotations

import sys
import time
from typing import Callable

from PySide6.QtCore import QThread
from PySide6.QtWidgets import QApplication, QMessageBox, QSystemTrayIcon

from planner.model import ConfigError, load_config

from . import gcguard, i18n, launcher
from .config_file import ensure_config
from .controller import Controller
from .identity import APP_NAME, DESKTOP_ENTRY
from .instance import Outcome, claim_instance
from .notification_actions import ActionListener, action_handler
from .shells import Shell, ShellChoice, choose_shell
from .shells.tray import TrayShell
from .shells.window import WindowShell
from storage.state import mark_seen


TRAY_WAIT_BACKGROUND_S = 5.0  # started at login: the panel or the tray extension may still be starting
TRAY_WAIT_INTERACTIVE_S = 1.0
NO_TRAY_HINT = "no-tray"


def wait_for_tray(
    timeout_s: float,
    probe: Callable[[], bool] = QSystemTrayIcon.isSystemTrayAvailable,
    sleep_ms: Callable[[int], None] = QThread.msleep,
    clock: Callable[[], float] = time.monotonic,
) -> bool:
    """Is there a system tray? At login it may appear a moment after the app starts, so ask again until the timeout."""
    deadline = clock() + timeout_s
    while True:
        if probe():
            return True
        if clock() >= deadline:
            return False
        sleep_ms(250)


def announce_tray_fallback(controller: Controller, choice: ShellChoice) -> bool:
    """Say once that the asked-for tray is missing and a window is shown instead. True if it was said now."""
    if not choice.fell_back or not mark_seen(NO_TRAY_HINT):  # once: it would nag at every start otherwise
        return False
    controller.notifier.send(
        "No system tray found",
        "The interface is set to a tray icon, but this desktop has none, so the app shows a window instead. "
        "Change it in Settings, on the Interface tab.",
    )
    return True


def create_shell(controller: Controller, tray_available: bool) -> tuple[Shell, ShellChoice]:
    choice = choose_shell(controller.cfg.interface.mode, tray_available)
    shell = TrayShell(controller) if choice.kind == "tray" else WindowShell(controller)
    return shell, choice


def main(argv: list[str] | None = None) -> int:
    app = QApplication(argv if argv is not None else sys.argv)
    app.setApplicationName("car-charge-planner")
    if launcher.is_installed():  # the portal looks the id up: set it only when there is an entry to find
        app.setDesktopFileName(DESKTOP_ENTRY)  # the Wayland app id: matches the launcher entry
    app.setQuitOnLastWindowClosed(False)
    gcguard.install(app)  # destroy Qt objects only on this thread, never from a background thread's allocation
    i18n.setup(app)  # the language, before any widget text is created

    outcome, instance = claim_instance()
    if outcome is Outcome.SIGNALLED:
        print(f"{APP_NAME} is already running: asked it to show itself.", file=sys.stderr)
        return 0
    if outcome is Outcome.UNAVAILABLE:
        print("Could not set up the single-instance socket; a second copy could start.", file=sys.stderr)

    config_file = ensure_config()
    try:
        cfg = load_config(config_file)
    except ConfigError as err:
        # started from autostart there is no terminal: show the problem where the owner will see it
        QMessageBox.critical(None, "Car charge planner", f"{err}\n\nFix the file and start the app again:\n{config_file}")
        print(err, file=sys.stderr)
        return 2
    background = "--background" in sys.argv[1:]  # the autostart entry starts the app hidden
    controller = Controller(cfg, config_file)
    wants_tray = cfg.interface.mode != "window"
    tray_available = wants_tray and wait_for_tray(TRAY_WAIT_BACKGROUND_S if background else TRAY_WAIT_INTERACTIVE_S)
    shell, choice = create_shell(controller, tray_available)
    announce_tray_fallback(controller, choice)
    if instance is not None:
        instance.show_requested.connect(shell.show_plan)  # a second start shows the plan of this one
    listener = ActionListener()  # a click on one of our notifications shows the plan
    listener.action_invoked.connect(action_handler(controller.notifier, shell.show_plan))
    app.aboutToQuit.connect(listener.stop)
    listener.start()
    shell.start(background=background)
    return app.exec()
