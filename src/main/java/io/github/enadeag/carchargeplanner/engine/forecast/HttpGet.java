package io.github.enadeag.carchargeplanner.engine.forecast;

import java.io.IOException;
import java.net.URI;

/** Fetches a web address: the one way the forecast providers reach the network, so tests can answer instead. */
public interface HttpGet {

    HttpReply get(URI uri) throws IOException, InterruptedException;

    record HttpReply(int status, String body) {
    }
}
