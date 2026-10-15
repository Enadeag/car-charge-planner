package io.github.enadeag.carchargeplanner.engine.forecast;

import java.util.List;

public record ForecastResult(List<ForecastStep> steps, ForecastSource source) {

    public ForecastResult {
        steps = List.copyOf(steps);
    }
}
