# One place for config, state and cache paths

Status: ready-for-agent
Type: task
Blocked by: M1

## Acceptance

- `platformdirs` is a dependency; one module `paths` gives the config file directory, state directory, cache directory and data directory for the app.
- The six places that read XDG variables today use it: config file, launcher, autostart, notifier icon lookup, forecast cache, state.
- **Linux is unchanged**: a test sets `XDG_CONFIG_HOME`, `XDG_STATE_HOME`, `XDG_CACHE_HOME`, `XDG_DATA_HOME` and shows the old paths come out, including with the variables unset (`~/.config/car-charge-planner`, `~/.local/state/...`, `~/.cache/...`).
- Windows and macOS give each system's usual places (`%APPDATA%`, `%LOCALAPPDATA%`; `~/Library/Application Support`, `~/Library/Caches`), tested with `platformdirs` pointed at fake environments.
- Existing tests that set `XDG_*` keep working; no migration is needed because Linux does not move.

## Comments
