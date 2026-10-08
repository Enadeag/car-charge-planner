# Open-Meteo forecast client with cache

Status: resolved
Type: task
Blocked by: 02

Fetch the solar forecast and feed it to the planner (spec 4.1, 4.7).

## Acceptance

- Client for `api.open-meteo.com/v1/forecast` with `global_tilted_irradiance`, tilt, azimuth, timezone Europe/Amsterdam, `forecast_days` up to 16.
- Cache of the last good response on disk under the XDG cache dir; planner runs from cache when offline and exposes the cache's age.
- Fetch at most hourly.
- Tests use a recorded JSON fixture: parsing, and cache fallback when the network fails.
- CLI from 02 now uses live data.

## Comments

Implemented in `forecast/open_meteo.py` with 11 tests (`tests/test_open_meteo.py`, recorded fixture `tests/fixtures/open_meteo_3days.json`).
Run live: `.venv/bin/python -m planner.cli --live --battery 45` (`--refresh` ignores the cache). Cache: `~/.cache/car-charge-planner/forecast.json`.

Findings:
- Open-Meteo hourly irradiance is the mean of the preceding hour (checked against `global_tilted_irradiance_instant`), so the value stamped T becomes the step [T-1h, T). Without this every window would be an hour late.
- Fetches 4 days by default (`FORECAST_DAYS`); cache is keyed on location, tilt, azimuth, timezone, so changing panel config forces a refetch.
- Cache failures never break planning; a failed refresh serves the cache flagged `stale`.
