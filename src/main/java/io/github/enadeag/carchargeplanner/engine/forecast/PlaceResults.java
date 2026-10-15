package io.github.enadeag.carchargeplanner.engine.forecast;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/** What a place search gives back: places (none when nothing matched), or why there are none. */
public sealed interface PlaceResults {

    /**
     * The search worked.
     *
     * @param places the places in the order the service ranks them; empty when nothing matched
     */
    record Found(List<Place> places) implements PlaceResults {

        /** Copies the places. */
        public Found {
            places = List.copyOf(places);
        }
    }

    /**
     * The search did not work.
     *
     * @param problem why
     * @param retryAfter what the service said about when to ask again, after a rate limit
     */
    record Failed(Problem problem, Optional<Duration> retryAfter) implements PlaceResults {

        /**
         * A failure without a time to ask again.
         *
         * @param problem why the search did not work
         */
        public Failed(Problem problem) {
            this(problem, Optional.empty());
        }
    }

    /** Why a search gave no places. */
    enum Problem {
        /** Fewer than two characters: nothing was asked. */
        TOO_SHORT,
        /** No network, a timeout, a server error. */
        UNAVAILABLE,
        /** The service refuses more requests for now. */
        RATE_LIMITED,
        /** An answer that is not what the service documents. */
        BAD_RESPONSE
    }
}
