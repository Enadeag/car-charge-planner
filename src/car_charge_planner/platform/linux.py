"""Linux: XDG autostart and application-menu entries, which are `.desktop` files."""

from __future__ import annotations

from pathlib import Path

from car_charge_planner import paths
from car_charge_planner.identity import APP_NAME, COMMENT, DESKTOP_ENTRY, ICON_NAME, exec_line

ICON_SOURCE = Path(__file__).parent.parent / "assets" / f"{ICON_NAME}.svg"
ENTRY_NAME = f"{DESKTOP_ENTRY}.desktop"


def autostart_dir() -> Path:
    return paths.shared_config_dir() / "autostart"


def applications_dir(base: Path | None = None) -> Path:
    return (base or paths.shared_data_dir()) / "applications"


def icons_dir(base: Path | None = None) -> Path:
    return (base or paths.shared_data_dir()) / "icons" / "hicolor" / "scalable" / "apps"


def autostart_entry(command: list[str]) -> str:
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


def launcher_entry(command: list[str]) -> str:
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


class LinuxStartAtLogin:
    def __init__(self, directory: Path | None = None) -> None:
        self._directory = directory

    @property
    def _entry(self) -> Path:
        return (self._directory or autostart_dir()) / ENTRY_NAME

    def enable(self, command: list[str]) -> str:
        self._entry.parent.mkdir(parents=True, exist_ok=True)
        self._entry.write_text(autostart_entry(command), encoding="utf-8")
        return str(self._entry)

    def disable(self) -> bool:
        if not self._entry.exists():
            return False
        self._entry.unlink()
        return True

    def is_enabled(self) -> bool:
        return self._entry.exists()


class LinuxMenuEntry:
    own_entry = True

    def __init__(self, base: Path | None = None) -> None:
        self._base = base

    @property
    def _files(self) -> tuple[Path, Path]:
        return applications_dir(self._base) / ENTRY_NAME, icons_dir(self._base) / f"{ICON_NAME}.svg"

    def install(self, command: list[str]) -> list[str]:
        entry, icon = self._files
        entry.parent.mkdir(parents=True, exist_ok=True)
        icon.parent.mkdir(parents=True, exist_ok=True)
        entry.write_text(launcher_entry(command), encoding="utf-8")
        icon.write_bytes(ICON_SOURCE.read_bytes())
        return [str(entry), str(icon)]

    def uninstall(self) -> list[str]:
        removed = []
        for path in self._files:
            if path.exists():
                path.unlink()
                removed.append(str(path))
        return removed

    def is_installed(self) -> bool:
        return self._files[0].exists()
