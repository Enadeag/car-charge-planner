"""Shells: how the app shows itself.

A shell draws what the `Controller` reports and turns the owner's actions into controller calls. It
needs only these:

    start(background=False)  show itself (unless background) and start the controller
    show_plan()  bring the plan in front of the owner (a popup, a window)
    (it listens to `controller.view_changed` for everything it draws)

`TrayShell` (app/shells/tray.py) is the tray icon with its popup; a window shell can implement the
same three things without a tray.
"""

from __future__ import annotations

from dataclasses import dataclass
from typing import Protocol


class Shell(Protocol):
    def start(self, background: bool = False) -> None: ...

    def show_plan(self) -> None: ...


@dataclass(frozen=True)
class ShellChoice:
    kind: str  # "tray" or "window"
    fell_back: bool = False  # the owner asked for a tray but there is none


def choose_shell(mode: str, tray_available: bool) -> ShellChoice:
    """Which shell to use for `interface.mode` ("auto", "tray" or "window")."""
    if mode == "window":
        return ShellChoice("window")
    if tray_available:
        return ShellChoice("tray")
    return ShellChoice("window", fell_back=(mode == "tray"))  # auto without a tray is simply a window
