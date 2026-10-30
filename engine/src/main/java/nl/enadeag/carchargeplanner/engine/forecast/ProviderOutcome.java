package nl.enadeag.carchargeplanner.engine.forecast;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** What asking one provider came to: a forecast to use, or the reason there is none. */
sealed interface ProviderOutcome {

    /**
     * A forecast to use.
     *
     * @param steps the forecast's steps
     * @param fetchedAt when it was fetched
     * @param freshness whether it was fetched just now or comes from the cache, and then whether a refresh was due
     * @param cooldownUntil the end of the provider's cool-down, when it has one
     */
    record Usable(List<ForecastStep> steps, Instant fetchedAt, Freshness freshness,
            Optional<Instant> cooldownUntil) implements ProviderOutcome {
    }

    /**
     * No forecast from this provider.
     *
     * @param reason why, starting with the provider's name
     */
    record Failure(String reason) implements ProviderOutcome {
    }
}
