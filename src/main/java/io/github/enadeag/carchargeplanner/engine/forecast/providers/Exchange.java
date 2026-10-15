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

    /**
     * Sends a request for a provider.
     *
     * @param http how to reach the network
     * @param request the request to send; its address may hold an API key
     * @param service the service's name, for the message
     * @return the service's reply, whatever its status
     * @throws ProviderError.Unavailable when the service could not be reached or the thread was interrupted; the
     *     message never holds the address
     */
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

    /**
     * Parses an answer's body.
     *
     * @param body the body, which may be anything
     * @return the parsed JSON, or a missing node when it is not JSON
     */
    static JsonNode json(String body) {
        try {
            return JSON.readTree(body);
        } catch (JacksonException e) {
            return MissingNode.getInstance();
        }
    }
}
