package io.github.enadeag.carchargeplanner.engine.forecast;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A service that forecasts the power of the owner's solar panels over the next days: the contract every provider
 * fulfils and the planner relies on. Caching, the fallback provider and retrying live in {@link ForecastService}.
 *
 * <p>A provider reaches the network only through the {@link HttpGet} it is made with, checks its settings before it
 * sends anything, and never puts a secret setting in an error message.
 */
public interface ForecastProvider {

    ProviderInfo info();

    /**
     * Blocks while the forecast is fetched.
     *
     * @param settings the provider's declared settings that have a value, secret ones included
     * @return the power of all panel arrays together, in steps that satisfy {@link Steps#problems}
     */
    List<ForecastStep> fetch(SystemDescription system, Map<String, String> settings, Instant now) throws ProviderError;

    /** The provider's place search, when it has one. */
    default Optional<PlaceSearch> placeSearch() {
        return Optional.empty();
    }
}
