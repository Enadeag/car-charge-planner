# Forecast providers

A **forecast provider** tells the app how much power the user's solar system is expected to produce over the next
days. The app hands it a description of the system (one or more panel arrays) and gets back one number per time
step: the expected power of all arrays together, in kW. Everything else (when to ask, what to keep, what to do when it fails, the Settings screen) is done for every
provider by the app.

Two providers are built in, [Open-Meteo](../src/car_charge_planner/forecast/providers/open_meteo.py) and
[Forecast.Solar](../src/car_charge_planner/forecast/providers/forecast_solar.py). Adding one is a pull request: one module, one test
module, a recorded answer. This guide is everything a contributor needs.

## The contract

All of it is in [`src/car_charge_planner/forecast/provider.py`](../src/car_charge_planner/forecast/provider.py).

```python
@dataclass(frozen=True)
class PanelArray:                  # panels with one tilt and one direction
    kwp: float; tilt_deg: float; azimuth_deg: float   # azimuth: 0 = south, -90 = east, 90 = west, 180 = north
    system_losses: float                              # a fraction (0.14 = 14%)

@dataclass(frozen=True)
class SystemDescription:           # what the provider is told
    latitude: float; longitude: float
    arrays: tuple[PanelArray, ...] # at least one; system.kwp is the size of all of them together
    timezone: str                  # the user's time zone, an IANA name

@dataclass(frozen=True)
class ForecastStep:                # what it returns, a list of them (src/car_charge_planner/planner/model.py)
    start: datetime; end: datetime # timezone-aware, [start, end)
    power_kw: float                # expected AC power of this system over the step

class ForecastProvider(Protocol):
    id: str                        # lower case letters, digits, hyphens: used in the config and in cache file names
    name: str                      # shown to the user
    description: str               # N_(...): what it is, in a sentence or two
    terms_url: str                 # https link to the terms or limits
    attribution: str               # the credit its terms ask for, shown as it is
    sends: str                     # N_(...): in plain words, what leaves the computer
    min_refresh: timedelta         # never asked more often than this
    horizon: timedelta             # how far ahead it forecasts
    max_useful_age: timedelta      # a forecast older than this is not worth keeping when it cannot be refreshed
    settings: tuple[SettingSpec, ...]
    search_places: PlaceSearch | None   # an optional ability (place search), None for now
    def fetch(self, system, settings, now) -> list[ForecastStep]: ...   # blocking
```

