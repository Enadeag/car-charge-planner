"""The desktop app's own settings (how it shows itself, in which units and language), kept in app.toml.

They are not the engine's: an engine on another machine would still leave them on this computer.
"""

from __future__ import annotations

import os
import re
import tomllib
from collections.abc import Callable
from dataclasses import dataclass, fields, replace
from pathlib import Path
from typing import Any, Protocol, cast

from . import paths, units
from .i18n import _
from .planner.schema import KEY, SchemaError, upgrade

APP_SCHEMA = 1  # the app.toml format this version writes
INTERFACE_MODES = ("auto", "tray", "window")
LANGUAGE_CODE = re.compile(r"^[A-Za-z]{2,3}([_-][A-Za-z0-9]{2,8})*$")
SECTION = "interface"
CONFIG_SPLIT_SCHEMA = 3  # config.toml held the [interface] table up to format 2

HEADER = """\
# The desktop app's own settings, managed by its Settings window.
# Edit by hand only while the app is closed; comments are not kept when the window saves.
"""


@dataclass(frozen=True)
class AppSettings:
    """How the app shows itself: a tray icon with a popup, an ordinary window, or whichever the desktop supports."""

    mode: str = "auto"
    units: str = units.METRIC  # how distances and consumption are shown and typed; stored values stay metric
    language: str = "auto"  # "auto" follows the system; otherwise a language code such as "nl" or "pt_BR"


class AppSettingsError(ValueError):
    """app.toml cannot be read or holds a value the app cannot use; the message is for the owner."""


def problems(settings: AppSettings) -> list[str]:
    """What is wrong with these settings, in words for the owner. Empty when nothing is."""
    found: list[str] = []
    if settings.mode not in INTERFACE_MODES:
        found.append(_("[interface] mode must be one of {modes}, not {mode!r}").format(modes=", ".join(INTERFACE_MODES), mode=settings.mode))
    if settings.units not in units.SYSTEMS:
        found.append(_("[interface] units must be one of {units}, not {value!r}").format(units=", ".join(units.SYSTEMS), value=settings.units))
    if settings.language != "auto" and not LANGUAGE_CODE.match(str(settings.language)):
        found.append(
            _("[interface] language must be \"auto\" or a language code such as \"nl\", not {language!r}").format(language=settings.language)
        )
    return found


def app_settings_path() -> Path:
    return paths.config_dir() / "app.toml"  # beside config.toml


def _read(table: Any, name: str) -> AppSettings:
    if not isinstance(table, dict):
        raise AppSettingsError(_("{name}: [interface] must be a table").format(name=name))
    table = cast("dict[str, Any]", table)
    unknown = set(table) - {f.name for f in fields(AppSettings)}
    if unknown:
        raise AppSettingsError(_("unknown keys in [{section}]: {keys}").format(section=SECTION, keys=sorted(unknown)))
    settings = replace(AppSettings(), **table)
    if found := problems(settings):
        raise AppSettingsError(_("Invalid settings in {name}:").format(name=name) + "\n- " + "\n- ".join(found))
    return settings


def _parse(path: Path) -> dict[str, Any]:
    try:
        return tomllib.loads(path.read_text(encoding="utf-8"))
    except tomllib.TOMLDecodeError as err:
        raise AppSettingsError(_("{name} is not valid TOML: {error}").format(name=path.name, error=err)) from err


def load_app_settings(path: Path) -> AppSettings:
    """The settings in `path`; the built-in ones when there is no such file yet. Raises `AppSettingsError`."""
    if not path.exists():
        return AppSettings()
    try:
        data = upgrade(_parse(path), APP_SCHEMA, {}, path.name)
    except SchemaError as err:
        raise AppSettingsError(str(err)) from err
    return _read(data.get(SECTION, {}), path.name)


def dump_app_settings(settings: AppSettings) -> str:
    lines = [HEADER.rstrip("\n"), "", f"{KEY} = {APP_SCHEMA}", "", f"[{SECTION}]"]
    lines += [f'{f.name} = "{getattr(settings, f.name)}"' for f in fields(settings)]  # checked values: no quotes inside
    return "\n".join(lines) + "\n"


def write_app_settings(settings: AppSettings, path: Path) -> None:
    """Check, then replace the file atomically. On any failure the old file is left as it was."""
    if found := problems(settings):
        raise AppSettingsError("\n".join(found))
    path.parent.mkdir(parents=True, exist_ok=True)
    tmp = path.with_name(path.name + ".tmp")
    try:
        tmp.write_text(dump_app_settings(settings), encoding="utf-8")
        os.replace(tmp, path)
    except OSError:
        tmp.unlink(missing_ok=True)
        raise


def adopt_interface(config_file: Path, app_file: Path) -> bool:
    """Take the settings a config.toml from before format 3 keeps in [interface] into app.toml. True when it did.

    Runs before the engine reads config.toml, which drops the table when it writes the file in the new format. So
    app.toml is written first: when the app stops in between, the next start finds app.toml and leaves it, and the
    engine still finds the old file to rewrite. An existing app.toml is never replaced.
    Raises `AppSettingsError` for a table it cannot use, `OSError` when app.toml cannot be written: the start must then
    stop, or the engine would drop the table.
    """
    if app_file.exists() or not config_file.exists():
        return False
    try:
        data = tomllib.loads(config_file.read_text(encoding="utf-8"))
    except tomllib.TOMLDecodeError:
        return False  # the engine reports a broken config.toml when it reads it, and writes nothing
    version: Any = data.get(KEY, 1)
    if SECTION not in data or not isinstance(version, int) or version >= CONFIG_SPLIT_SCHEMA:
        return False
    write_app_settings(_read(data[SECTION], config_file.name), app_file)
    return True


class AppSettingsStore(Protocol):
    def save(self, settings: AppSettings) -> None:
        """Checked by the caller. Raises `OSError`; on a failure the stored settings are unchanged."""
        ...


class AppSettingsFile:
    """app.toml in the user's config folder."""

    def __init__(self, path: Path | None = None) -> None:
        self.path = path or app_settings_path()

    def load(self) -> AppSettings:
        return load_app_settings(self.path)

    def save(self, settings: AppSettings) -> None:
        write_app_settings(settings, self.path)


class DesktopSettings:
    """The desktop app's own settings while it runs: the windows read `current` and change it only through `save`."""

    def __init__(self, current: AppSettings | None = None, store: AppSettingsStore | None = None) -> None:
        """Without a store they are kept for this run only."""
        self._current = current or AppSettings()
        self._store = store
        self._watchers: list[Callable[[AppSettings, AppSettings], None]] = []

    @property
    def current(self) -> AppSettings:
        return self._current

    def watch(self, watcher: Callable[[AppSettings, AppSettings], None]) -> None:
        """`watcher(old, new)` is called after new settings are saved."""
        self._watchers.append(watcher)

    def save(self, settings: AppSettings) -> list[str]:
        """Check and store them. Returns what went wrong, in words for the owner; then nothing changed."""
        if found := problems(settings):
            return found
        if settings == self._current:
            return []
        if self._store is not None:
            try:
                self._store.save(settings)
            except OSError as err:
                return [_("Could not write the app's settings: {error}").format(error=err)]
        old, self._current = self._current, settings
        for watcher in self._watchers:
            watcher(old, settings)
        return []
