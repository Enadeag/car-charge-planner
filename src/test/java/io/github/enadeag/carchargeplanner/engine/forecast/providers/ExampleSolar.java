package io.github.enadeag.carchargeplanner.engine.forecast.providers;

import io.github.enadeag.carchargeplanner.engine.config.PanelArray;
import io.github.enadeag.carchargeplanner.engine.config.ProviderId;
import io.github.enadeag.carchargeplanner.engine.forecast.ForecastProvider;
import io.github.enadeag.carchargeplanner.engine.forecast.ForecastStep;
import io.github.enadeag.carchargeplanner.engine.forecast.HttpGet;
import io.github.enadeag.carchargeplanner.engine.forecast.ProviderError;
import io.github.enadeag.carchargeplanner.engine.forecast.ProviderInfo;
import io.github.enadeag.carchargeplanner.engine.forecast.ProviderText;
import io.github.enadeag.carchargeplanner.engine.forecast.SettingSpec;
import io.github.enadeag.carchargeplanner.engine.forecast.Steps;
import io.github.enadeag.carchargeplanner.engine.forecast.SystemDescription;
import java.net.URI;
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
            List.of(new SettingSpec("api_key", "API key", "From your Example Solar account.", false, true)));

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
    public List<ForecastStep> fetch(SystemDescription system, Map<String, String> settings, Instant now)
            throws ProviderError {
        if (!INFO.missingRequired(settings).isEmpty()) {
            throw new ProviderError.NotConfigured("Example Solar needs an API key");
        }
        return Steps.requireValid(Steps.eachArray(system, array -> fetchArray(system, array, settings)));
    }

    private List<ForecastStep> fetchArray(SystemDescription system, PanelArray array, Map<String, String> settings)
            throws ProviderError {
        var uri = URI.create("https://api.example.com/v1/solar?lat=" + Decimals.plain(system.location().latitude())
                + "&lon=" + Decimals.plain(system.location().longitude()) + "&kwp=" + Decimals.plain(array.kwp())
                + "&tilt=" + Decimals.plain(array.tiltDeg()) + "&azimuth=" + Decimals.plain(array.azimuthDeg()));
        var request = new HttpGet.Request(uri, Map.of("Authorization", "Bearer " + settings.get("api_key")),
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
        } catch (DateTimeException | IllegalArgumentException | JacksonException e) {
            throw new ProviderError.BadResponse("Example Solar gave an answer that is not what it documents");
        }
        if (steps.isEmpty()) {
            throw new ProviderError.BadResponse("Example Solar gave no forecast");
        }
        return Steps.requireValid(steps);
    }
}
