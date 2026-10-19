package nl.enadeag.carchargeplanner.engine;

import nl.enadeag.carchargeplanner.engine.away.AwayFile;
import nl.enadeag.carchargeplanner.engine.away.AwayPeriod;
import nl.enadeag.carchargeplanner.engine.battery.BatteryFile;
import nl.enadeag.carchargeplanner.engine.battery.BatteryReading;
import nl.enadeag.carchargeplanner.engine.config.ConfigFile;
import nl.enadeag.carchargeplanner.engine.config.ConfigProblem;
import nl.enadeag.carchargeplanner.engine.config.EngineConfig;
import nl.enadeag.carchargeplanner.engine.config.HomeSystem;
import nl.enadeag.carchargeplanner.engine.config.Percent;
import nl.enadeag.carchargeplanner.engine.config.ProviderId;
import nl.enadeag.carchargeplanner.engine.forecast.ConnectionCheck;
import nl.enadeag.carchargeplanner.engine.forecast.ConnectionResult;
import nl.enadeag.carchargeplanner.engine.forecast.ForecastProvider;
import nl.enadeag.carchargeplanner.engine.forecast.ForecastResult;
import nl.enadeag.carchargeplanner.engine.forecast.ForecastService;
import nl.enadeag.carchargeplanner.engine.forecast.ForecastUnavailable;
import nl.enadeag.carchargeplanner.engine.forecast.PlaceResults;
import nl.enadeag.carchargeplanner.engine.forecast.ProviderInfo;
import nl.enadeag.carchargeplanner.engine.forecast.Providers;
import nl.enadeag.carchargeplanner.engine.forecast.SystemDescription;
import nl.enadeag.carchargeplanner.engine.notify.Announcer;
import nl.enadeag.carchargeplanner.engine.plan.AwayTimes;
import nl.enadeag.carchargeplanner.engine.plan.GridAdvisor;
import nl.enadeag.carchargeplanner.engine.plan.Planner;
import nl.enadeag.carchargeplanner.engine.secrets.ApiKeys;
import nl.enadeag.carchargeplanner.engine.secrets.KeyChanges;
import nl.enadeag.carchargeplanner.engine.secrets.KeyStorage;
import nl.enadeag.carchargeplanner.engine.secrets.Secrets;
import nl.enadeag.carchargeplanner.engine.secrets.SecretsProblem;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Consumer;

/**
 * Plans, fetches forecasts and owns the stored data, without any user interface. Clients get plain records back.
 *
 * <p>Once {@linkplain #start(Consumer) started}, the engine plans on its own thread every minute, and again after
 * each change to its config, keys, battery level or away periods, and tells its listener each new outlook. Each plan
 * uses the stored files as they are on disk, reading one again only when it changed; the forecast service decides
 * whether a provider is asked or its cache answers.
 */
public final class Engine {

    /** Until the owner enters the battery level, the plan starts from this one. */
    static final Percent ASSUMED_BATTERY_PCT = new Percent(50);

    private final ConfigFile configFile;
    private final ForecastService forecasts;
    private final Providers providers;
    private final ApiKeys apiKeys;
    private final AwayFile awayFile;
    private final BatteryFile batteryFile;
    private final Clock clock;
    private final Announcer announcer;
    private final PlanSchedule schedule;
    private final ConfigHealth configHealth = new ConfigHealth();

    /**
     * Makes the engine; nothing is read or fetched yet.
     *
     * @param configFile the owner's config file, read again for a plan when it changed on disk
     * @param forecasts gives the forecasts, from its cache or the providers
     * @param providers the providers known to this version, for the connection check and place search
     * @param apiKeys where the providers' secret settings are kept
     * @param awayFile where the away periods the owner enters by hand are kept, read again for a plan when it
     *     changed on disk
     * @param batteryFile where the battery level the owner entered last is kept, read again for a plan when it
     *     changed on disk
     * @param clock the time plans are made at, and that decides which away periods are old
     * @param announcer tells the owner about the plan's windows shortly before they start, and keeps the ones it told
     *     about as they were told
     * @param planner the engine's own thread, where it plans once {@linkplain #start(Consumer) started}; {@link
     *     #stop()} shuts it down. Nothing runs on it before the start; never null.
     */
    public Engine(ConfigFile configFile, ForecastService forecasts, Providers providers, ApiKeys apiKeys,
            AwayFile awayFile, BatteryFile batteryFile, Clock clock, Announcer announcer,
            ScheduledExecutorService planner) {
        this.configFile = configFile;
        this.forecasts = forecasts;
        this.providers = providers;
        this.apiKeys = apiKeys;
        this.awayFile = awayFile;
        this.batteryFile = batteryFile;
        this.clock = clock;
        this.announcer = announcer;
        this.schedule = new PlanSchedule(planner);
    }

