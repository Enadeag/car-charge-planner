package io.github.enadeag.carchargeplanner.engine.forecast;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** What asking one provider came to: a forecast to use, or the reason there is none. */
sealed interface Outcome {

    record Usable(List<ForecastStep> steps, Instant fetchedAt, boolean fromCache, boolean stale,
            Optional<Instant> cooldownUntil) implements Outcome {
    }

    record Failure(String reason) implements Outcome {
    }
}
