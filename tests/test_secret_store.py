"""Where the keys are kept: the system keychain, the file as fallback, and the move from one to the other."""

import json

import keyring
import pytest
from keyring.errors import PasswordSetError

from car_charge_planner.forecast.secret_store import dump_secrets, load_secrets, save_secrets, storage_in_use
from conftest import MemoryKeyring

KEYS = {"open-meteo": {"api_key": "OM-SECRET-1"}, "forecast-solar": {"api_key": "FS-SECRET-2"}}


def stored(keychain):
    return {name: value for (_service, name), value in keychain.entries.items()}


class FailingWrites(MemoryKeyring):
    """Refuses to store anything, as Credential Manager does over SSH without a logon session."""

    def set_password(self, service, username, password):
        raise PasswordSetError("A specified logon session does not exist")


class Forgetful(MemoryKeyring):
    """Says it stored a value and keeps nothing."""

    def set_password(self, service, username, password):
        pass


class Unreadable(MemoryKeyring):
    def get_password(self, service, username):
        raise OSError("locked")


def test_each_value_is_an_entry_named_by_provider_and_key_and_an_index_lists_the_names(tmp_path, keychain):
    save_secrets(KEYS, tmp_path / "secrets.toml")
    entries = stored(keychain)
    assert entries["forecast/open-meteo/api_key"] == "OM-SECRET-1"
    assert entries["forecast/forecast-solar/api_key"] == "FS-SECRET-2"
    assert json.loads(entries["index"]) == ["forecast/forecast-solar/api_key", "forecast/open-meteo/api_key"]
    assert not list(tmp_path.iterdir())


def test_saved_secrets_are_read_back_and_what_is_left_out_is_forgotten(tmp_path, keychain):
    path = tmp_path / "secrets.toml"
    save_secrets(KEYS, path)
    assert load_secrets(path) == KEYS
    save_secrets({"open-meteo": {"api_key": "OM-NEW", "empty": ""}}, path)
    assert load_secrets(path) == {"open-meteo": {"api_key": "OM-NEW"}}
    assert set(stored(keychain)) == {"index", "forecast/open-meteo/api_key"}


def test_a_file_from_an_earlier_version_moves_into_the_keychain_and_is_removed(tmp_path, keychain):
    path = tmp_path / "secrets.toml"
    path.write_text(dump_secrets(KEYS), encoding="utf-8")
    assert load_secrets(path) == KEYS
    assert not path.exists()
    assert stored(keychain)["forecast/open-meteo/api_key"] == "OM-SECRET-1"
    assert load_secrets(path) == KEYS


@pytest.mark.parametrize("backend", [FailingWrites, Forgetful])
def test_the_file_stays_and_is_used_when_the_move_does_not_work(tmp_path, backend):
    keyring.set_keyring(backend())
    path = tmp_path / "secrets.toml"
    path.write_text(dump_secrets(KEYS), encoding="utf-8")
    assert load_secrets(path) == KEYS
    assert path.exists() and load_secrets(path) == KEYS
    assert storage_in_use(path) == "file"


def test_when_the_keychain_refuses_a_save_the_keys_go_to_the_file(tmp_path):
    keyring.set_keyring(FailingWrites())
    path = tmp_path / "secrets.toml"
    save_secrets(KEYS, path)
    assert path.exists()
    assert load_secrets(path) == KEYS


def test_a_keychain_that_cannot_be_read_counts_as_none(tmp_path):
    keyring.set_keyring(Unreadable())
    path = tmp_path / "secrets.toml"
    assert load_secrets(path) == {}
    save_secrets(KEYS, path)
    assert load_secrets(path) == KEYS and storage_in_use(path) == "file"


def test_without_a_keychain_the_file_is_used(tmp_path, no_keychain):
    path = tmp_path / "secrets.toml"
    save_secrets(KEYS, path)
    assert load_secrets(path) == KEYS and storage_in_use(path) == "file"


def test_with_a_keychain_and_no_file_the_keychain_is_where_the_keys_are(tmp_path, keychain):
    assert storage_in_use(tmp_path / "secrets.toml") == "keychain"
