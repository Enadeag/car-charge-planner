package nl.enadeag.carchargeplanner.engine.forecast;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * What every {@link HttpGet} must do, as its Javadoc lists it: a platform's implementation passes when its test
 * extends this class and hands it a fresh instance per test. Against a small server on this computer's loopback
 * address, so nothing leaves the machine. Connecting is part of the timeout too, but no server here can be slow to
 * accept, so only the answer's part of it is checked. The server is the JDK's {@code com.sun.net.httpserver} and the
 * tests are JUnit 5, so a subclass runs on a JVM: on Android as a local unit test, not on a device or in Robolectric.
 */
public abstract class HttpGetContractTest {

    /** The largest body the requests here allow. */
    protected static final ByteSize SMALL_LIMIT = ByteSize.ofKilobytes(64);

    /** A timeout short enough to wait for. */
    protected static final Duration SHORT_TIMEOUT = Duration.ofMillis(400);

    private static final Duration ENOUGH = Duration.ofSeconds(5);

    private HttpServer server;
    private ExecutorService handlers;
    private final CountDownLatch over = new CountDownLatch(1);
    private final CountDownLatch streaming = new CountDownLatch(1);
    private final CountDownLatch hungUp = new CountDownLatch(1);
    private final AtomicReference<Headers> received = new AtomicReference<>();
    private final AtomicInteger redirectTargetAsked = new AtomicInteger();

    /**
     * The implementation under test, the same one for each call within a test; the subclass closes it after the test
     * when it holds anything.
     *
     * @return the implementation
     */
    protected abstract HttpGet http();

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        handlers = Executors.newCachedThreadPool(runnable -> {
            var thread = new Thread(runnable);
            thread.setDaemon(true);
            return thread;
        });
        server.setExecutor(handlers);
        server.createContext("/refusal", exchange -> {
            received.set(exchange.getRequestHeaders());
            exchange.getResponseHeaders().add("Retry-After", "90");
            answer(exchange, 429, "slow down".getBytes(StandardCharsets.UTF_8));
        });
        server.createContext("/moved", exchange -> {
            exchange.getResponseHeaders().add("Location", "/moved-here");
            answer(exchange, 302, new byte[0]);
        });
        server.createContext("/moved-here", exchange -> {
            redirectTargetAsked.incrementAndGet();
            answer(exchange, 200, "followed".getBytes(StandardCharsets.UTF_8));
        });
        server.createContext("/text", exchange -> {
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            answer(exchange, 200, "Zürich, 5 °C, € 0,25".getBytes(StandardCharsets.UTF_8));
        });
        server.createContext("/twice", exchange -> {
            exchange.getResponseHeaders().add("X-Several", "first");
            exchange.getResponseHeaders().add("X-Several", "second");
            answer(exchange, 200, new byte[0]);
        });
        server.createContext("/echo", exchange -> answer(exchange, 200,
                exchange.getRequestURI().getQuery().getBytes(StandardCharsets.UTF_8)));
        server.createContext("/exactly-the-limit",
                exchange -> answer(exchange, 200, new byte[(int) SMALL_LIMIT.bytes()]));
        server.createContext("/one-over", exchange -> {
            exchange.sendResponseHeaders(200, 0); // chunked: the length is not known before
            exchange.getResponseBody().write(new byte[(int) SMALL_LIMIT.bytes() + 1]);
            exchange.close();
        });
        server.createContext("/endless", exchange -> {
            exchange.sendResponseHeaders(200, 0); // chunked, with no end
            stream(exchange, 64 * 1024, Duration.ZERO);
        });
        server.createContext("/declared-huge", exchange -> {
            // only the length: a client that waits for the body runs into the timeout instead
            exchange.sendResponseHeaders(200, 2L * 1024 * 1024 * 1024);
            exchange.getResponseBody().flush();
            awaitTheEnd(exchange);
        });
        server.createContext("/silent", this::awaitTheEnd);
        server.createContext("/trickle", exchange -> {
            exchange.sendResponseHeaders(200, 0);
            stream(exchange, 1, Duration.ofMillis(50));
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        over.countDown();
        server.stop(0);
        handlers.shutdownNow();
    }

    private static void answer(HttpExchange exchange, int status, byte[] body) throws IOException {
        exchange.sendResponseHeaders(status, body.length == 0 ? -1 : body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }

    /**
     * Sends parts until the client hangs up or the test is over, at most ten seconds; between two parts it waits for
     * the end of the test as long as the pause.
     */
    private void stream(HttpExchange exchange, int partSize, Duration pause) {
        var part = new byte[partSize];
        Arrays.fill(part, (byte) '0');
        long end = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        try (exchange) {
            while (System.nanoTime() < end) {
                exchange.getResponseBody().write(part);
                exchange.getResponseBody().flush();
                streaming.countDown();
                if (!pause.isZero() && over.await(pause.toNanos(), TimeUnit.NANOSECONDS)) {
                    return;
                }
            }
        } catch (IOException e) {
            hungUp.countDown();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); // the test is over
        }
    }

    /** Answers nothing more until the test is over. */
    private void awaitTheEnd(HttpExchange exchange) {
        try (exchange) {
            over.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); // the test is over
        }
    }

