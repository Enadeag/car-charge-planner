package nl.enadeag.carchargeplanner.engine.forecast;

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

    /**
     * What the provider tells about itself.
     *
     * @return the same information on every call
     */
    ProviderInfo info();

    /**
     * Fetches a forecast; blocks while the service is asked.
     *
     * @param system where the panels are and their arrays; the forecast is for all arrays together
     * @param settings the provider's declared settings that have a value, secret ones included; not changed
     * @param now the moment of the request, for answers that are relative to it
     * @return the power of all panel arrays together, in steps that satisfy {@link StepRules#problems}
     * @throws ProviderError when there is no forecast; the kind says why
     */
    List<ForecastStep> fetch(SolarSite system, Map<String, String> settings, Instant now) throws ProviderError;

    /**
     * The provider's place search, when it has one.
     *
     * @return its place search, or empty when the owner has to type coordinates
     */
    default Optional<PlaceSearch> placeSearch() {
        return Optional.empty();
    }
}