    /**
     * Starts planning on the engine's own thread: at once, then every minute, and again after each change made through
     * the engine, also one that could not be saved, so the listener sees what is stored. Each round makes the
     * {@linkplain #outlook() outlook}, then tells the owner about a window that starts within the notification lead
     * time; the rounds come often enough for that. A round that fails, even with an {@link Error}, is logged, and the
     * next one is made as planned.
     *
     * @param listener gets each new outlook, on the engine's thread, so it should hand slow work on; never null
     * @throws IllegalStateException when the engine was started before
     */
    public void start(Consumer<Outlook> listener) {
        if (listener == null) {
            throw new IllegalArgumentException("the listener must not be null");
        }
        schedule.start(() -> listener.accept(round()));
    }

    /**
     * Stops planning, the counterpart of {@link #start(Consumer)}: a plan that is being made may finish for a few
     * seconds, and no listener is told after this returns. Stopping again does nothing.
     */
    public void stop() {
        schedule.stop();
    }

    /**
     * Makes a plan from the config file and a forecast; nobody is told about a window, which only the rounds of
     * {@link #start(Consumer)} do. Blocks while the forecast is fetched.
     *
     * @return the plan, or why there is none: the config file is not usable, or no forecast is
     */
    public Outlook outlook() {
        try {
            return outlook(readConfig());
        } catch (ConfigProblem e) {
            return new Outlook.NotSetUp(e.kind(), e.file(), e.detail());
        }
    }

    /** One scheduled round: the outlook, then the announcements that are due on it. */
    private Outlook round() {
        EngineConfig config;
        try {
            config = readConfig();
        } catch (ConfigProblem e) {
            return new Outlook.NotSetUp(e.kind(), e.file(), e.detail());
        }
        var outlook = outlook(config);
        if (outlook instanceof Outlook.Planned planned) {
            announce(planned, config);
        }
        return outlook;
    }

    /** The config file's config; whether it could be used is noted, and logged when that changed. */
    private EngineConfig readConfig() throws ConfigProblem {
        try {
            var config = configFile.read();
            configHealth.markUsable();
            return config;
        } catch (ConfigProblem e) {
            configHealth.markUnusable(e);
            throw e;
        }
    }

    private Outlook outlook(EngineConfig config) {
        var now = clock.instant();
        try {
            var forecast = forecasts.get(SystemDescription.of(config.system()), config.forecast(), now, false);
            return planned(config, forecast, now);
        } catch (ForecastUnavailable e) {
            return new Outlook.NoForecast(e.getMessage(), e.keychainLocked());
        }
    }

    /** Tells the owner about each window of the plan that is due, the grid advice's among them. */
    private void announce(Outlook.Planned planned, EngineConfig config) {
        planned.gridAdvice().ifPresentOrElse(
                advice -> announcer.announceDue(planned.days(), advice, planned.forecastSource(), planned.madeAt(),
                        config),
                () -> announcer.announceDue(planned.days(), planned.forecastSource(), planned.madeAt(), config));
    }

    /**
     * Makes a plan from a config that need not be saved, such as the one set-up shows before Finish, so the owner sees
     * their dashboard with their own data first. The config, the keys and the owner's other files stay as they are,
     * and nobody is told about a window; only a forecast that is fetched is cached, as for a plan, so the first plan
     * after saving the same system does not ask the provider again. Blocks while the forecast is fetched: call it away
     * from the user interface's thread.
     *
     * @param config the whole config to plan with, as the owner entered it
     * @param keys every provider's secret settings as the owner entered them, in place of the stored ones; may be
     *     none
     * @return the plan, or why there is none: no forecast; never {@link Outlook.NotSetUp}
     */
    public Outlook preview(EngineConfig config, Secrets keys) {
        var now = clock.instant();
        try {
            var forecast = forecasts.getWithKeys(SystemDescription.of(config.system()), config.forecast(), keys, now);
            return planned(config, forecast, now);
        } catch (ForecastUnavailable e) {
            return new Outlook.NoForecast(e.getMessage());
        }
    }

