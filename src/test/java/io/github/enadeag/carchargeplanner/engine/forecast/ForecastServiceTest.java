package io.github.enadeag.carchargeplanner.engine.forecast;

import static io.github.enadeag.carchargeplanner.engine.forecast.FakeProvider.crash;
import static io.github.enadeag.carchargeplanner.engine.forecast.FakeProvider.error;
import static io.github.enadeag.carchargeplanner.engine.forecast.FakeProvider.steps;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.enadeag.carchargeplanner.LogCapture;
import io.github.enadeag.carchargeplanner.engine.config.Coordinates;
import io.github.enadeag.carchargeplanner.engine.config.ForecastSelection;
import io.github.enadeag.carchargeplanner.engine.config.PanelArray;
import io.github.enadeag.carchargeplanner.engine.config.ProviderId;
import io.github.enadeag.carchargeplanner.engine.forecast.ProviderError.BadResponse;
import io.github.enadeag.carchargeplanner.engine.forecast.ProviderError.RateLimited;
import io.github.enadeag.carchargeplanner.engine.forecast.ProviderError.Unavailable;
import io.github.enadeag.carchargeplanner.engine.secrets.Secrets;
import io.github.enadeag.carchargeplanner.engine.secrets.SecretsProblem;
import io.github.enadeag.carchargeplanner.engine.secrets.SecretsSource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/** The forecast service over fake providers named Main (the primary) and Backup (the fallback provider). */
class ForecastServiceTest {

    private static final LocalDate DAY = LocalDate.of(2026, 7, 3);
    private static final Instant T0 = DAY.atTime(7, 0).atZone(SunnyDays.TZ).toInstant();
    private static final SystemDescription SYSTEM = ProviderCase.EXAMPLE_SYSTEM;
    private static final String SECRET = "SERVICE-SECRET-31";
    private static final ProviderId MAIN = new ProviderId("main");
    private static final ProviderId BACKUP = new ProviderId("backup");
    private static final ProviderId UNKNOWN = new ProviderId("solcast");
    private static final SettingSpec REQUIRED_KEY = new SettingSpec("api_key", "API key", "Your key.", false, true);

    @TempDir
    Path folder;

    private FakeProvider main;
    private FakeProvider backup;
    private SecretsSource secrets = () -> Secrets.NONE;
    private ForecastService service;
    private ForecastSelection selection = new ForecastSelection(MAIN, Optional.of(BACKUP), Map.of());

    @BeforeEach
    void makeTheService() {
        main = new FakeProvider("main", "Main", steps(SunnyDays.day(DAY, 3.0)));
        backup = new FakeProvider("backup", "Backup", steps(SunnyDays.day(DAY, 1.5)));
        restart();
    }

    private void restart() {
        service = new ForecastService(new Providers(List.of(main, backup)), new ForecastCache(cacheFolder()),
                () -> secrets.load());
    }

    private Path cacheFolder() {
        return folder.resolve("cache");
    }

    private void withoutFallback() {
        selection = new ForecastSelection(MAIN, Optional.empty(), Map.of());
    }

    private void mainNeedsAKey(FakeProvider.Answer... script) {
        main = new FakeProvider(FakeProvider.info("main", "Main", List.of(REQUIRED_KEY)), script);
        restart();
    }

    private ForecastResult get(Instant now, boolean force) throws ForecastUnavailable {
        return service.get(SYSTEM, selection, now, force);
    }

    private ForecastResult get(Instant now) throws ForecastUnavailable {
        return get(now, false);
    }

    private static Instant later(int minutes) {
        return T0.plus(Duration.ofMinutes(minutes));
    }

    private Path cacheFile(String providerId) {
        return cacheFolder().resolve("forecast-" + providerId + ".json");
    }

    @Test
    void thePrimaryIsAskedAndTheResultSaysWhoAnswered() throws ForecastUnavailable {
        var result = get(T0);

        assertThat(result.source()).isEqualTo(new ForecastSource(MAIN, "Main", "Forecast by Main", T0,
                Freshness.FRESH, ProviderRole.PRIMARY, "", Optional.empty()));
        assertThat(result.steps()).isEqualTo(SunnyDays.day(DAY, 3.0));
        assertThat(main.callCount()).isEqualTo(1);
        assertThat(backup.callCount()).isZero();
        assertThat(cacheFile("main")).exists();
    }

