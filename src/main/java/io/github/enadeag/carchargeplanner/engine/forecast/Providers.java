package io.github.enadeag.carchargeplanner.engine.forecast;

import io.github.enadeag.carchargeplanner.engine.config.ForecastSelection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** The forecast providers this version of the app knows, found by id. */
public final class Providers {

    private final Map<String, ForecastProvider> byId = new LinkedHashMap<>();

    public Providers(List<ForecastProvider> providers) {
        for (var provider : providers) {
            var id = provider.info().id();
            if (!ForecastSelection.PROVIDER_ID.matcher(id).matches()) {
                throw new IllegalArgumentException("a provider id is lower case letters, digits and hyphens, not '" + id + "'");
            }
            if (byId.putIfAbsent(id, provider) != null) {
                throw new IllegalArgumentException("there are two providers with the id '" + id + "'");
            }
        }
    }

    public Optional<ForecastProvider> find(String id) {
        return Optional.ofNullable(byId.get(id));
    }

    public List<String> ids() {
        return List.copyOf(byId.keySet());
    }
}
