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
        } catch (DateTimeException | IllegalArgumentException | JacksonException ignored) {
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
