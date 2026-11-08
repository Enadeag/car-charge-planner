package nl.enadeag.carchargeplanner.desktop.settings.store;

import nl.enadeag.carchargeplanner.desktop.app.AppSettingsFile;
import nl.enadeag.carchargeplanner.desktop.system.StartAtLogin;
import nl.enadeag.carchargeplanner.engine.Engine;
import nl.enadeag.carchargeplanner.engine.Outlook;
import nl.enadeag.carchargeplanner.engine.config.ConfigProblem;
import nl.enadeag.carchargeplanner.engine.config.EngineConfig;
import nl.enadeag.carchargeplanner.engine.config.HomeSystem;
import nl.enadeag.carchargeplanner.engine.config.ProviderId;
import nl.enadeag.carchargeplanner.engine.forecast.ConnectionResult;
import nl.enadeag.carchargeplanner.engine.forecast.PlaceResults;
import nl.enadeag.carchargeplanner.engine.forecast.ProviderInfo;
import nl.enadeag.carchargeplanner.engine.secrets.Secrets;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Where the settings are kept: the engine keeps its own, the desktop app keeps its own, and the system keeps start at
 * login.
 *
 * @param engine the engine, which reads, checks and writes its config file
 * @param appSettings the desktop app's settings file
 * @param startAtLogin the system's start at login; empty when this copy of the app cannot start at login
 */
public record SettingsStores(Engine engine, AppSettingsFile appSettings, Optional<StartAtLogin> startAtLogin) {

    /**
     * The forecast providers this version knows. The views reach the engine only through these methods, so that no
     * JavaFX class depends on the blocking {@link Engine} itself.
     *
     * @return the providers, the first being the default
     */
    public List<ProviderInfo> providers() {
        return engine.providers();
    }

    /**
     * Asks a provider's place search. Blocks, so call it through {@code Background}.
     *
     * @param provider the provider to ask
     * @param text what the owner typed
     * @param language the language of the results, such as "nl"
     * @return the places, or empty when the provider cannot search places
     */
    public Optional<PlaceResults> searchPlaces(ProviderId provider, String text, String language) {
        return engine.searchPlaces(provider, text, language);
    }

    /**
     * Asks a provider for one forecast with these settings. Blocks, so call it through {@code Background}.
     *
     * @param provider the provider to try
     * @param system the system as the owner entered it, which need not be saved yet
     * @param settings as the owner entered them, secret ones included
     * @return whether it worked, the days of forecast, and a message for the owner
     */
    public ConnectionResult checkConnection(ProviderId provider, HomeSystem system, Map<String, String> settings) {
        return engine.checkConnection(provider, system, settings);
    }

    /**
     * The stored system, for a connection check made before the owner has saved. Blocks, so call it through
     * {@code Background}.
     *
     * @return the system in the stored settings
     * @throws ConfigProblem when the settings file cannot be used
     */
    public HomeSystem storedSystem() throws ConfigProblem {
        return engine.config().system();
    }

    /**
     * Whether a provider has a place search, so the owner can be told that what they type is sent to it.
     *
     * @param provider the provider to ask about
     * @return true when {@link #searchPlaces(ProviderId, String, String)} asks it
     */
    public boolean hasPlaceSearch(ProviderId provider) {
        return engine.hasPlaceSearch(provider);
    }

    /**
     * Plans with settings that are not saved yet. Blocks while the forecast is fetched, so call it through
     * {@code Background}.
     *
     * @param config the whole config to plan with, as the owner entered it
     * @param keys every provider's secret settings as the owner entered them; may be none
     * @return the plan, or why there is none
     */
    public Outlook preview(EngineConfig config, Secrets keys) {
        return engine.preview(config, keys);
    }
}
