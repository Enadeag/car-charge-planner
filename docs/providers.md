# Forecast providers

A **forecast provider** tells the app how much power the user's solar system is expected to produce over the next days. The app hands it a description of the system (one or more panel arrays) and gets back one number per time step: the expected power of all arrays together, in kW. Everything else (when to ask, what to keep, what to do when it fails, the Settings page) is done for every provider by the app's forecast service.

Two providers are built in, [Open-Meteo](../engine/src/main/java/nl/enadeag/carchargeplanner/engine/forecast/providers/OpenMeteo.java) and [Forecast.Solar](../engine/src/main/java/nl/enadeag/carchargeplanner/engine/forecast/providers/ForecastSolar.java). Adding one is a pull request: one class, one test class, a recorded answer. This guide is everything a contributor needs.

## The contract

All of it is in the package `engine.forecast` ([`engine/src/main/java/nl/enadeag/carchargeplanner/engine/forecast/`](../engine/src/main/java/nl/enadeag/carchargeplanner/engine/forecast/)), except `PanelArray` and `ProviderId`, which are in `engine.config`:

| Type | What it is |
| --- | --- |
| `ForecastProvider` | The interface: `info()`, `fetch(system, settings, now)`, and optionally `placeSearch()`. |
| `ProviderInfo` | What a provider tells about itself: a `ProviderId` (lower-case letters, digits and hyphens, used in the config and in cache file names), the name, a description, an `https` terms link, the credit its terms ask for, in plain words what is sent, the shortest time between two requests (`minRefresh`), how far ahead it forecasts (`horizon`), how long a forecast stays worth keeping when it cannot be refreshed (`maxUsefulAge`), and its settings. |
| `SettingSpec` | One setting, such as a server address or an API key: a name, a label, a hint, whether it is optional, whether it is secret, and its `SettingFormat`: any text, a `SERVER_ADDRESS` a key may be sent to (`ServerAddress`: https, or plain http only to localhost, 127.0.0.1 or [::1]; no user name, query or fragment), or `LETTERS_AND_DIGITS`. Settings and set-up refuse a value of the wrong format when it is saved; check it again before you send, as a config file can be edited by hand. Any value a provider puts into an address's path or query or into a header must carry a format (`TEXT` refuses line breaks and other control characters; take a narrower one where the service allows) and is checked again before sending, as `ExampleSolar.apiKey` below does; a value in a query is encoded besides. |
| `SolarSite` | What a provider is told: the coordinates, the time zone, and the panel arrays (`PanelArray`: size in kWp, tilt, azimuth with 0 south, -90 east and 90 west, and the system losses as a share, 0.14 for 14%). |
| `ForecastStep` | What it returns, a list of them: `start` (inclusive), `end` (exclusive), both `Instant`s, and `powerKw`, the expected AC power of the whole system over the step. |
| `HttpGet` | The only way a provider reaches the network (below). |

