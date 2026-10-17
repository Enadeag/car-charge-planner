package io.github.enadeag.carchargeplanner.engine;

import static io.github.enadeag.carchargeplanner.engine.forecast.FakeProvider.error;
import static io.github.enadeag.carchargeplanner.engine.forecast.FakeProvider.steps;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import io.github.enadeag.carchargeplanner.LogCapture;
import io.github.enadeag.carchargeplanner.engine.away.AwayFile;
import io.github.enadeag.carchargeplanner.engine.battery.BatteryFile;
import io.github.enadeag.carchargeplanner.engine.away.AwayPeriod;
import io.github.enadeag.carchargeplanner.engine.config.ConfigFile;
import io.github.enadeag.carchargeplanner.engine.config.ConfigProblem;
import io.github.enadeag.carchargeplanner.engine.config.Coordinates;
import io.github.enadeag.carchargeplanner.engine.config.EngineConfig;
import io.github.enadeag.carchargeplanner.engine.config.Percent;
import io.github.enadeag.carchargeplanner.engine.config.PlanningPreferences;
import io.github.enadeag.carchargeplanner.engine.config.ProviderId;
import io.github.enadeag.carchargeplanner.engine.config.TypicalValues;
import io.github.enadeag.carchargeplanner.engine.forecast.ConnectionResult;
import io.github.enadeag.carchargeplanner.engine.forecast.FakeProvider;
import io.github.enadeag.carchargeplanner.engine.forecast.ForecastCache;
import io.github.enadeag.carchargeplanner.engine.forecast.ForecastProvider;
import io.github.enadeag.carchargeplanner.engine.forecast.ForecastService;
import io.github.enadeag.carchargeplanner.engine.forecast.ForecastSource;
import io.github.enadeag.carchargeplanner.engine.forecast.ForecastStep;
import io.github.enadeag.carchargeplanner.engine.forecast.Freshness;
import io.github.enadeag.carchargeplanner.engine.forecast.HttpGet.Reply;
import io.github.enadeag.carchargeplanner.engine.forecast.PlaceResults;
import io.github.enadeag.carchargeplanner.engine.forecast.ProviderError;
import io.github.enadeag.carchargeplanner.engine.forecast.ProviderRole;
import io.github.enadeag.carchargeplanner.engine.forecast.Providers;
import io.github.enadeag.carchargeplanner.engine.forecast.Recorded;
import io.github.enadeag.carchargeplanner.engine.forecast.ReplayHttp;
import io.github.enadeag.carchargeplanner.engine.forecast.SettingSpec;
import io.github.enadeag.carchargeplanner.engine.forecast.providers.ForecastSolar;
import io.github.enadeag.carchargeplanner.engine.forecast.providers.OpenMeteo;
import io.github.enadeag.carchargeplanner.engine.notify.AnnouncedWindows;
import io.github.enadeag.carchargeplanner.engine.notify.Announcement;
import io.github.enadeag.carchargeplanner.engine.notify.AnnouncementKind;
import io.github.enadeag.carchargeplanner.engine.notify.Announcer;
import io.github.enadeag.carchargeplanner.engine.secrets.ApiKeys;
import io.github.enadeag.carchargeplanner.engine.secrets.KeyStorage;
import io.github.enadeag.carchargeplanner.engine.secrets.LockableKeychain;
import io.github.enadeag.carchargeplanner.engine.secrets.NoKeychain;
import io.github.enadeag.carchargeplanner.engine.secrets.Secrets;
import io.github.enadeag.carchargeplanner.engine.secrets.SecretsFile;
import io.github.enadeag.carchargeplanner.engine.secrets.SecretsProblem;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EngineTest {

    private static final Path EXAMPLE = Path.of("docs", "example-engine.json");
    private static final ZoneId BRUSSELS = ZoneId.of("Europe/Brussels");
    private static final LocalDate DAY = LocalDate.of(2026, 7, 3);

    @TempDir
    Path folder;

    private final LockableKeychain keychain = new LockableKeychain();
    private final List<Announcement> told = new ArrayList<>();
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
        var known = new Providers(List.of(providers));
        var apiKeys = new ApiKeys(keychain, new SecretsFile(folder.resolve("secrets.json")));
        var service = new ForecastService(known, new ForecastCache(folder.resolve("cache")), apiKeys);
        return new Engine(new ConfigFile(config), service, known, apiKeys, new AwayFile(folder.resolve("away.json")),
                new BatteryFile(folder.resolve("battery.json")), clock, announcer(), new ManualScheduler());
    }

    /** Kept in the test's folder, so a new engine is like the app after a restart. */
    private Announcer announcer() {
        return new Announcer(new AnnouncedWindows(folder.resolve("announced.json")), told::add);
    }

    private Engine engine(Clock clock) {
        return engine(clock, main, backup);
    }

    private static Outlook.Planned planned(Engine engine) {
        return (Outlook.Planned) engine.plan();
    }

    private static ForecastSource source(Engine engine) {
        return planned(engine).forecast();
    }

    @Test
    void theExampleChoosesOpenMeteoWithForecastSolarAsTheFallback() throws ConfigProblem {
        var forecast = new ConfigFile(EXAMPLE).read().forecast();
        assertThat(forecast.order()).containsExactly(new ProviderId("open-meteo"), new ProviderId("forecast-solar"));
    }

    @Test
    void plansFromTheForecastForTheSystemInTheConfigFile() {
        var planned = planned(engine(clockAt(7)));

        assertThat(planned.zone()).isEqualTo(BRUSSELS);
        assertThat(planned.days()).extracting(day -> day.date()).containsExactly(DAY, DAY.plusDays(1));
        assertThat(planned.batteryPct()).isEqualTo(Engine.ASSUMED_BATTERY_PCT);
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
        var changed = stored.withPlanning(new PlanningPreferences(new Percent(40), new Percent(90), 0.85, 0.2, 45, 15));

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

        assertThat(planned.batteryPct()).isEqualTo(new Percent(72));
        assertThat(planned.batteryAssumed()).isFalse();
        assertThat(planned.days().getFirst().socStartPct()).isEqualTo(new Percent(72));
    }

    @Test
    void untilALevelIsEnteredThePlanSaysItsLevelIsAssumed() {
        var planned = planned(engine(clockAt(7)));

        assertThat(planned.batteryAssumed()).isTrue();
        assertThat(planned.days().getFirst().socStartPct()).isEqualTo(Engine.ASSUMED_BATTERY_PCT);
    }

    @Test
    void thePlanCarriesWhatTheChartsDraw() {
        var planned = planned(engine(clockAt(7)));

        // the example's house, minimum and target
        assertThat(planned.houseKw()).isEqualTo(0.5);
        assertThat(planned.minimumPct()).isEqualTo(new Percent(30));
        assertThat(planned.targetPct()).isEqualTo(new Percent(80));
        var today = planned.days().getFirst().forecast();
        assertThat(today).hasSize(24);
        assertThat(today.getFirst().start()).isEqualTo(DAY.atStartOfDay(BRUSSELS).toInstant());
        assertThat(today).filteredOn(step -> step.powerKw() > 0).hasSize(6)
                .allSatisfy(step -> assertThat(step.powerKw()).isEqualTo(4.0));
        assertThat(planned.days().get(1).forecast().getFirst().start())
                .isEqualTo(DAY.plusDays(1).atStartOfDay(BRUSSELS).toInstant());
    }

    @Test
    void anAwayPeriodIsStoredAndTheNextPlanKeepsItFree() throws IOException {
        var engine = engine(clockAt(7));
        var atHome = planned(engine).days();
        var away = new AwayPeriod(DAY.atTime(8, 0).atZone(BRUSSELS).toInstant(),
                DAY.atTime(18, 0).atZone(BRUSSELS).toInstant());

        assertThat(engine.addAwayPeriod(away)).containsExactly(away);
        assertThat(engine(clockAt(7)).awayPeriods()).containsExactly(away);
        assertThat(planned(engine).days().getFirst().windows()).isEmpty(); // the sun shines from 10:00 to 16:00

        assertThat(engine.removeAwayPeriod(away)).isEmpty();
        assertThat(planned(engine).days()).isEqualTo(atHome);
    }

    @Test
    void theWeeklyAwayPeriodsInTheConfigFileKeepThatTimeFree() throws IOException {
        var friday = DAY.getDayOfWeek().name();
        Files.writeString(config, Files.readString(config).replaceFirst("}$",
                ", \"away\": {\"recurring\": [{\"weekday\": \"" + friday + "\", \"start\": \"08:00\", \"end\": \"18:00\"}]}}"));

        var days = planned(engine(clockAt(7))).days();
        assertThat(days.getFirst().windows()).isEmpty();
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
        assertThat(advice.socEndPct()).isCloseTo(17.5, within(1e-9));
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
        assertThat(engine(clockAt(7)).plan()).isInstanceOfSatisfying(Outlook.NotSetUp.class,
                notSetUp -> assertThat(notSetUp.file()).isEqualTo(config));
        assertThat(main.callCount()).isZero(); // nothing leaves the computer before there is a location
    }

    @Test
    void whenNothingAnswersTheReasonsReachTheOutlook() throws IOException {
        choose("main", "");
        main.script(error(new ProviderError.Unavailable("no network")));

        assertThat(engine(clockAt(7)).plan()).isEqualTo(new Outlook.NoForecast("Main: no network"));
    }

    @Test
    void switchingTheProviderUsesTheNewOnesForecastAtTheNextPlan() throws IOException {
        var engine = engine(clockAt(7));
        engine.plan();
        choose("backup", "");

        assertThat(source(engine).attribution()).isEqualTo("Forecast by Backup");
        assertThat(backup.callCount()).isEqualTo(1);
    }

    @Test
    void aPlanningChangeKeepsTheForecastAndAsksNobody() throws IOException {
        var engine = engine(clockAt(7));
        engine.plan();
        Files.writeString(config, Files.readString(config).replace("\"margin\": 0.85", "\"margin\": 0.5"));

        assertThat(source(engine).freshness()).isEqualTo(Freshness.CACHED);
        assertThat(main.callCount()).isEqualTo(1);
    }

    @Test
    void duringACoolDownTheOutlookSaysUntilWhenTheProviderIsBusyAndAfterItDoesNot() {
        main.script(steps(days(2, 4.0)), error(new ProviderError.RateLimited("busy", Duration.ofHours(1))),
                steps(days(2, 4.0)));
        engine(clockAt(7)).plan();

        var busy = source(engine(clockAt(9))); // due again; the provider says wait
        assertThat(busy.freshness()).isEqualTo(Freshness.STALE);
        assertThat(busy.cooldownUntil()).contains(DAY.atTime(10, 0).atZone(BRUSSELS).toInstant());

        var after = source(engine(clockAt(11)));
        assertThat(after.cooldownUntil()).isEmpty();
        assertThat(main.callCount()).isEqualTo(3);
    }

    @Test
    void aPlanWithoutWindowsHasNoNextWindow() {
        var madeAt = Instant.parse("2026-07-03T05:00:00Z");
        var source = new ForecastSource(new ProviderId("main"), "Main", "Forecast by Main", madeAt, Freshness.FRESH, ProviderRole.PRIMARY,
                "", Optional.empty());
        var planned = new Outlook.Planned(madeAt, BRUSSELS, List.of(), new Percent(50), true, Optional.empty(), source, 0.4,
                new Percent(30), new Percent(80));
        assertThat(planned.nextWindow()).isEmpty();
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
        var draft = EngineConfig.firstRun(BRUSSELS, needyId).withSystem(
                exampleSystem().withLocation(new Coordinates(51, 5), BRUSSELS), TypicalValues.NONE);

        var preview = engine.preview(draft, new Secrets(Map.of(needyId, Map.of("api_key", "TYPED-KEY"))));

        assertThat(preview).isInstanceOfSatisfying(Outlook.Planned.class, planned -> {
            assertThat(planned.forecast().attribution()).isEqualTo("Forecast by Needy");
            assertThat(planned.minimumPct()).isEqualTo(PlanningPreferences.RECOMMENDED.minimumPct());
            assertThat(planned.nextWindow()).isPresent();
        });
        assertThat(needy.calls).extracting(call -> call.settings().get("api_key")).containsExactly("TYPED-KEY");
        assertThat(Files.readString(config)).isEqualTo(stored);
        assertThat(engine.apiKeys()).isEqualTo(Secrets.NONE);
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

        assertThat(engine.preview(EngineConfig.firstRun(BRUSSELS, new ProviderId("main")), Secrets.NONE))
                .isEqualTo(new Outlook.NoForecast("Main: no network"));
    }

    @Test
    void theFirstRunConfigHasTypicalValuesTaggedAndNoPlaceYet() {
        var draft = EngineConfig.firstRun(BRUSSELS, new ProviderId("open-meteo"));

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
        var apiKeys = new ApiKeys(new NoKeychain(), new SecretsFile(folder.resolve("secrets.json")));
        var withoutKeychain = new Engine(new ConfigFile(config),
                new ForecastService(known, new ForecastCache(folder.resolve("cache")), apiKeys), known, apiKeys,
                new AwayFile(folder.resolve("away.json")), new BatteryFile(folder.resolve("battery.json")), clockAt(7),
                announcer(), new ManualScheduler());
        withoutKeychain.saveApiKeys(new Secrets(Map.of(new ProviderId("main"), Map.of("api_key", "x"))));
        assertThat(withoutKeychain.keyStorage()).isEqualTo(KeyStorage.FILE);
    }

    @Test
    void whileTheKeyringThatHoldsTheKeysIsLockedTheOutlookSaysSoAndNoFileIsWritten() throws Exception {
        var needy = new FakeProvider(FakeProvider.info("needy", "Needy",
                List.of(new SettingSpec("api_key", "API key", "Your key.", false, true))), steps(days(2, 4.0)));
        backup.script(error(new ProviderError.Unavailable("down")));
        choose("needy", "backup");
        engine(clockAt(7), needy, backup).saveApiKeys(new Secrets(Map.of(new ProviderId("needy"),
                Map.of("api_key", "STORED-KEY"))));
        keychain.locked = true;

        var outlook = engine(clockAt(7), needy, backup).plan(); // as after a login that left the keyring locked

        assertThat(outlook).isInstanceOfSatisfying(Outlook.NoForecast.class,
                none -> assertThat(none.keychainLocked()).isTrue());
        assertThat(needy.calls).isEmpty();
        assertThat(folder.resolve("secrets.json")).doesNotExist();
        assertThat(engine(clockAt(7), needy, backup).keyStorage()).isEqualTo(KeyStorage.LOCKED_KEYCHAIN);
    }

    @Test
    void aMissingKeyIsNotALockedKeyring() throws Exception {
        var needy = new FakeProvider(FakeProvider.info("needy", "Needy",
                List.of(new SettingSpec("api_key", "API key", "Your key.", false, true))), steps(days(2, 4.0)));
        backup.script(error(new ProviderError.Unavailable("down")));
        choose("needy", "backup");

        assertThat(engine(clockAt(7), needy, backup).plan()).isInstanceOfSatisfying(Outlook.NoForecast.class,
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

    // the Python version's notification cases in tests/test_engine.py; the example's window starts at 10:00 and its
    // lead time is 15 minutes

    @Test
    void theOwnerIsToldOnceAheadOfTheWindowWhatToSet() {
        engine(clockAt(9, 50)).plan();
        var announcement = told.getFirst();
        assertThat(announcement.kind()).isEqualTo(AnnouncementKind.CHARGE_WINDOW);
        assertThat(announcement.window().start()).isEqualTo(DAY.atTime(10, 0).atZone(BRUSSELS).toInstant());
        assertThat(announcement.window().amps()).isEqualTo(13);

        engine(clockAt(9, 52)).plan(); // also after a restart: the announced windows are kept in a file

        assertThat(told).hasSize(1);
    }

    @Test
    void theOwnerIsNotToldBeforeTheLeadTime() {
        engine(clockAt(9, 30)).plan();
        assertThat(told).isEmpty();
    }

    @Test
    void theOwnerIsNotToldWhenTheForecastCouldNotBeRefreshed() {
        main.script(steps(days(2, 4.0)), error(new ProviderError.Unavailable("down")));
        backup.script(error(new ProviderError.Unavailable("down")));
        engine(clockAt(7)).plan();

        assertThat(source(engine(clockAt(9, 50))).freshness()).isEqualTo(Freshness.STALE);
        assertThat(told).isEmpty();
    }

    @Test
    void aLongerLeadTimeInTheConfigFileTellsTheOwnerSooner() throws IOException {
        var engine = engine(clockAt(9, 30));
        engine.plan();
        assertThat(told).isEmpty();

        Files.writeString(config, Files.readString(config).replace("\"notifyLeadMinutes\": 15", "\"notifyLeadMinutes\": 30"));
        engine.plan();

        assertThat(told).hasSize(1);
    }

    @Test
    void anAnnouncedWindowIsPlannedAsItWasAnnouncedWhileItRuns() throws IOException {
        engine(clockAt(9, 50)).plan();
        Files.writeString(config, Files.readString(config).replace("\"margin\": 0.85", "\"margin\": 0.5"));

        var window = planned(engine(clockAt(11))).nextWindow().orElseThrow();

        assertThat(window).extracting(w -> w.start(), w -> w.amps()).containsExactly(told.getFirst().window().start(), 13);
    }

    private static io.github.enadeag.carchargeplanner.engine.config.HomeSystem exampleSystem() {
        try {
            return new ConfigFile(EXAMPLE).read().system();
        } catch (ConfigProblem e) {
            throw new AssertionError(e);
        }
    }

    @Test
    void aConfigThatCannotBeUsedIsLoggedOnceAndSoIsOneThatCanBeUsedAgain() throws IOException {
        var valid = Files.readString(config);
        var engine = engine(clockAt(9));
        try (var log = LogCapture.of(ConfigHealth.class)) {
            engine.plan();
            Files.delete(config);
            engine.plan();
            Files.writeString(config, "{\"version\": 1}");
            engine.plan();
            engine.plan();
            Files.writeString(config, valid);
            engine.plan();
            engine.plan();

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
            } catch (InterruptedException e) {
                throw new ProviderError.Unavailable("interrupted");
            }
            return days(2, 4.0);
        });
        var engine = engine(clockAt(9));
        var planning = CompletableFuture.supplyAsync(engine::plan);
        assertThat(asked.await(10, TimeUnit.SECONDS)).as("the provider is being asked").isTrue();

        var keys = new Secrets(Map.of(new ProviderId("main"), Map.of("api_key", "NEW-KEY")));
        assertTimeoutPreemptively(Duration.ofSeconds(5), () -> engine.saveApiKeys(keys));

        answer.countDown();
        assertThat(planning.get(10, TimeUnit.SECONDS)).isInstanceOf(Outlook.Planned.class);
        assertThat(engine.apiKeys()).isEqualTo(keys);
    }
}
