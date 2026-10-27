package nl.enadeag.carchargeplanner.engine.route;

import java.time.Instant;
import java.util.Optional;

/** What looking up a calendar event's location gave: a place with its drive, no place, or why there is no answer. */
public sealed interface RouteOutcome {

    /**
     * A physical place, with the drive to it from home. The place's name can be an address, so {@link #toString()}
     * shows none of it.
     *
     * @param placeName the name or address of the place found, or the location text when the event gave the
     *     coordinates; possibly blank in that case
     * @param drive the drive from home one way
     * @param match how the place was settled
     * @param measure whether the drive is the route service's or an estimate
     */
    record Found(String placeName, Drive drive, Match match, Measure measure) implements RouteOutcome {

        /**
         * A description that may be logged.
         *
         * @return how the place was settled and how the drive was measured, without the place or the distance
         */
        @Override
        public String toString() {
            return "Found[" + match + ", " + measure + "]";
        }
    }

    /** The location is no physical place, such as an online meeting or a link; nothing was sent. */
    record NotAPlace() implements RouteOutcome {
    }

    /** The route service found no place for the location text. */
    record NotFound() implements RouteOutcome {
    }

    /**
     * The route service gave no answer to use.
     *
     * @param reason why, in words for the owner after the service's name; never a secret setting, a location text or
     *     coordinates
     * @param retryAt when the service is asked again; empty when it may be asked at once, such as after a missing
     *     setting, which sent nothing
     */
    record Failed(String reason, Optional<Instant> retryAt) implements RouteOutcome {
    }

    /** How the place of a found location was settled. */
    enum Match {
        /** The event held the coordinates itself; no place was searched for. */
        GIVEN,
        /** The route service found one place. */
        ONLY,
        /** The route service found several places, and the one closest to home was chosen. */
        CHOSEN
    }

    /** How the drive was measured. */
    enum Measure {
        /** The route service's route by car. */
        ROUTE,
        /**
         * Without a route: the straight-line distance times {@link RouteLookup#DETOUR_FACTOR}, at
         * {@link RouteLookup#ESTIMATE_SPEED_KMH}.
         */
        ESTIMATE
    }
}
