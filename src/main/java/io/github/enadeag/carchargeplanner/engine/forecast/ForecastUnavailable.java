package io.github.enadeag.carchargeplanner.engine.forecast;

/** No provider gave a forecast and nothing usable is cached; the message says why, per provider. */
public class ForecastUnavailable extends Exception {

    public ForecastUnavailable(String message) {
        super(message);
    }
}
