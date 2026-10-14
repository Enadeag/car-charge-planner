"""Shells: how the app shows itself. A shell draws what the `Controller` reports (`view_changed`) and turns the owner's
actions into controller calls. It needs `start(background=False)` and `show_plan()`; `TrayShell` and `WindowShell` are the
two.
"""

from __future__ import annotations

from dataclasses import dataclass
from typing import Protocol

from ..app_windows import AppWindows


class Shell(Protocol):
    windows: AppWindows

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
