package io.github.enadeag.carchargeplanner.engine.forecast;

import io.github.enadeag.carchargeplanner.engine.config.Coordinates;
import io.github.enadeag.carchargeplanner.engine.config.PanelArray;
import io.github.enadeag.carchargeplanner.engine.forecast.ReplayHttp.Route;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Describes a provider for the {@link ProviderConformance} kit: how to make it and the recorded answers to play.
 *
 * @param rateLimited null when the provider has no such answer
 * @param retryAfter what the rate-limited answer says, or null when it says nothing
 * @param settings a working set of plain settings, required ones filled in
 * @param secrets secret settings with recognisable values
 * @param now when the recorded answer was fetched
 */
public record ProviderCase(
        Function<HttpGet, ForecastProvider> make,
        List<Route> ok,
        List<Route> serverError,
        List<Route> badBody,
        List<Route> emptyBody,
        List<Route> rateLimited,
        Duration retryAfter,
        Map<String, String> settings,
        Map<String, String> secrets,
        Instant now) {

    public static final SystemDescription EXAMPLE_SYSTEM = new SystemDescription(new Coordinates(52.37, 4.90),
            ZoneId.of("Europe/Amsterdam"), List.of(new PanelArray("", 4.8, 35, -45, 0.14)));

    /** Added to the example's array for the check on several arrays. */
    static final PanelArray SECOND_ARRAY = new PanelArray("", 2.4, 15, 90, 0.10);

    public record Made(ForecastProvider provider, ReplayHttp http) {
    }

    public Made provider(List<Route> routes) {
        var http = new ReplayHttp(routes);
        return new Made(make.apply(http), http);
    }

    public Map<String, String> allSettings() {
        var all = new HashMap<>(settings);
        all.putAll(secrets);
        return all;
    }
}
