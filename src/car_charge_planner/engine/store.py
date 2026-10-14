"""Where the engine keeps its data: the `Store` port, and `FileStore`, the files and keychain of the user's account."""

from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path
from typing import Literal, Protocol

from car_charge_planner.config.io import load_current, write_config
from car_charge_planner.config.model import Config, load_config
from car_charge_planner.config_file import config_path, ensure_config
from car_charge_planner.forecast.secret_store import Secrets, load_secrets, save_secrets, storage_in_use
from car_charge_planner.i18n import _
from car_charge_planner.storage.state import State, load_state, save_state


class StoreError(Exception):
    """Something could not be written; the message is for the owner."""


@dataclass(frozen=True)
class KeyStorage:
    """Where the API keys are kept: in the system keychain, or in `file` when there is none."""

    kind: Literal["keychain", "file"]
    file: str


class Store(Protocol):
    def save_config(self, cfg: Config) -> None:
        """Validated by the caller. Raises `StoreError`; on a failure the stored config is unchanged."""
        ...

    def load_state(self) -> State: ...

    def save_state(self, state: State) -> None: ...

    def load_secrets(self) -> Secrets:
        """Raises `SecretsError` when they cannot be read."""
        ...

    def save_secrets(self, secrets: Secrets) -> None:
        """All of them: what is not in `secrets` is forgotten. Raises `SecretsError`."""
        ...

    def key_storage(self) -> KeyStorage: ...


class FileStore:
    """config.toml and state.json in the user's folders, the keys in the keychain or in secrets.toml beside config.toml."""

    def __init__(self, config_file: Path | None = None) -> None:
        self.config_file = config_file or config_path()
        self.secrets_file = self.config_file.with_name("secrets.toml")

    def load_config(self) -> Config:
        """Creates the first config file when there is none, and writes one in an older format in the current one.
        Raises `ConfigError` when the file is wrong.

        A desktop app takes its own settings out of an older file first (app_settings.adopt_interface): the new
        format has no place for them."""
        path = ensure_config(self.config_file)
        try:
            return load_current(path)
        except OSError:
            return load_config(path)  # not written now: the next start tries again, and the old file still reads

    def save_config(self, cfg: Config) -> None:
        try:
            write_config(cfg, self.config_file)
        except OSError as err:
            raise StoreError(_("Could not write {file}: {error}").format(file=self.config_file, error=err)) from None

    def load_state(self) -> State:
        return load_state()

    def save_state(self, state: State) -> None:
        save_state(state)

    def load_secrets(self) -> Secrets:
        return load_secrets(self.secrets_file)

    def save_secrets(self, secrets: Secrets) -> None:
        save_secrets(secrets, self.secrets_file)

    def key_storage(self) -> KeyStorage:
        return KeyStorage(storage_in_use(self.secrets_file), str(self.secrets_file))
