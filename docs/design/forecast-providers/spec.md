# Forecast providers: Spec

Status: agreed in a grilling session, 2026-10-06. Part of the open-source plan (the open-source plan, prerequisite 2). Vocabulary: `CONTEXT.md` (**forecast provider**, **fallback provider**).

## 1. Goal

The app no longer depends on one forecast service. The forecast comes from a replaceable **forecast provider** behind a small documented interface, with two providers at launch, an optional second one as a fallback, and a clear path for contributors to add more.

## 2. Decisions

| Topic | Decision |
| --- | --- |
| Name | **Forecast provider** in the UI and docs (not "weather source": some providers forecast solar production, not weather). |
| Contract | A provider receives a description of the system and returns **expected solar power (kW) per time step**. The planner's `gti_wm2` becomes `power_kw`. |
| At launch | **Open-Meteo** (irradiance, converted to power inside the provider) and **Forecast.Solar** (its own power estimate). Solcast stays a documented contribution. |
| Discovery | **Built in, added by pull request**, in a registry. A documented interface, a fake provider and a shared **conformance kit**. Entry-point plug-ins can be added later without breaking this. |
| Secrets | API keys and similar live in a separate **`secrets.toml`** (mode 0600) beside the config, never in `config.toml`. Settings shows them masked. |
| Selection | A **Forecast** tab in Settings: primary provider, optional **fallback provider**, each with its own fields, description, what it sends, limits, a terms link, and a **Test** button. The set-up wizard does not ask: it starts with Open-Meteo and no fallback and names the provider on its Welcome and Finish pages. |
| Failure | **Primary first at every refresh.** On failure or rate limit, the primary's last good forecast keeps being used while it is within the provider's declared useful age. Only then is the fallback asked. The primary takes over again as soon as it works. |
| Caching | Per provider and per system description. A rate-limited answer starts a cool-down. A shorter horizon shows fewer days without errors. |
| Visibility | The footer names the source, with that provider's attribution; a fallback forecast reads "from <Fallback>, primary unavailable". |
| Place search | An **optional ability** of a provider (Open-Meteo has one). Without it the wizard falls back to coordinates. |

## 3. The interface

```python
@dataclass(frozen=True)
class SystemDescription:
    latitude: float; longitude: float
    kwp: float; tilt_deg: float; azimuth_deg: float   # 0 = south, -90 = east, 90 = west
    system_losses: float                              # a fraction
    timezone: str

@dataclass(frozen=True)
class ForecastStep:           # replaces the irradiance step
    start: datetime; end: datetime   # timezone-aware, [start, end)
    power_kw: float                  # expected AC power of this system

class ForecastProvider(Protocol):
    id: str; name: str; description: str
    terms_url: str; attribution: str; sends: str      # shown in Settings and the README
    min_refresh: timedelta        # never ask more often than this
    horizon: timedelta            # how far ahead it forecasts
    max_useful_age: timedelta     # a forecast older than this is not worth keeping when it cannot be refreshed
    settings: tuple[SettingSpec, ...]    # key, label, hint, optional, secret
    def fetch(self, system, settings, now) -> list[ForecastStep]: ...   # blocking; may raise a ProviderError
    search_places: PlaceSearch | None    # optional ability

class ProviderError(Exception): ...
class Unavailable(ProviderError): ...       # no network, server error
class RateLimited(ProviderError):           # retry_after may be None
class BadResponse(ProviderError): ...       # not what the provider documents
class NotConfigured(ProviderError): ...     # a required setting is missing
```

- **Losses.** The system losses setting is given to the provider. The Open-Meteo provider applies it (its formula is `kwp x irradiance / 1000 x (1 - losses)`). Forecast.Solar models losses itself, so the setting is not applied again; the provider's description says so.
- **Steps** may be 15, 30 or 60 minutes. The planner already works on step lengths.
- **Time zones.** Providers that answer in local time (Forecast.Solar) are converted to aware datetimes, with DST gaps and repeats handled.

## 4. The providers

**Open-Meteo.** Today's client, moved. Requests tilted irradiance for the system; converts to power; horizon 4 days; refresh at most hourly; attribution "Weather data: Open-Meteo.com" (CC BY 4.0); free tier non-commercial. Optional settings: **server address** (a self-hosted or commercial endpoint) and **API key** (secret). Has place search (its geocoding service). The unused cloud-cover request is dropped.

