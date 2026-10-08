# Apply a new config without restarting

Status: resolved
Type: task
Blocked by: none

`TrayApp.apply_config(cfg)` (spec 3.3), usable by anything that produces a `Config`.

## Acceptance

- Swaps the config, updates the app's and the popup's time zone, replans at once.
- Calls `refresh()`: a changed location, tilt, direction or time zone changes the cache key so the forecast is fetched again; other changes reuse the cache.
- A refresh that began under the old config and finishes after the swap does not leave the old result in place: the app fetches again.
- Announced (pinned) windows are unaffected.
- Offscreen tests: margin change replans without a fetch; tilt change triggers a fetch; late result from the old config is discarded.

## Comments

Implemented as `TrayApp.apply_config(cfg)` in `app/tray.py`, plus `forecast_key(cfg)` in `forecast/open_meteo.py`. 6 tests in `tests/test_apply_config.py`.

Notes:
- A change to place, tilt, direction or time zone clears the current forecast (the tray shows "Loading forecast..." until the new one arrives) instead of planning with a forecast for the wrong panels. Other changes keep it.
- The `forecast_loaded` signal now carries the `Config` the fetch ran under. A result from an older config is dropped and a new fetch starts.
- Mutation check: with that guard disabled, the late-fetch test fails.
