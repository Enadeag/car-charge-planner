package io.github.enadeag.carchargeplanner.engine.forecast;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public final class JdkHttpGet implements HttpGet {

    private static final String USER_AGENT = "car-charge-planner (+https://github.com/Enadeag/car-charge-planner)";
    private static final Duration TIMEOUT = Duration.ofSeconds(15);

    private final HttpClient client;

    public JdkHttpGet(HttpClient client) {
        this.client = client;
    }

    @Override
    public HttpReply get(URI uri) throws IOException, InterruptedException {
        var request = HttpRequest.newBuilder(uri).header("User-Agent", USER_AGENT).timeout(TIMEOUT).GET().build();
        var response = client.send(request, HttpResponse.BodyHandlers.ofString());
        return new HttpReply(response.statusCode(), response.body());
    }
}