**Steps** may be 15, 30 or 60 minutes, or irregular (Forecast.Solar's follow sunrise and sunset). They must be in order, must not overlap, and may have gaps (night). Power is finite and not negative. `StepRules.problems(steps)` says what is wrong with a list of steps in words; `StepRules.requireValid(steps)` throws `BadResponse` for you.

**Several panel arrays.** A home can have panels facing different ways (an east and a west roof, a garage); each is a `PanelArray` in `system.arrays()`, and the forecast you return is their total. A service that takes one plane per request is asked once per array: `ArraySum.sumOverArrays(system, fetchOne)` calls `fetchOne` for each and adds the answers up with `ArraySum.addUp`. When the steps line up (one service, the same place) each step's power is the sum; when they do not, the steps are cut where any array's step starts or ends, so no energy is lost; an array without a step at some time adds nothing then, and the total only covers the time every array's answer covers. A failure for any array must fail the whole `fetch`: a total without one of the arrays would look right and be wrong. A service that takes several planes in one request may use that instead. Remember that every request counts towards a rate limit: a household with three arrays spends three requests per refresh.

**Losses.** Each array's `systemLosses` is given to you. If your service does not model losses itself, apply it (Open-Meteo: `kwp x irradiance / 1000 x (1 - losses)`). If it does (Forecast.Solar), do not apply it twice and say so in the description.

**Errors.** `fetch` throws one of the kinds of `ProviderError`, so the forecast service can react to each:

| Throw | When | The app |
| --- | --- | --- |
| `Unavailable` | no network, a timeout, a 5xx | tries again in five minutes |
| `RateLimited(message, retryAfter)` | the service refuses more requests; `retryAfter` is a `Duration` if it says when, else `null` | leaves the provider alone until then, or for its `minRefresh` |
| `BadResponse` | an answer that is not what the service documents, a refused key (401, 403), a bad request, steps that break the contract | waits the `minRefresh` |
| `MissingSetting` | a setting is missing or unusable (checked before anything is sent) | reports it |

A `RuntimeException` is treated as a bug: the app carries on without that provider and logs only the kind of error. Error messages are shown to the user: **never put the URL (it may hold a key) or a setting's value in one.** Text the server wrote, such as the reason in an error answer, goes in only through `ProviderText.quoted(INFO.name(), text, INFO.secretValues(settings))`: it reads "Example Solar says: ...", without the keys, without control or direction characters, and at most 200 characters. The forecast service and the connection check also replace every stored key with `***` in whatever a provider reports, before it is shown or logged.

## The network

A provider never opens a connection itself. It takes an `HttpGet` when it is made and sends every request through it: a `Request` (address, headers, timeout, largest body) goes in, a `Reply` (status, body, headers) comes out. A refusal such as a 429 or a 503 is a `Reply` with that status; only "could not talk to the server at all", an answer that did not come whole within the timeout and one larger than the request allows (`HttpGet.TooLarge`) throw, as an `IOException`. `Request.of(uri)` and the constants `HttpGet.DEFAULT_TIMEOUT` (15 s) and `HttpGet.DEFAULT_MAX_BODY` (2 MB) suit a forecast. `Exchange.send` in the providers' package turns a failure into `Unavailable` without the address in the message, `Exchange.json` reads at most 2 MB and 200 000 tokens of JSON, and `Reply.retryAfter()` reads a `Retry-After` header. The app passes the real one (`JdkHttpGet`); tests pass a `ReplayHttp` with recorded answers.

## A skeleton

This is a complete provider for an imaginary service, and it passes the conformance kit. It is also real code: the three blocks below are `ExampleSolar.java`, `ExampleSolarTest.java` and `example_solar.json` in the engine's test tree (`engine/src/test/.../engine/forecast/providers/`), where the build runs the kit on it, and `ProvidersGuideTest` fails when a block here and its file differ.

```java
package nl.enadeag.carchargeplanner.engine.forecast.providers;

import nl.enadeag.carchargeplanner.engine.config.PanelArray;
import nl.enadeag.carchargeplanner.engine.config.ProviderId;
import nl.enadeag.carchargeplanner.engine.forecast.ArraySum;
import nl.enadeag.carchargeplanner.engine.forecast.ForecastProvider;
import nl.enadeag.carchargeplanner.engine.forecast.ForecastStep;
import nl.enadeag.carchargeplanner.engine.forecast.HttpGet;
import nl.enadeag.carchargeplanner.engine.forecast.ProviderError;
import nl.enadeag.carchargeplanner.engine.forecast.ProviderInfo;
import nl.enadeag.carchargeplanner.engine.forecast.ProviderText;
import nl.enadeag.carchargeplanner.engine.forecast.SettingFormat;
import nl.enadeag.carchargeplanner.engine.forecast.SettingSpec;
import nl.enadeag.carchargeplanner.engine.forecast.SolarSite;
import nl.enadeag.carchargeplanner.engine.forecast.StepRules;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import tools.jackson.core.JacksonException;

/** Example Solar (https://example.com), an imaginary service that forecasts one panel array per request. */
public final class ExampleSolar implements ForecastProvider {

    /** What Example Solar tells about itself. */
    static final ProviderInfo INFO = new ProviderInfo(
            new ProviderId("example-solar"),
            "Example Solar",
            "An imaginary forecast service, here to show what a provider looks like.",
            "https://example.com/terms",
            "Solar forecast: Example Solar",
            "Your location and the size, tilt and direction of your panels, and your API key.",
            Duration.ofHours(1),
            Duration.ofDays(3),
            Duration.ofHours(6),
            List.of(new SettingSpec("api_key", "API key", "From your Example Solar account.", false, true,
                    SettingFormat.LETTERS_AND_DIGITS)));

    private final HttpGet http;

    /**
     * Makes the provider.
     *
     * @param http the only way it reaches the network; tests pass recorded answers
     */
    public ExampleSolar(HttpGet http) {
        this.http = http;
    }

    @Override
    public ProviderInfo info() {
        return INFO;
    }

    @Override
    public List<ForecastStep> fetch(SolarSite system, Map<String, String> settings, Instant now)
            throws ProviderError {
        var key = apiKey(settings); // checked before anything is sent
        return StepRules.requireValid(ArraySum.sumOverArrays(system, array -> fetchArray(system, array, key, settings)));
    }

    /**
     * The key, checked again although Settings checked it when it was saved: the settings file and the keychain can be
     * changed by hand, and the key goes into a header.
     */
    private static String apiKey(Map<String, String> settings) throws ProviderError.MissingSetting {
        var key = settings.getOrDefault("api_key", "").strip();
        if (key.isEmpty()) {
            throw new ProviderError.MissingSetting("Example Solar needs an API key");
        }
        if (!SettingFormat.LETTERS_AND_DIGITS.accepts(key)) {
            throw new ProviderError.MissingSetting("the API key may hold only letters and digits");
        }
        return key;
    }

    private List<ForecastStep> fetchArray(SolarSite system, PanelArray array, String key,
            Map<String, String> settings) throws ProviderError {
        var uri = URI.create("https://api.example.com/v1/solar?" + String.join("&",
                parameter("lat", Decimals.plain(system.location().latitude())),
                parameter("lon", Decimals.plain(system.location().longitude())),
                parameter("kwp", Decimals.plain(array.kwp())),
                parameter("tilt", Decimals.plain(array.tiltDeg())),
                parameter("azimuth", Decimals.plain(array.azimuthDeg()))));
        var request = new HttpGet.Request(uri, Map.of("Authorization", "Bearer " + key),
                HttpGet.DEFAULT_TIMEOUT, HttpGet.DEFAULT_MAX_BODY);
        // Exchange turns a failed connection into Unavailable, without the address in the message
        var reply = Exchange.send(http, request, INFO.name());
        if (reply.status() == 429) {
            throw new ProviderError.RateLimited("Example Solar says too many requests", reply.retryAfter().orElse(null));
        }
        if (reply.status() >= 500) {
            throw new ProviderError.Unavailable("Example Solar has a problem (status " + reply.status() + ")");
        }
        if (!reply.ok()) {
            // the server's own words, such as {"error": "unknown key"}, only through ProviderText
            var words = Exchange.json(reply.body()).path("error");
            throw new ProviderError.BadResponse("Example Solar did not accept the request (status " + reply.status() + ")"
                    + (words.isString() ? "; " + ProviderText.quoted(INFO.name(), words.stringValue(),
                            INFO.secretValues(settings)) : ""));
        }
        var steps = new ArrayList<ForecastStep>();
        try {
            for (var row : Exchange.json(reply.body()).path("steps")) {
                steps.add(new ForecastStep(Instant.parse(row.path("start").asString()),
                        Instant.parse(row.path("end").asString()), row.path("kw").asDouble()));
            }
        } catch (DateTimeException | IllegalArgumentException | JacksonException _) {
            throw new ProviderError.BadResponse("Example Solar gave an answer that is not what it documents");
        }
        if (steps.isEmpty()) {
            throw new ProviderError.BadResponse("Example Solar gave no forecast");
        }
        return StepRules.requireValid(steps);
    }

    /** One query parameter, its value encoded, so no value can end the parameter or change the address. */
    private static String parameter(String name, String value) {
        return name + "=" + URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
```

its test, which runs the whole conformance kit:

```java
package nl.enadeag.carchargeplanner.engine.forecast.providers;

import static nl.enadeag.carchargeplanner.engine.forecast.ReplayHttp.Route.answer;

import nl.enadeag.carchargeplanner.engine.forecast.HttpGet.Reply;
import nl.enadeag.carchargeplanner.engine.forecast.ProviderCase;
import nl.enadeag.carchargeplanner.engine.forecast.ProviderConformance;
import nl.enadeag.carchargeplanner.engine.forecast.Recorded;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import tools.jackson.databind.json.JsonMapper;

class ExampleSolarTest extends ProviderConformance {

    private static final String RECORDED = Recorded.answer("example_solar.json");

    @Override
    protected ProviderCase providerCase() {
        return new ProviderCase(ExampleSolar::new,
                List.of(answer("/v1/solar", new Reply(200, RECORDED))),
                List.of(answer("", new Reply(503, "Service Unavailable"))),
                List.of(answer("", new Reply(200, "<html>maintenance</html>"))),
                List.of(answer("", new Reply(200, "{}"))),
                List.of(answer("", new Reply(429, "", Map.of("Retry-After", "120")))),
                Duration.ofSeconds(120),
                Map.of(),
                Map.of("api_key", "ExampleSecretKey123"),
                Instant.parse("2026-07-03T07:00:00Z"),
                text -> List.of(answer("", new Reply(400,
                        new JsonMapper().createObjectNode().put("error", text).toString()))));
    }
}
```

and the recorded answer the test replays, `example_solar.json` beside the other recorded answers:

```json
{"steps": [
  {"start": "2026-07-03T08:00:00Z", "end": "2026-07-03T09:00:00Z", "kw": 1.5},
  {"start": "2026-07-03T09:00:00Z", "end": "2026-07-03T10:00:00Z", "kw": 2.5}
]}
```

## Adding yours

1. **The class**: `engine/src/main/java/nl/enadeag/carchargeplanner/engine/forecast/providers/<YourService>.java`, as above.
2. **Register it**: add it to the list of providers in `DesktopApp.engine`, in the package `start` (`new Providers(List.of(new OpenMeteo(get), new ForecastSolar(get), ...))`).
3. **Translate its texts**: the name and the attribution are shown as they are, but the description, what is sent, and each setting's label and hint are translated. Add keys to `TextKey.java` and every `messages_*.properties`, and map them in `desktop/settings/form/ProviderTexts.java`; a provider without texts there is shown in the engine's English.
4. **Check the service's live documentation** before you write a line: the URL shape, the azimuth convention, the time zone of the answer (Forecast.Solar answers in the place's local time), what a timestamp means (the start or the end of a period: Open-Meteo's value covers the hour *before* its stamp), and what the free plan allows. Write what you found in the class's Javadoc with the date.
5. **Record an answer** from the real service for a neutral place (a city centre, not your home) and keep it under `engine/src/test/resources/nl/enadeag/carchargeplanner/engine/forecast/providers/`. Scrub anything personal (your address in a rate-limit zone, a request id). Add a line to `NOTICE.md` there that credits the service and its licence, and says which answers are made by hand (a rate-limit answer you cannot easily provoke, a daylight saving day).
6. **The test class**: `<YourService>Test` extends `ProviderConformance` (below), then tests what is particular to your service: the request it builds, each error, the time-zone and daylight saving handling, that a key never shows up in an error message. When its free plan needs no key, add it to `LiveProvidersTest` as well, which asks the real services before each release (CONTRIBUTING.md, What is tested where).
7. **The documents**: add your provider's "what is sent", terms link, free-plan limits and whether commercial use is allowed to [What is sent, and to whom](privacy.md#what-is-sent-and-to-whom) in `docs/privacy.md`.

### Rate limits and terms

Say in `docs/privacy.md`, from the provider's own pages and with the date you read them: what the free plan allows (requests per day or hour, per whom), whether commercial use is allowed, whether credit is required. Declare a `minRefresh` that keeps a household well inside it; remember that limits per internet address are shared with everything else at home. If the service documents a "retry at" time, return it in `RateLimited`: the app obeys it, and some services punish ignoring it.

### Secrets

Declare a setting secret (the argument of `SettingSpec` just before its `format`). The app keeps it in the system keychain (or, where there is none, in `secrets.json`, readable only by the user), never in `engine.json`, shows it masked in Settings, and passes it to `fetch` in `settings` with the other settings. A setting that is empty is absent from the map.

## Testing a provider: the conformance kit

Every provider is run through the same checks, against recorded answers and without touching the network. The kit is [`ProviderConformance`](../engine/src/test/java/nl/enadeag/carchargeplanner/engine/forecast/ProviderConformance.java); a provider's test class extends it and describes the provider in a `ProviderCase`: how to make it from an `HttpGet`, the recorded routes for a good answer, a server error, a body that is not as documented, an empty body and a rate limit (`ReplayHttp.Route.answer(part, reply)` matches a request whose address contains `part`), the retry time the rate-limit answer gives, working plain settings, secret settings with recognisable values (to find leaks), the moment the answer was recorded, and an error answer in which the server says a given text.

What it checks:

| Check | What a provider must do |
| --- | --- |
| Metadata | A name, description, attribution and a plain-words "what is sent", an `https` terms link, positive refresh interval and horizon, a useful age that is at least the refresh interval, unique setting keys with labels and hints. |
| A good answer | At least one step; the steps keep the contract and stay within the declared horizon; it went through the `HttpGet` it was given and asked for nothing unrecorded. |
| Several panel arrays | A system with a second array gives one valid forecast. If the provider asks once per array, it is the sum of the single-array forecasts, and a failure for the second array fails the whole forecast. |
| Same answer, same steps | Deterministic; it leaves its settings alone. |
| Rate limit | A refusal for too many requests throws `RateLimited` with the retry time the server gave. |
| Server error, no network | A 5xx and a failed connection (refused, timeout, reset, an answer too large) throw `Unavailable`. |
| Limits | Every request reads at most `HttpGet.DEFAULT_MAX_BODY` and waits at most `HttpGet.DEFAULT_TIMEOUT`. |
| Not as documented | A 200 with the wrong content throws `BadResponse`; an empty answer throws a `ProviderError` or gives valid steps, and never anything else. |
| Required settings | A missing required setting throws `MissingSetting` before anything is sent. |
| Setting formats | Every setting, holding a value with a line break that its format refuses: `MissingSetting` before anything is sent, or the value never reaches a request's address or headers unencoded. |
| Secrets | A secret setting never appears in a log line or an error message, not even when the server echoes it; nor does another provider's stored key. |
| The server's words | An error text from the server is shown as "<provider> says: ...", without the key (also where it is cut short), control or direction characters, and at most 200 characters. |

For tests of the code that *uses* providers (the forecast service, the Settings page) there is a scripted [`FakeProvider`](../engine/src/test/java/nl/enadeag/carchargeplanner/engine/forecast/FakeProvider.java): steps, errors and crashes in the order a test gives them, with the calls it got.

## Place search (optional)

A provider may also offer to turn what a person typed ("Utrecht", a postcode) into places with coordinates and a time zone; the Location page of set-up and Settings uses the **primary** provider's. Return a `PlaceSearch` from `placeSearch()`, or leave the default, which returns none: the page then asks for coordinates only and says why. Open-Meteo has one ([`OpenMeteoPlaces`](../engine/src/main/java/nl/enadeag/carchargeplanner/engine/forecast/providers/OpenMeteoPlaces.java)); Forecast.Solar does not.

- `search(text, language)` returns `PlaceResults`: `Found` with the places (each a `Place`: name, coordinates, time zone, and the optional region, country and finer area for telling two places that read alike apart), or `Failed` with a problem: `TOO_SHORT` (fewer than two characters: nothing may be sent), `UNAVAILABLE` (no network, a timeout, a 5xx), `RATE_LIMITED` (with the retry time when the service says) or `BAD_RESPONSE`. Matching nothing is not a problem: it is `Found` with no places.
- It **never throws**, and uses a short timeout (a person is waiting at a search box) and a small largest body (Open-Meteo's is 256 kB): set them in the `Request`.
- Leave out results that lack coordinates or a time zone you know.
- The text typed is sent to the provider: say so in what it sends (or in its terms) and in `docs/privacy.md`.

## Credit and attribution

The attribution is the credit your provider's terms ask for ("Weather data: Open-Meteo.com (CC BY 4.0)"). The app shows it as it is, on the dashboard next to the age of the forecast, whenever that provider made the forecast on screen; it is not translated. If the forecast comes from the fallback, the footer says so.

## Wanted: Solcast

[Solcast](https://solcast.com) is a good second source for some regions. Its hobbyist plan has constraints a provider has to respect, which is why it is not built in:

- an account and a **registered rooftop site** are needed first (the site, not the request, carries the panel description), and an API key;
- **10 calls a day**, so a `minRefresh` of a few hours and a long `maxUsefulAge`;
- **personal use only**: `docs/privacy.md` must say so;
- 30-minute steps, in UTC.

Check all of this against Solcast's live documentation before building; plans and limits change.

## Not yet: plug-ins

Providers are built in and added by pull request. Loading a provider from another jar is deliberately not offered yet; if you need it, open an issue and describe the use.
