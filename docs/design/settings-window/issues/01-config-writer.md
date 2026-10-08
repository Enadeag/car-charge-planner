# Config writer

Status: resolved
Type: task
Blocked by: none

Write a valid `config.toml` from a `Config` (spec 3.5). No UI.

## Acceptance

- `planner/config_io.py`: `dump_config(cfg) -> str` and `write_config(cfg, path)`.
- Header comment says the Settings window manages the file; values only otherwise.
- Same keys and tables `load_config` reads, including the away rules array.
- Atomic write (temp file, then rename); a failed write leaves the old file untouched and raises.
- Round trip: `load_config(write_config(cfg)) == cfg` for defaults, a changed config, several away rules, and no away rules.
- `ensure_config` creates its first file through the writer; remove `DEFAULT_CONFIG_TOML` and update its test.

## Comments

Implemented in `planner/config_io.py` (`dump_config`, `write_config`); `ensure_config` now creates the first file through it and `DEFAULT_CONFIG_TOML` is gone. 12 tests in `tests/test_config_io.py`.

Notes:
- `write_config` runs `validate()` first, so an invalid config can never reach the file.
- An empty away list is written as `recurring = []`. If the key were missing, `load_config` would bring back the default example rule on the next start.
- Floats use `repr`, so `km_per_day = 85.71428571428571` round-trips exactly.
- The existing `~/.config/car-charge-planner/config.toml` (with the old comments) is untouched until the Settings window saves for the first time.
