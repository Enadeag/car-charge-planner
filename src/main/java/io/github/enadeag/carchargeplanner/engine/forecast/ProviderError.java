package io.github.enadeag.carchargeplanner.engine.forecast;

import java.time.Duration;
import java.util.Optional;

/**
 * A provider could not give a forecast. The kind says why, so the forecast service can react: try again soon, wait out
 * a rate limit, or wait the full refresh interval. A message never holds a secret setting such as an API key.
 */
public abstract sealed class ProviderError extends Exception {

    private ProviderError(String message) {
        super(message);
    }

    /** No network, a timeout, or a server error: try again later. */
    public static final class Unavailable extends ProviderError {
        /**
         * Makes the error.
         *
         * @param message why, in words for the owner; never a secret setting
         */
        public Unavailable(String message) {
            super(message);
        }
    }

    /** The provider refuses more requests for now. */
    public static final class RateLimited extends ProviderError {
        private final Duration retryAfter;

        /**
         * Makes the error.
         *
         * @param message why, in words for the owner; never a secret setting
         * @param retryAfter what the provider said about when to ask again, or null when it said nothing
         */
        public RateLimited(String message, Duration retryAfter) {
            super(message);
            this.retryAfter = retryAfter;
        }

        /**
         * When the provider may be asked again.
         *
         * @return what the provider said, measured from the moment it said it; empty when it said nothing
         */
        public Optional<Duration> retryAfter() {
            return Optional.ofNullable(retryAfter);
        }
    }

    /** An answer that is not what the provider documents, including steps that break the contract. */
    public static final class BadResponse extends ProviderError {
        /**
         * Makes the error.
         *
         * @param message why, in words for the owner; never a secret setting
         */
        public BadResponse(String message) {
            super(message);
        }
    }

    /** A setting is missing or wrong; nothing was sent. */
    public static final class NotConfigured extends ProviderError {
        /**
         * Makes the error.
         *
         * @param message why, in words for the owner; never a secret setting
         */
        public NotConfigured(String message) {
            super(message);
        }
    }
}
