package io.github.enadeag.carchargeplanner.engine.forecast;

import io.github.enadeag.carchargeplanner.engine.config.HomeSystem;
import java.util.List;

/** A service that forecasts the power of the owner's solar panels over the next days. */
public interface ForecastProvider {

    /** The steps in time order, without gaps or overlaps, with the power of all panel arrays together. */
    List<ForecastStep> forecast(HomeSystem system) throws ForecastUnavailable;
}
