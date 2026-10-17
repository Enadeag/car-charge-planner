package io.github.enadeag.carchargeplanner.engine.forecast;

import io.github.enadeag.carchargeplanner.engine.config.ProviderId;
import java.lang.System.Logger.Level;
import java.util.HashMap;
import java.util.Map;

/**
 * Whether each provider gave a forecast the last time it was asked, so that a provider that starts failing, and one
 * that answers again, is logged once rather than at every planning round. The reason logged is the one the owner
 * sees, which never holds a secret setting or the request's address. Not safe for several threads: the forecast
 * service uses it under its own lock.
 */
final class ProviderHealth {

    private static final System.Logger LOG = System.getLogger(ProviderHealth.class.getName());

    private final Map<ProviderId, Boolean> answering = new HashMap<>();

    /** Starts knowing nothing of any provider. */
    ProviderHealth() {
    }

    /**
     * The provider gave a forecast; logged when it is the first one, or the first after a failure.
     *
     * @param info the provider
     */
    void answered(ProviderInfo info) {
        var before = answering.put(info.id(), true);
        if (before == null) {
            LOG.log(Level.INFO, info.name() + " gives forecasts");
        } else if (!before) {
            LOG.log(Level.INFO, info.name() + " gives forecasts again");
        }
    }

    /**
     * The provider gave no forecast; logged when it gave one before, or was not asked yet.
     *
     * @param info the provider
     * @param reason why, in the words the owner is told after the provider's name
     */
    void failed(ProviderInfo info, String reason) {
        var before = answering.put(info.id(), false);
        if (before == null || before) {
            LOG.log(Level.WARNING, info.name() + " gives no forecast: " + reason);
        }
    }
}
