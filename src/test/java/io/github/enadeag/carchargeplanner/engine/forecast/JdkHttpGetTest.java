package io.github.enadeag.carchargeplanner.engine.forecast;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Against a small server on this computer's loopback address: nothing leaves the machine. */
class JdkHttpGetTest {

    private static final ByteSize SMALL_LIMIT = ByteSize.ofKilobytes(64);
    private static final Duration SHORT_TIMEOUT = Duration.ofMillis(400);

    private HttpServer server;
    private ExecutorService handlers;
    private HttpClient client;
    private final AtomicReference<com.sun.net.httpserver.Headers> received = new AtomicReference<>();
    private final AtomicLong sent = new AtomicLong();

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        handlers = Executors.newCachedThreadPool(Thread.ofPlatform().daemon().factory());
        server.setExecutor(handlers);
        server.createContext("/refusal", exchange -> {
            received.set(exchange.getRequestHeaders());
            exchange.getResponseHeaders().add("Retry-After", "90");
            answer(exchange, 429, "slow down".getBytes(StandardCharsets.UTF_8));
        });
        server.createContext("/exactly-the-limit", exchange -> answer(exchange, 200, new byte[(int) SMALL_LIMIT.bytes()]));
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
            exchange.sendResponseHeaders(200, 2L * 1024 * 1024 * 1024);
            stream(exchange, 64 * 1024, Duration.ZERO);
        });
        server.createContext("/trickle", exchange -> {
            exchange.sendResponseHeaders(200, 0);
            stream(exchange, 1, Duration.ofMillis(50));
        });
        server.start();
        client = JdkHttpGet.newClient();
    }

    @AfterEach
    void stopServer() {
        client.close();
        server.stop(0);
        handlers.shutdownNow();
    }

    private static void answer(HttpExchange exchange, int status, byte[] body) throws IOException {
        exchange.sendResponseHeaders(status, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }

    /** Sends parts until the client stops reading or ten seconds have passed. */
    private void stream(HttpExchange exchange, int partSize, Duration pause) {
        var part = new byte[partSize];
        Arrays.fill(part, (byte) '0');
        long end = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        try (exchange) {
            while (System.nanoTime() < end) {
                exchange.getResponseBody().write(part);
                exchange.getResponseBody().flush();
                sent.addAndGet(partSize);
                if (pause.isPositive()) {
                    Thread.sleep(pause);
                }
            }
        } catch (IOException | InterruptedException e) {
            // the client hung up, or the test is over
        }
    }

    private URI at(String path) {
        return URI.create("http://localhost:" + server.getAddress().getPort() + path);
    }

    private HttpGet.Reply get(String path, Duration timeout) throws Exception {
        return new JdkHttpGet(client).get(new HttpGet.Request(at(path), Map.of("Accept", "application/json"), timeout,
                SMALL_LIMIT));
    }

    @Test
    void aRefusalIsAReplyWithItsStatusBodyAndHeadersAndTheRequestNamesTheProject() throws Exception {
        var reply = get("/refusal", Duration.ofSeconds(5));

        assertThat(reply.status()).isEqualTo(429);
        assertThat(reply.body()).isEqualTo("slow down");
        assertThat(reply.retryAfter()).contains(Duration.ofSeconds(90));
        assertThat(received.get().getFirst("User-Agent")).contains("car-charge-planner", "https://github.com/");
        assertThat(received.get().getFirst("Accept")).isEqualTo("application/json");
    }

    @Test
    void aBodyOfExactlyTheLimitIsRead() throws Exception {
        assertThat(get("/exactly-the-limit", Duration.ofSeconds(5)).body()).hasSize((int) SMALL_LIMIT.bytes());
    }

    @Test
    void anEndlessBodyIsRefusedOnceItPassesTheLimit() {
        long started = System.nanoTime();
        var error = catchThrowable(() -> get("/endless", Duration.ofSeconds(5)));

        assertThat(error).isInstanceOf(HttpGet.TooLarge.class);
        assertThat(Duration.ofNanos(System.nanoTime() - started)).as("not waiting for the timeout")
                .isLessThan(Duration.ofSeconds(3));
    }

    @Test
    void aBodyDeclaredLargerThanTheLimitIsRefusedBeforeItIsRead() {
        assertThat(catchThrowable(() -> get("/declared-huge", Duration.ofSeconds(5))))
                .isInstanceOf(HttpGet.TooLarge.class);
    }

    @Test
    void aTricklingBodyEndsAtTheTimeoutAlthoughTheHeadersCameAtOnce() {
        long started = System.nanoTime();
        var error = catchThrowable(() -> get("/trickle", SHORT_TIMEOUT));
        var took = Duration.ofNanos(System.nanoTime() - started);

        assertThat(error).isInstanceOf(HttpTimeoutException.class);
        assertThat(sent.get()).as("the body had started").isPositive().isLessThan(SMALL_LIMIT.bytes());
        assertThat(took).isGreaterThanOrEqualTo(SHORT_TIMEOUT).isLessThan(SHORT_TIMEOUT.plusSeconds(2));
    }

    @Test
    void aBodyOfUnknownLengthOneByteOverTheLimitIsRefused() {
        assertThat(catchThrowable(() -> get("/one-over", Duration.ofSeconds(5)))).isInstanceOf(HttpGet.TooLarge.class);
    }
}
