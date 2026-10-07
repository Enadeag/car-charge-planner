"""secrets.toml: API keys and the like, kept apart from config.toml.

    [open-meteo]
    api_key = "..."

The file is private to the user (mode 0600 wherever file modes exist), written atomically, and its values are
never put in a log line or an error message here.
"""

from __future__ import annotations

import json
import os
import re
import tomllib
from pathlib import Path

from app.i18n import _

HEADER = """\
# API keys for the car charge planner's forecast providers. Written by its Settings window; keep it private.
"""
KEY = re.compile(r"^[a-z0-9][a-z0-9_-]*$")
Secrets = dict[str, dict[str, str]]  # provider id -> {setting key: value}


class SecretsError(Exception):
    """secrets.toml cannot be read or written safely. The message never contains a secret."""


def _has_file_modes() -> bool:
    return os.name == "posix"


def ensure_private(path: Path) -> bool:
    """Make the file private to its owner. True when its mode had to be corrected.

    Raises `SecretsError` when it is open to others and cannot be fixed.
    """
    if not _has_file_modes():
        return False
    try:
        mode = path.stat().st_mode & 0o777
        if mode & 0o077 == 0:
            return False
        os.chmod(path, 0o600)
    except OSError as err:
        raise SecretsError(_("{file} is readable by other users and could not be made private: {error}").format(file=path, error=err.strerror)) from None
    return True


def load_secrets(path: Path) -> Secrets:
    """The stored secrets; {} when there is no file. A file open to others is made private first."""
    path = Path(path)
    if not path.exists():
        return {}
    ensure_private(path)
    try:
        data = tomllib.loads(path.read_text())
    except OSError as err:
        raise SecretsError(_("Could not read {file}: {error}").format(file=path, error=err.strerror)) from None
    except tomllib.TOMLDecodeError:  # its text would quote part of the file: say where, not what
        raise SecretsError(_("{file} is not valid TOML. Fix it or delete it and enter the keys again.").format(file=path)) from None
    secrets: Secrets = {}
    for provider_id, values in data.items():
        if not isinstance(values, dict) or not all(isinstance(v, str) for v in values.values()):
            raise SecretsError(_("{file}: [{id}] must hold texts only").format(file=path, id=provider_id))
        secrets[provider_id] = dict(values)
    return secrets


def dump_secrets(secrets: Secrets) -> str:
    lines = [HEADER.rstrip("\n")]
    for provider_id in sorted(secrets):
        values = {k: v for k, v in secrets[provider_id].items() if v}
        if not values:
            continue
        if not KEY.match(provider_id) or not all(KEY.match(k) for k in values):
            raise SecretsError(_("A provider id or setting name has characters that are not allowed"))
        lines += ["", f"[{provider_id}]"]
        lines += [f"{key} = {json.dumps(values[key])}" for key in sorted(values)]
    return "\n".join(lines) + "\n"


def save_secrets(secrets: Secrets, path: Path) -> None:
    """Write atomically. The file is created private: there is no moment when it can be read by others."""
    path = Path(path)
    text = dump_secrets(secrets)
    path.parent.mkdir(parents=True, exist_ok=True)
    tmp = path.with_name(path.name + ".tmp")
    try:
        fd = os.open(tmp, os.O_WRONLY | os.O_CREAT | os.O_TRUNC, 0o600)
        with os.fdopen(fd, "w") as handle:
            handle.write(text)
        if _has_file_modes():
            os.chmod(tmp, 0o600)  # also when an old temporary file existed with another mode
        os.replace(tmp, path)
    except OSError as err:
        tmp.unlink(missing_ok=True)
        raise SecretsError(_("Could not write {file}: {error}").format(file=path, error=err.strerror)) from None