    private URI at(String path) {
        return URI.create("http://localhost:" + server.getAddress().getPort() + path);
    }

    /**
     * Asks the implementation under test for one of the server's paths, sending an Accept header and allowing
     * {@link #SMALL_LIMIT}.
     *
     * @param path the path on the test server
     * @param timeout how long the whole answer may take
     * @return the answer
     * @throws IOException as {@link HttpGet#get} throws it
     * @throws InterruptedException when the test's thread was interrupted
     */
    protected HttpGet.Reply get(String path, Duration timeout) throws IOException, InterruptedException {
        return http().get(new HttpGet.Request(at(path), Map.of("Accept", "application/json"), timeout, SMALL_LIMIT));
    }

    /**
     * The headers of the last request to {@code /refusal}.
     *
     * @return the headers, or null before such a request
     */
    protected Headers receivedHeaders() {
        return received.get();
    }

    /**
     * Whether the client dropped a connection on which the server was still sending: {@code /trickle} or
     * {@code /endless}, the paths that stream.
     *
     * @return true once the server failed to send more, within five seconds
     * @throws InterruptedException when the test's thread was interrupted
     */
    protected boolean theClientHungUp() throws InterruptedException {
        return hungUp.await(ENOUGH.toMillis(), TimeUnit.MILLISECONDS);
    }

    @Test
    void aRefusalIsAReplyWithItsStatusBodyAndHeadersAndTheRequestCarriesItsHeadersAndNamesTheApp() throws Exception {
        var reply = get("/refusal", ENOUGH);

        assertThat(reply.status()).isEqualTo(429);
        assertThat(reply.body()).isEqualTo("slow down");
        assertThat(reply.retryAfter()).contains(Duration.ofSeconds(90));
        assertThat(received.get().getFirst("Accept")).isEqualTo("application/json");
        assertThat(received.get().getFirst("User-Agent")).contains("car-charge-planner");
    }

    @Test
    void anAddressWhereNothingListensIsAnIOException() throws IOException {
        int port;
        try (var closedAgain = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            port = closedAgain.getLocalPort();
        }
        var nowhere = new HttpGet.Request(URI.create("http://localhost:" + port + "/"), Map.of(), ENOUGH, SMALL_LIMIT);

        assertThat(catchThrowable(() -> http().get(nowhere))).isInstanceOf(IOException.class)
                .isNotInstanceOf(HttpGet.TooLarge.class);
    }

    @Test
    void aRedirectIsReturnedNotFollowed() throws Exception {
        var reply = get("/moved", ENOUGH);

        assertThat(reply.status()).isEqualTo(302);
        assertThat(reply.header("Location")).contains("/moved-here");
        assertThat(redirectTargetAsked).hasValue(0);
    }

