package nl.enadeag.carchargeplanner.engine.forecast;

import nl.enadeag.carchargeplanner.engine.forecast.providers.OpenMeteo;

/**
 * A class of {@code engine.forecast} that makes a provider itself, which breaks {@code EngineArchitectureTest}'s rule
 * that only {@code Providers} knows the providers' classes; it is only imported by
 * {@code BrokenEngineArchitectureTest}, never run.
 */
public final class ForecastThatKnowsAProvider {

    private ForecastThatKnowsAProvider() {
    }

    /**
     * Makes the provider.
     *
     * @param http the way to the network; never null
     * @return the provider
     */
    public static ForecastProvider provider(HttpGet http) {
        return new OpenMeteo(http);
    }
}