    /** The plan for a config and a forecast, from the stored away periods and battery level. */
    private Outlook.Planned planned(EngineConfig config, ForecastResult forecast, Instant now) {
        var system = config.system();
        var away = new AwayTimes(config.away().recurring(), awayFile.periods());
        var entered = batteryFile.reading();
        var battery = entered.map(BatteryReading::level).orElse(ASSUMED_BATTERY_PCT);
        var days = new Planner(system, config.planning(), away)
                .plan(forecast.steps(), battery, now, announcer.announced());
        var advice = new GridAdvisor(system, config.planning(), away).advise(days, forecast.steps(), now);
        var planning = config.planning();
        return new Outlook.Planned(now, system.timezone(), days, battery, entered.isEmpty(), advice, forecast.source(),
                system.house().baseloadKw(), planning.minimumPct(), planning.targetPct());
    }

    /**
     * The config as the file holds it now, for a client that shows it to be changed.
     *
     * @return the whole config, every value present and valid
     * @throws ConfigProblem when the file is missing, unreadable or not valid; the problem names the file and the
     *     setting
     */
    public EngineConfig config() throws ConfigProblem {
        return configFile.read();
    }

    /**
     * Replaces the config file by this config, at once and whole, so the next plan uses it; a provider that failed
     * before is asked again at the next plan instead of after its wait, as a corrected setting deserves a try. A value
     * the owner entered is checked as the config's records are made, which refuse it with an {@link
     * nl.enadeag.carchargeplanner.engine.setting.InvalidSetting} that names the setting. Once started, the
     * engine plans again soon after.
     *
     * @param config the whole config to keep
     * @throws IOException when the file could not be written; it is then left as it was
     */
    public void saveConfig(EngineConfig config) throws IOException {
        try {
            configFile.write(config);
            forecasts.forgetFailures();
        } finally {
            schedule.runSoon();
        }
    }

