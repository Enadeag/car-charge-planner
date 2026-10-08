"""A launcher entry and icon, so the app appears in the application menu and the desktop can tie
its windows and notifications to it (GNOME needs this; KDE benefits)."""

from __future__ import annotations

import os
from pathlib import Path

from .identity import APP_NAME, COMMENT, DESKTOP_ENTRY, ICON_NAME, exec_line

ICON_SOURCE = Path(__file__).parent / "assets" / f"{ICON_NAME}.svg"


def data_home() -> Path:
    return Path(os.environ.get("XDG_DATA_HOME", Path.home() / ".local" / "share"))


def applications_dir(base: Path | None = None) -> Path:
    return (base or data_home()) / "applications"


def icons_dir(base: Path | None = None) -> Path:
    return (base or data_home()) / "icons" / "hicolor" / "scalable" / "apps"


def desktop_entry(command: list[str]) -> str:
    return f"""[Desktop Entry]
Type=Application
Name={APP_NAME}
Comment={COMMENT}
Exec={exec_line(command)}
Icon={ICON_NAME}
Terminal=false
Categories=Utility;
StartupWMClass={DESKTOP_ENTRY}
"""


def is_installed(base: Path | None = None) -> bool:
    return (applications_dir(base) / f"{DESKTOP_ENTRY}.desktop").exists()


def install(command: list[str], base: Path | None = None) -> tuple[Path, Path]:
    """Write the launcher entry and the icon. Safe to repeat. Returns (entry, icon)."""
    entry = applications_dir(base) / f"{DESKTOP_ENTRY}.desktop"
    icon = icons_dir(base) / f"{ICON_NAME}.svg"
    entry.parent.mkdir(parents=True, exist_ok=True)
    icon.parent.mkdir(parents=True, exist_ok=True)
    entry.write_text(desktop_entry(command))
    icon.write_bytes(ICON_SOURCE.read_bytes())
    return entry, icon


def uninstall(base: Path | None = None) -> list[Path]:
    """Remove what install wrote, and nothing else. Returns the files that were removed."""
    removed = []
    for path in (applications_dir(base) / f"{DESKTOP_ENTRY}.desktop", icons_dir(base) / f"{ICON_NAME}.svg"):
        if path.exists():
            path.unlink()
            removed.append(path)
    return removed
