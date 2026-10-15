package io.github.enadeag.carchargeplanner.engine.forecast;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

/**
 * Asks a provider for one forecast to see whether it works with these settings: set-up's and Settings' connection
 * check. Nothing is cached or saved, and the answer is in plain words.
 */
public final class ConnectionCheck {

    private ConnectionCheck() {
    }

    /** Blocks while the provider is asked; never throws. */
    public static ConnectionResult attempt(ForecastProvider provider, SystemDescription system,
            Map<String, String> settings, Instant now) {
        var info = provider.info();
        var missing = info.missingRequired(settings);
        if (!missing.isEmpty()) {
            return new ConnectionResult(false, 0, String.join(", ", missing) + " is required.");
        }
        List<ForecastStep> steps;
        try {
            steps = provider.fetch(system, settings, now);
        } catch (ProviderError e) {
            return new ConnectionResult(false, 0, describe(info, e));
        } catch (RuntimeException e) {
            // a bug in the provider: say what kind, not what it said, which may hold a key
            return new ConnectionResult(false, 0, info.name() + ": unexpected error (" + e.getClass().getSimpleName() + ")");
        }
        int days = forecastDays(steps, now, system.timezone());
        return new ConnectionResult(true, days, "OK: " + days + (days == 1 ? " day" : " days") + " of forecast");
    }

    /**
     * How many days, today onwards, the steps cover. A step counts on the day of its middle: Open-Meteo's first step is
     * the hour before midnight, which belongs to yesterday.
     */
    static int forecastDays(List<ForecastStep> steps, Instant now, ZoneId zone) {
        var today = now.atZone(zone).toLocalDate();
        return (int) steps.stream()
                .map(step -> step.start().plus(Duration.between(step.start(), step.end()).dividedBy(2)))
                .map(middle -> middle.atZone(zone).toLocalDate())
                .filter(day -> !day.isBefore(today))
                .distinct()
                .count();
    }

    static String describe(ProviderInfo info, ProviderError error) {
        return switch (error) {
            case ProviderError.RateLimited limited -> limited.retryAfter()
                    .map(wait -> info.name() + " says there were too many requests. Try again in " + minutes(wait) + ".")
                    .orElse(info.name() + " says there were too many requests. Try again later.");
            case ProviderError.Unavailable unavailable ->
                    "Could not get a forecast from " + info.name() + ": " + unavailable.getMessage();
            case ProviderError.NotConfigured notConfigured ->
                    info.name() + " is not set up completely: " + notConfigured.getMessage();
            case ProviderError.BadResponse bad ->
                    info.name() + " gave an answer the app does not understand: " + bad.getMessage();
        };
    }

    private static String minutes(Duration wait) {
        long minutes = Math.max(1, (long) Math.rint(wait.toSeconds() / 60.0));
        return minutes + (minutes == 1 ? " minute" : " minutes");
    }
}
