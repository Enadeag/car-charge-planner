"""macOS: a LaunchAgent starts the app at login; the app bundle is its own menu entry."""

from __future__ import annotations

import plistlib
from pathlib import Path

from car_charge_planner import paths
from car_charge_planner.identity import DESKTOP_ENTRY

LABEL = DESKTOP_ENTRY  # the agent's name; launchd wants a unique one per user, which is all the app needs


class MacStartAtLogin:
    def __init__(self, directory: Path | None = None) -> None:
        self._directory = directory

    @property
    def _plist(self) -> Path:
        return (self._directory or paths.launch_agents_dir()) / f"{LABEL}.plist"

    def enable(self, command: list[str]) -> str:
        agent = {"Label": LABEL, "ProgramArguments": [*command, "--background"], "RunAtLoad": True}
        self._plist.parent.mkdir(parents=True, exist_ok=True)
        self._plist.write_bytes(plistlib.dumps(agent))
        return str(self._plist)

    def disable(self) -> bool:
        if not self._plist.exists():
            return False
        self._plist.unlink()
        return True

    def is_enabled(self) -> bool:
        return self._plist.exists()


class MacMenuEntry:
    own_entry = False

    def install(self, command: list[str]) -> list[str]:
        return []

    def uninstall(self) -> list[str]:
        return []

    def is_installed(self) -> bool:
        return False