    /** {@code /trickle} sends its headers at once and then a byte now and then, never reaching the limit. */
    @ParameterizedTest
    @ValueSource(strings = {"/silent", "/trickle"})
    void anAnswerThatIsNotWholeWithinTheTimeoutEndsThere(String path) {
        long started = System.nanoTime();
        var error = catchThrowable(() -> get(path, SHORT_TIMEOUT));
        var took = Duration.ofNanos(System.nanoTime() - started);

        assertThat(error).isInstanceOf(IOException.class).isNotInstanceOf(HttpGet.TooLarge.class);
        assertThat(took).isGreaterThanOrEqualTo(SHORT_TIMEOUT).isLessThan(SHORT_TIMEOUT.plusSeconds(2));
    }

    @Test
    void aRequestThatRanOutOfTimeIsEndedSoTheServerCannotHoldIt() throws InterruptedException {
        assertThat(catchThrowable(() -> get("/trickle", SHORT_TIMEOUT))).isInstanceOf(IOException.class);

        assertThat(theClientHungUp()).as("the server saw the request end").isTrue();
    }

    @Test
    void aBodyOfExactlyTheLimitIsRead() throws Exception {
        assertThat(get("/exactly-the-limit", ENOUGH).body()).hasSize((int) SMALL_LIMIT.bytes());
    }

    @Test
    void aBodyOfUnknownLengthOneByteOverTheLimitIsRefused() {
        assertThat(catchThrowable(() -> get("/one-over", ENOUGH))).isInstanceOf(HttpGet.TooLarge.class);
    }

    @Test
    void anEndlessBodyIsRefusedOnceItPassesTheLimit() throws InterruptedException {
        long started = System.nanoTime();
        var error = catchThrowable(() -> get("/endless", ENOUGH));

        assertThat(error).isInstanceOf(HttpGet.TooLarge.class);
        assertThat(Duration.ofNanos(System.nanoTime() - started)).as("not waiting for the timeout")
                .isLessThan(Duration.ofSeconds(3));
        assertThat(theClientHungUp()).as("the rest of the body was not read").isTrue();
    }

    @Test
    void aBodyDeclaredLargerThanTheLimitIsRefusedBeforeItComes() {
        long started = System.nanoTime();
        var error = catchThrowable(() -> get("/declared-huge", ENOUGH));

        assertThat(error).isInstanceOf(HttpGet.TooLarge.class);
        assertThat(Duration.ofNanos(System.nanoTime() - started)).as("not waiting for the body")
                .isLessThan(Duration.ofSeconds(3));
    }

    @Test
    void theBodyIsReadAsUtf8() throws Exception {
        assertThat(get("/text", ENOUGH).body()).isEqualTo("Zürich, 5 °C, € 0,25");
    }

    @Test
    void ofAHeaderSentTwiceTheFirstValueCounts() throws Exception {
        assertThat(get("/twice", ENOUGH).header("X-Several")).contains("first");
    }

    @Test
    void anInterruptedThreadAbortsTheRequest() throws Exception {
        var outcome = new AtomicReference<Throwable>();
        var asking = new Thread(() -> outcome.set(catchThrowable(() -> get("/trickle", ENOUGH))));
        asking.start();
        assertThat(streaming.await(ENOUGH.toMillis(), TimeUnit.MILLISECONDS)).as("the body had started").isTrue();
        asking.interrupt();
        asking.join(ENOUGH.toMillis());

        assertThat(asking.isAlive()).as("still waiting").isFalse();
        assertThat(outcome.get()).isInstanceOf(InterruptedException.class);
        assertThat(theClientHungUp()).as("the server saw the request end").isTrue();
    }

    @Test
    void severalThreadsCanAskAtOnce() throws Exception {
        var askers = Executors.newFixedThreadPool(8);
        try {
            var asked = new ArrayList<Future<String>>();
            for (int i = 0; i < 16; i++) {
                var query = "n=" + i;
                asked.add(askers.submit((Callable<String>) () -> get("/echo?" + query, ENOUGH).body()));
            }
            var answers = new ArrayList<String>();
            for (var answer : asked) {
                answers.add(answer.get(ENOUGH.toMillis(), TimeUnit.MILLISECONDS));
            }
            assertThat(answers).isEqualTo(IntStream.range(0, 16).mapToObj(i -> "n=" + i).collect(Collectors.toList()));
        } finally {
            askers.shutdownNow();
        }
    }
}
