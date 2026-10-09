"""Let the XDG_*_HOME variables decide where the app keeps its files, on every system.

`paths` follows each system's own convention (platformdirs), which on Windows and macOS ignores these variables. Tests use
them to give each test its own folders, so they put the locations of `paths` on the variables instead.
"""

import os
from pathlib import Path

from car_charge_planner import paths


def _base(variable: str, fallback: Path) -> Path:
    return Path(os.environ.get(variable) or fallback)


def locations(fallback: Path) -> dict[str, object]:
    """The functions of `paths` with the XDG variables as their source and `fallback` for any that is not set."""
    app = paths.APP_DIR
    return {
        "config_dir": lambda: _base("XDG_CONFIG_HOME", fallback / "config") / app,
        "state_dir": lambda: _base("XDG_STATE_HOME", fallback / "state") / app,
        "cache_dir": lambda: _base("XDG_CACHE_HOME", fallback / "cache") / app,
        "data_dir": lambda: _base("XDG_DATA_HOME", fallback / "data") / app,
        "shared_config_dir": lambda: _base("XDG_CONFIG_HOME", fallback / "config"),
        "shared_data_dir": lambda: _base("XDG_DATA_HOME", fallback / "data"),
        "start_menu_dir": lambda: fallback / "start-menu",
        "launch_agents_dir": lambda: fallback / "launch-agents",
    }


def apply() -> None:
    """For a process the test starts, which sets every variable: from here on `paths` follows them."""
    for name, function in locations(Path("unset")).items():
        setattr(paths, name, function)
