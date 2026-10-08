# The provider contract and power-based steps

Status: done (not committed yet)
Type: task
Blocked by: none

Spec sections 2, 3. Every other provider ticket builds on this.

## Acceptance

- `SystemDescription`, the power-based `ForecastStep(start, end, power_kw)`, the `ForecastProvider` protocol with its metadata and settings description, the `ProviderError` family (`Unavailable`, `RateLimited` with an optional retry time, `BadResponse`, `NotConfigured`), and a registry of built-in providers.
- The planner consumes `power_kw`: `planner/surplus.py` no longer applies kWp, irradiance or losses; the irradiance formula is removed from the planner.
- `planner.cli`'s custom forecast file uses `power_kw`; the demo forecasts produce power for an example system; the README notes the format change.
- The test helper that builds forecasts converts an irradiance profile into power for the example system (4.8 kWp, 14% losses) so the existing expectations keep their meaning; every existing planner, advice, window and view test passes without changed assertions.
- Contract tests of the types (aware datetimes, ordering, non-negative).

## Comments

- Contract in `forecast/provider.py` (`SystemDescription`, `SettingSpec`, `ForecastProvider`, the `ProviderError` family, `check_steps` / `require_valid_steps`), registry in `forecast/registry.py` (empty until P3 and P4 register the built-ins). `ForecastStep(start, end, power_kw)` stays in `planner/model.py` because the planner uses it.
- `RateLimited.retry_after` is a `timedelta` (what a `Retry-After` header gives), not a time.
- The contract is checked by `check_steps` (aware times, positive length, ordered without overlap, finite and non-negative power), not by the dataclass, so the planner's own tests can still build odd steps; the conformance kit (P2) and the service (P6) call it.
- Until P3 moves it into the provider, the irradiance-to-power formula is `forecast.open_meteo.power_kw`, used by the existing Open-Meteo client (`parse`), the demo forecasts and the test helper; the planner has none.
- `--demo` now turns the bell-shaped irradiance into power for the configured panels (it used to scale at planning time), so demo output is unchanged. A forecast file in the old `gti` format is refused with a clear message.
- Existing planner, advice, window and view tests pass with unchanged assertions; only the tests that built a step by hand (two in `test_planner.py`) or read `gti_wm2` (two in `test_open_meteo.py`) changed their numbers.
- Mutation-checked: making the planner scale by the panel size again fails the new test.
