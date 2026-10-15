package io.github.enadeag.carchargeplanner.engine.forecast;

import io.github.enadeag.carchargeplanner.engine.config.ProviderId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** The forecast providers this version of the app knows, found by id. */
public final class Providers {

    private final Map<ProviderId, ForecastProvider> byId = new LinkedHashMap<>();

    /**
     * Collects the providers.
     *
     * @param providers every provider this version knows, in the order they are offered
     * @throws IllegalArgumentException when two providers share an id
     */
    public Providers(List<ForecastProvider> providers) {
        for (var provider : providers) {
            var id = provider.info().id();
            if (byId.putIfAbsent(id, provider) != null) {
                throw new IllegalArgumentException("there are two providers with the id '" + id + "'");
            }
        }
    }

    /**
     * Finds a provider.
     *
     * @param id the provider's id, as the config file names it; it need not be known
     * @return the provider, or empty when this version does not know it
     */
    public Optional<ForecastProvider> find(ProviderId id) {
        return Optional.ofNullable(byId.get(id));
    }

    /**
     * The known providers.
     *
     * @return their ids, in the order they were given
     */
    public List<ProviderId> ids() {
        return List.copyOf(byId.keySet());
    }
}
