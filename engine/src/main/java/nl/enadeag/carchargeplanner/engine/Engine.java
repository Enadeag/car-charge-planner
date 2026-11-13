package nl.enadeag.carchargeplanner.engine;

import nl.enadeag.carchargeplanner.engine.away.AwayFile;
import nl.enadeag.carchargeplanner.engine.away.OneOffAwayPeriod;
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
import nl.enadeag.carchargeplanner.engine.forecast.ForecastCache;
import nl.enadeag.carchargeplanner.engine.forecast.ForecastProvider;
import nl.enadeag.carchargeplanner.engine.forecast.ForecastResult;
import nl.enadeag.carchargeplanner.engine.forecast.ForecastService;
import nl.enadeag.carchargeplanner.engine.forecast.ForecastUnavailable;
import nl.enadeag.carchargeplanner.engine.forecast.HttpGet;
import nl.enadeag.carchargeplanner.engine.forecast.PlaceResults;
import nl.enadeag.carchargeplanner.engine.forecast.ProviderInfo;
import nl.enadeag.carchargeplanner.engine.forecast.Providers;
import nl.enadeag.carchargeplanner.engine.forecast.SolarSite;
import nl.enadeag.carchargeplanner.engine.notify.AnnouncedWindows;
import nl.enadeag.carchargeplanner.engine.notify.Announcer;
import nl.enadeag.carchargeplanner.engine.notify.Notifier;
import nl.enadeag.carchargeplanner.engine.notify.PlanLevel;
import nl.enadeag.carchargeplanner.engine.notify.ToldTrips;
import nl.enadeag.carchargeplanner.engine.plan.AwayTimes;
import nl.enadeag.carchargeplanner.engine.plan.GridAdvisor;
import nl.enadeag.carchargeplanner.engine.plan.Planner;
import nl.enadeag.carchargeplanner.engine.secrets.ApiKeys;
import nl.enadeag.carchargeplanner.engine.secrets.KeyChanges;
import nl.enadeag.carchargeplanner.engine.secrets.KeyStorage;
import nl.enadeag.carchargeplanner.engine.secrets.Secrets;
import nl.enadeag.carchargeplanner.engine.secrets.SecretsProblem;
import nl.enadeag.carchargeplanner.engine.setting.InvalidSetting;
import nl.enadeag.carchargeplanner.engine.store.PrivateFiles;
import nl.enadeag.carchargeplanner.engine.trip.Trip;
import nl.enadeag.carchargeplanner.engine.trip.TripFile;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.Year;
import java.time.ZoneId;
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
 * each change to its config, keys, battery level, away periods or trips, and tells its listener each new outlook.
 * Each plan uses the stored files as they are on disk, reading one again only when it changed; the forecast service
 * decides whether a provider is asked or its cache answers.
 *
 * <p>The changes to the away periods and to the trips may be called from any thread, also at the same time: each
 * reads, changes and writes its file as one step, so no change is lost to another.
 */
public final class Engine {

    /** The name of the owner's settings file, which both apps keep in the same format. */
    public static final String CONFIG_FILE_NAME = "engine.json";

    /** Until the owner enters the battery level, the plan starts from this one. */
    static final Percent ASSUMED_BATTERY_PCT = new Percent(50);

    private final ConfigFile configFile;
    private final ForecastService forecasts;
    private final Providers providers;
    private final ApiKeys apiKeys;
    private final AwayFile awayFile;
    private final TripFile tripFile;
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
     * @param tripFile where the trips the owner enters are kept, read again for a plan when it changed on disk
     * @param batteryFile where the battery level the owner entered last is kept, read again for a plan when it
     *     changed on disk
     * @param clock the time plans are made at, and that decides which away periods and trips are old
     * @param announcer tells the owner about the plan's windows shortly before they start, and keeps the ones it told
     *     about as they were told
     * @param executor the engine's own thread, where it plans once {@linkplain #start(Consumer) started}; {@link
     *     #stop()} shuts it down. Nothing runs on it before the start; never null.
     */
    public Engine(ConfigFile configFile, ForecastService forecasts, Providers providers, ApiKeys apiKeys,
            AwayFile awayFile, TripFile tripFile, BatteryFile batteryFile, Clock clock, Announcer announcer,
            ScheduledExecutorService executor) {
        this.configFile = configFile;
        this.forecasts = forecasts;
        this.providers = providers;
        this.apiKeys = apiKeys;
        this.awayFile = awayFile;
        this.tripFile = tripFile;
        this.batteryFile = batteryFile;
        this.clock = clock;
        this.announcer = announcer;
        this.schedule = new PlanSchedule(executor);
    }

