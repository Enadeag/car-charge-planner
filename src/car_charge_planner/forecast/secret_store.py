"""API keys and the like: in the system keychain, or in secrets.toml when there is none.

The keychain (Secret Service, Credential Manager, Keychain) holds one entry per value, named
`forecast/<provider>/<key>` under the service `car-charge-planner`, so other integrations can add their own
prefix. A keychain cannot list its entries, so one more entry, `index`, holds the names as a JSON list. It is in
the keychain, not the config, so that the names and the values cannot get out of step when the config is
copied or deleted; it holds no values.

Without a usable keychain the secrets live in secrets.toml beside the config:

    [open-meteo]
    api_key = "..."

That file is private to the user (mode 0600 wherever file modes exist) and written atomically. A file found
while a keychain is usable is moved into it once, and removed only after every value was written and read back.
Values are never put in a log line or an error message here.
"""

from __future__ import annotations

import json
import os
import re
import tomllib
from pathlib import Path
from typing import Any, Literal, cast

import keyring
from keyring.backends import fail, null
from keyring.errors import PasswordDeleteError

from car_charge_planner.i18n import _

HEADER = """\
# API keys for the car charge planner's forecast providers. Written by its Settings window; keep it private.
"""
KEY = re.compile(r"^[a-z0-9][a-z0-9_-]*$")
Secrets = dict[str, dict[str, str]]  # provider id -> {setting key: value}
SERVICE = "car-charge-planner"
INDEX = "index"


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


def _entry(provider_id: str, key: str) -> str:
    return f"forecast/{provider_id}/{key}"


def _clean(secrets: Secrets) -> Secrets:
    """Without empty values; refuses names that could not be told apart in an entry name."""
    cleaned: Secrets = {}
    for provider_id, values in secrets.items():
        kept = {k: v for k, v in values.items() if v}
        if not kept:
            continue
        if not KEY.match(provider_id) or not all(KEY.match(k) for k in kept):
            raise SecretsError(_("A provider id or setting name has characters that are not allowed"))
        cleaned[provider_id] = kept
    return cleaned


def keychain_usable() -> bool:
    """False when there is no real backend (no Secret Service on a headless or minimal Linux, for one) or it fails."""
    if isinstance(keyring.get_keyring(), fail.Keyring | null.Keyring):
        return False
    try:
        keyring.get_password(SERVICE, INDEX)
    except Exception:  # a backend that is there but cannot be used, e.g. Credential Manager without a logon session (error 1312)
        return False
    return True


def _read_index() -> list[str]:
    raw = keyring.get_password(SERVICE, INDEX)
    names: object = json.loads(raw) if raw else []
    if not isinstance(names, list) or not all(isinstance(n, str) for n in cast("list[object]", names)):
        raise ValueError("index")
    return cast("list[str]", names)


def _read_keychain() -> Secrets:
    secrets: Secrets = {}
    for name in _read_index():
        _, provider_id, key = name.split("/", 2)
        value = keyring.get_password(SERVICE, name)
        if value:
            secrets.setdefault(provider_id, {})[key] = value
    return secrets


def _write_keychain(secrets: Secrets) -> None:
    """Replace everything with `secrets`; raises when any value did not read back. The index is written last."""
    wanted = {_entry(p, k): v for p, values in secrets.items() for k, v in values.items()}
    try:
        old = _read_index()
    except (ValueError, TypeError):  # a damaged index: its entries are lost to us, not worth stopping for
        old = []
    for name, value in wanted.items():
        keyring.set_password(SERVICE, name, value)
        if keyring.get_password(SERVICE, name) != value:
            raise SecretsError(_("The system keychain did not keep a value that was written to it"))
    index = json.dumps(sorted(wanted))
    keyring.set_password(SERVICE, INDEX, index)
    if keyring.get_password(SERVICE, INDEX) != index:
        raise SecretsError(_("The system keychain did not keep a value that was written to it"))
    for name in set(old) - set(wanted):
        try:
            keyring.delete_password(SERVICE, name)
        except PasswordDeleteError:
            pass  # already gone


def _read_file(path: Path) -> Secrets:
    ensure_private(path)
    try:
        data = tomllib.loads(path.read_text(encoding="utf-8"))
    except OSError as err:
        raise SecretsError(_("Could not read {file}: {error}").format(file=path, error=err.strerror)) from None
    except tomllib.TOMLDecodeError:  # its text would quote part of the file: say where, not what
        raise SecretsError(_("{file} is not valid TOML. Fix it or delete it and enter the keys again.").format(file=path)) from None
    secrets: Secrets = {}
    for provider_id, values in data.items():
        if not isinstance(values, dict) or not all(isinstance(v, str) for v in cast("dict[str, Any]", values).values()):
            raise SecretsError(_("{file}: [{id}] must hold texts only").format(file=path, id=provider_id))
        secrets[provider_id] = dict(cast("dict[str, str]", values))
    return secrets


def _write_file(secrets: Secrets, path: Path) -> None:
    """Write atomically. The file is created private: there is no moment when it can be read by others."""
    text = dump_secrets(secrets)
    path.parent.mkdir(parents=True, exist_ok=True)
    tmp = path.with_name(path.name + ".tmp")
    try:
        fd = os.open(tmp, os.O_WRONLY | os.O_CREAT | os.O_TRUNC, 0o600)
        with os.fdopen(fd, "w", encoding="utf-8") as handle:
            handle.write(text)
        if _has_file_modes():
            os.chmod(tmp, 0o600)  # also when an old temporary file existed with another mode
        os.replace(tmp, path)
    except OSError as err:
        tmp.unlink(missing_ok=True)
        raise SecretsError(_("Could not write {file}: {error}").format(file=path, error=err.strerror)) from None


def _move_to_keychain(path: Path) -> Secrets | None:
    """Move the file into the keychain. The file's secrets when that failed and the file stays, else None."""
    secrets = _clean(_read_file(path))
    try:
        _write_keychain(secrets)
        if _read_keychain() != secrets:
            raise SecretsError("not equal")
    except Exception:  # any keychain error (backends raise their own types, some not keyring's): keep the file
        return secrets
    try:
        path.unlink()
    except OSError:
        pass  # the values are safe in the keychain; the next start tries again
    return None


def load_secrets(path: Path) -> Secrets:
    """The stored secrets; {} when there are none. `path` is secrets.toml, the fallback and the source of the move."""
    path = Path(path)
    if keychain_usable():
        if path.exists():
            kept = _move_to_keychain(path)
            if kept is not None:
                return kept
        try:
            return _read_keychain()
        except Exception:  # see _move_to_keychain; the message holds no secret
            raise SecretsError(_("The system keychain could not be read. Unlock it, or enter the keys again.")) from None
    return _read_file(path) if path.exists() else {}


def save_secrets(secrets: Secrets, path: Path) -> None:
    """Store them all (what is not in `secrets` is forgotten): in the keychain, or in `path` when that fails."""
    path = Path(path)
    secrets = _clean(secrets)
    if keychain_usable():
        try:
            _write_keychain(secrets)
        except Exception:  # e.g. Windows error 1312 over SSH: no keychain after all, fall back to the file
            pass
        else:
            try:
                path.unlink(missing_ok=True)
            except OSError:
                pass  # the values are safe in the keychain; the next start tries again
            return
    _write_file(secrets, path)


def storage_in_use(path: Path) -> Literal["keychain", "file"]:
    """Where the secrets are kept now: "file" when there is no usable keychain or the file could not be moved."""
    return "file" if not keychain_usable() or Path(path).exists() else "keychain"
