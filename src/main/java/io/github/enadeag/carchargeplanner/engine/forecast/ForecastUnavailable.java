package io.github.enadeag.carchargeplanner.engine.forecast;

/** No forecast now; the message says why, in words the owner can act on. */
public class ForecastUnavailable extends Exception {

    public ForecastUnavailable(String message) {
        super(message);
    }
}
