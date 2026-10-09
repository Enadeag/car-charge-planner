"""The user-facing config file: location, default contents, creation on demand."""

from __future__ import annotations

from pathlib import Path

from car_charge_planner import paths
from car_charge_planner.planner.config_io import write_config
from car_charge_planner.planner.model import first_run_config


def config_path() -> Path:
    return paths.config_dir() / "config.toml"


def secrets_path() -> Path:
    return config_path().with_name("secrets.toml")


def ensure_config(path: Path | None = None) -> Path:
    """Create the default config file if missing; never overwrite an existing one."""
    path = path or config_path()
    if not path.exists():
        write_config(first_run_config(), path)
    return path
