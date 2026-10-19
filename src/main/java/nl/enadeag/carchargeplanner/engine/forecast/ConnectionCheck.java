package nl.enadeag.carchargeplanner.engine.forecast;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Asks a provider for one forecast to see whether it works with these settings: set-up's and Settings' connection
 * check. Nothing is cached or saved, and the answer is in plain words.
 */
public final class ConnectionCheck {

    private ConnectionCheck() {
    }

    /**
     * Asks the provider for one forecast; blocks while it is asked and never throws.
     *
     * @param provider the provider to try
     * @param system the system to ask a forecast for
     * @param settings the settings to try, secret ones included, as the provider's fetch would get them
     * @param now the current moment; the days are counted from its date in the system's time zone
     * @param otherSecrets further secret values the message must never show, such as every stored key; may be empty
     * @return whether it worked, the days of forecast, and a message for the owner, which never holds a secret value
     */
    public static ConnectionResult attempt(ForecastProvider provider, SystemDescription system,
            Map<String, String> settings, Instant now, Collection<String> otherSecrets) {
        var info = provider.info();
        var missing = info.missingRequired(settings);
        if (!missing.isEmpty()) {
            return new ConnectionResult(false, 0, String.join(", ", missing) + " is required.");
        }
        List<ForecastStep> steps;
        try {
            steps = provider.fetch(system, settings, now);
        } catch (ProviderError e) {
            // a server can echo what it was sent
            var secrets = Stream.concat(info.secretValues(settings).stream(), otherSecrets.stream()).toList();
            return new ConnectionResult(false, 0, ProviderText.redacted(describe(info, e), secrets));
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
     *
     * @param steps the forecast, possibly empty
     * @param now the current moment; its date in {@code zone} is today
     * @param zone the time zone of the home, in which the days are counted
     * @return the number of different dates, today or later, the steps' middles fall on
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

    /**
     * What went wrong, in plain words for the owner.
     *
     * @param info the provider that failed, for its name
     * @param error what it reported
     * @return one or two sentences naming the provider and the problem
     */
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