    /**
     * The engine on the files of one data folder, with the settings file {@value #CONFIG_FILE_NAME} in it too, and the
     * {@linkplain Providers#standard(HttpGet) built-in providers}; nothing is read, created or fetched yet.
     *
     * @param dataFolder the folder that holds the settings file, the away periods, the trips, the battery level, the
     *     forecast cache and what was told; it need not exist yet; never null
     * @param http the way to the network the providers ask through; never null
     * @param files how the engine writes its files; never null
     * @param apiKeys where the providers' secret settings are kept; never null
     * @param notifier tells the owner what a round finds due; never null
     * @param clock the time plans are made at, and a file that cannot be used is renamed with; never null
     * @param planner the engine's own thread, where it plans once {@linkplain #start(Consumer) started}; {@link
     *     #stop()} shuts it down; never null
     * @return the engine, not started
     */
    public static Engine inFolder(Path dataFolder, HttpGet http, PrivateFiles files, ApiKeys apiKeys,
            Notifier notifier, Clock clock, ScheduledExecutorService planner) {
        return inFolder(dataFolder, dataFolder.resolve(CONFIG_FILE_NAME), http, files, apiKeys, notifier, clock,
                planner);
    }

    /**
     * The engine on the files of one data folder and a settings file kept elsewhere, with the
     * {@linkplain Providers#standard(HttpGet) built-in providers}; nothing is read, created or fetched yet.
     *
     * @param dataFolder the folder that holds the away periods, the trips, the battery level, the forecast cache and
     *     what was told; it need not exist yet; never null
     * @param configFile the owner's settings file, where the platform keeps it outside the data folder, such as in its
     *     own settings folder or where the owner named it; it need not exist yet; never null
     * @param http the way to the network the providers ask through; never null
     * @param files how the engine writes its files; never null
     * @param apiKeys where the providers' secret settings are kept; never null
     * @param notifier tells the owner what a round finds due; never null
     * @param clock the time plans are made at, and a file that cannot be used is renamed with; never null
     * @param planner the engine's own thread, where it plans once {@linkplain #start(Consumer) started}; {@link
     *     #stop()} shuts it down; never null
     * @return the engine, not started
     */
    public static Engine inFolder(Path dataFolder, Path configFile, HttpGet http, PrivateFiles files,
            ApiKeys apiKeys, Notifier notifier, Clock clock, ScheduledExecutorService planner) {
        var providers = Providers.standard(http);
        var announcer = new Announcer(new AnnouncedWindows(dataFolder.resolve("announced.json"), files, clock),
                new ToldTrips(dataFolder.resolve("trip-notices.json"), files, clock), notifier);
        return new Engine(new ConfigFile(configFile, files, clock),
                new ForecastService(providers, new ForecastCache(dataFolder.resolve("forecasts"), files, clock),
                        apiKeys),
                providers, apiKeys, new AwayFile(dataFolder.resolve("away.json"), files, clock),
                new TripFile(dataFolder.resolve("trips.json"), files, clock),
                new BatteryFile(dataFolder.resolve("battery.json"), files, clock), clock, announcer, planner);
    }

