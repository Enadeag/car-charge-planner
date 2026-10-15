package io.github.enadeag.carchargeplanner.engine;

import io.github.enadeag.carchargeplanner.engine.config.ConfigFile;
import io.github.enadeag.carchargeplanner.engine.config.ConfigProblem;
import io.github.enadeag.carchargeplanner.engine.config.EngineConfig;
import io.github.enadeag.carchargeplanner.engine.config.HomeSystem;
import io.github.enadeag.carchargeplanner.engine.forecast.ConnectionCheck;
import io.github.enadeag.carchargeplanner.engine.forecast.ConnectionResult;
import io.github.enadeag.carchargeplanner.engine.forecast.ForecastProvider;
import io.github.enadeag.carchargeplanner.engine.forecast.ForecastResult;
import io.github.enadeag.carchargeplanner.engine.forecast.ForecastService;
import io.github.enadeag.carchargeplanner.engine.forecast.ForecastUnavailable;
import io.github.enadeag.carchargeplanner.engine.forecast.PlaceResults;
import io.github.enadeag.carchargeplanner.engine.forecast.Providers;
import io.github.enadeag.carchargeplanner.engine.forecast.SystemDescription;
import io.github.enadeag.carchargeplanner.engine.plan.GridAdvisor;
import io.github.enadeag.carchargeplanner.engine.plan.Planner;
import io.github.enadeag.carchargeplanner.engine.secrets.ApiKeys;
import io.github.enadeag.carchargeplanner.engine.secrets.KeyStorage;
import io.github.enadeag.carchargeplanner.engine.secrets.Secrets;
import io.github.enadeag.carchargeplanner.engine.secrets.SecretsProblem;
import java.time.Clock;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Plans, fetches forecasts and owns the stored data, without any user interface. Clients get plain records back.
 *
 * <p>Each plan reads the config file again; the forecast service decides whether a provider is asked or its cache
 * answers.
 */
public final class Engine {

    /** Until the owner can enter the battery level, the plan starts from this one. */
    static final double ASSUMED_BATTERY_PCT = 50;

    private final ConfigFile configFile;
    private final ForecastService forecasts;
    private final Providers providers;
    private final ApiKeys apiKeys;
    private final Clock clock;

    public Engine(ConfigFile configFile, ForecastService forecasts, Providers providers, ApiKeys apiKeys, Clock clock) {
        this.configFile = configFile;
        this.forecasts = forecasts;
        this.providers = providers;
        this.apiKeys = apiKeys;
        this.clock = clock;
    }

    /** Blocks while the forecast is fetched: call it away from the user interface's thread. */
    public Outlook plan() {
        EngineConfig config;
        try {
            config = configFile.read();
        } catch (ConfigProblem e) {
            return new Outlook.NotSetUp(e.getMessage());
        }
        var now = clock.instant();
        var system = config.system();
        ForecastResult forecast;
        try {
            forecast = forecasts.get(SystemDescription.of(system), config.forecast(), now, false);
        } catch (ForecastUnavailable e) {
            return new Outlook.NoForecast(e.getMessage());
        }
        // nothing is announced until there are notifications, so there is no announced window to keep
        var days = new Planner(system, config.planning()).plan(forecast.steps(), ASSUMED_BATTERY_PCT, now, List.of());
        var advice = new GridAdvisor(system, config.planning()).advise(days, forecast.steps(), now);
        return new Outlook.Planned(now, system.timezone(), days, ASSUMED_BATTERY_PCT, true, advice,
                Optional.of(forecast.source()));
    }

    /**
     * Asks a provider for one forecast with these settings, for set-up's and Settings' connection check. Nothing is
     * cached or saved. Blocks while the provider is asked.
     *
     * @param settings as the owner entered them, secret ones included; a secret one left out comes from the keychain
     */
    public ConnectionResult checkConnection(String providerId, HomeSystem system, Map<String, String> settings) {
        var found = providers.find(providerId);
        if (found.isEmpty()) {
            return new ConnectionResult(false, 0,
                    "The forecast provider '" + providerId + "' is not known to this version of the app.");
        }
        var secret = new HashMap<>(storedSecrets().of(providerId));
        secret.putAll(settings);
        var values = found.get().info().settingsFrom(settings, secret);
        return ConnectionCheck.attempt(found.get(), SystemDescription.of(system), values, clock.instant());
    }

    /**
     * Places for what the owner typed, from the provider's place search; empty when the provider has none, and then
     * the owner types coordinates. Blocks while the service is asked.
     */
    public Optional<PlaceResults> searchPlaces(String providerId, String text, String language) {
        return providers.find(providerId).flatMap(ForecastProvider::placeSearch)
                .map(search -> search.search(text, language));
    }

    /** Where the API keys are kept, so a client can tell the owner when it is a file rather than the keychain. */
    public KeyStorage keyStorage() {
        return apiKeys.storage();
    }

    /** Stores the providers' secret settings, replacing all of them; a provider that failed before is asked again. */
    public void saveApiKeys(Secrets secrets) throws SecretsProblem {
        apiKeys.save(secrets);
        forecasts.forgetFailures();
    }

    private Secrets storedSecrets() {
        try {
            return apiKeys.load();
        } catch (SecretsProblem e) {
            return Secrets.NONE; // the check then says which key is missing
        }
    }
}
