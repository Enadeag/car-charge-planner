package io.github.enadeag.carchargeplanner.engine.forecast;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/** The recorded answers in the test resources of the providers' package. */
public final class Recorded {

    private Recorded() {
    }

    public static String answer(String name) {
        try (var in = Recorded.class.getResourceAsStream("providers/" + name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