    /**
     * Starts planning on the engine's own thread: at once, then every minute, and again after each change made through
     * the engine, also one that could not be saved, so the listener sees what is stored. Each is a
     * {@linkplain #round(ForecastUse) round} that may ask the providers; the rounds come often enough to tell the owner
     * about a window within the notification lead time. A round that fails, even with an {@link Error}, is logged, and
     * the next one is made as planned.
     *
     * @param listener gets each new outlook, on the engine's thread, so it should hand slow work on; never null
     * @throws IllegalStateException when the engine was started before
     */
    public void start(Consumer<Outlook> listener) {
        if (listener == null) {
            throw new IllegalArgumentException("the listener must not be null");
        }
        schedule.start(() -> listener.accept(round(ForecastUse.ASK_PROVIDERS)));
    }

    /**
     * Stops planning, the counterpart of {@link #start(Consumer)}: a plan that is being made may finish for a few
     * seconds, and no listener is told after this returns. Stopping again does nothing.
     */
    public void stop() {
        schedule.stop();
    }

    /**
     * Makes a plan from the config file and a forecast; nobody is told about a window, which only a
     * {@linkplain #round(ForecastUse) round} does. Blocks while the forecast is fetched.
     *
     * @return the plan, or why there is none: not set up, a config file that cannot be used, or no forecast
     */
    public Outlook outlook() {
        try {
            return outlook(readConfig(), ForecastUse.ASK_PROVIDERS);
        } catch (ConfigProblem e) {
            return withoutConfig(e);
        }
    }

    /**
     * One planning round, as {@link #start(Consumer)} makes one every minute, for a client that plans at moments of
     * its own instead: first, in a round that may ask the providers, the API keys move from their file into the
     * keychain when they can, so the move follows the keychain's unlocking within a round, while a round on the stored
     * forecast only reads no key at all; then the outlook, and on a plan the owner is told what is due: each
     * window within the notification lead time before its start, and what is due about the trips. What was told is
     * kept, so no round tells it again. Blocks while the forecast is fetched: call it away from the user interface's
     * thread.
     *
     * @param use where the forecast may come from: the providers as their rules allow, or only the stored forecast,
     *     which never asks a provider; without a stored forecast that is still useful the outlook has no forecast, and
     *     nothing is told on one more than 6 hours old, nor on one whose provider failed when it was last asked, also
     *     by another process, as it is stale then
     * @return the plan, or why there is none: not set up, a config file that cannot be used, or no forecast
     */
    public Outlook round(ForecastUse use) {
        if (use == null) {
            throw new IllegalArgumentException("the forecast use must not be null");
        }
        if (use == ForecastUse.ASK_PROVIDERS) {
            apiKeys.moveFileIntoKeychain();
        }
        EngineConfig config;
        try {
            config = readConfig();
        } catch (ConfigProblem e) {
            return withoutConfig(e);
        }
        var outlook = outlook(config, use);
        if (outlook instanceof Outlook.Planned planned) {
            announce(planned, config);
        }
        return outlook;
    }

    /**
     * When the next notice is due on a plan that a round made, for a client that wakes the device for it: a window
     * that was not announced yet, at its start minus the notification lead time, or the start of a trip's grid charge,
     * by the same rules a round tells them. The battery check and a short trip are not part of it: they wait for the
     * rounds that come anyway. Reads the config file for the lead time.
     *
     * @param outlook what a round gave, after it told what was due
     * @return the earliest moment, now when a notice is due now; empty when nothing is to come, when the outlook has
     *     no plan, its forecast could not be refreshed or would be more than 6 hours old by then, or the config file
     *     cannot be used
     */
    public Optional<Instant> nextNoticeDue(Outlook outlook) {
        if (!(outlook instanceof Outlook.Planned planned)) {
            return Optional.empty();
        }
        EngineConfig config;
        try {
            config = readConfig();
        } catch (ConfigProblem e) {
            return Optional.empty();
        }
        var now = clock.instant();
        return planned.gridAdvice()
                .map(advice -> announcer.nextDue(planned.days(), advice, planned.tripAdvice(),
                        planned.forecastSource(), now, config))
                .orElseGet(() -> announcer.nextDue(planned.days(), planned.tripAdvice(), planned.forecastSource(),
                        now, config));
    }

