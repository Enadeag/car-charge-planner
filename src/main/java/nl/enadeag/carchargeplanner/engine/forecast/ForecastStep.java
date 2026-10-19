package nl.enadeag.carchargeplanner.engine.forecast;

import java.time.Duration;
import java.time.Instant;

/**
 * The expected power of the solar system over a stretch of time.
 *
 * @param start the first moment of the step, inclusive
 * @param end the end of the step, exclusive
 * @param powerKw the expected AC power of the whole system in kW, 0 or more
 */
public record ForecastStep(Instant start, Instant end, double powerKw) {

    /**
     * The length of the step.
     *
     * @return the hours from start to end, in real time
     */
    public double hours() {
        return Duration.between(start, end).toNanos() / 3_600e9;
    }
}
