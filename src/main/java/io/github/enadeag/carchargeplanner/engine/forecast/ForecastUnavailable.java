package io.github.enadeag.carchargeplanner.engine.forecast;

/** No provider gave a forecast and nothing usable is cached; the message says why, per provider. */
public class ForecastUnavailable extends Exception {

    /**
     * Makes the exception.
     *
     * @param message the reason of each provider asked, in words for the owner
     */
    public ForecastUnavailable(String message) {
        super(message);
    }
}
