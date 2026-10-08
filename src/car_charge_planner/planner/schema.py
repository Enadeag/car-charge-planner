"""The format version of the files the app writes (config.toml, state.json) and the steps between versions."""

from __future__ import annotations

from collections.abc import Callable, Mapping
from typing import Any

from car_charge_planner.i18n import _

Migration = Callable[[dict[str, Any]], dict[str, Any]]
"""Turns the data of version n into the data of version n + 1 (named migrate_<n>_to_<n+1> by convention)."""

KEY = "schema"
FIRST_VERSION = 1  # a file without the key predates versioning, which is version 1


class SchemaError(ValueError):
    """The file's version cannot be handled; the message is meant for the owner."""


class NewerVersionError(SchemaError):
    """The file was written by a newer version of the app than this one."""


def upgrade(data: dict[str, Any], current: int, migrations: Mapping[int, Migration], name: str) -> dict[str, Any]:
    """Bring parsed file data up to `current` by running the migrations in order; the `schema` key is removed."""
    found: Any = data.pop(KEY, FIRST_VERSION)
    if isinstance(found, bool) or not isinstance(found, int) or found < FIRST_VERSION:
        raise SchemaError(_("{name}: schema must be a whole number from 1 up, not {value!r}").format(name=name, value=found))
    if found > current:
        raise NewerVersionError(
            _("{name} was written by a newer version of the app (file format {found}, this version reads up to {current}). Update the app to use it.").format(
                name=name, found=found, current=current
            )
        )
    for version in range(found, current):
        step = migrations.get(version)
        if step is None:
            raise SchemaError(
                _("{name} is in file format {found} and cannot be upgraded to {current}.").format(name=name, found=version, current=current)
            )
        data = step(data)
    return data
