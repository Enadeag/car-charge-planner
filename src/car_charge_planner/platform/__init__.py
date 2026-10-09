"""What differs between systems: starting the app at login, and its entry in the application menu."""

from __future__ import annotations

import sys
from typing import Protocol


class StartAtLogin(Protocol):
    def enable(self, command: list[str]) -> str:
        """Start `command` hidden (with `--background`) at every login. Safe to repeat. Returns where it was recorded."""
        ...

    def disable(self) -> bool:
        """Stop starting at login. False when it was not set up."""
        ...

    def is_enabled(self) -> bool: ...


class MenuEntry(Protocol):
    own_entry: bool
    """False where the application itself is the menu entry (a macOS bundle): `install` and `uninstall` then do nothing."""

    def install(self, command: list[str]) -> list[str]:
        """Add the app to the application menu. Safe to repeat. Returns what was written."""
        ...

    def uninstall(self) -> list[str]:
        """Remove what `install` wrote and nothing else. Returns what was removed."""
        ...

    def is_installed(self) -> bool: ...


def start_at_login() -> StartAtLogin:
    if sys.platform == "win32":
        from .windows import WindowsStartAtLogin

        return WindowsStartAtLogin()
    if sys.platform == "darwin":
        from .macos import MacStartAtLogin

        return MacStartAtLogin()
    from .linux import LinuxStartAtLogin

    return LinuxStartAtLogin()


def menu_entry() -> MenuEntry:
    if sys.platform == "win32":
        from .windows import WindowsMenuEntry

        return WindowsMenuEntry()
    if sys.platform == "darwin":
        from .macos import MacMenuEntry

        return MacMenuEntry()
    from .linux import LinuxMenuEntry

    return LinuxMenuEntry()
