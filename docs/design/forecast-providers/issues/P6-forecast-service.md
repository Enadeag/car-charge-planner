# ForecastService: primary, fallback, cache, cool-down

Status: done (not committed yet)
Type: feature
Blocked by: P1, P3, P5

Spec section 5. Replaces the direct fetch in the controller.

## Acceptance

- The service implements steps 1 to 6 of spec section 5 exactly; every result says which provider made it, whether it came from the fallback, its age, and whether it is stale; a cool-down after `RateLimited` (the retry time when given, else a default) is respected and visible.
- Caches are per provider, keyed by the system description and the provider's non-secret settings (`forecast-<id>.json`, atomic writes, tolerant of corruption); the old `forecast.json` is ignored.
- Fetches run off the UI thread; the late-result guard for configuration changes is kept; changing provider or system drops the shown forecast.
- The controller uses it; `ForecastResult` carries the new fields; the plan keeps working for any horizon.
- Tests with the fake provider: the full matrix in spec section 8 (primary ok; fails with fresh cache; fails with stale cache and a fallback; fallback also fails; rate limit and cool-down; recovery; changes in flight).

## Comments

- `forecast/service.py`: `ForecastService.get(cfg, now, force)` and the moved `ForecastResult` (the old fields first, so existing callers still work; new: `provider_id`, `provider_name`, `attribution`, `from_fallback`, `primary_name`, `cooldown_until`) and `ForecastUnavailable`. The old `forecast/open_meteo.py` and its tests are gone; their behaviour is in the provider tests (P3) and the service tests.
- **The rules, as built** (each is a test, and the main ones were mutation-checked): a fresh cache (younger than the provider's minimum refresh) answers without a request; a forced refresh skips that but never a cool-down; on failure the primary's cache keeps being used while younger than its useful age (`stale`); only then the fallback is asked, with its own cache and rules; a recovered primary answers again at the next refresh; nothing usable gives `ForecastUnavailable` with the reason of each provider.
- **Three kinds of failure are told apart**: `Unavailable` (never got an answer) is retried after 5 minutes (it did not use the provider's allowance; a forced refresh may push sooner); `BadResponse` or an unexpected error counts as a request and waits the minimum refresh; `RateLimited` starts a cool-down of the retry time given, else the minimum refresh. The cool-down is **stored in the cache file**, so a restart does not ask again at once (Forecast.Solar warns about 429 loops).
- **Caches** are `forecast-<id>.json` (steps as power, fetched time, the key, the cool-down), atomic, tolerant of damage (an unreadable file, a wrong shape or steps that break the contract are ignored). The key is the system description plus the provider's non-secret settings: a bigger array or another place makes the cache not apply, a changed key does not. The old `forecast.json` is never read.
- **The controller** owns a `ForecastService` (injectable); `forecast_key` now includes panel size and losses (the provider applies them) and the selected providers, so changing either drops the shown forecast. The fetch still runs on a worker thread with the late-result guard; the service has a lock so two fetches never both spend a provider's allowance (tested with two threads).
- A secrets file that cannot be read does not stop providers that need no key; its message is added to the reasons only if nothing answered.
- `planner.cli --live` uses the service and says who answered. README updated (providers, cache files, `secrets.toml`).
- The test seam moved: tests patch `forecast.service.ForecastService.get` where they used to patch `app.controller.get_forecast`.
- Not here: the footer text and the Settings tab (P8, P7).