    /**
     * Stores the battery level the owner read off the car, so the next plan starts from it instead of an assumed
     * level. The level is kept until the owner enters another one. Once started, the engine plans again soon after.
     *
     * @param level the charge level of the car's battery now
     * @throws IOException when the level could not be saved; the stored one is then unchanged
     */
    public void setBatteryLevel(Percent level) throws IOException {
        try {
            batteryFile.save(new BatteryReading(level, clock.instant()));
        } finally {
            schedule.runSoon();
        }
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
     * day ago are forgotten at the same time. Once started, the engine plans again soon after.
     *
     * @param period the time the car is away; one that is stored already is kept once
     * @return the stored periods after the change, in order of their start
     * @throws IOException when the periods could not be saved, or the stored ones could not be read whole and are
     *     left as they are; the stored ones are then unchanged
     */
    public List<AwayPeriod> addAwayPeriod(AwayPeriod period) throws IOException {
        try {
            return awayFile.add(period, clock.instant());
        } finally {
            schedule.runSoon();
        }
    }

    /**
     * Forgets an away period entered by hand, so the next plan may use that time again. Once started, the engine
     * plans again soon after.
     *
     * @param period the period as {@link #awayPeriods()} gave it; one that is not stored changes nothing
     * @return the stored periods after the change, in order of their start
     * @throws IOException when the periods could not be saved, or the stored ones could not be read whole and are
     *     left as they are; the stored ones are then unchanged
     */
    public List<AwayPeriod> removeAwayPeriod(AwayPeriod period) throws IOException {
        try {
            return awayFile.remove(period);
        } finally {
            schedule.runSoon();
        }
    }

    /**
     * Asks a provider for one forecast with these settings, for set-up's and Settings' connection check. Nothing is
     * cached or saved. Blocks while the provider is asked.
     *
     * @param providerId the provider to try; one this version does not know gives a failed result
     * @param system the system as the owner entered it, which need not be saved yet
     * @param settings as the owner entered them, secret ones included; a secret one left out comes from the stored
     *     keys, in the keychain or the private file
     * @return whether it worked, the days of forecast, and a message for the owner
     */
    public ConnectionResult checkConnection(ProviderId providerId, HomeSystem system, Map<String, String> settings) {
        var found = providers.find(providerId);
        if (found.isEmpty()) {
            return new ConnectionResult(false, 0,
                    "The forecast provider '" + providerId + "' is not known to this version of the app.");
        }
        var stored = storedSecrets();
        var secret = new HashMap<>(stored.of(providerId));
        secret.putAll(settings);
        var values = found.get().info().settingsFrom(settings, secret);
        return ConnectionCheck.attempt(found.get(), SystemDescription.of(system), values, clock.instant(),
                stored.values());
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
     * Whether a provider has a place search, so a client can tell the owner that what they type is sent to it.
     *
     * @param providerId the provider to ask about; one this version does not know has none
     * @return true when {@link #searchPlaces(ProviderId, String, String)} asks it
     */
    public boolean hasPlaceSearch(ProviderId providerId) {
        return providers.find(providerId).flatMap(ForecastProvider::placeSearch).isPresent();
    }

    /**
     * Where the API keys are kept, so a client can tell the owner when it is a file rather than the keychain, or when
     * the keychain is locked. May ask the keychain, which can take a few seconds: call it away from the user
     * interface's thread.
     *
     * @return the keychain, the locked keychain, or the private file
     */
    public KeyStorage keyStorage() {
        return apiKeys.storage();
    }

    /**
     * What the known forecast providers tell about themselves, for a client that lets the owner choose.
     *
     * @return their information, in the order they are offered
     */
    public List<ProviderInfo> providers() {
        return providers.ids().stream().flatMap(id -> providers.find(id).stream()).map(ForecastProvider::info)
                .toList();
    }

    /**
     * The providers' secret settings as they are stored, for a client that shows them to be changed. May ask the
     * keychain, which can take a few seconds: call it away from the user interface's thread.
     *
     * @return every provider's secret settings, possibly none
     * @throws SecretsProblem when the keychain or the private file could not be read, or of the kind
     *     {@link SecretsProblem.Kind#LOCKED} while the keychain that holds them is locked; the message says so in words
     *     for the owner
     */
    public Secrets storedApiKeys() throws SecretsProblem {
        return apiKeys.load();
    }

    /**
     * Stores the providers' secret settings, replacing all of them; a provider that failed before is asked again at
     * the next plan instead of after its wait. Once started, the engine plans again soon after. When the system
     * keychain is locked, the system asks the owner to unlock it, and this waits for their answer, up to
     * {@link ApiKeys#UNLOCK_WAIT}: call it away from the user interface's thread.
     *
     * @param secrets every provider's secret settings; what is left out is forgotten
     * @throws SecretsProblem when neither the keychain nor the private file could store them; of the kind
     *     {@link SecretsProblem.Kind#LOCKED} when the keychain stayed locked and nothing was stored
     */
    public void saveApiKeys(Secrets secrets) throws SecretsProblem {
        try {
            apiKeys.save(secrets);
            forecasts.forgetFailures();
        } finally {
            schedule.runSoon();
        }
    }

    /**
     * Applies the owner's changes to the stored secret settings: every key they did not enter or empty stays, also one
     * that could not be read, such as from a locked keyring. Otherwise as {@link #saveApiKeys(Secrets)}, which this
     * waits for the owner as.
     *
     * @param changes what the owner entered and emptied
     * @param prompting runs, on the calling thread, once the system's prompt to unlock the keychain shows
     * @throws SecretsProblem as {@link #saveApiKeys(Secrets)}
     */
    public void applyKeyChanges(KeyChanges changes, Runnable prompting) throws SecretsProblem {
        try {
            apiKeys.applyChanges(changes, prompting);
            forecasts.forgetFailures();
        } finally {
            schedule.runSoon();
        }
    }

    /**
     * Stops a save of the keys that waits for the owner to unlock the system keychain, such as when the window that
     * saves closes; that save then stores nothing. Nothing happens when no save waits.
     */
    public void stopUnlocking() {
        apiKeys.stopUnlocking();
    }

    private Secrets storedSecrets() {
        try {
            return apiKeys.load();
        } catch (SecretsProblem _) {
            return Secrets.NONE; // the check then says which key is missing
        }
    }
}