**Steps** may be 15, 30 or 60 minutes, or irregular (Forecast.Solar's follow sunrise and sunset). They must be
in order, must not overlap, and may have gaps (night). Power is finite and not negative. `check_steps(steps)`
returns what is wrong with a list of steps in words; the app and the conformance kit use it, and
`require_valid_steps(steps)` raises `BadResponse` for you.

**Several panel arrays.** A home can have panels facing different ways (an east and a west roof, a garage);
each is a `PanelArray` in `system.arrays`, and the forecast you return is their total. A service that takes one
plane per request is asked once per array: `each_array(system, fetch_one)` calls `fetch_one(array)` for each and
adds the answers up with `add_up`. When the steps line up (one service, the same place) each step's power is the
sum; when they do not, the steps are cut where any array's step starts or ends, so no energy is lost; an array
without a step at some time adds nothing then, and the total only covers the time every array's answer covers. A
failure for any array must fail the whole `fetch`: a total without one of the arrays would look right and be
wrong. A service that takes several planes in one request may use that instead. Remember that every request
counts towards a rate limit: a household with three arrays spends three requests per refresh.

**Losses.** Each array's `system_losses` is given to you. If your service does not model losses itself, apply it
(Open-Meteo: `kwp x irradiance / 1000 x (1 - losses)`). If it does (Forecast.Solar), do not apply it twice and
say so in the `description`.

**Errors.** `fetch` raises one of these, so the app can react to each differently:

| Raise | When | The app |
| --- | --- | --- |
| `Unavailable` | no network, a timeout, a 5xx | tries again in a few minutes |
| `RateLimited(message, retry_after)` | the service refuses more requests; `retry_after` is a `timedelta` if it says when | leaves the provider alone until then |
| `BadResponse` | an answer that is not what the service documents, a refused key (401, 403), a bad request, steps that break the contract | waits the minimum refresh |
| `NotConfigured` | a setting is missing or unusable (checked before anything is sent) | reports it |

Anything else you raise is treated as a bug: the app carries on without that provider and logs only the kind of
error. Error messages are shown to the user: **never put the URL (it may hold a key) or a setting's value in one.**

## The transport

A provider never opens a connection itself. It takes a `Transport` when it is created and makes every request
through it (`src/car_charge_planner/forecast/transport.py`): a `Request` (URL, headers) goes in, a `Reply` (status, body, headers) comes
out. A refusal such as a 429 or a 503 is a `Reply` with that status; only "could not talk to the server at all"
raises, as an `OSError`. The default transport is the real one; tests give yours a `ReplayTransport`.

## A skeleton

This is a complete provider for an imaginary service, and it passes the conformance kit
(`tests/test_provider_guide.py` runs this very text through it, so it stays true).

```python skeleton
from datetime import datetime, timedelta

from car_charge_planner.i18n import N_
from car_charge_planner.forecast.provider import (
    BadResponse, RateLimited, SettingSpec, Unavailable, each_array, require_valid_steps,
)
from car_charge_planner.forecast.transport import Request, urllib_transport
from car_charge_planner.planner.model import ForecastStep


class Example:
    id = "example"
    name = "Example Solar"
    description = N_("An imaginary forecast service, here to show what a provider looks like.")
    terms_url = "https://example.com/terms"
    attribution = "Solar forecast: Example Solar"
    sends = N_("Your location and the size, tilt and direction of your panels. With an API key, the key too.")
    min_refresh = timedelta(hours=1)
    horizon = timedelta(days=3)
    max_useful_age = timedelta(hours=6)
    settings = (SettingSpec("api_key", N_("API key"), N_("From your Example account."), secret=True),)
    search_places = None

    def __init__(self, transport=urllib_transport):
        self.transport = transport

    def fetch(self, system, settings, now):
        # the service forecasts one plane per request: ask for each panel array and add them up
        return require_valid_steps(each_array(system, lambda array: self._fetch_array(system, array, settings)))

    def _fetch_array(self, system, array, settings):
        key = settings.get("api_key", "")
        url = (
            f"https://api.example.com/v1/solar?lat={system.latitude}&lon={system.longitude}"
            f"&kwp={array.kwp}&tilt={array.tilt_deg}&azimuth={array.azimuth_deg}"
        )
        try:
            reply = self.transport(Request(url, {"Authorization": f"Bearer {key}"} if key else {}))
        except OSError as err:  # the text never carries the URL
            raise Unavailable(f"could not reach Example Solar ({type(err).__name__})") from None
        if reply.status == 429:
            wait = reply.header("Retry-After")
            raise RateLimited("Example Solar says too many requests", timedelta(seconds=int(wait)) if wait and wait.isdigit() else None)
        if reply.status >= 500:
            raise Unavailable(f"Example Solar has a problem (status {reply.status})")
        if not reply.ok:
            raise BadResponse(f"Example Solar did not accept the request (status {reply.status})")
        try:
            rows = reply.json()["steps"]
            steps = [ForecastStep(datetime.fromisoformat(r["start"]), datetime.fromisoformat(r["end"]), float(r["kw"])) for r in rows]
        except (ValueError, KeyError, TypeError):
            raise BadResponse("Example Solar gave an answer that is not what it documents") from None
        return require_valid_steps(steps)
```

and the recorded answer its test replays:

```json recorded
{"steps": [
  {"start": "2026-07-03T08:00:00+00:00", "end": "2026-07-03T09:00:00+00:00", "kw": 1.5},
  {"start": "2026-07-03T09:00:00+00:00", "end": "2026-07-03T10:00:00+00:00", "kw": 2.5}
]}
```

## Adding yours

1. **The module**: `src/car_charge_planner/forecast/providers/<your_id>.py`, as above. Mark `description`, `sends` and every setting's
   label and hint with `N_()` so they are translated; the name and the attribution are shown as they are.
2. **Register it**: add it to `_register_builtin()` in `src/car_charge_planner/forecast/registry.py`.
3. **Check the service's live documentation** before you write a line: the URL shape, the azimuth convention,
   the time zone of the answer (Forecast.Solar answers in the place's local time), what a timestamp means (the
   start or the end of a period: Open-Meteo's value covers the hour *before* its stamp), and what the free
   plan allows. Write what you found in the module's docstring with the date.
4. **Record an answer** from the real service for a neutral place (a city centre, not your home) and keep it
   under `tests/fixtures/`. Scrub anything personal (your address in a rate-limit zone, a request id). Add a line
   to `tests/fixtures/NOTICE.md` that credits the service and its licence, and says which fixtures are made by hand
   (a rate-limit answer you cannot easily provoke, a daylight-saving day).
5. **The test module**: `tests/test_<your_id>_provider.py` subclasses `ProviderConformance` (below), then tests what
   is particular to your service: the request it builds, each error, the time-zone and daylight-saving handling,
   that a key never shows up in an error message.
6. **The documents**: your provider's `sends` and `terms_url` must appear in the README's "Data and privacy"
   section (a test checks it), with its free-tier limits and whether commercial use is allowed.

### Rate limits and terms

Say in the README, from the provider's own pages and with the date you read them: what the free plan allows
(requests per day or hour, per whom), whether commercial use is allowed, whether credit is required. Declare a
`min_refresh` that keeps a household well inside it; remember that limits per internet address are shared with
everything else at home. If the service documents a "retry at" time, return it in `RateLimited`: the app obeys
it, and some services punish ignoring it.

### Secrets

Declare a setting `secret=True`. The app keeps it in `secrets.toml` beside the config (mode 0600), never in
`config.toml`, shows it masked in Settings, and passes it to `fetch` in `settings` with the other settings.
A setting that is empty is absent from the dictionary.

## Testing a provider: the conformance kit

Every provider is run through the same checks, against recorded answers and without touching the network.
The kit is `tests/provider_kit.py`; `tests/test_provider_kit.py` shows a small provider passing it and broken ones
being caught.

```python
class TestMyProvider(ProviderConformance):
    @pytest.fixture
    def case(self):
        return ProviderCase(
            make=lambda transport: MyProvider(transport),
            ok=[("api.example.com/forecast", Reply(200, RECORDED_ANSWER))],   # a real, recorded answer
            server_error=[("", Reply(503))],
            bad_body=[("", Reply(200, b"<html>"))],
            empty_body=[("", Reply(200, b"{}"))],
            rate_limited=[("", Reply(429, b"", {"Retry-After": "120"}))],     # when the provider has one
            retry_after=timedelta(seconds=120),
            secrets={"api_key": "SECRET-KEY-123"},                            # recognisable values, to find leaks
            now=datetime(2026, 7, 3, 7, 0, tzinfo=timezone.utc),              # when the answer was recorded
        )
```

What it checks:

| Check | What a provider must do |
| --- | --- |
| Metadata | A lower-case id, a name, description, attribution and a plain-words "what is sent", an `https` terms link, positive refresh interval and horizon, a useful age that is at least the refresh interval, unique setting keys with labels and hints. |
| A good answer | At least one step; steps carry a time zone, last a positive time, follow each other without overlap, have finite non-negative power, and stay within the declared horizon; it went through the transport and asked for nothing unrecorded. |
| Several panel arrays | A system with a second array gives one valid forecast. If the provider asks once per array, it is the sum of the single-array forecasts, and a failure for the second array fails the whole forecast. |
| Same answer, same steps | Deterministic; it leaves its inputs alone. |
| Rate limit | A refusal for too many requests raises `RateLimited` with the retry time the server gave (or `None`). |
| Server error, no network | A 5xx and a failed connection (`OSError`, timeout, reset) raise `Unavailable`. |
| Not as documented | A 200 with the wrong content raises `BadResponse`; an empty answer raises a `ProviderError` or gives valid steps; never a stray `KeyError` or `JSONDecodeError`. |
| Required settings | A missing required setting raises `NotConfigured` before anything is sent. |
| Secrets | A secret setting never appears in a log line or an error message. |

For tests of the code that *uses* providers (the forecast service, the Settings tab) there is a scripted
`FakeProvider` in `tests/fake_provider.py`: successes, errors, rate limits with retry times, and `Held` entries
that wait for an event so a test can have a fetch in flight. Given `per_array` (a function from a `PanelArray` to
its steps) instead of a script, it forecasts each array and adds them up the way a real provider does.

## Place search (optional)

A provider may also offer to turn what a person typed ("Utrecht", a postcode) into places with coordinates and a
time zone; the set-up screen's Location page uses the **primary** provider's. Set `search_places` to a callable
`search(text, language="en") -> PlaceResults` (`src/car_charge_planner/forecast/provider.py`), or leave it `None`: the page then asks for
coordinates only and says why. Open-Meteo has one (`src/car_charge_planner/forecast/providers/open_meteo_geocoding.py`); Forecast.Solar
does not.

- `PlaceResults` holds `places` (each a `Place`: name, latitude, longitude, an IANA `timezone`, and the optional
  `region`, `country` and finer `area` for telling two places that read alike apart) or a `problem`:
  `TOO_SHORT` (fewer than two characters: nothing may be sent), `UNAVAILABLE` (no network, a timeout, a 5xx),
  `RATE_LIMITED` (with `retry_after` when the service says) or `BAD_RESPONSE`. Matching nothing is not a problem:
  it is `ok` with no places.
- It **never raises**, and uses a short timeout (a person is waiting at a search box): set `Request.timeout_s`.
- Leave out results that lack coordinates or a time zone you know.
- The text typed is sent to the provider: say so in `sends` (or in its terms) and in the README.
- Test it with `PlaceSearchConformance` and a `PlaceCase` (`tests/provider_kit.py`), like the forecast itself.

## Credit and attribution

`attribution` is the credit your provider's terms ask for ("Weather data: Open-Meteo.com (CC BY 4.0)"). The app
shows it as it is, next to the age of the forecast, whenever that provider made the forecast on screen; it is not
translated. If the forecast comes from the fallback, the footer also says so, and the tooltip explains. `sends`
and `description` are translated (mark them with `N_()`).

## Wanted: Solcast

[Solcast](https://solcast.com) is a good second source for some regions. Its hobbyist plan has constraints a
provider has to respect, which is why it is not built in:

- an account and a **registered rooftop site** are needed first (the site, not the request, carries the panel
  description), and an API key;
- **10 calls a day**, so `min_refresh` of a few hours and a long `max_useful_age`;
- **personal use only**: the README must say so;
- 30-minute steps, in UTC.

Check all of this against Solcast's live documentation before building; plans and limits change.

## Not yet: plug-ins

Providers are built in and added by pull request. Loading a provider from another installed package (Python entry
points) is deliberately not offered yet; if you need it, open an issue and describe the use.
