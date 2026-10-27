package nl.enadeag.carchargeplanner.engine.route;

import java.time.Duration;
import java.util.Optional;

/**
 * A route service gave no answer to use. The kind says why, so the lookup can react: try again soon, wait out a
 * cool-down, or wait longer. A message never holds a secret setting, a location text or coordinates.
 */
public abstract sealed class RouteError extends Exception {

    private RouteError(String message) {
        super(message);
    }

    /** No network, a timeout, or a server error: try again later. */
    public static final class Unavailable extends RouteError {
        /**
         * Makes the error.
         *
         * @param message why, in words for the owner; never a secret setting, a location text or coordinates
         */
        public Unavailable(String message) {
            super(message);
        }
    }

    /** The service refuses more requests for now. */
    public static final class RateLimited extends RouteError {
        private final Duration retryAfter;

        /**
         * Makes the error.
         *
         * @param message why, in words for the owner; never a secret setting, a location text or coordinates
         * @param retryAfter what the service said about when to ask again, or null when it said nothing
         */
        public RateLimited(String message, Duration retryAfter) {
            super(message);
            this.retryAfter = retryAfter;
        }

        /**
         * When the service may be asked again.
         *
         * @return what the service said, measured from the moment it said it; empty when it said nothing
         */
        public Optional<Duration> retryAfter() {
            return Optional.ofNullable(retryAfter);
        }
    }

    /** The service refused the API key. */
    public static final class Refused extends RouteError {
        /**
         * Makes the error.
         *
         * @param message why, in words for the owner; never a secret setting, a location text or coordinates
         */
        public Refused(String message) {
            super(message);
        }
    }

    /** An answer that is not what the service documents, or a request it did not accept. */
    public static final class BadResponse extends RouteError {
        /**
         * Makes the error.
         *
         * @param message why, in words for the owner; never a secret setting, a location text or coordinates
         */
        public BadResponse(String message) {
            super(message);
        }
    }

    /** A setting is missing or wrong; nothing was sent. */
    public static final class MissingSetting extends RouteError {
        /**
         * Makes the error.
         *
         * @param message why, in words for the owner; never a secret setting
         */
        public MissingSetting(String message) {
            super(message);
        }
    }
}
