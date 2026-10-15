package io.github.enadeag.carchargeplanner.engine.forecast;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Against a small server on this computer's loopback address: nothing leaves the machine. */
class JdkHttpGetTest {

    private HttpServer server;
    private final AtomicReference<com.sun.net.httpserver.Headers> received = new AtomicReference<>();

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/", exchange -> {
            received.set(exchange.getRequestHeaders());
            var body = "slow down".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Retry-After", "90");
            exchange.sendResponseHeaders(429, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void aRefusalIsAReplyWithItsStatusBodyAndHeadersAndTheRequestNamesTheProject() throws Exception {
        var uri = URI.create("http://localhost:" + server.getAddress().getPort() + "/forecast");
        try (var client = HttpClient.newHttpClient()) {
            var reply = new JdkHttpGet(client).get(new HttpGet.Request(uri, Map.of("Accept", "application/json"),
                    Duration.ofSeconds(5)));

            assertThat(reply.status()).isEqualTo(429);
            assertThat(reply.body()).isEqualTo("slow down");
            assertThat(reply.retryAfter()).contains(Duration.ofSeconds(90));
        }
        assertThat(received.get().getFirst("User-Agent")).contains("car-charge-planner", "https://github.com/");
        assertThat(received.get().getFirst("Accept")).isEqualTo("application/json");
    }
}
