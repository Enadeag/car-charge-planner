"""Windows: a per-user `Run` registry value starts the app at login; a Start menu shortcut is its menu entry."""

# winreg has no members on the platform pyright checks for
# pyright: reportAttributeAccessIssue=false
from __future__ import annotations

import os
import subprocess
from collections.abc import Callable
from pathlib import Path
from typing import Protocol

from car_charge_planner import paths
from car_charge_planner.identity import APP_NAME, COMMENT

RUN_KEY = r"Software\Microsoft\Windows\CurrentVersion\Run"


class Registry(Protocol):
    """The string values of one registry key, so that tests can stand in for the real registry."""

    def get(self, name: str) -> str | None: ...

    def set(self, name: str, value: str) -> None: ...

    def delete(self, name: str) -> bool: ...


class RunKey:
    """The real `HKEY_CURRENT_USER\\Software\\Microsoft\\Windows\\CurrentVersion\\Run`."""

    def get(self, name: str) -> str | None:
        import winreg

        try:
            with winreg.OpenKey(winreg.HKEY_CURRENT_USER, RUN_KEY) as key:
                return str(winreg.QueryValueEx(key, name)[0])
        except FileNotFoundError:
            return None

    def set(self, name: str, value: str) -> None:
        import winreg

        with winreg.CreateKey(winreg.HKEY_CURRENT_USER, RUN_KEY) as key:
            winreg.SetValueEx(key, name, 0, winreg.REG_SZ, value)

    def delete(self, name: str) -> bool:
        import winreg

        try:
            with winreg.OpenKey(winreg.HKEY_CURRENT_USER, RUN_KEY, 0, winreg.KEY_SET_VALUE) as key:
                winreg.DeleteValue(key, name)
        except FileNotFoundError:
            return False
        return True


def command_line(command: list[str], *extra: str) -> str:
    return subprocess.list2cmdline([*command, *extra])


def windowless(command: list[str]) -> list[str]:
    """`python.exe -m ...` opens a console window at login; `pythonw.exe` does not. The installed command is left as it is."""
    first = Path(command[0])
    if first.name.lower() == "python.exe" and (first.with_name("pythonw.exe")).exists():
        return [str(first.with_name("pythonw.exe")), *command[1:]]
    return command


class WindowsStartAtLogin:
    def __init__(self, registry: Registry | None = None) -> None:
        self._registry = registry or RunKey()

    def enable(self, command: list[str]) -> str:
        self._registry.set(APP_NAME, command_line(windowless(command), "--background"))
        return rf"HKEY_CURRENT_USER\{RUN_KEY}\{APP_NAME}"

    def disable(self) -> bool:
        return self._registry.delete(APP_NAME)

    def is_enabled(self) -> bool:
        return self._registry.get(APP_NAME) is not None


# The paths and texts reach PowerShell through the environment, never through the script text, so that no
# character in them (a quote, a dollar sign) can change what the script does.
_CREATE_SHORTCUT = """
$shell = New-Object -ComObject WScript.Shell
$link = $shell.CreateShortcut($env:CCP_LINK)
$link.TargetPath = $env:CCP_TARGET
$link.Arguments = $env:CCP_ARGUMENTS
$link.Description = $env:CCP_DESCRIPTION
$link.Save()
"""


def create_shortcut(link: Path, target: str, arguments: str, description: str) -> None:
    """Write a `.lnk` file. It needs COM; PowerShell's WScript.Shell is the way that adds no dependency."""
    env = {**os.environ, "CCP_LINK": str(link), "CCP_TARGET": target, "CCP_ARGUMENTS": arguments, "CCP_DESCRIPTION": description}
    flags = getattr(subprocess, "CREATE_NO_WINDOW", 0)
    done = subprocess.run(
        ["powershell.exe", "-NoProfile", "-NonInteractive", "-Command", _CREATE_SHORTCUT],
        env=env, capture_output=True, text=True, timeout=60, creationflags=flags, check=False,
    )
    if done.returncode != 0 or not link.exists():
        raise OSError(f"could not create the shortcut {link}: {done.stderr.strip() or done.stdout.strip()}")


class WindowsMenuEntry:
    own_entry = True

    def __init__(self, directory: Path | None = None, make_shortcut: Callable[[Path, str, str, str], None] = create_shortcut) -> None:
        self._directory = directory
        self._make_shortcut = make_shortcut

    @property
    def _link(self) -> Path:
        return (self._directory or paths.start_menu_dir()) / f"{APP_NAME}.lnk"

    def install(self, command: list[str]) -> list[str]:
        self._link.parent.mkdir(parents=True, exist_ok=True)
        self._make_shortcut(self._link, command[0], command_line(command[1:]), COMMENT)
        return [str(self._link)]

    def uninstall(self) -> list[str]:
        if not self._link.exists():
            return []
        self._link.unlink()
        return [str(self._link)]

    def is_installed(self) -> bool:
        return self._link.exists()
