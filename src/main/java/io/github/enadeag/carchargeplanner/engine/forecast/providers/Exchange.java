package io.github.enadeag.carchargeplanner.engine.forecast.providers;

import io.github.enadeag.carchargeplanner.engine.forecast.HttpGet;
import io.github.enadeag.carchargeplanner.engine.forecast.ProviderError;
import java.io.IOException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.MissingNode;

/** What the providers share when they talk to their service: sending a request and reading JSON. */
final class Exchange {

    private static final JsonMapper JSON = new JsonMapper();

    private Exchange() {
    }

    /** The reply, or unavailable when the service could not be reached; the message never holds the address. */
    static HttpGet.Reply send(HttpGet http, HttpGet.Request request, String service) throws ProviderError.Unavailable {
        try {
            return http.get(request);
        } catch (IOException e) {
            // the address may hold an API key, and an IOException's message may hold the address
            throw new ProviderError.Unavailable("could not reach " + service + " (" + e.getClass().getSimpleName() + ")");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ProviderError.Unavailable("the request to " + service + " was interrupted");
        }
    }

    /** The parsed body, or a missing node when it is not JSON. */
    static JsonNode json(String body) {
        try {
            return JSON.readTree(body);
        } catch (JacksonException e) {
            return MissingNode.getInstance();
        }
    }
}
