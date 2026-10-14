"""Start the app: pick the shell, load the config, run."""

from __future__ import annotations

import sys
import time
from collections.abc import Callable

from PySide6.QtCore import QThread, QTimer
from PySide6.QtWidgets import QApplication, QMessageBox, QSystemTrayIcon
from shiboken6 import delete

from car_charge_planner.planner.model import ConfigError, load_config
from car_charge_planner.storage.state import mark_seen

from . import gcguard, i18n
from .config_file import ensure_config
from .controller import Controller
from .i18n import _
from .identity import APP_NAME, APP_USER_MODEL_ID, DESKTOP_ENTRY
from .instance import Outcome, claim_instance
from .notification_actions import action_handler, click_listener
from .platform import menu_entry
from .shells import Shell, ShellChoice, choose_shell
from .shells.icons import make_icon
from .shells.tray import TrayShell
from .shells.window import WindowShell
from .tray_notifier import TrayNotifier

TRAY_WAIT_BACKGROUND_S = 5.0  # started at login: the panel or the tray extension may still be starting
TRAY_WAIT_INTERACTIVE_S = 1.0
NO_TRAY_HINT = "no-tray"
SMOKE_TEST = "--smoke-test"  # start up hidden, then quit with exit code 0 once the event loop runs: a check of a packaged build


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
    """Say once that the asked-for tray is missing and a window is shown instead. True if it was said now.

    Only on Linux, where a desktop may have no tray (stock GNOME). Windows and macOS always have one.
    """
    if sys.platform != "linux" or not choice.fell_back or not mark_seen(NO_TRAY_HINT):  # once: it would nag at every start otherwise
        return False
    controller.notifier.send(
        _("No system tray found"),
        _(
            "The interface is set to a tray icon, but this desktop has none, so the app shows a window instead. "
            "Change it in Settings, on the Interface tab."
        ),
    )
    return True


def create_shell(controller: Controller, tray_available: bool) -> tuple[Shell, ShellChoice]:
    choice = choose_shell(controller.cfg.interface.mode, tray_available)
    shell = TrayShell(controller) if choice.kind == "tray" else WindowShell(controller)
    return shell, choice


def notification_tray(show: Callable[[], None]) -> QSystemTrayIcon:
    """Windows and macOS show notifications through a tray icon, so in window mode there is one just for them."""
    tray = QSystemTrayIcon(make_icon("none"))
    tray.setToolTip(APP_NAME)
    tray.activated.connect(lambda _reason: show())
    tray.show()
    return tray


def missing_from_build() -> str | None:
    """For --smoke-test: what a packaged build can lack and still start. Windows and macOS always have a keychain; a build
    without keyring's backend for it would quietly keep API keys in a file instead."""
    if sys.platform == "linux":
        return None  # a Linux desktop may really have no keychain
    import keyring
    from keyring.backends import fail, null

    backend = keyring.get_keyring()
    return f"no keychain backend ({type(backend).__name__})" if isinstance(backend, fail.Keyring | null.Keyring) else None


def delete_windows(app: QApplication) -> None:
    """Delete every window that has no parent, with all its widgets, while Python and the QApplication are still whole.

    Left alive, they are deleted by PySide's clean-up at interpreter exit, which goes through its objects in no fixed order.
    A widget added to a layout before that layout had a widget stays owned by Python while Qt gives it a parent, so that
    order can destroy it twice: an access violation at exit, now and then (seen on Windows).
    """
    # asked again after each one: deleting a window can delete other parentless windows Qt made for it
    while windows := [widget for widget in app.topLevelWidgets() if widget.parentWidget() is None]:
        delete(windows[0])


def main(argv: list[str] | None = None) -> int:
    if sys.platform == "win32" and getattr(sys, "frozen", False):
        # Only the installed build: its Start menu shortcut carries the same id, which gives notifications the app's name
        # and icon. Run from source there is no such shortcut, and Windows would show the bare id instead.
        from .platform.windows import set_app_user_model_id

        set_app_user_model_id(APP_USER_MODEL_ID)
    smoke_test = SMOKE_TEST in sys.argv[1:]
    app = QApplication(argv if argv is not None else sys.argv)
    app.setApplicationName("car-charge-planner")
    if menu_entry().is_installed():  # the portal looks the id up: set it only when there is an entry to find
        app.setDesktopFileName(DESKTOP_ENTRY)  # the Wayland app id
    app.setQuitOnLastWindowClosed(False)
    gcguard.install(app)  # destroy Qt objects only on this thread, never from a background thread's allocation
    i18n.setup(app)  # the language, before any widget text is created

    instance = None
    if not smoke_test:  # a check of the build must not wake a copy that is already running, nor take its place
        outcome, instance = claim_instance()
        if outcome is Outcome.SIGNALLED:
            print(_("{app} is already running: asked it to show itself.").format(app=APP_NAME), file=sys.stderr)
            return 0
        if outcome is Outcome.NO_ANSWER:
            print(_("{app} is already running, but it did not respond.").format(app=APP_NAME), file=sys.stderr)
            return 1
        if outcome is Outcome.UNAVAILABLE:
            print(_("Could not set up the single-instance socket; a second copy could start."), file=sys.stderr)

    config_file = ensure_config()
    try:
        cfg = load_config(config_file)
    except ConfigError as err:
        # started from autostart there is no terminal: show the problem where the owner will see it
        QMessageBox.critical(
            None,
            _("Car charge planner"),
            _("{error}\n\nFix the file and start the app again:\n{file}").format(error=err, file=config_file),
        )
        print(err, file=sys.stderr)
        return 2
    if cfg.interface.language != "auto":
        i18n.setup(app, [cfg.interface.language])  # the owner's choice replaces the system language
    background = smoke_test or "--background" in sys.argv[1:]
    controller = Controller(cfg, config_file)
    wants_tray = cfg.interface.mode != "window"
    tray_available = wants_tray and wait_for_tray(TRAY_WAIT_BACKGROUND_S if background else TRAY_WAIT_INTERACTIVE_S)
    shell, choice = create_shell(controller, tray_available)
    if isinstance(controller.notifier, TrayNotifier):
        tray = shell.tray if isinstance(shell, TrayShell) else notification_tray(shell.show_plan)
        controller.notifier.use_tray(tray)
    announce_tray_fallback(controller, choice)
    if instance is not None:
        instance.show_requested.connect(shell.show_plan)
        instance.quit_requested.connect(app.quit)
    listener = click_listener(controller.notifier)
    listener.action_invoked.connect(action_handler(controller.notifier, lambda: controller.notification_clicked(shell.show_plan)))
    app.aboutToQuit.connect(listener.stop)
    listener.start()
    controller.setup_completed.connect(shell.show_plan)
    shell.start(background=background)
    if smoke_test:
        if problem := missing_from_build():
            print(problem, file=sys.stderr)
            return 3
        QTimer.singleShot(0, app.quit)
    else:
        controller.offer_setup(background)
    exit_code = app.exec()
    delete_windows(app)
    return exit_code
