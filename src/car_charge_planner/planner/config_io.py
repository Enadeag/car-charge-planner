"""Writing config.toml from a Config (the reading side is load_config in model.py)."""

from __future__ import annotations

import json
import os
import re
from dataclasses import fields
from pathlib import Path

from .model import CONFIG_SCHEMA, Config, ConfigError, validate
from .schema import KEY

HEADER = """\
# Managed by the car charge planner's Settings window.
# Edit by hand only while the app is closed; comments are not kept when the window saves.
"""

SECRET_LOOKING = re.compile(r"key|secret|token|password|passwd", re.IGNORECASE)
SECTIONS = ("house", "car", "charger", "planning", "notify", "interface")  # after the [[panels]] arrays


def _value(value: object) -> str:
    if isinstance(value, bool):  # not used today; keeps TOML booleans lowercase if one is added
        return "true" if value else "false"
    if isinstance(value, float):
        return repr(value)  # shortest text that reads back as the same float
    if isinstance(value, int):
        return str(value)
    if isinstance(value, str):
        return json.dumps(value)  # TOML basic strings use the same escapes for ordinary text
    raise TypeError(f"cannot write {value!r} to TOML")


def dump_config(cfg: Config) -> str:
    lines = [HEADER.rstrip("\n"), "", f"{KEY} = {CONFIG_SCHEMA}", ""]
    lines += [
        f"timezone = {_value(cfg.timezone)}",
        f"latitude = {_value(cfg.latitude)}",
        f"longitude = {_value(cfg.longitude)}",
    ]
    for panels in cfg.panels:  # one [[panels]] table for each array; the name only when there is one
        lines += ["", "[[panels]]"] + ([f"name = {_value(panels.name)}"] if panels.name else [])
        lines += [f"{f.name} = {_value(getattr(panels, f.name))}" for f in fields(panels) if f.name != "name"]
    for name in SECTIONS:
        section = getattr(cfg, name)
        lines += ["", f"[{name}]"]
        lines += [f"{f.name} = {_value(getattr(section, f.name))}" for f in fields(section)]
    lines += ["", "[forecast]", f"primary = {_value(cfg.forecast.primary)}", f"fallback = {_value(cfg.forecast.fallback)}"]
    for provider_id in sorted(cfg.forecast.settings):
        values = cfg.forecast.settings[provider_id]
        for key in values:
            if SECRET_LOOKING.search(key):  # a backstop: secrets belong in secrets.toml, never here
                raise ConfigError(f"{key!r} looks like a secret and must not be written to the config file")
        lines += ["", f"[forecast.{provider_id}]"]
        lines += [f"{key} = {_value(values[key])}" for key in sorted(values)]
    typical = ", ".join(_value(key) for key in cfg.setup.typical)
    lines += ["", "[setup]", f"typical = [{typical}]"]
    lines += ["", "[away]"]
    # always written, even empty: a missing key would bring back the default rule on the next start
    if cfg.away_rules:
        lines.append("recurring = [")
        for rule in cfg.away_rules:
            lines.append(
                f'  {{weekday = {rule.weekday}, start = "{rule.start:%H:%M}", end = "{rule.end:%H:%M}"}},'
            )
        lines.append("]")
    else:
        lines.append("recurring = []")
    return "\n".join(lines) + "\n"


def write_config(cfg: Config, path: Path) -> None:
    """Validate, then replace the file atomically. On any failure the old file is left as it was."""
    validate(cfg)  # raises ConfigError before anything is written
    path = Path(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    tmp = path.with_name(path.name + ".tmp")
    try:
        tmp.write_text(dump_config(cfg), encoding="utf-8")
        os.replace(tmp, path)
    except OSError:
        tmp.unlink(missing_ok=True)
        raise
