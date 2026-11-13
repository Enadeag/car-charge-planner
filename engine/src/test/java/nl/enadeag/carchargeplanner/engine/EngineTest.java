package nl.enadeag.carchargeplanner.engine;

import static nl.enadeag.carchargeplanner.engine.forecast.FakeProvider.error;
import static nl.enadeag.carchargeplanner.engine.forecast.FakeProvider.steps;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.assertj.core.api.Assertions.within;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import nl.enadeag.carchargeplanner.LogCapture;
import nl.enadeag.carchargeplanner.engine.away.AwayFile;
import nl.enadeag.carchargeplanner.engine.away.OneOffAwayPeriod;
import nl.enadeag.carchargeplanner.engine.battery.BatteryFile;
import nl.enadeag.carchargeplanner.engine.config.ConfigFile;
import nl.enadeag.carchargeplanner.engine.config.ConfigProblem;
import nl.enadeag.carchargeplanner.engine.config.Coordinates;
import nl.enadeag.carchargeplanner.engine.config.EngineConfig;
import nl.enadeag.carchargeplanner.engine.config.Percent;
import nl.enadeag.carchargeplanner.engine.config.PlanningPreferences;
import nl.enadeag.carchargeplanner.engine.config.ProviderId;
import nl.enadeag.carchargeplanner.engine.config.TypicalValues;
import nl.enadeag.carchargeplanner.engine.forecast.ConnectionResult;
import nl.enadeag.carchargeplanner.engine.forecast.FakeProvider;
import nl.enadeag.carchargeplanner.engine.forecast.ForecastCache;
import nl.enadeag.carchargeplanner.engine.forecast.ForecastProvider;
import nl.enadeag.carchargeplanner.engine.forecast.ForecastService;
import nl.enadeag.carchargeplanner.engine.forecast.ForecastSource;
import nl.enadeag.carchargeplanner.engine.forecast.ForecastStep;
import nl.enadeag.carchargeplanner.engine.forecast.Freshness;
import nl.enadeag.carchargeplanner.engine.forecast.HttpGet.Reply;
import nl.enadeag.carchargeplanner.engine.forecast.PlaceResults;
import nl.enadeag.carchargeplanner.engine.forecast.ProviderError;
import nl.enadeag.carchargeplanner.engine.forecast.ProviderInfo;
import nl.enadeag.carchargeplanner.engine.forecast.ProviderRole;
import nl.enadeag.carchargeplanner.engine.forecast.Providers;
import nl.enadeag.carchargeplanner.engine.forecast.Recorded;
import nl.enadeag.carchargeplanner.engine.forecast.ReplayHttp;
import nl.enadeag.carchargeplanner.engine.forecast.SettingSpec;
import nl.enadeag.carchargeplanner.engine.forecast.providers.ForecastSolar;
import nl.enadeag.carchargeplanner.engine.forecast.providers.OpenMeteo;
import nl.enadeag.carchargeplanner.engine.notify.AnnouncedWindows;
import nl.enadeag.carchargeplanner.engine.notify.Announcement;
import nl.enadeag.carchargeplanner.engine.notify.AnnouncementKind;
import nl.enadeag.carchargeplanner.engine.notify.Announcer;
import nl.enadeag.carchargeplanner.engine.notify.BatteryCheck;
import nl.enadeag.carchargeplanner.engine.notify.Notice;
import nl.enadeag.carchargeplanner.engine.notify.ToldTrips;
import nl.enadeag.carchargeplanner.engine.plan.DayPlan;
import nl.enadeag.carchargeplanner.engine.plan.GridAdvice;
import nl.enadeag.carchargeplanner.engine.plan.TripAdvice;
import nl.enadeag.carchargeplanner.engine.plan.Window;
import nl.enadeag.carchargeplanner.engine.secrets.ApiKeys;
import nl.enadeag.carchargeplanner.engine.secrets.KeyChanges;
import nl.enadeag.carchargeplanner.engine.secrets.KeyFallback;
import nl.enadeag.carchargeplanner.engine.secrets.KeyStorage;
import nl.enadeag.carchargeplanner.engine.secrets.LockableKeychain;
import nl.enadeag.carchargeplanner.engine.secrets.NoKeychain;
import nl.enadeag.carchargeplanner.engine.secrets.Secrets;
import nl.enadeag.carchargeplanner.engine.secrets.SecretsFile;
import nl.enadeag.carchargeplanner.engine.secrets.SecretsProblem;
import nl.enadeag.carchargeplanner.engine.store.FilePrivacy;
import nl.enadeag.carchargeplanner.engine.store.PrivateFiles;
import nl.enadeag.carchargeplanner.engine.trip.Trip;
import nl.enadeag.carchargeplanner.engine.trip.TripFile;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Year;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class EngineTest {

    private static final PrivateFiles FILES = new PrivateFiles(FilePrivacy.NONE);

    private static final Clock CLOCK = Clock.systemUTC();

    private static final Path EXAMPLE = Path.of("docs", "example-engine.json");
    private static final ZoneId BRUSSELS = ZoneId.of("Europe/Brussels");
    private static final LocalDate DAY = LocalDate.of(2026, 7, 3);

    @TempDir
    Path folder;

    private final LockableKeychain keychain = new LockableKeychain();
    private final List<Announcement> told = new ArrayList<>();
    private final List<Notice> toldAboutTrips = new ArrayList<>();
    private final List<Outlook> rounds = new ArrayList<>();
    private FakeProvider main;
    private FakeProvider backup;
    private Path config;

    /** Days with 4 kW (or the given power) from 10:00 to 16:00 local time, nothing the rest of the day. */
    private static List<ForecastStep> days(int count, double powerFrom10To16Kw) {
        var steps = new ArrayList<ForecastStep>();
        for (int day = 0; day < count; day++) {
            for (int hour = 0; hour < 24; hour++) {
                var start = DAY.plusDays(day).atTime(hour, 0).atZone(BRUSSELS).toInstant();
                double powerKw = hour >= 10 && hour < 16 ? powerFrom10To16Kw : 0;
                steps.add(new ForecastStep(start, start.plus(Duration.ofHours(1)), powerKw));
            }
        }
        return steps;
    }

    private static Clock clockAt(int hour) {
        return clockAt(hour, 0);
    }

    private static Clock clockAt(int hour, int minute) {
        return Clock.fixed(DAY.atTime(hour, minute).atZone(BRUSSELS).toInstant(), ZoneOffset.UTC);
    }

    @BeforeEach
    void twoSunnyDaysFromTheMainProviderAndAFallback() throws IOException {
        main = new FakeProvider("main", "Main", steps(days(2, 4.0)));
        backup = new FakeProvider("backup", "Backup", steps(days(2, 3.0)));
        choose("main", "backup");
    }

    /** The example config with these providers chosen. */
    private void choose(String primary, String fallback) throws IOException {
        var example = Files.readString(EXAMPLE);
        var chosen = example.substring(0, example.indexOf("\"forecast\""))
                + "\"forecast\": {\"primary\": \"" + primary + "\", \"fallback\": \"" + fallback + "\", \"settings\": {}}}";
        config = Files.writeString(folder.resolve("engine.json"), chosen);
    }

    private Engine engine(Clock clock, ForecastProvider... providers) {
        return engine(clock, new ManualScheduler(), providers);
    }

    private Engine engine(Clock clock, ManualScheduler scheduler, ForecastProvider... providers) {
        var known = new Providers(List.of(providers));
        var apiKeys = new ApiKeys(keychain, new SecretsFile(folder.resolve("secrets.json"), FILES, clock),
                KeyFallback.PRIVATE_FILE, Clock.systemUTC());
        var service = new ForecastService(known, new ForecastCache(folder.resolve("cache"), FILES, clock), apiKeys);
        return new Engine(new ConfigFile(config, FILES, clock), service, known, apiKeys,
                new AwayFile(folder.resolve("away.json"), FILES, clock),
                new TripFile(folder.resolve("trips.json"), FILES, clock),
                new BatteryFile(folder.resolve("battery.json"), FILES, clock), clock, announcer(), scheduler);
    }

    /** Starts the engine and runs its first round, which announces what is due; its outlooks go to {@link #rounds}. */
    private void start(Engine engine, ManualScheduler scheduler) {
        engine.start(rounds::add);
        scheduler.runPending();
    }

    /** A new engine at that time, started as by {@link #start(Engine, ManualScheduler)}. */
    private ManualScheduler startedAt(Clock clock) {
        var scheduler = new ManualScheduler();
        start(engine(clock, scheduler, main, backup), scheduler);
        return scheduler;
    }

    /** Kept in the test's folder, so a new engine is like the app after a restart. */
    private Announcer announcer() {
        return new Announcer(new AnnouncedWindows(folder.resolve("announced.json"), FILES, CLOCK),
                new ToldTrips(folder.resolve("trip-notices.json"), FILES, CLOCK), notice -> {
                    if (notice instanceof Announcement announcement) {
                        told.add(announcement);
                    } else {
                        toldAboutTrips.add(notice);
                    }
                });
    }

    private Engine engine(Clock clock) {
        return engine(clock, main, backup);
    }

    private static Outlook.Planned planned(Engine engine) {
        return (Outlook.Planned) engine.outlook();
    }

    private static ForecastSource source(Engine engine) {
        return planned(engine).forecastSource();
    }

    @Test
    void theExampleChoosesOpenMeteoWithForecastSolarAsTheFallback() throws ConfigProblem {
        var forecast = new ConfigFile(EXAMPLE, FILES, CLOCK).read().forecast();
        assertThat(forecast.order()).containsExactly(new ProviderId("open-meteo"), new ProviderId("forecast-solar"));
    }

    @Test
    void plansFromTheForecastForTheSystemInTheConfigFile() {
        var planned = planned(engine(clockAt(7)));

        assertThat(planned.zone()).isEqualTo(BRUSSELS);
        assertThat(planned.days()).extracting(day -> day.date()).containsExactly(DAY, DAY.plusDays(1));
        assertThat(planned.batteryLevelPct()).isEqualTo(Engine.ASSUMED_BATTERY_PCT);
        // 4 kW less the 0.5 kW house, times the 0.85 margin: 2.975 kW. 13 A (2.99 kW) on one phase takes a little
        // from the grid, within the 0.2 kWh the example allows
        var window = planned.nextWindow().orElseThrow();
        assertThat(window.start()).isEqualTo(DAY.atTime(10, 0).atZone(BRUSSELS).toInstant());
        assertThat(window.amps()).isEqualTo(13);
        assertThat(planned.gridAdvice()).isEmpty();
    }

    @Test
    void theNextPlanUsesASavedConfig() throws ConfigProblem, IOException {
        var engine = engine(clockAt(7));
        var stored = engine.config();
        var changed = stored.withPlanning(new PlanningPreferences(new Percent(40), new Percent(90), 0.85, 0.2,
                Duration.ofMinutes(45), Duration.ofMinutes(15)));

        engine.saveConfig(changed);

        assertThat(engine.config()).isEqualTo(changed);
        var planned = planned(engine);
        assertThat(planned.minimumPct()).isEqualTo(new Percent(40));
        assertThat(planned.targetPct()).isEqualTo(new Percent(90));
    }

    @Test
    void anEnteredBatteryLevelIsWhereTheNextPlanStartsAlsoAfterARestart() throws IOException {
        engine(clockAt(7)).setBatteryLevel(new Percent(72));

        var planned = planned(engine(clockAt(8)));

        assertThat(planned.batteryLevelPct()).isEqualTo(new Percent(72));
        assertThat(planned.batteryAssumed()).isFalse();
        assertThat(planned.days().get(0).startLevelPct()).isEqualTo(new Percent(72));
    }

    @Test
    void untilALevelIsEnteredThePlanSaysItsLevelIsAssumed() {
        var planned = planned(engine(clockAt(7)));

        assertThat(planned.batteryAssumed()).isTrue();
        assertThat(planned.days().get(0).startLevelPct()).isEqualTo(Engine.ASSUMED_BATTERY_PCT);
    }

    @Test
    void thePlanCarriesWhatTheChartsDraw() {
        var planned = planned(engine(clockAt(7)));

        // the example's house, minimum and target
        assertThat(planned.baseloadKw()).isEqualTo(0.5);
        assertThat(planned.minimumPct()).isEqualTo(new Percent(30));
        assertThat(planned.targetPct()).isEqualTo(new Percent(80));
        var today = planned.days().get(0).forecast();
        assertThat(today).hasSize(24);
        assertThat(today.get(0).start()).isEqualTo(DAY.atStartOfDay(BRUSSELS).toInstant());
        assertThat(today).filteredOn(step -> step.powerKw() > 0).hasSize(6)
                .allSatisfy(step -> assertThat(step.powerKw()).isEqualTo(4.0));
        assertThat(planned.days().get(1).forecast().get(0).start())
                .isEqualTo(DAY.plusDays(1).atStartOfDay(BRUSSELS).toInstant());
    }

    @Test
    void anAwayPeriodIsStoredAndTheNextPlanKeepsItFree() throws IOException {
        var engine = engine(clockAt(7));
        var atHome = planned(engine).days();
        var away = new OneOffAwayPeriod(DAY.atTime(8, 0).atZone(BRUSSELS).toInstant(),
                DAY.atTime(18, 0).atZone(BRUSSELS).toInstant());

        assertThat(engine.addAwayPeriod(away)).containsExactly(away);
        assertThat(engine(clockAt(7)).awayPeriods()).containsExactly(away);
        assertThat(planned(engine).days().get(0).windows()).isEmpty(); // the sun shines from 10:00 to 16:00

        assertThat(engine.removeAwayPeriod(away)).isEmpty();
        assertThat(planned(engine).days()).isEqualTo(atHome);
    }

    @Test
    void theWeeklyAwayPeriodsInTheConfigFileKeepThatTimeFree() throws IOException {
        var friday = DAY.getDayOfWeek().name();
        Files.writeString(config, Files.readString(config).replaceFirst("}$",
                ", \"away\": {\"recurring\": [{\"weekday\": \"" + friday + "\", \"start\": \"08:00\", \"end\": \"18:00\"}]}}"));

        var days = planned(engine(clockAt(7))).days();
        assertThat(days.get(0).windows()).isEmpty();
        assertThat(days.get(1).windows()).isNotEmpty();
    }

    @Test
    void theOutlookCreditsTheProviderThatMadeTheForecast() {
        var source = source(engine(clockAt(7)));

        assertThat(source.attribution()).isEqualTo("Forecast by Main");
        assertThat(source.providerRole()).isEqualTo(ProviderRole.PRIMARY);
        assertThat(main.callCount()).isEqualTo(1);
    }

    @Test
    void aFallbackForecastIsWhatThePlanUsesAndTheOutlookSaysSo() {
        main.script(error(new ProviderError.Unavailable("down")));
        var source = source(engine(clockAt(7)));

        assertThat(source).extracting(ForecastSource::attribution, ForecastSource::providerRole, ForecastSource::primaryName)
                .containsExactly("Forecast by Backup", ProviderRole.FALLBACK, "Main");
    }

    @Test
    void theOutlookAdvisesAGridChargeWhenTheBatteryWouldEndADayBelowTheMinimum() {
        main.script(steps(days(3, 0)));
        var planned = planned(engine(clockAt(7)));

        // without sun the example's 12 % a day of driving takes the battery from 50 % to 17.5 % on the third day,
        // below the minimum of 30 %
        var advice = planned.gridAdvice().orElseThrow();
        assertThat(advice.day()).isEqualTo(DAY.plusDays(2));
        assertThat(advice.endLevelPct()).isCloseTo(17.5, within(1e-9));
        assertThat(advice.window()).isPresent();
    }

    @Test
    void theNextWindowIsTomorrowsOnceTodaysHasEnded() {
        var window = planned(engine(clockAt(17))).nextWindow().orElseThrow();
        assertThat(window.start()).isEqualTo(DAY.plusDays(1).atTime(10, 0).atZone(BRUSSELS).toInstant());
    }

    @Test
    void withoutAConfigFileThereIsNoPlanButTheReasonNamesTheFile() throws IOException {
        Files.delete(config);
        assertThat(engine(clockAt(7)).outlook()).isInstanceOfSatisfying(Outlook.NotSetUp.class,
                notSetUp -> assertThat(notSetUp.file()).isEqualTo(config));
        assertThat(main.callCount()).isZero(); // nothing leaves the computer before there is a location
    }

    @Test
    void aConfigFileThatIsNotValidCannotBeUsedAndTheOutlookSaysWhy() throws IOException {
        Files.writeString(config, "{\"version\": 1}");

        assertThat(engine(clockAt(7)).outlook()).isInstanceOfSatisfying(Outlook.ConfigUnusable.class, unusable -> {
            assertThat(unusable.kind()).isEqualTo(ConfigProblem.Kind.INVALID);
            assertThat(unusable.file()).isEqualTo(config);
            assertThat(unusable.detail()).contains("system");
        });
        assertThat(main.callCount()).isZero();
    }

    @Test
    void thisYearTurnsAtMidnightInTheDevicesZoneAheadOfUtc() {
        var newYearInBrussels = Clock.fixed(Instant.parse("2026-12-31T23:30:00Z"), ZoneOffset.UTC);

        assertThat(engine(newYearInBrussels).thisYear(ZoneId.of("Europe/Brussels"))).isEqualTo(Year.of(2027));
    }

    @Test
    void thisYearTurnsAtMidnightInTheDevicesZoneBehindUtc() {
        var newYearInUtc = Clock.fixed(Instant.parse("2027-01-01T00:30:00Z"), ZoneOffset.UTC);

        assertThat(engine(newYearInUtc).thisYear(ZoneId.of("America/New_York"))).isEqualTo(Year.of(2026));
    }

    @Test
    void aConfigFileOfANewerVersionCannotBeUsedForThatReason() throws IOException {
        Files.writeString(config, Files.readString(config).replace("\"version\": 2", "\"version\": 3"));

        assertThat(engine(clockAt(7)).outlook()).isInstanceOfSatisfying(Outlook.ConfigUnusable.class,
                unusable -> assertThat(unusable.kind()).isEqualTo(ConfigProblem.Kind.NEWER_VERSION));
    }

    @Test
    void aConfigFileSetAsideIsKeptBesideItAndTheEngineIsNotSetUpThen() throws IOException {
        Files.writeString(config, "{\"version\": 1}");
        var scheduler = new ManualScheduler();
        var engine = engine(clockAt(7), scheduler, main, backup);
        start(engine, scheduler);

        var aside = engine.setConfigAside();
        scheduler.runPending();

        assertThat(aside).hasContent("{\"version\": 1}").hasParent(folder);
        assertThat(rounds).last().isEqualTo(new Outlook.NotSetUp(config));
        assertThatThrownBy(engine::config).isInstanceOfSatisfying(ConfigProblem.class,
                problem -> assertThat(problem.kind()).isEqualTo(ConfigProblem.Kind.MISSING));
    }

    @Test
    void aMissingConfigFileIsNotSetUpRatherThanUnusable() {
        assertThatThrownBy(() -> new Outlook.ConfigUnusable(ConfigProblem.Kind.MISSING, config, ""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aRoundWithoutAConfigFileTellsTheListenerItIsNotSetUp() throws IOException {
        Files.delete(config);

        startedAt(clockAt(9, 50));

        assertThat(rounds).singleElement().isEqualTo(new Outlook.NotSetUp(config));
        assertThat(told).isEmpty();
    }

    @Test
    void whenNothingAnswersTheReasonsReachTheOutlook() throws IOException {
        choose("main", "");
        main.script(error(new ProviderError.Unavailable("no network")));

        assertThat(engine(clockAt(7)).outlook()).isEqualTo(new Outlook.NoForecast("Main: no network"));
    }

    @Test
    void switchingTheProviderUsesTheNewOnesForecastAtTheNextPlan() throws IOException {
        var engine = engine(clockAt(7));
        engine.outlook();
        choose("backup", "");

        assertThat(source(engine).attribution()).isEqualTo("Forecast by Backup");
        assertThat(backup.callCount()).isEqualTo(1);
    }

    @Test
    void aPlanningChangeKeepsTheForecastAndAsksNobody() throws IOException {
        var engine = engine(clockAt(7));
        engine.outlook();
        Files.writeString(config, Files.readString(config).replace("\"margin\": 0.85", "\"margin\": 0.5"));

        assertThat(source(engine).freshness()).isEqualTo(Freshness.CACHED);
        assertThat(main.callCount()).isEqualTo(1);
    }

    @Test
    void duringACoolDownTheOutlookSaysUntilWhenTheProviderIsBusyAndAfterItDoesNot() {
        main.script(steps(days(2, 4.0)), error(new ProviderError.RateLimited("busy", Duration.ofHours(1))),
                steps(days(2, 4.0)));
        engine(clockAt(7)).outlook();

        var busy = source(engine(clockAt(9))); // due again; the provider says wait
        assertThat(busy.freshness()).isEqualTo(Freshness.STALE);
        assertThat(busy.cooldownUntil()).contains(DAY.atTime(10, 0).atZone(BRUSSELS).toInstant());

        var after = source(engine(clockAt(11)));
        assertThat(after.cooldownUntil()).isEmpty();
        assertThat(main.callCount()).isEqualTo(3);
    }

    @ParameterizedTest
    @CsvSource({"2026-07-02T21:59:59Z, 2026-07-02", "2026-07-02T22:00:00Z, 2026-07-03",
            "2026-07-03T21:59:59Z, 2026-07-03"})
    void aPlansTodayIsTheDayItWasMadeInTheHomesTimeZone(Instant madeAt, LocalDate today) {
        var source = new ForecastSource(new ProviderId("main"), "Main", "Forecast by Main", madeAt, Freshness.FRESH,
                ProviderRole.PRIMARY, "", Optional.empty(), List.of());
        var planned = new Outlook.Planned(madeAt, BRUSSELS, List.of(), new Percent(50), true, Optional.empty(),
                List.of(), source, 0.4, new Percent(30), new Percent(80));
        assertThat(planned.today()).isEqualTo(today);
    }

    @Test
    void aPlanWithoutWindowsHasNoNextWindow() {
        var madeAt = Instant.parse("2026-07-03T05:00:00Z");
        var source = new ForecastSource(new ProviderId("main"), "Main", "Forecast by Main", madeAt, Freshness.FRESH, ProviderRole.PRIMARY,
                "", Optional.empty(), List.of());
        var planned = new Outlook.Planned(madeAt, BRUSSELS, List.of(), new Percent(50), true, Optional.empty(), List.of(), source, 0.4,
                new Percent(30), new Percent(80));
        assertThat(planned.nextWindow()).isEmpty();
    }

    @Test
    void theNextWindowIsTheFirstThatHasNotEndedAndAHouseMayUseNothing() {
        var madeAt = DAY.atTime(10, 0).atZone(BRUSSELS).toInstant();
        var source = new ForecastSource(new ProviderId("main"), "Main", "Forecast by Main", madeAt, Freshness.FRESH,
                ProviderRole.PRIMARY, "", Optional.empty(), List.of());
        var ended = new Window(madeAt.minus(Duration.ofHours(2)), madeAt,
                10, 2.3, 4.6, 0);
        var next = new Window(madeAt.plus(Duration.ofHours(1)),
                madeAt.plus(Duration.ofHours(3)), 10, 2.3, 4.6, 0);
        var day = new DayPlan(DAY, new Percent(50), List.of(ended, next),
                new Percent(60), new Percent(80), new Percent(50), 10, List.of());
        var planned = new Outlook.Planned(madeAt, BRUSSELS, List.of(day), new Percent(50), true, Optional.empty(), List.of(), source,
                0, new Percent(30), new Percent(80));

        assertThat(planned.nextWindow()).contains(next);
    }

    @Test
    void theConnectionCheckAsksTheProviderOnceAndCountsTheDays() {
        var result = engine(clockAt(7), main, backup).checkConnection(new ProviderId("main"), exampleSystem(), Map.of());

        assertThat(result).isEqualTo(new ConnectionResult(true, 2, "OK: 2 days of forecast"));
        assertThat(folder.resolve("cache")).doesNotExist(); // nothing is cached or saved
    }

    @Test
    void theConnectionCheckNamesAnUnknownProvider() {
        assertThat(engine(clockAt(7)).checkConnection(new ProviderId("solcast"), exampleSystem(), Map.of()).message())
                .contains("'solcast' is not known");
    }

    @Test
    void theConnectionCheckUsesTheStoredKeyWhenNoneIsGiven() throws SecretsProblem {
        var needy = new FakeProvider(FakeProvider.info("needy", "Needy",
                List.of(new SettingSpec("api_key", "API key", "Your key.", false, true))), steps(days(2, 4.0)));
        var engine = engine(clockAt(7), needy);
        var needyId = new ProviderId("needy");

        assertThat(engine.checkConnection(needyId, exampleSystem(), Map.of()))
                .isEqualTo(new ConnectionResult(false, 0, "API key is required."));
        engine.saveApiKeys(new Secrets(Map.of(needyId, Map.of("api_key", "STORED-KEY"))));
        assertThat(engine.checkConnection(needyId, exampleSystem(), Map.of()).worked()).isTrue();
        assertThat(engine.checkConnection(needyId, exampleSystem(), Map.of("api_key", "TYPED-KEY")).worked()).isTrue();
        assertThat(needy.calls).extracting(call -> call.settings().get("api_key")).containsExactly("STORED-KEY", "TYPED-KEY");
    }

    @Test
    void placesAreSearchedWithTheProvidersOwnSearch() {
        var http = ReplayHttp.always(new Reply(200, Recorded.answer("open_meteo_geocoding_amsterdam.json")));
        var engine = engine(clockAt(7), new OpenMeteo(http), new ForecastSolar(http));

        assertThat(engine.searchPlaces(new ProviderId("open-meteo"), "Amsterdam", "en")).hasValueSatisfying(results ->
                assertThat(((PlaceResults.Found) results).places()).hasSize(8));
        assertThat(engine.searchPlaces(new ProviderId("forecast-solar"), "Amsterdam", "en")).isEmpty(); // then the owner types coordinates
        assertThat(engine.searchPlaces(new ProviderId("solcast"), "Amsterdam", "en")).isEmpty();
    }

    @Test
    void thePreviewPlansAConfigThatIsNotSavedWithTheKeysAsEnteredAndTellsNobody() throws Exception {
        var needy = new FakeProvider(FakeProvider.info("needy", "Needy",
                List.of(new SettingSpec("api_key", "API key", "Your key.", false, true))), steps(days(2, 4.0)));
        var engine = engine(clockAt(9, 50), needy);
        var needyId = new ProviderId("needy");
        var stored = Files.readString(config);
        var draft = EngineConfig.setUpDraft(BRUSSELS, needyId).withSystem(
                exampleSystem().withUnnamedLocation(new Coordinates(51, 5), BRUSSELS), TypicalValues.NONE);

        var preview = engine.preview(draft, new Secrets(Map.of(needyId, Map.of("api_key", "TYPED-KEY"))));

        assertThat(preview).isInstanceOfSatisfying(Outlook.Planned.class, planned -> {
            assertThat(planned.forecastSource().attribution()).isEqualTo("Forecast by Needy");
            assertThat(planned.minimumPct()).isEqualTo(PlanningPreferences.RECOMMENDED.minimumPct());
            assertThat(planned.nextWindow()).isPresent();
        });
        assertThat(needy.calls).extracting(call -> call.settings().get("api_key")).containsExactly("TYPED-KEY");
        assertThat(Files.readString(config)).isEqualTo(stored);
        assertThat(engine.storedApiKeys()).isEqualTo(Secrets.NONE);
        assertThat(told).isEmpty(); // the window starts at 10:00, within the lead time, yet nobody is told
    }

    @Test
    void theFirstPlanAfterSavingWhatThePreviewShowedAsksTheProviderNoMore() throws Exception {
        var engine = engine(clockAt(7));
        var draft = engine.config().withPlanning(PlanningPreferences.RECOMMENDED);
        engine.preview(draft, Secrets.NONE);

        engine.saveConfig(draft);

        assertThat(source(engine).freshness()).isEqualTo(Freshness.CACHED);
        assertThat(main.callCount()).isEqualTo(1);
    }

    @Test
    void aPreviewWithoutAForecastSaysWhy() throws IOException {
        choose("main", "");
        main.script(error(new ProviderError.Unavailable("no network")));
        var engine = engine(clockAt(7));

        assertThat(engine.preview(EngineConfig.setUpDraft(BRUSSELS, new ProviderId("main")), Secrets.NONE))
                .isEqualTo(new Outlook.NoForecast("Main: no network"));
    }

    @Test
    void aPreviewWithoutAForecastNamesTheProviderThatRefusedTheTypedKey() throws IOException {
        choose("main", "");
        main.script(error(new ProviderError.KeyRefused("Main refused the request (status 401): check the API key")));
        var engine = engine(clockAt(7));

        var preview = engine.preview(EngineConfig.setUpDraft(BRUSSELS, new ProviderId("main")),
                new Secrets(Map.of(new ProviderId("main"), Map.of("api_key", "TYPED-WRONG"))));

        assertThat(preview).isInstanceOfSatisfying(Outlook.NoForecast.class, none -> {
            assertThat(none.keyRefusedBy()).containsExactly(new ProviderId("main"));
            assertThat(none.keychainLocked()).isFalse();
        });
    }

    @Test
    void aFailingPreviewLeavesTheSavedSystemsStoredForecastToldAsCached() throws Exception {
        engine(clockAt(7)).outlook();
        main.script(error(new ProviderError.KeyRefused("Main refused the request (status 401): check the API key")));
        backup.script(error(new ProviderError.Unavailable("down")));
        var form = engine(clockAt(8, 30));
        form.preview(form.config(), new Secrets(Map.of(new ProviderId("main"), Map.of("api_key", "TYPED-WRONG"))));

        var outlook = engine(clockAt(9, 50)).round(ForecastUse.STORED_ONLY);

        assertThat(((Outlook.Planned) outlook).forecastSource().freshness()).isEqualTo(Freshness.CACHED);
        assertThat(told).hasSize(1);
    }

    @Test
    void theFirstRunConfigHasTypicalValuesListedAndNoPlaceYet() {
        var draft = EngineConfig.setUpDraft(BRUSSELS, new ProviderId("open-meteo"));

        assertThat(draft.system().location().isChosen()).isFalse();
        assertThat(draft.typical().system()).isEqualTo(TypicalValues.SYSTEM_SETTINGS);
        assertThat(draft.typical().ofPanel(0)).isEqualTo(TypicalValues.PANEL_SETTINGS);
        assertThat(draft.forecast().order()).containsExactly(new ProviderId("open-meteo"));
    }

    @Test
    void theOwnerCanBeToldWhetherAProviderSearchesPlaces() {
        var http = ReplayHttp.always(new Reply(200, Recorded.answer("open_meteo_geocoding_amsterdam.json")));
        var engine = engine(clockAt(7), new OpenMeteo(http), new ForecastSolar(http));

        assertThat(engine.hasPlaceSearch(new ProviderId("open-meteo"))).isTrue();
        assertThat(engine.hasPlaceSearch(new ProviderId("forecast-solar"))).isFalse();
        assertThat(engine.hasPlaceSearch(new ProviderId("solcast"))).isFalse();
    }

    @Test
    void theOwnerCanBeToldWhereTheKeysAreKept() throws SecretsProblem {
        assertThat(engine(clockAt(7)).keyStorage()).isEqualTo(KeyStorage.KEYCHAIN);

        var known = new Providers(List.of(main));
        var apiKeys = new ApiKeys(new NoKeychain(), new SecretsFile(folder.resolve("secrets.json"), FILES, CLOCK),
                KeyFallback.PRIVATE_FILE, Clock.systemUTC());
        var withoutKeychain = new Engine(new ConfigFile(config, FILES, CLOCK),
                new ForecastService(known, new ForecastCache(folder.resolve("cache"), FILES, CLOCK), apiKeys), known,
                apiKeys,
                new AwayFile(folder.resolve("away.json"), FILES, CLOCK),
                new TripFile(folder.resolve("trips.json"), FILES, CLOCK),
                new BatteryFile(folder.resolve("battery.json"), FILES, CLOCK), clockAt(7),
                announcer(), new ManualScheduler());
        withoutKeychain.saveApiKeys(new Secrets(Map.of(new ProviderId("main"), Map.of("api_key", "x"))));
        assertThat(withoutKeychain.keyStorage()).isEqualTo(KeyStorage.FILE);
    }

    @Test
    void whileTheKeychainThatHoldsTheKeysIsLockedTheOutlookSaysSoAndNoFileIsWritten() throws Exception {
        var needy = new FakeProvider(FakeProvider.info("needy", "Needy",
                List.of(new SettingSpec("api_key", "API key", "Your key.", false, true))), steps(days(2, 4.0)));
        backup.script(error(new ProviderError.Unavailable("down")));
        choose("needy", "backup");
        engine(clockAt(7), needy, backup).saveApiKeys(new Secrets(Map.of(new ProviderId("needy"),
                Map.of("api_key", "STORED-KEY"))));
        keychain.locked = true;

        var outlook = engine(clockAt(7), needy, backup).outlook(); // as after a login that left the keychain locked

        assertThat(outlook).isInstanceOfSatisfying(Outlook.NoForecast.class,
                none -> assertThat(none.keychainLocked()).isTrue());
        assertThat(needy.calls).isEmpty();
        assertThat(folder.resolve("secrets.json")).doesNotExist();
        assertThat(engine(clockAt(7), needy, backup).keyStorage()).isEqualTo(KeyStorage.LOCKED_KEYCHAIN);
    }

    @Test
    void eachRoundMovesKeysKeptInTheFileIntoTheKeychainOnceItIsUnlocked() throws IOException {
        var file = Files.writeString(folder.resolve("secrets.json"),
                "{\"version\": 1, \"main\": {\"api_key\": \"KEPT-IN-FILE\"}}");
        keychain.locked = true;
        var scheduler = startedAt(clockAt(7));
        assertThat(file).exists();

        keychain.locked = false; // by the owner, outside the app
        scheduler.advance(PlanSchedule.EVERY);

        assertThat(file).doesNotExist();
        assertThat(keychain.entries).containsEntry("forecast/main/api_key", "KEPT-IN-FILE");
    }

    @Test
    void anOutlookLeavesKeysKeptInTheFileWhereTheyAre() throws IOException {
        var file = Files.writeString(folder.resolve("secrets.json"),
                "{\"version\": 1, \"main\": {\"api_key\": \"KEPT-IN-FILE\"}}");

        engine(clockAt(7)).outlook();

        assertThat(file).exists();
        assertThat(keychain.entries).isEmpty();
    }

    @Test
    void aRoundOnTheStoredForecastOnlyLeavesKeysKeptInTheFileWhereTheyAre() throws IOException {
        var file = Files.writeString(folder.resolve("secrets.json"),
                "{\"version\": 1, \"main\": {\"api_key\": \"KEPT-IN-FILE\"}}");

        engine(clockAt(7)).round(ForecastUse.STORED_ONLY);

        assertThat(file).exists();
        assertThat(keychain.entries).isEmpty();
    }

    @Test
    void aMissingKeyIsNotALockedKeychain() throws Exception {
        var needy = new FakeProvider(FakeProvider.info("needy", "Needy",
                List.of(new SettingSpec("api_key", "API key", "Your key.", false, true))), steps(days(2, 4.0)));
        backup.script(error(new ProviderError.Unavailable("down")));
        choose("needy", "backup");

        assertThat(engine(clockAt(7), needy, backup).outlook()).isInstanceOfSatisfying(Outlook.NoForecast.class,
                none -> assertThat(none.keychainLocked()).isFalse());
    }

    @Test
    void savingKeysLetsAProviderThatFailedBeAskedAgainAtOnce() throws SecretsProblem {
        main.script(error(new ProviderError.Unavailable("down")), steps(days(2, 4.0)));
        var engine = engine(clockAt(7));
        assertThat(source(engine).providerRole()).isEqualTo(ProviderRole.FALLBACK);

        engine.saveApiKeys(Secrets.NONE);

        assertThat(source(engine).providerRole()).isEqualTo(ProviderRole.PRIMARY);
        assertThat(main.callCount()).isEqualTo(2);
    }

    // the notifications through the engine's rounds; the example's window starts at 10:00 and its lead time is 15
    // minutes

    @Test
    void theOwnerIsToldOnceAheadOfTheWindowWhatToSet() {
        startedAt(clockAt(9, 50));
        var announcement = told.get(0);
        assertThat(announcement.kind()).isEqualTo(AnnouncementKind.SOLAR_WINDOW);
        assertThat(announcement.window().start()).isEqualTo(DAY.atTime(10, 0).atZone(BRUSSELS).toInstant());
        assertThat(announcement.window().amps()).isEqualTo(13);

        startedAt(clockAt(9, 52)); // also after a restart: the announced windows are kept in a file

        assertThat(told).hasSize(1);
    }

    @Test
    void anOutlookAloneTellsNobody() {
        var outlook = engine(clockAt(9, 50)).outlook();

        assertThat(((Outlook.Planned) outlook).nextWindow()).hasValueSatisfying(window ->
                assertThat(window.start()).isEqualTo(DAY.atTime(10, 0).atZone(BRUSSELS).toInstant()));
        assertThat(told).isEmpty(); // within the lead time, yet only a round announces
    }

    @Test
    void theOwnerIsToldAheadOfTheGridAdvicesWindowToo() throws IOException {
        // without sun, 35 % falls below the minimum of 30 % that day: the grid charge starts at 07:00, the start of
        // the charging hours
        main.script(steps(days(3, 0)));
        var seven = DAY.plusDays(1).atTime(7, 0).atZone(BRUSSELS).toInstant();
        var scheduler = new ManualScheduler();
        var engine = engine(Clock.fixed(seven.minus(Duration.ofMinutes(10)), ZoneOffset.UTC), scheduler, main,
                backup);
        engine.setBatteryLevel(new Percent(35));

        start(engine, scheduler);

        assertThat(told).extracting(Announcement::kind, announcement -> announcement.window().start())
                .containsExactly(tuple(AnnouncementKind.GRID_CHARGE, seven));
    }

    @Test
    void theOwnerIsNotToldBeforeTheLeadTime() {
        startedAt(clockAt(9, 30));
        assertThat(rounds).singleElement().isInstanceOf(Outlook.Planned.class);
        assertThat(told).isEmpty();
    }

    @Test
    void theOwnerIsNotToldWhenTheForecastCouldNotBeRefreshed() {
        main.script(steps(days(2, 4.0)), error(new ProviderError.Unavailable("down")));
        backup.script(error(new ProviderError.Unavailable("down")));
        engine(clockAt(7)).outlook();

        startedAt(clockAt(9, 50));

        assertThat(((Outlook.Planned) rounds.get(0)).forecastSource().freshness()).isEqualTo(Freshness.STALE);
        assertThat(told).isEmpty();
    }

    @Test
    void aLongerLeadTimeInTheConfigFileTellsTheOwnerSooner() throws IOException {
        var scheduler = startedAt(clockAt(9, 30));
        assertThat(told).isEmpty();

        Files.writeString(config, Files.readString(config).replace("\"notifyLeadMinutes\": 15", "\"notifyLeadMinutes\": 30"));
        scheduler.advance(PlanSchedule.EVERY);

        assertThat(told).hasSize(1);
    }

    /** Tomorrow from 09:00 to 17:00, longer than a full battery reaches. */
    private static Trip longTripTomorrow(boolean notWithThisCar) {
        return new Trip(DAY.plusDays(1).atTime(9, 0).atZone(BRUSSELS).toInstant(),
                DAY.plusDays(1).atTime(17, 0).atZone(BRUSSELS).toInstant(), 300, false, notWithThisCar);
    }

    @Test
    void aRoundAsksWhetherTheAssumedLevelIsRightForATripThatDependsOnIt() throws IOException {
        var trip = longTripTomorrow(false);
        engine(clockAt(8)).addTrip(trip);

        startedAt(clockAt(8));

        assertThat(toldAboutTrips).contains(new BatteryCheck(trip, new Percent(50), BRUSSELS));
    }

    @Test
    void aLevelTheOwnerJustEnteredIsNotQuestioned() throws IOException {
        var engine = engine(clockAt(8));
        engine.addTrip(longTripTomorrow(false));
        engine.setBatteryLevel(new Percent(50));

        startedAt(clockAt(8));

        assertThat(toldAboutTrips).noneMatch(BatteryCheck.class::isInstance);
    }

    @Test
    void aTripNotWithThisCarIsNotToldAbout() throws IOException {
        engine(clockAt(8)).addTrip(longTripTomorrow(true));

        startedAt(clockAt(8));
        startedAt(clockAt(19));

        assertThat(toldAboutTrips).isEmpty();
    }

    @Test
    void anAnnouncedWindowIsPlannedAsItWasAnnouncedWhileItRuns() throws IOException {
        startedAt(clockAt(9, 50));
        Files.writeString(config, Files.readString(config).replace("\"margin\": 0.85", "\"margin\": 0.5"));

        var window = planned(engine(clockAt(11))).nextWindow().orElseThrow();

        assertThat(window).extracting(w -> w.start(), w -> w.amps()).containsExactly(told.get(0).window().start(), 13);
    }

    private static nl.enadeag.carchargeplanner.engine.config.HomeSystem exampleSystem() {
        try {
            return new ConfigFile(EXAMPLE, FILES, CLOCK).read().system();
        } catch (ConfigProblem e) {
            throw new AssertionError(e);
        }
    }

    @Test
    void aConfigThatCannotBeUsedIsLoggedOnceAndSoIsOneThatCanBeUsedAgain() throws IOException {
        var valid = Files.readString(config);
        var engine = engine(clockAt(9));
        try (var log = LogCapture.of(ConfigHealth.class)) {
            engine.outlook();
            Files.delete(config);
            engine.outlook();
            Files.writeString(config, "{\"version\": 1}");
            engine.outlook();
            engine.outlook();
            Files.writeString(config, valid);
            engine.outlook();
            engine.outlook();

            assertThat(log.lines()).satisfiesExactly(
                    line -> assertThat(line).isEqualTo("INFO there is no config file at " + config),
                    line -> assertThat(line).startsWith("WARNING the config file " + config
                            + " cannot be used (INVALID): ").hasSizeGreaterThan(70),
                    line -> assertThat(line).isEqualTo("INFO the config file can be used again"));
        }
    }


    @Test
    void savingTheKeysDoesNotWaitForAForecastThatIsBeingFetched() throws Exception {
        var asked = new CountDownLatch(1);
        var answer = new CountDownLatch(1);
        main.script(() -> {
            asked.countDown();
            try {
                answer.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException ignored) {
                throw new ProviderError.Unavailable("interrupted");
            }
            return days(2, 4.0);
        });
        var engine = engine(clockAt(9));
        var planning = CompletableFuture.supplyAsync(engine::outlook);
        assertThat(asked.await(10, TimeUnit.SECONDS)).as("the provider is being asked").isTrue();

        var keys = new Secrets(Map.of(new ProviderId("main"), Map.of("api_key", "NEW-KEY")));
        assertTimeoutPreemptively(Duration.ofSeconds(5), () -> engine.saveApiKeys(keys));

        answer.countDown();
        assertThat(planning.get(10, TimeUnit.SECONDS)).isInstanceOf(Outlook.Planned.class);
        assertThat(engine.storedApiKeys()).isEqualTo(keys);
    }

    @Test
    void savingTheConfigLetsAProviderThatFailedBeAskedAgainAtOnce() throws ConfigProblem, IOException {
        main.script(error(new ProviderError.Unavailable("down")), steps(days(2, 4.0)));
        var engine = engine(clockAt(7));
        assertThat(source(engine).providerRole()).isEqualTo(ProviderRole.FALLBACK);

        engine.saveConfig(engine.config());

        assertThat(source(engine).providerRole()).isEqualTo(ProviderRole.PRIMARY);
        assertThat(main.callCount()).isEqualTo(2);
    }

    @Test
    void savingKeyChangesStoresThemAndLetsAProviderThatFailedBeAskedAgainAtOnce() throws SecretsProblem {
        main.script(error(new ProviderError.Unavailable("down")), steps(days(2, 4.0)));
        var engine = engine(clockAt(7));
        assertThat(source(engine).providerRole()).isEqualTo(ProviderRole.FALLBACK);
        var keys = new Secrets(Map.of(new ProviderId("main"), Map.of("api_key", "NEW-KEY")));

        engine.applyKeyChanges(KeyChanges.entering(keys), () -> { });

        assertThat(engine.storedApiKeys()).isEqualTo(keys);
        assertThat(source(engine).providerRole()).isEqualTo(ProviderRole.PRIMARY);
        assertThat(main.callCount()).isEqualTo(2);
    }

    @Test
    void savingTheApiKeysStoresThemAtOnce() throws Exception {
        var engine = engine(clockAt(7));
        var keys = new Secrets(Map.of(new ProviderId("main"), Map.of("api_key", "NEW-KEY")));

        assertTimeoutPreemptively(Duration.ofSeconds(2), () -> engine.saveApiKeys(keys));

        assertThat(keychain.entries).containsEntry("forecast/main/api_key", "NEW-KEY");
    }

    @Test
    void stoppingTheUnlockingTellsTheKeychain() {
        var engine = engine(clockAt(7));

        assertTimeoutPreemptively(Duration.ofSeconds(2), engine::stopUnlocking);

        assertThat(keychain.stops).hasValue(1);
    }

    @Test
    void aSaveThatWaitsForTheKeychainToBeUnlockedStopsWhenAsked() throws Exception {
        var engine = engine(clockAt(7));
        engine.saveApiKeys(new Secrets(Map.of(new ProviderId("main"), Map.of("api_key", "OLD-KEY"))));
        keychain.locked = true;
        keychain.answer = null;
        var saving = CompletableFuture.runAsync(() -> {
            try {
                engine.saveApiKeys(new Secrets(Map.of(new ProviderId("main"), Map.of("api_key", "NEW-KEY"))));
            } catch (SecretsProblem e) {
                throw new CompletionException(e);
            }
        });
        assertThat(keychain.prompting.await(10, TimeUnit.SECONDS)).as("the prompt shows").isTrue();

        engine.stopUnlocking();

        assertThatThrownBy(() -> saving.get(10, TimeUnit.SECONDS)).hasCauseInstanceOf(SecretsProblem.class);
        assertThat(keychain.entries).doesNotContainValue("NEW-KEY").containsValue("OLD-KEY");
    }

    @Test
    void theConnectionCheckWorksWithoutTheStoredKeysWhileTheKeychainIsLocked() throws SecretsProblem {
        var needy = new FakeProvider(FakeProvider.info("needy", "Needy",
                List.of(new SettingSpec("api_key", "API key", "Your key.", false, true))), steps(days(2, 4.0)));
        var engine = engine(clockAt(7), needy);
        var needyId = new ProviderId("needy");
        engine.saveApiKeys(new Secrets(Map.of(needyId, Map.of("api_key", "STORED-KEY"))));
        keychain.locked = true;

        assertThat(engine(clockAt(7), needy).checkConnection(needyId, exampleSystem(), Map.of()))
                .isEqualTo(new ConnectionResult(false, 0, "API key is required."));
        assertThat(needy.calls).isEmpty();
    }

    @Test
    void removingAnAwayPeriodGivesTheOnesThatAreLeft() throws IOException {
        var engine = engine(clockAt(7));
        var morning = new OneOffAwayPeriod(DAY.atTime(8, 0).atZone(BRUSSELS).toInstant(),
                DAY.atTime(9, 0).atZone(BRUSSELS).toInstant());
        var evening = new OneOffAwayPeriod(DAY.atTime(18, 0).atZone(BRUSSELS).toInstant(),
                DAY.atTime(20, 0).atZone(BRUSSELS).toInstant());
        engine.addAwayPeriod(evening);
        engine.addAwayPeriod(morning);

        assertThat(engine.removeAwayPeriod(morning)).containsExactly(evening);
        assertThat(engine.awayPeriods()).containsExactly(evening);
    }

    @Test
    void aTripIsStoredAndTheNextPlanKeepsItsTimeFree() throws IOException {
        var engine = engine(clockAt(7));
        var atHome = planned(engine).days();
        var trip = new Trip(DAY.atTime(9, 0).atZone(BRUSSELS).toInstant(),
                DAY.plusDays(1).atTime(17, 0).atZone(BRUSSELS).toInstant(), 300, false);

        assertThat(engine.addTrip(trip)).containsExactly(trip);
        assertThat(engine(clockAt(7)).trips()).containsExactly(trip);
        var away = planned(engine).days(); // the sun shines from 10:00 to 16:00
        assertThat(away.get(0).windows()).isEmpty();
        assertThat(away.get(1).windows()).isEmpty();

        assertThat(engine.removeTrip(trip)).isEmpty();
        assertThat(planned(engine).days()).isEqualTo(atHome);
    }

    @Test
    void thePlanSaysWhatItDoesForATrip() throws IOException {
        var engine = engine(clockAt(7));
        var trip = new Trip(DAY.plusDays(1).atTime(9, 0).atZone(BRUSSELS).toInstant(),
                DAY.plusDays(1).atTime(17, 0).atZone(BRUSSELS).toInstant(), 100, false);
        engine.addTrip(trip);

        var advice = planned(engine).tripAdvice();
        assertThat(advice).extracting(TripAdvice::trip).containsExactly(trip);
        // 30 % + 100 km at 0.207 kWh of the 60 kWh battery + the day's 40 km at 0.18 kWh
        assertThat(advice.get(0).neededPct().value()).isCloseTo(30 + 34.5 + 12, within(1e-9));
    }

    @Test
    void aTripNotWithThisCarIsKeptButLeavesThePlanAsAtHome() throws IOException {
        var engine = engine(clockAt(7));
        var atHome = planned(engine);
        var trip = new Trip(DAY.atTime(9, 0).atZone(BRUSSELS).toInstant(),
                DAY.plusDays(1).atTime(17, 0).atZone(BRUSSELS).toInstant(), 300, false, true);

        assertThat(engine.addTrip(trip)).containsExactly(trip);

        var planned = planned(engine);
        assertThat(planned.days()).isEqualTo(atHome.days());
        assertThat(planned.tripAdvice()).isEmpty();
    }

    @Test
    void aChangedTripTakesThePlaceOfTheOldOne() throws IOException {
        var engine = engine(clockAt(7));
        var atHome = planned(engine).days();
        var trip = new Trip(DAY.atTime(9, 0).atZone(BRUSSELS).toInstant(),
                DAY.plusDays(1).atTime(17, 0).atZone(BRUSSELS).toInstant(), 300, false);
        var evening = new Trip(DAY.atTime(18, 0).atZone(BRUSSELS).toInstant(),
                DAY.atTime(22, 0).atZone(BRUSSELS).toInstant(), 40, true);
        engine.addTrip(trip);

        assertThat(engine.changeTrip(trip, evening)).containsExactly(evening);
        assertThat(engine.trips()).containsExactly(evening);
        // the evening leaves the sunny hours free
        assertThat(planned(engine).days().get(0).windows()).isEqualTo(atHome.get(0).windows());
    }

    @Test
    void removingATripGivesTheOnesThatAreLeft() throws IOException {
        var engine = engine(clockAt(7));
        var morning = new Trip(DAY.atTime(8, 0).atZone(BRUSSELS).toInstant(),
                DAY.atTime(9, 0).atZone(BRUSSELS).toInstant(), 30, false);
        var evening = new Trip(DAY.atTime(18, 0).atZone(BRUSSELS).toInstant(),
                DAY.atTime(20, 0).atZone(BRUSSELS).toInstant(), 60, false);
        engine.addTrip(evening);
        engine.addTrip(morning);

        assertThat(engine.removeTrip(morning)).containsExactly(evening);
        assertThat(engine.trips()).containsExactly(evening);
    }

    @Test
    void aTripIsForgottenADayAfterItsReturn() throws IOException {
        var trip = new Trip(DAY.atTime(8, 0).atZone(BRUSSELS).toInstant(),
                DAY.atTime(9, 0).atZone(BRUSSELS).toInstant(), 30, false);
        engine(clockAt(7)).addTrip(trip);

        assertThat(engine(clockAt(7)).trips()).containsExactly(trip);
        assertThat(engine(Clock.offset(clockAt(9), Duration.ofDays(1))).trips()).isEmpty();
    }

    @Test
    void theProvidersAreOfferedInTheirOrder() {
        assertThat(engine(clockAt(7)).providers()).containsExactly(main.info(), backup.info());
    }

    // one round in public, as a client that plans at moments of its own calls it

    @Test
    void aRoundGivesThePlanAndTellsWhatIsDueOnce() {
        var engine = engine(clockAt(9, 50));

        var outlook = engine.round(ForecastUse.ASK_PROVIDERS);

        assertThat(outlook).isInstanceOf(Outlook.Planned.class);
        assertThat(told).extracting(announcement -> announcement.window().start())
                .containsExactly(DAY.atTime(10, 0).atZone(BRUSSELS).toInstant());
        engine.round(ForecastUse.ASK_PROVIDERS);
        assertThat(told).hasSize(1);
    }

    @Test
    void aRoundNeedsToKnowWhereItsForecastMayComeFrom() {
        assertThatThrownBy(() -> engine(clockAt(7)).round(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aRoundOnTheStoredForecastOnlyAsksNoProviderYetTellsWhatIsDue() {
        engine(clockAt(7)).outlook();

        var outlook = engine(clockAt(9, 50)).round(ForecastUse.STORED_ONLY);

        assertThat(((Outlook.Planned) outlook).forecastSource())
                .extracting(ForecastSource::fetchedAt, ForecastSource::freshness)
                .containsExactly(clockAt(7).instant(), Freshness.CACHED);
        assertThat(told).hasSize(1);
        assertThat(main.callCount()).isEqualTo(1); // the refresh was due at 08:00, yet nobody is asked
    }

    @Test
    void withoutAStoredForecastARoundOnTheStoredForecastOnlyHasNoForecastAndAsksNobody() {
        var outlook = engine(clockAt(9, 50)).round(ForecastUse.STORED_ONLY);

        assertThat(outlook).isInstanceOf(Outlook.NoForecast.class);
        assertThat(main.callCount() + backup.callCount()).isZero();
        assertThat(told).isEmpty();
    }

    /** Fetched at 03:49 or 03:50, the forecast is just over or exactly 6 hours old at 09:50. */
    @ParameterizedTest
    @CsvSource({"50, true", "49, false"})
    void aRoundOnAStoredForecastTellsNothingOnceItIsMoreThanSixHoursOld(int fetchedMinute, boolean toldAny)
            throws IOException {
        // useful for 12 hours, so a forecast more than 6 hours old still makes a plan
        var lasting = new FakeProvider(new ProviderInfo(new ProviderId("lasting"), "Lasting", "A scripted provider.",
                "https://example.test/terms", "Forecast by Lasting", "Nothing.", Duration.ofHours(1),
                Duration.ofDays(2), Duration.ofHours(12), List.of()), steps(days(2, 4.0)));
        choose("lasting", "");
        engine(clockAt(3, fetchedMinute), lasting).outlook();

        var outlook = engine(clockAt(9, 50), lasting).round(ForecastUse.STORED_ONLY);

        assertThat(outlook).isInstanceOf(Outlook.Planned.class);
        assertThat(told.isEmpty()).isEqualTo(!toldAny);
    }

    @Test
    void anOutlookWithoutAForecastNamesTheProviderThatRefusedItsKey() {
        main.script(error(new ProviderError.KeyRefused("Main refused the request (status 401): check the API key")));
        backup.script(error(new ProviderError.Unavailable("down")));

        var outlook = engine(clockAt(7)).round(ForecastUse.ASK_PROVIDERS);

        assertThat(outlook).isInstanceOfSatisfying(Outlook.NoForecast.class, none -> {
            assertThat(none.keyRefusedBy()).containsExactly(new ProviderId("main"));
            assertThat(none.keychainLocked()).isFalse();
        });
        assertThat(engine(clockAt(7)).outlook()).isInstanceOfSatisfying(Outlook.NoForecast.class,
                none -> assertThat(none.keyRefusedBy()).containsExactly(new ProviderId("main")));
    }

    @Test
    void anOutlookWithoutAForecastForAnotherReasonNamesNoRefusedKey() {
        main.script(error(new ProviderError.Unavailable("down")));
        backup.script(error(new ProviderError.BadResponse("odd")));

        assertThat(engine(clockAt(7)).round(ForecastUse.ASK_PROVIDERS)).isInstanceOfSatisfying(
                Outlook.NoForecast.class, none -> assertThat(none.keyRefusedBy()).isEmpty());
    }

    @Test
    void aPlanFromTheFallbackNamesThePrimaryThatRefusedItsKey() {
        main.script(error(new ProviderError.KeyRefused("Main refused the request (status 401): check the API key")));

        var outlook = engine(clockAt(7)).round(ForecastUse.ASK_PROVIDERS);

        assertThat(((Outlook.Planned) outlook).forecastSource())
                .extracting(ForecastSource::providerRole, ForecastSource::keyRefusedBy)
                .containsExactly(ProviderRole.FALLBACK, List.of(new ProviderId("main")));
    }

    @Test
    void aFailedRefreshInOneEngineMakesAStoredOnlyRoundOfAFreshEngineStale() {
        engine(clockAt(7)).outlook();
        main.script(error(new ProviderError.Unavailable("down")));
        backup.script(error(new ProviderError.Unavailable("down")));
        engine(clockAt(8, 30)).round(ForecastUse.ASK_PROVIDERS);

        var engine = engine(clockAt(9, 50));
        var outlook = engine.round(ForecastUse.STORED_ONLY);

        assertThat(((Outlook.Planned) outlook).forecastSource().freshness()).isEqualTo(Freshness.STALE);
        assertThat(told).isEmpty();
        assertThat(engine.nextNoticeDue(outlook)).isEmpty();
    }

    @Test
    void aRefreshThatSucceedsAfterAFailedOneLetsAStoredOnlyRoundTellAgain() {
        engine(clockAt(7)).outlook();
        main.script(error(new ProviderError.Unavailable("down")), steps(days(2, 4.0)));
        engine(clockAt(8, 30)).round(ForecastUse.ASK_PROVIDERS);
        engine(clockAt(9, 40)).round(ForecastUse.ASK_PROVIDERS);

        var outlook = engine(clockAt(9, 50)).round(ForecastUse.STORED_ONLY);

        assertThat(((Outlook.Planned) outlook).forecastSource())
                .extracting(ForecastSource::fetchedAt, ForecastSource::freshness)
                .containsExactly(clockAt(9, 40).instant(), Freshness.CACHED);
        assertThat(told).hasSize(1);
    }

    // when the next notice is due, for a client that wakes the device for it

    @Test
    void afterARoundTheNextNoticeIsDueAtTheWindowsStartLessTheLeadTime() {
        var engine = engine(clockAt(7));

        var due = engine.nextNoticeDue(engine.round(ForecastUse.ASK_PROVIDERS));

        assertThat(due).contains(DAY.atTime(9, 45).atZone(BRUSSELS).toInstant());
        assertThat(told).isEmpty();
    }

    @Test
    void aLongerLeadTimeInTheConfigFileMakesTheNextNoticeDueSooner() throws IOException {
        Files.writeString(config,
                Files.readString(config).replace("\"notifyLeadMinutes\": 15", "\"notifyLeadMinutes\": 30"));
        var engine = engine(clockAt(7));

        assertThat(engine.nextNoticeDue(engine.round(ForecastUse.ASK_PROVIDERS)))
                .contains(DAY.atTime(9, 30).atZone(BRUSSELS).toInstant());
    }

    @Test
    void onceTodaysWindowIsAnnouncedTomorrowsWouldBeOnAForecastTooOldSoNothingIsDue() {
        var engine = engine(clockAt(9, 50));

        var due = engine.nextNoticeDue(engine.round(ForecastUse.ASK_PROVIDERS));

        assertThat(told).hasSize(1);
        assertThat(due).isEmpty(); // tomorrow's 09:45 is long after 15:50, when this forecast is 6 hours old
    }

    @Test
    void theGridAdvicesWindowIsDueToo() throws IOException {
        // without sun, 35 % falls below the minimum of 30 %: the grid charge starts at 07:00 tomorrow
        main.script(steps(days(3, 0)));
        var six = DAY.plusDays(1).atTime(6, 0).atZone(BRUSSELS).toInstant();
        var engine = engine(Clock.fixed(six, ZoneOffset.UTC), main, backup);
        engine.setBatteryLevel(new Percent(35));

        assertThat(engine.nextNoticeDue(engine.round(ForecastUse.ASK_PROVIDERS)))
                .contains(DAY.plusDays(1).atTime(6, 45).atZone(BRUSSELS).toInstant());
    }

    /** A trip today from 13:00 to 14:00, with the battery at 50 % from 06:00. */
    private Engine withATripToday(int km) throws IOException {
        var engine = engine(clockAt(6));
        engine.addTrip(new Trip(DAY.atTime(13, 0).atZone(BRUSSELS).toInstant(),
                DAY.atTime(14, 0).atZone(BRUSSELS).toInstant(), km, false));
        engine.setBatteryLevel(new Percent(50));
        return engine;
    }

    @Test
    void aTripsGridChargeStartIsDueBeforeALaterWindow() throws IOException {
        var engine = withATripToday(100);

        var planned = (Outlook.Planned) engine.round(ForecastUse.ASK_PROVIDERS);

        assertThat(planned.gridAdvice()).isEmpty();
        assertThat(planned.nextWindow()).hasValueSatisfying(window ->
                assertThat(window.start()).isEqualTo(DAY.atTime(10, 0).atZone(BRUSSELS).toInstant()));
        var start = planned.tripAdvice().get(0).grid().orElseThrow().window().start();
        assertThat(start).isEqualTo(DAY.atTime(8, 0).atZone(BRUSSELS).toInstant());
        assertThat(engine.nextNoticeDue(planned)).contains(start.minus(Duration.ofMinutes(15)));
    }

    @Test
    void aTripsGridChargeStartIsDueBeforeTheGridAdvicesWindow() throws IOException {
        main.script(steps(days(3, 0)));
        var engine = withATripToday(60);

        var planned = (Outlook.Planned) engine.round(ForecastUse.ASK_PROVIDERS);

        assertThat(planned.gridAdvice().flatMap(GridAdvice::window)).hasValueSatisfying(window ->
                assertThat(window.start()).isEqualTo(DAY.plusDays(1).atTime(7, 0).atZone(BRUSSELS).toInstant()));
        var start = planned.tripAdvice().get(0).grid().orElseThrow().window().start();
        assertThat(start).isEqualTo(DAY.atTime(10, 0).atZone(BRUSSELS).toInstant());
        assertThat(engine.nextNoticeDue(planned)).contains(start.minus(Duration.ofMinutes(15)));
    }

    @Test
    void nothingIsDueWithoutAPlan() throws IOException {
        Files.delete(config);
        var engine = engine(clockAt(7));

        assertThat(engine.nextNoticeDue(engine.round(ForecastUse.ASK_PROVIDERS))).isEmpty();
    }

    @Test
    void nothingIsDueOnAForecastThatCouldNotBeRefreshed() {
        main.script(steps(days(2, 4.0)), error(new ProviderError.Unavailable("down")));
        backup.script(error(new ProviderError.Unavailable("down")));
        engine(clockAt(7)).outlook();
        var engine = engine(clockAt(9));

        var outlook = engine.round(ForecastUse.ASK_PROVIDERS);

        assertThat(((Outlook.Planned) outlook).forecastSource().freshness()).isEqualTo(Freshness.STALE);
        assertThat(engine.nextNoticeDue(outlook)).isEmpty();
    }

    @Test
    void nothingIsDueWhenTheConfigFileCannotBeUsedAnyMore() throws IOException {
        var engine = engine(clockAt(7));
        var outlook = engine.round(ForecastUse.ASK_PROVIDERS);
        Files.writeString(config, "{");

        assertThat(engine.nextNoticeDue(outlook)).isEmpty();
    }
}
