package nl.enadeag.carchargeplanner.engine.forecast;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;

/**
 * Fetches a web address: the one way the forecast providers reach the network, so tests can answer instead. A
 * server's refusal (a 429, a 503) is a {@link Reply} with that status; only "could not talk to the server at all",
 * an answer that took too long and one that was too large throw.
 */
public interface HttpGet {

    /** How long a provider waits for the whole answer, body included, unless it asks otherwise. */
    Duration DEFAULT_TIMEOUT = Duration.ofSeconds(15);

    /** The largest forecast answer read; a week of hourly values for one plane is some tens of kilobytes. */
    ByteSize DEFAULT_MAX_BODY = ByteSize.ofMegabytes(2);

    /**
     * Sends the request and waits for the whole answer, at most the request's timeout.
     *
     * @param request what to fetch
     * @return the server's answer, whatever its status
     * @throws TooLarge when the body is larger than the request allows; nothing more of it is read
     * @throws IOException when the server could not be reached or the whole answer did not come in time
     * @throws InterruptedException when the waiting thread was interrupted
     */
    Reply get(Request request) throws IOException, InterruptedException;

    /**
     * A request to send.
     *
     * @param uri the address
     * @param headers extra headers, by name
     * @param timeout how long to wait for the whole answer, body included
     * @param maxBody the largest body that is read; a larger one is refused
     */
    record Request(URI uri, Map<String, String> headers, Duration timeout, ByteSize maxBody) {

        /** Copies the headers. */
        public Request {
            headers = Map.copyOf(headers);
        }

        /**
         * A request without extra headers, with the usual timeout and the usual largest body.
         *
         * @param uri the address to fetch
         * @return the request
         */
        public static Request of(URI uri) {
            return new Request(uri, Map.of(), DEFAULT_TIMEOUT, DEFAULT_MAX_BODY);
        }
    }

    /** The answer's body was larger than the request allows. */
    final class TooLarge extends IOException {

        /**
         * Makes the error.
         *
         * @param limit the largest body the request allowed
         */
        public TooLarge(ByteSize limit) {
            super("the answer is larger than " + limit.bytes() + " bytes");
        }
    }

    /**
     * A server's answer.
     *
     * @param status the HTTP status code
     * @param body the body as text
     * @param headers the headers, by name, with the first value of each
     */
    record Reply(int status, String body, Map<String, String> headers) {

        /** Copies the headers. */
        public Reply {
            headers = Map.copyOf(headers);
        }

        /**
         * An answer without headers.
         *
         * @param status the HTTP status code
         * @param body the body as text
         */
        public Reply(int status, String body) {
            this(status, body, Map.of());
        }

        /**
         * Whether the server did what was asked.
         *
         * @return true for a 2xx status
         */
        public boolean ok() {
            return status >= 200 && status < 300;
        }

        /**
         * One header's value.
         *
         * @param name the header's name, in any case: header names are case-insensitive
         * @return its value, or empty when the answer has no such header
         */
        public Optional<String> header(String name) {
            return headers.entrySet().stream()
                    .filter(header -> header.getKey().equalsIgnoreCase(name))
                    .map(Map.Entry::getValue)
                    .findFirst();
        }

        /**
         * When the server may be asked again, from the Retry-After header in its seconds form; the date form is not
         * used by the providers here.
         *
         * @return the wait, or empty when the header is missing or not a number of seconds
         */
        public Optional<Duration> retryAfter() {
            return header("Retry-After").map(String::strip)
                    .filter(value -> value.matches("\\d{1,9}"))
                    .map(value -> Duration.ofSeconds(Long.parseLong(value)));
        }
    }
}
