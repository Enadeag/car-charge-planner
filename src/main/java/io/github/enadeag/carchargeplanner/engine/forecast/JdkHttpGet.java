package io.github.enadeag.carchargeplanner.engine.forecast;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;

/** Fetches web addresses with Java's own HTTP client, naming the app and its home page in the User-Agent. */
public final class JdkHttpGet implements HttpGet {

    private static final String USER_AGENT = "car-charge-planner (+https://github.com/Enadeag/car-charge-planner)";

    private final HttpClient client;

    /**
     * Makes the fetcher.
     *
     * @param client the client to send with; it is shared by every request and not closed here
     */
    public JdkHttpGet(HttpClient client) {
        this.client = client;
    }

    @Override
    public Reply get(Request request) throws IOException, InterruptedException {
        var builder = HttpRequest.newBuilder(request.uri()).header("User-Agent", USER_AGENT).timeout(request.timeout());
        request.headers().forEach(builder::header);
        var response = client.send(builder.GET().build(), HttpResponse.BodyHandlers.ofString());
        var headers = new HashMap<String, String>();
        response.headers().map().forEach((name, values) -> headers.put(name, values.getFirst()));
        return new Reply(response.statusCode(), response.body(), headers);
    }
}
