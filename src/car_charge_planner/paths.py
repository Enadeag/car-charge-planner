"""Where the app keeps its files: one place for every system's usual directories."""

from __future__ import annotations

from pathlib import Path

from platformdirs import PlatformDirs

APP_DIR = "car-charge-planner"


def _dirs(app: str | None, roaming: bool = False) -> PlatformDirs:
    return PlatformDirs(app, appauthor=False, roaming=roaming)


def config_dir() -> Path:
    return Path(_dirs(APP_DIR, roaming=True).user_config_dir)  # the settings follow the user on Windows; state and cache stay on the machine


def state_dir() -> Path:
    return Path(_dirs(APP_DIR).user_state_dir)


def cache_dir() -> Path:
    return Path(_dirs(APP_DIR).user_cache_dir)


def data_dir() -> Path:
    return Path(_dirs(APP_DIR).user_data_dir)


def shared_config_dir() -> Path:
    """The user's own config directory, shared with other programs (Linux: where `autostart/` lives)."""
    return Path(_dirs(None, roaming=True).user_config_dir)


def shared_data_dir() -> Path:
    """The user's own data directory, shared with other programs (Linux: where launcher entries and icons live)."""
    return Path(_dirs(None).user_data_dir)
