package io.github.enadeag.carchargeplanner.engine;

import io.github.enadeag.carchargeplanner.engine.away.AwayFile;
import io.github.enadeag.carchargeplanner.engine.away.AwayPeriod;
import io.github.enadeag.carchargeplanner.engine.config.ConfigFile;
import io.github.enadeag.carchargeplanner.engine.config.ConfigProblem;
import io.github.enadeag.carchargeplanner.engine.config.EngineConfig;
import io.github.enadeag.carchargeplanner.engine.config.HomeSystem;
import io.github.enadeag.carchargeplanner.engine.config.Percent;
import io.github.enadeag.carchargeplanner.engine.config.ProviderId;
import io.github.enadeag.carchargeplanner.engine.forecast.ConnectionCheck;
import io.github.enadeag.carchargeplanner.engine.forecast.ConnectionResult;
import io.github.enadeag.carchargeplanner.engine.forecast.ForecastProvider;
import io.github.enadeag.carchargeplanner.engine.forecast.ForecastResult;
import io.github.enadeag.carchargeplanner.engine.forecast.ForecastService;
import io.github.enadeag.carchargeplanner.engine.forecast.ForecastUnavailable;
import io.github.enadeag.carchargeplanner.engine.forecast.PlaceResults;
import io.github.enadeag.carchargeplanner.engine.forecast.Providers;
import io.github.enadeag.carchargeplanner.engine.forecast.SystemDescription;
import io.github.enadeag.carchargeplanner.engine.plan.AwayTimes;
import io.github.enadeag.carchargeplanner.engine.plan.GridAdvisor;
import io.github.enadeag.carchargeplanner.engine.plan.Planner;
import io.github.enadeag.carchargeplanner.engine.secrets.ApiKeys;
import io.github.enadeag.carchargeplanner.engine.secrets.KeyStorage;
import io.github.enadeag.carchargeplanner.engine.secrets.Secrets;
import io.github.enadeag.carchargeplanner.engine.secrets.SecretsProblem;
import java.io.IOException;
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
    static final Percent ASSUMED_BATTERY_PCT = new Percent(50);

    private final ConfigFile configFile;
    private final ForecastService forecasts;
    private final Providers providers;
    private final ApiKeys apiKeys;
    private final AwayFile awayFile;
    private final Clock clock;

    /**
     * Makes the engine; nothing is read or fetched yet.
     *
     * @param configFile the owner's config file, read again for every plan
     * @param forecasts gives the forecasts, from its cache or the providers
     * @param providers the providers known to this version, for the connection check and place search
     * @param apiKeys where the providers' secret settings are kept
     * @param awayFile where the away periods the owner enters by hand are kept, read again for every plan
     * @param clock the time plans are made at, and that decides which away periods are old
     */
    public Engine(ConfigFile configFile, ForecastService forecasts, Providers providers, ApiKeys apiKeys,
            AwayFile awayFile, Clock clock) {
        this.configFile = configFile;
        this.forecasts = forecasts;
        this.providers = providers;
        this.apiKeys = apiKeys;
        this.awayFile = awayFile;
        this.clock = clock;
    }

    /**
     * Makes a plan from the config file and a forecast. Blocks while the forecast is fetched: call it away from the
     * user interface's thread.
     *
     * @return the plan, or why there is none: the config file is not usable, or no forecast is
     */
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
        var away = new AwayTimes(config.away().recurring(), awayFile.periods());
        // nothing is announced until there are notifications, so there is no announced window to keep
        var days = new Planner(system, config.planning(), away)
                .plan(forecast.steps(), ASSUMED_BATTERY_PCT, now, List.of());
        var advice = new GridAdvisor(system, config.planning(), away).advise(days, forecast.steps(), now);
        return new Outlook.Planned(now, system.timezone(), days, ASSUMED_BATTERY_PCT, true, advice, forecast.source());
    }

    /**
     * The away periods the owner entered by hand; the weekly ones are in the config file.
     *
     * @return the stored periods in order of their start, including any that ended in the last day
     */
    public List<AwayPeriod> awayPeriods() {
        return awayFile.periods();
    }

    /**
     * Stores an away period entered by hand, so the next plan leaves that time free. Periods that ended more than a
     * day ago are forgotten at the same time.
     *
     * @param period the time the car is away; one that is stored already is kept once
     * @return the stored periods after the change, in order of their start
     * @throws IOException when the periods could not be saved; the stored ones are then unchanged
     */
    public List<AwayPeriod> addAwayPeriod(AwayPeriod period) throws IOException {
        return awayFile.add(period, clock.instant());
    }

    /**
     * Forgets an away period entered by hand, so the next plan may use that time again.
     *
     * @param period the period as {@link #awayPeriods()} gave it; one that is not stored changes nothing
     * @return the stored periods after the change, in order of their start
     * @throws IOException when the periods could not be saved; the stored ones are then unchanged
     */
    public List<AwayPeriod> removeAwayPeriod(AwayPeriod period) throws IOException {
        return awayFile.remove(period);
    }

    /**
     * Asks a provider for one forecast with these settings, for set-up's and Settings' connection check. Nothing is
     * cached or saved. Blocks while the provider is asked.
     *
     * @param providerId the provider to try; one this version does not know gives a failed result
     * @param system the system as the owner entered it, which need not be saved yet
     * @param settings as the owner entered them, secret ones included; a secret one left out comes from the keychain
     * @return whether it worked, the days of forecast, and a message for the owner
     */
    public ConnectionResult checkConnection(ProviderId providerId, HomeSystem system, Map<String, String> settings) {
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
     *
     * @param providerId the provider whose search to use, normally the primary
     * @param text what the owner typed: a name or a postcode
     * @param language the language the names should come in, such as "nl"; empty means English
     * @return the results, or empty when the provider is unknown or has no place search
     */
    public Optional<PlaceResults> searchPlaces(ProviderId providerId, String text, String language) {
        return providers.find(providerId).flatMap(ForecastProvider::placeSearch)
                .map(search -> search.search(text, language));
    }

    /**
     * Where the API keys are kept, so a client can tell the owner when it is a file rather than the keychain. Asks the
     * keychain whether it can be used.
     *
     * @return the keychain, or the private file
     */
    public KeyStorage keyStorage() {
        return apiKeys.storage();
    }

    /**
     * Stores the providers' secret settings, replacing all of them; a provider that failed before is asked again at
     * the next plan instead of after its wait.
     *
     * @param secrets every provider's secret settings; what is left out is forgotten
     * @throws SecretsProblem when neither the keychain nor the private file could store them
     */
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
