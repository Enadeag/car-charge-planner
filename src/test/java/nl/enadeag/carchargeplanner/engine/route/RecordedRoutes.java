package nl.enadeag.carchargeplanner.engine.route;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/** The route service's answers in the test resources of the services' package, made by hand. */
public final class RecordedRoutes {

    private RecordedRoutes() {
    }

    public static String answer(String name) {
        try (var in = RecordedRoutes.class.getResourceAsStream("services/" + name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
