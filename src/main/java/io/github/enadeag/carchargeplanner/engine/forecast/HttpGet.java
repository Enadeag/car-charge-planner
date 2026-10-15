package io.github.enadeag.carchargeplanner.engine.forecast;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;

/**
 * Fetches a web address: the one way the forecast providers reach the network, so tests can answer instead. A
 * server's refusal (a 429, a 503) is a {@link Reply} with that status; only "could not talk to the server at all"
 * throws.
 */
public interface HttpGet {

    Duration DEFAULT_TIMEOUT = Duration.ofSeconds(15);

    Reply get(Request request) throws IOException, InterruptedException;

    record Request(URI uri, Map<String, String> headers, Duration timeout) {

        public Request {
            headers = Map.copyOf(headers);
        }

        public static Request of(URI uri) {
            return new Request(uri, Map.of(), DEFAULT_TIMEOUT);
        }
    }

    record Reply(int status, String body, Map<String, String> headers) {

        public Reply {
            headers = Map.copyOf(headers);
        }

        public Reply(int status, String body) {
            this(status, body, Map.of());
        }

        public boolean ok() {
            return status >= 200 && status < 300;
        }

        /** Header names are case-insensitive. */
        public Optional<String> header(String name) {
            return headers.entrySet().stream()
                    .filter(header -> header.getKey().equalsIgnoreCase(name))
                    .map(Map.Entry::getValue)
                    .findFirst();
        }

        /** The Retry-After header in its seconds form; the date form is not used by the providers here. */
        public Optional<Duration> retryAfter() {
            return header("Retry-After").map(String::strip)
                    .filter(value -> value.matches("\\d{1,9}"))
                    .map(value -> Duration.ofSeconds(Long.parseLong(value)));
        }
    }
}