    @Test
    void theProviderIsGivenTheSystemTheSettingsAndTheTime() throws ForecastUnavailable {
        get(T0);
        assertThat(main.calls).singleElement().isEqualTo(new FakeProvider.Call(SYSTEM, Map.of(), T0));
    }

    @Test
    void withinTheMinimumRefreshTheCacheAnswersAndNothingIsAsked() throws ForecastUnavailable {
        get(T0);
        var result = get(later(59));

        assertThat(result.source().freshness()).isEqualTo(Freshness.CACHED);
        assertThat(result.steps()).isEqualTo(SunnyDays.day(DAY, 3.0));
        assertThat(main.callCount()).isEqualTo(1);
    }

    @Test
    void theOwnerCanForceARefreshInsideTheMinimumInterval() throws ForecastUnavailable {
        get(T0);
        assertThat(get(later(5), true).source().freshness()).isEqualTo(Freshness.FRESH);
        assertThat(main.callCount()).isEqualTo(2);
    }

    @Test
    void afterTheMinimumRefreshItIsAskedAgain() throws ForecastUnavailable {
        get(T0);
        assertThat(get(later(61)).source().freshness()).isEqualTo(Freshness.FRESH);
        assertThat(main.callCount()).isEqualTo(2);
    }

    @Test
    void theCacheSurvivesARestart() throws ForecastUnavailable {
        get(T0);
        restart();
        assertThat(get(later(30)).source().freshness()).isEqualTo(Freshness.CACHED);
        assertThat(main.callCount()).isEqualTo(1);
    }

    @Test
    void aFreshCacheWithAFailingProviderIsNeverAReasonToAsk() throws ForecastUnavailable {
        main.script(steps(SunnyDays.day(DAY, 3.0)), error(new Unavailable("down")));
        get(T0);
        assertThat(get(later(30)).source().freshness()).isEqualTo(Freshness.CACHED);
        assertThat(main.callCount()).isEqualTo(1);
    }

    @Test
    void aFailureKeepsThePrimarysLastGoodForecastWhileItIsStillUseful() throws ForecastUnavailable {
        main.script(steps(SunnyDays.day(DAY, 3.0)), error(new Unavailable("no network")));
        get(T0);
        var result = get(later(120)); // due again, and it fails

        assertThat(result.source()).extracting(ForecastSource::freshness, ForecastSource::providerRole,
                ForecastSource::providerId, ForecastSource::fetchedAt)
                .containsExactly(Freshness.STALE, ProviderRole.PRIMARY, MAIN, T0);
        assertThat(backup.callCount()).isZero();
    }

    @Test
    void aCacheTooOldToBeUsefulGoesToTheFallback() throws ForecastUnavailable {
        main.script(steps(SunnyDays.day(DAY, 3.0)), error(new Unavailable("no network")));
        get(T0);
        var result = get(later(7 * 60)); // older than the 6 hours the provider calls useful

        assertThat(result.source()).extracting(ForecastSource::providerRole, ForecastSource::providerId,
                ForecastSource::primaryName, ForecastSource::attribution, ForecastSource::freshness)
                .containsExactly(ProviderRole.FALLBACK, BACKUP, "Main", "Forecast by Backup", Freshness.FRESH);
        assertThat(cacheFile("backup")).exists();
    }

    @Test
    void withNoCacheAtAllTheFallbackIsUsedAtOnce() throws ForecastUnavailable {
        main.script(error(new Unavailable("no network")));
        assertThat(get(T0).source().providerRole()).isEqualTo(ProviderRole.FALLBACK);
        assertThat(main.callCount()).isEqualTo(1);
        assertThat(backup.callCount()).isEqualTo(1);
    }

    @Test
    void theFallbackHasItsOwnCacheAndRules() throws ForecastUnavailable {
        main.script(error(new Unavailable("no network")));
        get(T0);
        var again = get(later(10)); // the primary is asked again and fails again; the fallback's cache is fresh

        assertThat(again.source().providerRole()).isEqualTo(ProviderRole.FALLBACK);
        assertThat(again.source().freshness()).isEqualTo(Freshness.CACHED);
        assertThat(main.callCount()).isEqualTo(2);
        assertThat(backup.callCount()).isEqualTo(1);

        var pushed = get(later(11), true); // the owner pressed refresh: whoever answers is asked again
        assertThat(pushed.source().freshness()).isEqualTo(Freshness.FRESH);
        assertThat(backup.callCount()).isEqualTo(2);
    }

