"""Start the tray app at login via an XDG autostart entry (works on KDE Plasma)."""

from __future__ import annotations

import os
from pathlib import Path

ENTRY_NAME = "car-charge-planner.desktop"


def autostart_dir() -> Path:
    return Path(os.environ.get("XDG_CONFIG_HOME", Path.home() / ".config")) / "autostart"


def desktop_entry(python: Path, project: Path) -> str:
    return f"""[Desktop Entry]
Type=Application
Name=Car charge planner
Comment=Tells you when to charge the car from your solar panels
Exec={python} -m app
Path={project}
Terminal=false
X-KDE-autostart-after=panel
"""


def install(python: Path, project: Path, directory: Path | None = None) -> Path:
    target = (directory or autostart_dir()) / ENTRY_NAME
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(desktop_entry(python, project))
    return target


def uninstall(directory: Path | None = None) -> bool:
    target = (directory or autostart_dir()) / ENTRY_NAME
    if target.exists():
        target.unlink()
        return True
    return False
