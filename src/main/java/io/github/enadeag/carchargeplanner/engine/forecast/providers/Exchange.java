package io.github.enadeag.carchargeplanner.engine.forecast.providers;

import io.github.enadeag.carchargeplanner.engine.forecast.HttpGet;
import io.github.enadeag.carchargeplanner.engine.forecast.ProviderError;
import java.io.IOException;
import tools.jackson.core.JacksonException;
import tools.jackson.core.StreamReadConstraints;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.MissingNode;

/** What the providers share when they talk to their service: sending a request and reading JSON. */
final class Exchange {

    /** The most a forecast answer may hold, in characters, as the body is limited in bytes. */
    static final int MAX_DOCUMENT_LENGTH = 2 * 1024 * 1024;
    /** Some hundred values a day for a few days, with room to spare. */
    static final long MAX_TOKENS = 200_000;

    private static final JsonMapper JSON = JsonMapper.builder(JsonFactory.builder()
            .streamReadConstraints(StreamReadConstraints.builder()
                    .maxDocumentLength(MAX_DOCUMENT_LENGTH)
                    .maxTokenCount(MAX_TOKENS)
                    .build())
            .build()).build();

    private Exchange() {
    }

    /**
     * Sends a request for a provider.
     *
     * @param http how to reach the network
     * @param request the request to send; its address may hold an API key
     * @param service the service's name, for the message
     * @return the service's reply, whatever its status
     * @throws ProviderError.Unavailable when the service could not be reached, did not answer in time, gave an answer
     *     larger than the request allows, or the thread was interrupted; the message never holds the address
     */
    static HttpGet.Reply send(HttpGet http, HttpGet.Request request, String service) throws ProviderError.Unavailable {
        try {
            return http.get(request);
        } catch (HttpGet.TooLarge e) {
            throw new ProviderError.Unavailable(service + " gave an answer larger than " + request.maxBody().bytes()
                    + " bytes");
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
     * @return the parsed JSON, or a missing node when it is not JSON or has more than {@link #MAX_DOCUMENT_LENGTH}
     *     characters or {@link #MAX_TOKENS} tokens
     */
    static JsonNode json(String body) {
        try {
            return JSON.readTree(body);
        } catch (JacksonException e) {
            return MissingNode.getInstance();
        }
    }
}