    @Test
    void withNothingUsableAnywhereTheReasonsAreGivenForBoth() {
        main.script(error(new Unavailable("no network")));
        backup.script(error(new BadResponse("not what it documents")));

        assertThatThrownBy(() -> get(T0)).isInstanceOf(ForecastUnavailable.class)
                .hasMessageContaining("Main: no network").hasMessageContaining("Backup: not what it documents");
    }

    @Test
    void withoutAFallbackAndWithoutACacheThereIsNoForecast() {
        withoutFallback();
        main.script(error(new Unavailable("no network")));

        assertThatThrownBy(() -> get(T0)).isInstanceOf(ForecastUnavailable.class).hasMessage("Main: no network");
        assertThat(backup.callCount()).isZero();
    }

    @Test
    void aRecoveredPrimaryTakesOverAtTheNextRefresh() throws ForecastUnavailable {
        main.script(error(new Unavailable("down")), steps(SunnyDays.day(DAY, 4.0)));
        assertThat(get(T0).source().providerRole()).isEqualTo(ProviderRole.FALLBACK);

        var result = get(later(6)); // past the short wait after an unanswered request
        assertThat(result.source().providerRole()).isEqualTo(ProviderRole.PRIMARY);
        assertThat(result.source().primaryName()).isEmpty();
    }

    @Test
    void aProviderThatCouldNotBeReachedIsTriedAgainSoonButNotEveryMinute() throws ForecastUnavailable {
        main.script(error(new Unavailable("down")));
        get(T0);
        get(later(1));
        get(later(4));
        assertThat(main.callCount()).isEqualTo(1); // waiting out the short delay: the fallback's cache answered
        get(later(6));
        assertThat(main.callCount()).isEqualTo(2);
        get(later(7), true); // the owner may push
        assertThat(main.callCount()).isEqualTo(3);
    }

    @Test
    void aBadAnswerCountsAsARequestAndWaitsTheFullInterval() throws ForecastUnavailable {
        main.script(error(new BadResponse("odd")));
        get(T0);
        get(later(30));
        assertThat(main.callCount()).isEqualTo(1);
        get(later(61));
        assertThat(main.callCount()).isEqualTo(2);
    }

    @Test
    void aProviderThatBreaksTheContractIsTreatedAsABadAnswer() throws ForecastUnavailable {
        main.script(steps(List.of(new ForecastStep(T0, T0.plus(Duration.ofHours(1)), -1.0))));
        assertThat(get(T0).source().providerRole()).isEqualTo(ProviderRole.FALLBACK); // the primary's answer was refused, not used
    }

    @Test
    void aProviderThatStartsFailingAndOneThatAnswersAgainAreEachLoggedOnce() throws ForecastUnavailable {
        withoutFallback();
        main.script(steps(SunnyDays.day(DAY, 3.0)), error(new Unavailable("down")), error(new Unavailable("down")),
                steps(SunnyDays.day(DAY, 3.5)));
        try (var log = LogCapture.of(ProviderHealth.class)) {
            get(T0);
            get(later(120));
            get(later(240));
            get(later(360));

            assertThat(main.callCount()).isEqualTo(4);
            assertThat(log.lines()).containsExactly("INFO Main gives forecasts", "WARNING Main gives no forecast: down",
                    "INFO Main gives forecasts again");
        }
    }

    /** A server can echo what it was sent: neither the provider's own key nor any other stored one is shown or logged. */
    @Test
    void aServerThatEchoesKeysPutsNoneInTheReasonOrTheLog() {
        var otherKey = "BACKUP-KEY-77";
        var echoing = new ForecastProvider() {
            @Override
            public ProviderInfo info() {
                return FakeProvider.info("main", "Main", List.of(REQUIRED_KEY));
            }

            @Override
            public List<ForecastStep> fetch(SystemDescription system, Map<String, String> settings, Instant now)
                    throws ProviderError {
                throw new BadResponse("Main says: you sent " + settings.get("api_key") + "; we know " + otherKey);
            }
        };
        secrets = () -> new Secrets(Map.of(MAIN, Map.of("api_key", SECRET), BACKUP, Map.of("api_key", otherKey)));
        withoutFallback();
        var echoed = new ForecastService(new Providers(List.of(echoing, backup)), new ForecastCache(cacheFolder()),
                () -> secrets.load());

        try (var log = LogCapture.of("io.github.enadeag.carchargeplanner")) {
            assertThatThrownBy(() -> echoed.get(SYSTEM, selection, T0, false)).isInstanceOf(ForecastUnavailable.class)
                    .hasMessage("Main: Main says: you sent ***; we know ***");
            assertThat(log.lines()).containsExactly("WARNING Main gives no forecast: Main says: you sent ***; we know ***");
        }
    }