    /** Why there is no plan when the config file cannot be read: not set up yet, or a file that cannot be used. */
    private static Outlook withoutConfig(ConfigProblem problem) {
        return problem.kind() == ConfigProblem.Kind.MISSING ? new Outlook.NotSetUp(problem.file())
                : new Outlook.ConfigUnusable(problem.kind(), problem.file(), problem.detail());
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

    private Outlook outlook(EngineConfig config, ForecastUse use) {
        var now = clock.instant();
        var site = SolarSite.of(config.system());
        try {
            var forecast = use == ForecastUse.STORED_ONLY ? forecasts.stored(site, config.forecast(), now)
                    : forecasts.get(site, config.forecast(), now);
            return planned(config, forecast, now);
        } catch (ForecastUnavailable e) {
            return new Outlook.NoForecast(e.getMessage(), e.keychainLocked(), e.keyRefusedBy());
        }
    }

    /**
     * Tells the owner about each window of the plan that is due, the grid advice's among them, and what is due about
     * the trips.
     */
    private void announce(Outlook.Planned planned, EngineConfig config) {
        planned.gridAdvice().ifPresentOrElse(
                advice -> announcer.announceDue(planned.days(), advice, planned.forecastSource(), planned.madeAt(),
                        config),
                () -> announcer.announceDue(planned.days(), planned.forecastSource(), planned.madeAt(), config));
        // a level entered since the plan was made counts as entered: it is planned with in the next round
        var level = new PlanLevel(planned.batteryLevelPct(), batteryFile.reading().map(BatteryReading::enteredAt));
        announcer.announceTripsDue(planned.tripAdvice(), level, planned.forecastSource(), planned.madeAt(), config);
    }

    /**
     * Makes a plan from a config that need not be saved, such as the one set-up shows before Finish, so the owner sees
     * their dashboard with their own data first. The config, the keys and the owner's other files stay as they are,
     * and nobody is told about a window; only a forecast that is fetched is cached, as for a plan, so the first plan
     * after saving the same system does not ask the provider again. A provider's failure is not kept in its cache file,
     * neither as a failed refresh nor as a cool-down, so a wrong key typed in the form does not mark the saved
     * system's forecast as stale. Blocks while the forecast is fetched: call it away from the user interface's
     * thread.
     *
     * @param config the whole config to plan with, as the owner entered it
     * @param keys every provider's secret settings as the owner entered them, in place of the stored ones; may be
     *     none
     * @return the plan, or why there is none: no forecast, with the providers that refused their API key; never
     *     {@link Outlook.NotSetUp} or {@link Outlook.ConfigUnusable}
     */
    public Outlook preview(EngineConfig config, Secrets keys) {
        var now = clock.instant();
        try {
            var forecast = forecasts.getWithKeys(SolarSite.of(config.system()), config.forecast(), keys, now);
            return planned(config, forecast, now);
        } catch (ForecastUnavailable e) {
            return new Outlook.NoForecast(e.getMessage(), e.keychainLocked(), e.keyRefusedBy());
        }
    }

    /**
     * The plan for a config and a forecast, from the stored away periods, the trips made with this car and the battery
     * level.
     */
    private Outlook.Planned planned(EngineConfig config, ForecastResult forecast, Instant now) {
        var system = config.system();
        var trips = tripFile.trips(now).stream().filter(trip -> !trip.notWithThisCar()).toList();
        var away = new AwayTimes(config.away().weekly(), awayFile.periods(), trips);
        var entered = batteryFile.reading();
        var battery = entered.map(BatteryReading::level).orElse(ASSUMED_BATTERY_PCT);
        var plan = new Planner(system, config.planning(), away)
                .planWithTrips(forecast.steps(), battery, now, announcer.announced());
        var advice = new GridAdvisor(system, config.planning(), away).advise(plan, forecast.steps(), now);
        var planning = config.planning();
        return new Outlook.Planned(now, system.timezone(), plan.days(), battery, entered.isEmpty(), advice,
                plan.trips(), forecast.source(), system.house().baseloadKw(), planning.minimumPct(),
                planning.targetPct());
    }

    /**
     * The config as the file holds it now, for a client that shows it to be changed.
     *
     * @return the whole config, every value present and valid
     * @throws ConfigProblem when the file is missing, unreadable, not valid, or written by a newer version of the app
     *     ({@link ConfigProblem.Kind#NEWER_VERSION}); the problem names the file and the setting
     */
    public EngineConfig config() throws ConfigProblem {
        return configFile.read();
    }

    /**
     * Replaces the config file by this config, at once and whole, so the next plan uses it; a provider that failed
     * before is asked again at the next plan instead of after its wait, as a corrected setting deserves a try. A value
     * the owner entered is checked as the config's records are made, which refuse it with an {@link InvalidSetting}
     * that names the setting. Once started, the engine plans again soon after.
     *
     * @param config the whole config to keep
     * @throws IOException when the file was written by a newer version of the app
     *     ({@link ConfigProblem.Kind#NEWER_VERSION}), which is never written over, or could not be written; it is then
     *     left as it was
     */
    public void saveConfig(EngineConfig config) throws IOException {
        thenReplan(() -> {
            configFile.write(config);
            forecasts.forgetFailures();
            return null;
        });
    }

    /**
     * Moves the config file aside to {@code <name>.damaged-<time>}, so the owner can set up again over a file that
     * cannot be used while what it held is kept; the engine is then not set up. A file of a newer version is never
     * moved ({@link ConfigProblem.Kind#NEWER_VERSION}). Once started, the engine plans again soon after.
     *
     * @return where the file is kept now, so the owner can be told
     * @throws java.nio.file.NoSuchFileException when there is no config file
     * @throws IOException when something other than a plain file is in its place, the file was written by a newer
     *     version, or it could not be moved; it is then left as it was
     */
    public Path setConfigAside() throws IOException {
        return thenReplan(() -> configFile.setAside());
    }

    /**
     * Stores the battery level the owner read off the car, so the next plan starts from it instead of an assumed
     * level. The level is kept until the owner enters another one. Once started, the engine plans again soon after.
     *
     * @param level the charge level of the car's battery now
     * @throws IOException when the level could not be saved; the stored one is then unchanged
     */
    public void setBatteryLevel(Percent level) throws IOException {
        thenReplan(() -> {
            batteryFile.save(new BatteryReading(level, clock.instant()));
            return null;
        });
    }

    /**
     * The away periods the owner entered by hand; the weekly ones are in the config file.
     *
     * @return the stored periods in order of their start, including any that ended in the last day
     */
    public List<OneOffAwayPeriod> awayPeriods() {
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
    public List<OneOffAwayPeriod> addAwayPeriod(OneOffAwayPeriod period) throws IOException {
        return thenReplan(() -> awayFile.add(period, clock.instant()));
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
    public List<OneOffAwayPeriod> removeAwayPeriod(OneOffAwayPeriod period) throws IOException {
        return thenReplan(() -> awayFile.remove(period));
    }

    /**
     * The trips the owner entered.
     *
     * @return the stored trips in order of their departure, including any that returned in the last day
     */
    public List<Trip> trips() {
        return tripFile.trips(clock.instant());
    }

    /**
     * Stores a trip, so the next plan leaves the time it takes free. Trips that returned more than a day ago are
     * forgotten at the same time. Once started, the engine plans again soon after.
     *
     * @param trip the trip; one that is stored already is kept once
     * @return the stored trips after the change, as {@link #trips()} gives them
     * @throws IOException when the trips could not be saved, or the stored ones could not be read whole and are left
     *     as they are; the stored ones are then unchanged
     */
    public List<Trip> addTrip(Trip trip) throws IOException {
        return thenReplan(() -> tripFile.add(trip, clock.instant()));
    }

    /**
     * Replaces a stored trip by the owner's changed version of it. Trips that returned more than a day ago are
     * forgotten at the same time. Once started, the engine plans again soon after.
     *
     * @param trip the trip as {@link #trips()} gave it; when it is no longer stored, the changed one is added
     * @param changed the trip as the owner changed it
     * @return the stored trips after the change, as {@link #trips()} gives them
     * @throws IOException when the trips could not be saved, or the stored ones could not be read whole and are left
     *     as they are; the stored ones are then unchanged
     */
    public List<Trip> changeTrip(Trip trip, Trip changed) throws IOException {
        return thenReplan(() -> tripFile.change(trip, changed, clock.instant()));
    }

    /**
     * Forgets a trip, so the next plan may use its time again. Once started, the engine plans again soon after.
     *
     * @param trip the trip as {@link #trips()} gave it; one that is not stored changes nothing
     * @return the stored trips after the change, as {@link #trips()} gives them
     * @throws IOException when the trips could not be saved, or the stored ones could not be read whole and are left
     *     as they are; the stored ones are then unchanged
     */
    public List<Trip> removeTrip(Trip trip) throws IOException {
        return thenReplan(() -> tripFile.remove(trip, clock.instant()));
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
        return ConnectionCheck.attempt(found.get(), SolarSite.of(system), values, clock.instant(),
                stored.values());
    }

    /**
     * The current year on the device's clock, which a client checks a panel array's year placed against
     * ({@link nl.enadeag.carchargeplanner.engine.config.PanelSize.Panels#notPlacedAfter(Year)}): the engine's clock in
     * the device's time zone, so the owner can enter the new year from midnight on 1 January, local time. Reads
     * nothing.
     *
     * @param deviceZone the device's time zone as it is now, which the owner may change while the app runs; never null
     * @return this year in that zone
     */
    public Year thisYear(ZoneId deviceZone) {
        return Year.from(clock.instant().atZone(deviceZone));
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
     * @return the keychain, the locked keychain, or the private file; or, without the file as fallback
     *     ({@link nl.enadeag.carchargeplanner.engine.secrets.KeyFallback#NONE}), unavailable while the keychain cannot
     *     be used
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
        thenReplan(() -> {
            apiKeys.save(secrets);
            forecasts.forgetFailures();
            return null;
        });
    }

    /**
     * Applies the owner's changes to the stored secret settings: every key they did not enter or empty stays, also one
     * that could not be read, such as from a locked keychain. Otherwise as {@link #saveApiKeys(Secrets)}: it also waits
     * for the owner to unlock a locked keychain.
     *
     * @param changes what the owner entered and emptied
     * @param prompting runs, on the calling thread, once the system's prompt to unlock the keychain shows
     * @throws SecretsProblem as {@link #saveApiKeys(Secrets)}
     */
    public void applyKeyChanges(KeyChanges changes, Runnable prompting) throws SecretsProblem {
        thenReplan(() -> {
            apiKeys.applyChanges(changes, prompting);
            forecasts.forgetFailures();
            return null;
        });
    }

    /**
     * Stops a save of the keys that waits for the owner to unlock the system keychain, such as when the window that
     * saves closes; that save then stores nothing. Nothing happens when no save waits.
     */
    public void stopUnlocking() {
        apiKeys.stopUnlocking();
    }

    /** Runs a change of the stored data, then plans again soon, also when the change failed. */
    private <T, X extends Exception> T thenReplan(Change<T, X> change) throws X {
        try {
            return change.run();
        } finally {
            schedule.runSoon();
        }
    }

    @FunctionalInterface
    private interface Change<T, X extends Exception> {
        T run() throws X;
    }

    private Secrets storedSecrets() {
        try {
            return apiKeys.load();
        } catch (SecretsProblem ignored) {
            return Secrets.NONE; // the check then says which key is missing
        }
    }
}
