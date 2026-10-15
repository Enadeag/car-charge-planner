package io.github.enadeag.carchargeplanner.engine.forecast;

import java.time.Duration;
import java.time.Instant;

/** Expected AC power of the solar system (kW) from start (inclusive) to end (exclusive). */
public record ForecastStep(Instant start, Instant end, double powerKw) {

    public double hours() {
        return Duration.between(start, end).toNanos() / 3_600e9;
    }
}