    @Test
    void aBugInAProviderDoesNotStopTheAppAndItsMessageIsNeitherLoggedNorShown() throws ForecastUnavailable {
        var logged = new CopyOnWriteArrayList<String>();
        var capture = new Handler() {
            @Override
            public void publish(LogRecord record) {
                logged.add(record.getMessage());
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        };
        var logger = Logger.getLogger(ProviderAttempt.class.getName());
        logger.addHandler(capture);
        logger.setUseParentHandlers(false); // the test's output stays clean
        try {
            main.script(crash(new IllegalStateException("oops with " + SECRET)));
            assertThat(get(T0).source().providerRole()).isEqualTo(ProviderRole.FALLBACK);
            withoutFallback();
            assertThatThrownBy(() -> get(later(120))).isInstanceOf(ForecastUnavailable.class)
                    .hasMessageContaining("IllegalStateException").hasMessageNotContaining(SECRET);
        } finally {
            logger.removeHandler(capture);
            logger.setUseParentHandlers(true);
        }
        assertThat(logged).containsExactly("forecast provider main failed unexpectedly (IllegalStateException)",
                "forecast provider main failed unexpectedly (IllegalStateException)");
    }

    @Test
    void aRateLimitStartsACoolDownThatEvenAForcedRefreshRespects() throws ForecastUnavailable {
        main.script(steps(SunnyDays.day(DAY, 3.0)), error(new RateLimited("busy", Duration.ofMinutes(30))),
                steps(SunnyDays.day(DAY, 3.5)));
        get(T0);
        var limited = get(later(120));

        assertThat(limited.source().freshness()).isEqualTo(Freshness.STALE);
        assertThat(limited.source().cooldownUntil()).contains(later(150));
        assertThat(main.callCount()).isEqualTo(2);
        get(later(125), true);
        get(later(140), true);
        assertThat(main.callCount()).isEqualTo(2); // not asked during the cool-down, forced or not

        var again = get(later(151));
        assertThat(main.callCount()).isEqualTo(3);
        assertThat(again.source().freshness()).isEqualTo(Freshness.FRESH);
        assertThat(again.source().cooldownUntil()).isEmpty();
    }

    @Test
    void withoutARetryTimeTheCoolDownIsTheMinimumRefresh() throws ForecastUnavailable {
        main.script(steps(SunnyDays.day(DAY, 3.0)), error(new RateLimited("busy", null)), steps(SunnyDays.day(DAY, 3.0)));
        get(T0);
        get(later(120));
        get(later(179));
        assertThat(main.callCount()).isEqualTo(2);
        get(later(181));
        assertThat(main.callCount()).isEqualTo(3);
    }

    @Test
    void duringACoolDownWithNoUsableCacheTheFallbackAnswers() throws ForecastUnavailable {
        main.script(error(new RateLimited("busy", Duration.ofHours(1))));
        assertThat(get(T0).source().providerRole()).isEqualTo(ProviderRole.FALLBACK);
        assertThat(get(later(30), true).source().providerRole()).isEqualTo(ProviderRole.FALLBACK);
        assertThat(main.callCount()).isEqualTo(1);
    }

    @Test
    void theCoolDownIsRememberedAcrossARestart() throws ForecastUnavailable {
        main.script(error(new RateLimited("busy", Duration.ofHours(1))));
        get(T0);
        restart();
        get(later(10));
        assertThat(main.callCount()).isEqualTo(1);
    }

    @Test
    void aCoolDownThatHasPassedIsForgotten() throws ForecastUnavailable {
        main.script(error(new RateLimited("busy", Duration.ofMinutes(10))), steps(SunnyDays.day(DAY, 3.0)));
        get(T0);
        restart();
        assertThat(get(later(11)).source().providerRole()).isEqualTo(ProviderRole.PRIMARY);
        assertThat(main.callCount()).isEqualTo(2);
    }

    @Test
    void aChangedSystemOrSettingMakesTheCacheNotApply() throws ForecastUnavailable {
        get(T0);
        var bigger = SYSTEM.withArrays(List.of(new PanelArray("", 9.0, 35, -45, 0.14)));
        assertThat(service.get(bigger, selection, later(5), false).source().freshness()).isEqualTo(Freshness.FRESH);
        var moved = new SystemDescription(new Coordinates(51.0, SYSTEM.location().longitude()), SYSTEM.timezone(), SYSTEM.arrays());
        assertThat(service.get(moved, selection, later(6), false).source().freshness()).isEqualTo(Freshness.FRESH);
        assertThat(main.callCount()).isEqualTo(3);
        assertThat(service.get(moved, selection, later(7), false).source().freshness()).isEqualTo(Freshness.CACHED); // cached in turn
    }

    @Test
    void renamingAPanelArrayKeepsTheCache() throws ForecastUnavailable {
        get(T0);
        var renamed = SYSTEM.withArrays(List.of(new PanelArray("Garage", 4.8, 35, -45, 0.14)));
        assertThat(service.get(renamed, selection, later(5), false).source().freshness()).isEqualTo(Freshness.CACHED);
    }

    @ParameterizedTest
    @ValueSource(strings = {"not json", "[]", "{\"key\": 1}", "{\"key\": {}, \"fetchedAt\": \"x\", \"steps\": 5}", ""})
    void aCorruptCacheIsIgnored(String junk) throws Exception {
        get(T0);
        Files.writeString(cacheFile("main"), junk);
        assertThat(get(later(5)).source().freshness()).isEqualTo(Freshness.FRESH);
        assertThat(main.callCount()).isEqualTo(2);
    }

    @Test
    void aCacheWithStepsThatBreakTheContractIsIgnored() throws Exception {
        get(T0);
        var json = new JsonMapper();
        var blob = (ObjectNode) json.readTree(Files.readString(cacheFile("main")));
        ((ArrayNode) blob.path("steps").path(3)).set(2, -4.0);
        Files.writeString(cacheFile("main"), json.writeValueAsString(blob));
        main.script(error(new Unavailable("down")));

        assertThat(get(later(61)).source().providerRole()).isEqualTo(ProviderRole.FALLBACK); // the damaged cache is not worth using
    }

    /** The clock was set back after the forecast was cached, so the cache seems to come from the future. */
    @Test
    void aCacheStampedAnHourAheadIsNotTakenForFresh() throws ForecastUnavailable {
        service.get(SYSTEM, selection, later(60), false);
        var result = get(T0);

        assertThat(result.source().freshness()).isEqualTo(Freshness.FRESH);
        assertThat(main.callCount()).isEqualTo(2);
    }

    @Test
    void aCacheStampedAheadIsNotUsedAsAStaleForecastEither() {
        withoutFallback();
        main.script(steps(SunnyDays.day(DAY, 3.0)), error(new Unavailable("no network")));
        assertThat(cacheFolder()).doesNotExist();

        assertThatThrownBy(() -> {
            service.get(SYSTEM, selection, later(60), false);
            get(T0);
        }).isInstanceOf(ForecastUnavailable.class).hasMessage("Main: no network");
    }

    @Test
    void anUnwritableCacheFolderDoesNotBreakPlanning() throws Exception {
        Files.writeString(cacheFolder(), "a file where the folder should be");
        assertThat(get(T0).steps()).isEqualTo(SunnyDays.day(DAY, 3.0)); // the fetch worked; nothing could be saved
    }

    @Test
    void aMissingRequiredSettingMeansNothingIsSent() {
        mainNeedsAKey(steps(SunnyDays.day(DAY, 3.0)));
        withoutFallback();

        assertThatThrownBy(() -> get(T0)).isInstanceOf(ForecastUnavailable.class).hasMessage("Main: API key is required");
        assertThat(main.callCount()).isZero();
    }

    @Test
    void aMissingRequiredSettingGoesToTheFallback() throws ForecastUnavailable {
        mainNeedsAKey(steps(SunnyDays.day(DAY, 3.0)));
        assertThat(get(T0).source().providerRole()).isEqualTo(ProviderRole.FALLBACK);
        assertThat(main.callCount()).isZero();
    }

    @Test
    void secretsComeFromTheSecretStoreAndPlainSettingsFromTheConfig() throws ForecastUnavailable {
        mainNeedsAKey(steps(SunnyDays.day(DAY, 3.0)));
        secrets = () -> new Secrets(Map.of(MAIN, Map.of("api_key", SECRET)));
        selection = new ForecastSelection(MAIN, Optional.of(BACKUP), Map.of(MAIN, Map.of("colour", "ignored")));

        get(T0);

        assertThat(main.calls.getFirst().settings()).isEqualTo(Map.of("api_key", SECRET));
    }

    @Test
    void aChangedSecretDoesNotThrowTheCachedForecastAway() throws ForecastUnavailable {
        mainNeedsAKey(steps(SunnyDays.day(DAY, 3.0)));
        secrets = () -> new Secrets(Map.of(MAIN, Map.of("api_key", "first")));
        get(T0);
        secrets = () -> new Secrets(Map.of(MAIN, Map.of("api_key", "second")));

        assertThat(get(later(5)).source().freshness()).isEqualTo(Freshness.CACHED);
    }

    @Test
    void unreadableSecretsDoNotStopProvidersThatNeedNoKey() throws ForecastUnavailable {
        secrets = () -> {
            throw new SecretsProblem("secrets.json is not valid JSON");
        };
        assertThat(get(T0).source().providerId()).isEqualTo(MAIN);

        mainNeedsAKey(steps(SunnyDays.day(DAY, 3.0)));
        backup.script(error(new Unavailable("down")));
        assertThatThrownBy(() -> get(later(120), true)).isInstanceOf(ForecastUnavailable.class)
                .hasMessageContaining("not valid JSON");
    }

    @Test
    void theSecretsAreReadOnlyWhenAProviderIsAskedNotWhenTheCacheAnswers() throws ForecastUnavailable {
        mainNeedsAKey(steps(SunnyDays.day(DAY, 3.0)));
        var reads = new AtomicInteger();
        secrets = () -> {
            reads.incrementAndGet();
            return new Secrets(Map.of(MAIN, Map.of("api_key", SECRET)));
        };

        get(T0);
        assertThat(reads).hasValue(1);
        for (int minute = 1; minute < 15; minute++) {
            assertThat(get(later(minute)).source().freshness()).isEqualTo(Freshness.CACHED);
        }

        assertThat(reads).hasValue(1);
    }

    @Test
    void anUnknownProviderIsAReasonNotACrash() throws ForecastUnavailable {
        selection = new ForecastSelection(UNKNOWN, Optional.of(BACKUP), Map.of());
        var result = get(T0);
        assertThat(result.source().providerRole()).isEqualTo(ProviderRole.FALLBACK);
        assertThat(result.source().primaryName()).isEmpty(); // the primary is not known: there is no name to give

        selection = new ForecastSelection(UNKNOWN, Optional.empty(), Map.of());
        assertThatThrownBy(() -> get(T0)).isInstanceOf(ForecastUnavailable.class).hasMessageContaining("'solcast' is not known");
    }

    @Test
    void twoRequestsAtOnceAskTheProviderOnce() throws Exception {
        var release = new CountDownLatch(1);
        var day = SunnyDays.day(DAY, 3.0);
        main.script(() -> {
            try {
                release.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                throw new ProviderError.Unavailable("interrupted");
            }
            return day;
        });
        var results = new CopyOnWriteArrayList<ForecastResult>();
        var workers = new ArrayList<Thread>();
        for (int i = 0; i < 2; i++) {
            workers.add(Thread.ofPlatform().start(() -> {
                try {
                    results.add(get(T0));
                } catch (ForecastUnavailable e) {
                    throw new AssertionError(e);
                }
            }));
        }
        release.countDown();
        for (var worker : workers) {
            worker.join(5000);
        }

        assertThat(results).hasSize(2).extracting(r -> r.source().freshness())
                .containsExactlyInAnyOrder(Freshness.FRESH, Freshness.CACHED);
        assertThat(main.callCount()).isEqualTo(1);
    }

    @Test
    void savedSettingsMakeTheServiceTryAgainAtOnce() throws ForecastUnavailable {
        main.script(error(new Unavailable("down")), steps(SunnyDays.day(DAY, 4.0)));
        assertThat(get(T0).source().providerRole()).isEqualTo(ProviderRole.FALLBACK);
        assertThat(get(later(1)).source().providerRole()).isEqualTo(ProviderRole.FALLBACK); // waiting out the short delay
        assertThat(main.callCount()).isEqualTo(1);

        service.forgetFailures();

        assertThat(get(later(2)).source().providerRole()).isEqualTo(ProviderRole.PRIMARY);
        assertThat(main.callCount()).isEqualTo(2);
    }

    @Test
    void forgettingFailuresDoesNotForgetARateLimit() throws ForecastUnavailable {
        main.script(error(new RateLimited("busy", Duration.ofHours(1))), steps(SunnyDays.day(DAY, 3.0)));
        get(T0);
        service.forgetFailures();
        get(later(5));
        assertThat(main.callCount()).isEqualTo(1);
    }

    @Test
    void theWaitIsToldInTheTimeOfTheHome() {
        withoutFallback();
        main.script(error(new RateLimited("busy", Duration.ofMinutes(30))));
        assertThatThrownBy(() -> get(T0)).isInstanceOf(ForecastUnavailable.class).hasMessage("Main: busy");
        // 07:30 in Amsterdam
        assertThatThrownBy(() -> get(later(10))).isInstanceOf(ForecastUnavailable.class)
                .hasMessage("Main: not asked again until 07:30");
    }

    @Test
    void severalArraysAreServedFromTheCache() throws IOException, ForecastUnavailable {
        var two = SYSTEM.withArrays(List.of(new PanelArray("", 3.0, 30, -90, 0.14), new PanelArray("", 2.0, 30, 90, 0.14)));
        service.get(two, selection, T0, false);
        restart();
        assertThat(service.get(two, selection, later(10), false).source().freshness()).isEqualTo(Freshness.CACHED);
        assertThat(main.callCount()).isEqualTo(1);
    }

    @Test
    void exactlyAtTheMinimumRefreshItIsAskedAgain() throws ForecastUnavailable {
        get(T0);
        assertThat(get(later(60)).source().freshness()).isEqualTo(Freshness.FRESH);
        assertThat(main.callCount()).isEqualTo(2);
    }

    @Test
    void aForecastExactlyAsOldAsItIsUsefulIsStillUsedWhenTheProviderFails() throws ForecastUnavailable {
        main.script(steps(SunnyDays.day(DAY, 3.0)), error(new Unavailable("no network")));
        get(T0);

        var result = get(later(6 * 60));

        assertThat(result.source()).extracting(ForecastSource::freshness, ForecastSource::providerRole)
                .containsExactly(Freshness.STALE, ProviderRole.PRIMARY);
        assertThat(backup.callCount()).isZero();
    }

    @Test
    void whileAFailedProviderWaitsItsLastGoodForecastIsUsed() throws ForecastUnavailable {
        main.script(steps(SunnyDays.day(DAY, 3.0)), error(new Unavailable("no network")));
        get(T0);
        get(later(120));

        var result = get(later(121));

        assertThat(result.source()).extracting(ForecastSource::freshness, ForecastSource::providerRole, ForecastSource::fetchedAt)
                .containsExactly(Freshness.STALE, ProviderRole.PRIMARY, T0);
        assertThat(result.steps()).isEqualTo(SunnyDays.day(DAY, 3.0));
        assertThat(main.callCount()).isEqualTo(2);
    }

    @Test
    void aProviderThatFailsFromTheStartIsLoggedOnce() {
        withoutFallback();
        main.script(error(new Unavailable("down")));
        try (var log = LogCapture.of(ProviderHealth.class)) {
            assertThatThrownBy(() -> get(T0)).isInstanceOf(ForecastUnavailable.class);
            assertThatThrownBy(() -> get(later(120))).isInstanceOf(ForecastUnavailable.class);

            assertThat(main.callCount()).isEqualTo(2);
            assertThat(log.lines()).containsExactly("WARNING Main gives no forecast: down");
        }
    }

    @Test
    void thePlainSettingsArePartOfTheCacheKeyInNameOrder() {
        var key = new ForecastCache(cacheFolder()).key(SYSTEM, Map.of("b", "2", "a", "1"));
        assertThat(key.path("settings").propertyNames()).containsExactly("a", "b");
        assertThat(key.path("settings").path("a").stringValue()).isEqualTo("1");
        assertThat(key.path("settings").path("b").stringValue()).isEqualTo("2");
    }
}
