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

from typing import Protocol


class Shell(Protocol):
    def start(self, background: bool = False) -> None: ...

    def show_plan(self) -> None: ...