**Forecast.Solar.** The public plan needs no key: hourly estimates for this and the next day, one plane per call, 12 calls per hour per address. Returns power estimates by local timestamp. Rate-limit answers become `RateLimited` with the retry time when given. Optional **API key** (secret) for paid plans. No place search. To be checked against its live documentation at implementation time (path format, azimuth convention, time zone field).

**Solcast** (not shipped): hobbyist plan with an account and a registered rooftop site, an API key, 10 calls a day, 30-minute steps, personal use only. Listed as a contribution in the provider guide.

## 5. Orchestration

A `ForecastService` replaces the direct fetch in the controller:

1. Ask the primary if its minimum refresh interval has passed and it is not cooling down.
2. On success: store in the primary's cache and use it.
3. On failure or rate limit: keep using the primary's cached forecast while it is younger than `max_useful_age`; start a cool-down on `RateLimited`.
4. If that cache is too old or missing and a fallback is configured: ask the fallback (same rules for it). Use its result and record that it came from the fallback.
5. If nothing is usable: report unavailable (the plan shows "No forecast available", as now).
6. The primary is tried first again at the next refresh, so a recovered primary takes over at once.

The result carries: the steps, when it was fetched, its age, `provider_id`, `from_fallback`, `stale`, and any cool-down. The late-result guard for configuration changes is kept. Changing the provider or the system description drops the shown forecast until a new one arrives.

## 6. Configuration

```toml
[forecast]
primary = "open-meteo"
fallback = ""                       # empty means none

[forecast.open-meteo]
base_url = ""                       # empty means the public endpoint
```
`secrets.toml` (0600), written by Settings: `[open-meteo] api_key = "..."`, `[forecast-solar] api_key = "..."`. A config without `[forecast]` means Open-Meteo, no fallback. The old single cache file is ignored; caches are `forecast-<provider>.json`, keyed by the system description and the provider's non-secret settings.

## 7. User interface

- **Settings, Forecast tab:** primary dropdown, then its fields; fallback dropdown ("None") and its fields; for each provider the description, what is sent, its limits and a terms link; a **Test** button that fetches once on a worker thread and reports "OK: 4 days of forecast" or the error; validation that primary and fallback differ and that required settings are present.
- **Footer** (popup and window): "Forecast 12 min old · Open-Meteo.com", or "from Forecast.Solar, primary unavailable", or "provider busy, retrying later" during a cool-down. The footer's tooltip carries the full attribution.
- **Plan page:** shows however many days the provider returns.
- **Wizard:** Welcome names the provider and what it receives; Finish does its connection check with the primary and names it.
- Everything is translatable (see the set-up and i18n spec).

## 8. Testing

- A **conformance kit** every provider must pass, run against recorded responses: returns aware, ordered, non-overlapping steps; power is non-negative and finite; maps each documented error to the right `ProviderError`; respects `min_refresh` through the service; never reaches the network in tests (a replay transport); declares complete metadata and settings; handles an empty or malformed response without crashing.
- A **fake provider** (scripted successes, failures, rate limits) for the orchestration tests.
- Orchestration matrix: primary ok; primary fails with fresh cache; with stale cache and a fallback; fallback also fails; rate limit and cool-down; recovery; same provider twice rejected; provider changes while a fetch is in flight.
- Open-Meteo and Forecast.Solar parsing against recorded fixtures (with attribution notices in the fixture folder); time-zone and DST cases for Forecast.Solar.
- Secrets: written 0600, never in `config.toml`, never logged, masked in Settings.
- The planner works unchanged in meaning after the step type changes (the test helper turns an irradiance profile into power for the example system).

## 9. Order of work

```
P1 contract + planner uses power -> P2 conformance kit + fake provider
P3 Open-Meteo as a provider      P4 Forecast.Solar        (both need P1, P2)
P5 provider settings + secrets
P6 ForecastService (primary, fallback, cache, cool-down)  (needs P1, P3, P5)
P7 Forecast tab in Settings      P8 footer, attribution, horizon in the UI   (need P6, i18n I1)
P9 place search as a provider ability                    (needs S4)
P10 provider guide, README data and privacy              P11 review
```

## 10. Risks and open items

- The Forecast.Solar and Open-Meteo API details must be re-verified against their live documentation when built; the free tiers and terms can change.
- Two different models (irradiance conversion versus a provider's own estimate) will give different plans for the same system; the footer names the source so this is visible.
- The Forecast.Solar public plan's two-day horizon means a shorter outlook and, with a low plan, later notice of a coming sunny day.
- The custom forecast file for `planner.cli --forecast` changes from `gti` to `power_kw`; documented.
- A third-party plug-in mechanism is deliberately not provided yet.
