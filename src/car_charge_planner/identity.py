"""How the app names itself. The launcher entry, the autostart entry, the notification hint and the
window's app id must all agree, or the desktop cannot tie notifications and windows to the launcher."""

from __future__ import annotations

import shutil
import sys
from pathlib import Path

APP_NAME = "Car charge planner"
DESKTOP_ENTRY = "car-charge-planner"  # the .desktop file name without extension, also the Wayland app id
ICON_NAME = "car-charge-planner"
COMMAND = "car-charge-planner"  # the installed command (pip and pipx put it next to the interpreter)
COMMENT = "Tells you when to charge the car from your solar panels"
APP_USER_MODEL_ID = "CarChargePlanner.CarChargePlanner"  # Windows: the installer's Start menu shortcut carries the same id
CONSOLE_SUFFIX = "-cli"  # Windows build: `car-charge-planner-cli.exe` is the console twin of the windowed program, for the commands that print


def launch_command() -> list[str]:
    """What a launcher or autostart entry should run to start this very installation of the app.

    The program in a packaged build (the windowed one when started through its console twin, so that nothing opens a
    console window at login), else the installed command when this interpreter has one next to
    it, else this interpreter with `-m`. Never a folder to change to: the entry works from wherever the desktop
    starts it.
    """
    if getattr(sys, "frozen", False):
        program = Path(sys.executable)
        windowed = program.with_name(program.name.replace(CONSOLE_SUFFIX + ".", "."))
        return [str(windowed if windowed.exists() else program)]
    installed = shutil.which(COMMAND, path=str(Path(sys.executable).parent))
    if installed is not None:
        if sys.platform == "win32":  # PATHEXT is in capitals (`.EXE`); the file is `.exe`, and a Windows path means the same either way
            installed = str(Path(installed).with_suffix(Path(installed).suffix.lower()))
        return [installed]
    return [sys.executable, "-m", "car_charge_planner"]


def exec_line(command: list[str], *extra: str) -> str:
    """The `Exec=` value of a desktop entry for a command: arguments with spaces or special characters are quoted
    the way the Desktop Entry specification asks (double quotes, with \\ \" ` $ escaped)."""
    def quote(argument: str) -> str:
        if argument and not any(c in argument for c in ' \t\n"\'\\><~|&;$*?#()`'):
            return argument
        return '"' + "".join("\\" + c if c in '"`$\\' else c for c in argument).replace("%", "%%") + '"'

    return " ".join(quote(part) for part in [*command, *extra])
