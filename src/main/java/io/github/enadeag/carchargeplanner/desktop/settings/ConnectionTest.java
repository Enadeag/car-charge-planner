package io.github.enadeag.carchargeplanner.desktop.settings;

import io.github.enadeag.carchargeplanner.engine.config.ProviderId;
import io.github.enadeag.carchargeplanner.engine.forecast.ConnectionResult;
import java.util.Map;

/** Asks a forecast provider for one forecast with the settings as the owner entered them, nothing saved. */
@FunctionalInterface
interface ConnectionTest {

    /**
     * Asks the provider; blocks while it is asked, so call it away from the JavaFX thread.
     *
     * @param provider the provider to try
     * @param settings its settings as entered, secret ones included; empty ones left out
     * @return whether it worked, the days of forecast, and the engine's words about it
     */
    ConnectionResult attempt(ProviderId provider, Map<String, String> settings);
}
