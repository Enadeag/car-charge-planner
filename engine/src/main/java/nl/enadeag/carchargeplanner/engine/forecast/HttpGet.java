package nl.enadeag.carchargeplanner.engine.forecast;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;

/**
 * Fetches a web address: the one way the forecast providers reach the network, so tests can answer instead, and so
 * the engine brings no HTTP client of its own: each platform hands in one, as Java's own HTTP client is not
 * there on Android (ADR 0010). A server's refusal (a 429, a 503) is a {@link Reply} with that status; only "could not
 * talk to the server at all", an answer that took too long and one that was too large throw.
 *
 * <p>What every implementation must do, which {@code HttpGetContractTest} among the engine's tests checks:
 * <ul>
 *   <li>send the request's headers, and a User-Agent that names the app with {@code car-charge-planner} (the rest
 *   of its text is the implementation's);</li>
 *   <li>throw an {@link IOException} when nothing answers at the address, or the server could not be reached at
 *       all;</li>
 *   <li>return a redirect (3xx) as the {@link Reply} it is, without following it;</li>
 *   <li>end the request when the request's timeout has passed without the whole answer, connecting and the body
 *       included, so a server that trickles its body cannot hold it; the timeout is an {@link IOException} of no
 *       fixed type;</li>
 *   <li>refuse a body larger than the request allows with {@link TooLarge}, without reading the rest of it, and
 *       before reading any of it when its {@code Content-Length} already says so;</li>
 *   <li>read the body as UTF-8, which is what JSON is sent in;</li>
 *   <li>keep the first value of a header the server sent more than once;</li>
 *   <li>abort the request when the waiting thread is interrupted, and throw {@link InterruptedException};</li>
 *   <li>be safe to use from several threads at once: the providers share one, and are asked from the planner's
 *       thread and from a client's background work alike.</li>
 * </ul>
 */
public interface HttpGet {

    /** How long a provider waits for the whole answer, body included, unless it asks otherwise. */
    Duration DEFAULT_TIMEOUT = Duration.ofSeconds(15);

    /** The largest forecast answer read; a week of hourly values for one panel array is some tens of kilobytes. */
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
