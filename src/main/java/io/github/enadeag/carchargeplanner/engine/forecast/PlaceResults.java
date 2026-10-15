package io.github.enadeag.carchargeplanner.engine.forecast;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/** What a place search gives back: places (none when nothing matched), or why there are none. */
public sealed interface PlaceResults {

    record Found(List<Place> places) implements PlaceResults {
        public Found {
            places = List.copyOf(places);
        }
    }

    /** @param retryAfter what the service said about when to ask again, after a rate limit */
    record Failed(Problem problem, Optional<Duration> retryAfter) implements PlaceResults {

        public Failed(Problem problem) {
            this(problem, Optional.empty());
        }
    }

    enum Problem {
        /** Fewer than two characters: nothing was asked. */
        TOO_SHORT,
        /** No network, a timeout, a server error. */
        UNAVAILABLE,
        RATE_LIMITED,
        BAD_RESPONSE
    }
}
