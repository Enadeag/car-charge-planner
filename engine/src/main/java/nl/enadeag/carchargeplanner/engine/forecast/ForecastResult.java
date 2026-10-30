package nl.enadeag.carchargeplanner.engine.forecast;

import java.util.List;

/**
 * A forecast and where it came from.
 *
 * @param steps the power of all panel arrays together, in time order
 * @param source who made it, and how fresh it is
 */
public record ForecastResult(List<ForecastStep> steps, ForecastSource source) {

    /** Copies the steps. */
    public ForecastResult {
        steps = List.copyOf(steps);
    }
}
