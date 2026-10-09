"""Start the tray app at login via an XDG autostart entry."""

from __future__ import annotations

from pathlib import Path

from . import paths
from .identity import APP_NAME, COMMENT, DESKTOP_ENTRY, ICON_NAME, exec_line

ENTRY_NAME = f"{DESKTOP_ENTRY}.desktop"


def autostart_dir() -> Path:
    return paths.shared_config_dir() / "autostart"


def desktop_entry(command: list[str]) -> str:
    return f"""[Desktop Entry]
Type=Application
Name={APP_NAME}
Comment={COMMENT}
Exec={exec_line(command, "--background")}
Icon={ICON_NAME}
Terminal=false
X-KDE-autostart-after=panel
X-GNOME-Autostart-enabled=true
"""


def install(command: list[str], directory: Path | None = None) -> Path:
    target = (directory or autostart_dir()) / ENTRY_NAME
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(desktop_entry(command), encoding="utf-8")
    return target


def uninstall(directory: Path | None = None) -> bool:
    target = (directory or autostart_dir()) / ENTRY_NAME
    if target.exists():
        target.unlink()
        return True
    return False
