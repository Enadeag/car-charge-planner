# Provider settings and secrets

Status: done (not committed yet)
Type: task
Blocked by: P1

Spec section 6.

## Acceptance

- Config `[forecast]` (`primary`, `fallback`) and `[forecast.<id>]` per provider; validated: known ids, primary and fallback differ, required settings present; a config without it means Open-Meteo and no fallback.
- `secrets.toml` beside the config: read and written atomically with mode 0600; a looser mode is corrected or refused with a clear message; keys never written to `config.toml`, never logged.
- The settings window's field machinery supports a masked "secret" kind and fields declared by a provider at run time.
- `config_io` writes the new sections; the round trip is exact.
- Tests: permissions, separation of secrets from the config, masking, validation, an old config.

## Comments

- **Config**: `Config.forecast` (`primary`, `fallback`, `settings`: provider id -> plain settings), written as `[forecast]` and `[forecast.<id>]`; a config without it (every config made so far) means Open-Meteo, no fallback. `validate` checks the *shape* (ids are lower case letters, digits and hyphens; primary not empty; fallback empty or different; setting keys and values are texts). An unknown key in `[forecast]` is refused like in every other section.
- **What needs the providers lives outside the planner**: `forecast/selection.py` has `check_selection` (unknown provider ids, unknown setting keys, required settings missing, with secrets taken from the secrets file), `provider_settings` (what `fetch` gets) and `split_settings` (plain versus secret). An unknown provider id in the file is *not* a load error: it may come from a newer version, and the app should still start. The service (P6) and the Settings tab (P7) call `check_selection`.
- **Secrets**: `forecast/secret_store.py`. Created private (`os.open` with 0600, so there is no moment it is readable by others, even with umask 0 or a stale temporary file), atomic replace, a file open to others is **corrected** on load and refused only if the correction fails; where file modes do not exist (Windows) nothing is changed. Errors never contain a value (TOML errors are reported as "not valid TOML" with the file name only). `app/config_file.secrets_path()` gives the location.
- **A backstop in `config_io`**: a plain setting whose key looks like a secret (`key`, `secret`, `token`, `password`) is refused when writing `config.toml`. A secret found in `config.toml` is also never *used* (tested; found by a mutation check).
- **Settings machinery**: field kinds `text` and `secret` (a masked box with a Show/Hide button); `provider_fields(provider)` builds the fields a provider declares, keys `forecast.<id>.<setting>`; `get_value`/`set_value` read and write them in the config (an empty value removes the setting); a `FieldTab` takes the secrets by field key and gives them back with `collect_secrets()`, and `apply_to` never puts a secret in the `Config`.
- **For P7 to remember**: the window's "unsaved changes" test compares configs, so an edited secret does not mark the window dirty yet: the Forecast tab must include `collect_secrets()` in it, and save through `split_settings` and `save_secrets`.
- Provider ids may not contain a dot (the registry enforces the config's rule).
