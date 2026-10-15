package io.github.enadeag.carchargeplanner.engine.forecast;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** What asking one provider came to: a forecast to use, or the reason there is none. */
sealed interface Outcome {

    /**
     * A forecast to use.
     *
     * @param steps the forecast's steps
     * @param fetchedAt when it was fetched
     * @param fromCache true when the network was not used for it
     * @param stale true when a refresh was due but could not be done
     * @param cooldownUntil the end of the provider's rate limit, when it has one
     */
    record Usable(List<ForecastStep> steps, Instant fetchedAt, boolean fromCache, boolean stale,
            Optional<Instant> cooldownUntil) implements Outcome {
    }

    /**
     * No forecast from this provider.
     *
     * @param reason why, starting with the provider's name
     */
    record Failure(String reason) implements Outcome {
    }
}
