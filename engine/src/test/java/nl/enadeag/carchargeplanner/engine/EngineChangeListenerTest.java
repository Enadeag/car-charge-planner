package nl.enadeag.carchargeplanner.engine;

import static nl.enadeag.carchargeplanner.engine.forecast.FakeProvider.steps;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import nl.enadeag.carchargeplanner.LogCapture;
import nl.enadeag.carchargeplanner.engine.away.AwayFile;
import nl.enadeag.carchargeplanner.engine.away.OneOffAwayPeriod;
import nl.enadeag.carchargeplanner.engine.battery.BatteryFile;
import nl.enadeag.carchargeplanner.engine.config.ConfigFile;
import nl.enadeag.carchargeplanner.engine.config.Percent;
import nl.enadeag.carchargeplanner.engine.config.ProviderId;
import nl.enadeag.carchargeplanner.engine.forecast.FakeProvider;
import nl.enadeag.carchargeplanner.engine.forecast.ForecastCache;
import nl.enadeag.carchargeplanner.engine.forecast.ForecastService;
import nl.enadeag.carchargeplanner.engine.forecast.ForecastStep;
import nl.enadeag.carchargeplanner.engine.forecast.Providers;
import nl.enadeag.carchargeplanner.engine.notify.AnnouncedWindows;
import nl.enadeag.carchargeplanner.engine.notify.Announcer;
import nl.enadeag.carchargeplanner.engine.notify.ToldTrips;
import nl.enadeag.carchargeplanner.engine.secrets.ApiKeys;
import nl.enadeag.carchargeplanner.engine.secrets.KeyChanges;
import nl.enadeag.carchargeplanner.engine.secrets.KeyFallback;
import nl.enadeag.carchargeplanner.engine.secrets.KeychainException;
import nl.enadeag.carchargeplanner.engine.secrets.LockableKeychain;
import nl.enadeag.carchargeplanner.engine.secrets.Secrets;
import nl.enadeag.carchargeplanner.engine.secrets.SecretsFile;
import nl.enadeag.carchargeplanner.engine.store.FilePrivacy;
import nl.enadeag.carchargeplanner.engine.store.PrivateFiles;
import nl.enadeag.carchargeplanner.engine.trip.Trip;
import nl.enadeag.carchargeplanner.engine.trip.TripFile;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Who is told of a change: every public method of the engine is either a read, which tells nobody, or a change of
 * what the plan depends on, which tells each listener once, also when it fails. A new public method fails
 * {@link #everyPublicMethodIsAReadOrAChange()} until it is put in one of the two.
 */
class EngineChangeListenerTest {

    private static final PrivateFiles FILES = new PrivateFiles(FilePrivacy.NONE);
    private static final Instant NOW = Instant.parse("2026-07-03T06:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final OneOffAwayPeriod AWAY =
            new OneOffAwayPeriod(NOW.plus(Duration.ofHours(2)), NOW.plus(Duration.ofHours(12)));
    private static final Trip TRIP = new Trip(NOW.plus(Duration.ofHours(2)), NOW.plus(Duration.ofHours(12)), 300,
            false);
    private static final ProviderId MAIN = new ProviderId("main");
    private static final Path EXAMPLE = Path.of("docs", "example-engine.json");

    /** A call of one public method of the engine. */
    @FunctionalInterface
    private interface Call {
        void on(Engine engine) throws Exception;
    }

    /** The methods that change what the plan depends on, by their signature, with a call of each. */
    private static final Map<String, Call> CHANGES = calls(List.of(
            named("saveConfig(EngineConfig)",
                    engine -> engine.saveConfig(new ConfigFile(EXAMPLE, FILES, CLOCK).read())),
            named("setConfigAside()", Engine::setConfigAside),
            named("setBatteryLevel(Percent)", engine -> engine.setBatteryLevel(new Percent(72))),
            named("addAwayPeriod(OneOffAwayPeriod)", engine -> engine.addAwayPeriod(AWAY)),
            named("removeAwayPeriod(OneOffAwayPeriod)", engine -> engine.removeAwayPeriod(AWAY)),
            named("addTrip(Trip)", engine -> engine.addTrip(TRIP)),
            named("changeTrip(Trip, Trip)", engine -> engine.changeTrip(TRIP, TRIP)),
            named("removeTrip(Trip)", engine -> engine.removeTrip(TRIP)),
            named("saveApiKeys(Secrets)", engine -> engine.saveApiKeys(Secrets.NONE)),
            named("applyKeyChanges(KeyChanges, Runnable)",
                    engine -> engine.applyKeyChanges(KeyChanges.entering(Secrets.NONE), () -> { }))));

    /**
     * The methods that change nothing the plan depends on, with a call of each: the reads, and what a round keeps of
     * its own (what it told, the keys it moved into the keychain), which the next round reads anyway.
     */
    private static final Map<String, Call> READS = calls(List.of(
            named("outlook()", Engine::outlook),
            named("round(ForecastUse)", engine -> engine.round(ForecastUse.ASK_PROVIDERS)),
            named("nextNoticeDue(Outlook)", engine -> engine.nextNoticeDue(engine.outlook())),
            named("preview(EngineConfig, Secrets)", engine -> engine.preview(engine.config(), Secrets.NONE)),
            named("config()", Engine::config),
            named("awayPeriods()", Engine::awayPeriods),
            named("trips()", Engine::trips),
            named("checkConnection(ProviderId, HomeSystem, Map)",
                    engine -> engine.checkConnection(MAIN, engine.config().system(), Map.of())),
            named("thisYear(ZoneId)", engine -> engine.thisYear(ZoneId.of("Europe/Brussels"))),
            named("searchPlaces(ProviderId, String, String)", engine -> engine.searchPlaces(MAIN, "Brussels", "nl")),
            named("hasPlaceSearch(ProviderId)", engine -> engine.hasPlaceSearch(MAIN)),
            named("keyStorage()", Engine::keyStorage),
            named("providers()", Engine::providers),
            named("storedApiKeys()", Engine::storedApiKeys),
            named("stopUnlocking()", Engine::stopUnlocking),
            named("addChangeListener(ChangeListener)", engine -> engine.addChangeListener(() -> { }))));

    @TempDir
    Path folder;

    private final LockableKeychain keychain = new LockableKeychain();
    private final List<Thread> told = new ArrayList<>();
    private Path config;
    private Engine engine;

    private static Map.Entry<String, Call> named(String signature, Call call) {
        return Map.entry(signature, call);
    }

    /** The calls in their order, by their signature. */
    private static Map<String, Call> calls(List<Map.Entry<String, Call>> named) {
        var calls = new LinkedHashMap<String, Call>();
        named.forEach(call -> calls.put(call.getKey(), call.getValue()));
        return calls;
    }

    @BeforeEach
    void anEngineWithTheExampleConfigAndAListener() throws Exception {
        config = Files.copy(EXAMPLE, folder.resolve("engine.json"));
        var example = Files.readString(config);
        Files.writeString(config, example.substring(0, example.indexOf("\"forecast\""))
                + "\"forecast\": {\"primary\": \"main\", \"fallback\": \"\", \"settings\": {}}}");
        var sunny = new ArrayList<ForecastStep>();
        for (int hour = 0; hour < 48; hour++) {
            var start = NOW.plus(Duration.ofHours(hour));
            sunny.add(new ForecastStep(start, start.plus(Duration.ofHours(1)), 4.0));
        }
        var providers = new Providers(List.of(new FakeProvider("main", "Main", steps(sunny))));
        var apiKeys = new ApiKeys(keychain, new SecretsFile(folder.resolve("secrets.json"), FILES, CLOCK),
                KeyFallback.PRIVATE_FILE, Clock.systemUTC());
        engine = new Engine(new ConfigFile(config, FILES, CLOCK),
                new ForecastService(providers, new ForecastCache(folder.resolve("cache"), FILES, CLOCK), apiKeys),
                providers, apiKeys,
                new AwayFile(folder.resolve("away.json"), FILES, CLOCK),
                new TripFile(folder.resolve("trips.json"), FILES, CLOCK),
                new BatteryFile(folder.resolve("battery.json"), FILES, CLOCK), CLOCK,
                new Announcer(new AnnouncedWindows(folder.resolve("announced.json"), FILES, CLOCK),
                        new ToldTrips(folder.resolve("trip-notices.json"), FILES, CLOCK), ignored -> { }));
        engine.addChangeListener(() -> told.add(Thread.currentThread()));
    }

    static Stream<Arguments> changes() {
        return CHANGES.entrySet().stream().map(change -> Arguments.of(change.getKey(), change.getValue()));
    }

    static Stream<Arguments> reads() {
        return READS.entrySet().stream().map(read -> Arguments.of(read.getKey(), read.getValue()));
    }

    private static String signature(Method method) {
        return method.getName() + Arrays.stream(method.getParameterTypes()).map(Class::getSimpleName)
                .collect(Collectors.joining(", ", "(", ")"));
    }

    @Test
    void everyPublicMethodIsAReadOrAChange() {
        var methods = Arrays.stream(Engine.class.getDeclaredMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers()) && !Modifier.isStatic(method.getModifiers()))
                .map(EngineChangeListenerTest::signature).collect(Collectors.toSet());
        var both = new HashSet<>(CHANGES.keySet());
        both.retainAll(READS.keySet());
        var listed = new HashSet<>(CHANGES.keySet());
        listed.addAll(READS.keySet());

        assertThat(both).as("in both lists").isEmpty();
        assertThat(methods).as("each public method of the engine, as a read or a change")
                .containsExactlyInAnyOrderElementsOf(listed);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("changes")
    void aChangeTellsTheListenerOnceOnTheCallersThread(String signature, Call change) throws Exception {
        change.on(engine);

        assertThat(told).containsExactly(Thread.currentThread());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("changes")
    void aChangeThatFailsTellsTheListenerOnceToo(String signature, Call change) throws Exception {
        breakTheStoredFiles();

        assertThatThrownBy(() -> change.on(engine)).as("the change fails").isInstanceOf(Exception.class);

        assertThat(told).hasSize(1);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("reads")
    void aReadTellsNobody(String signature, Call read) throws Exception {
        read.on(engine);

        assertThat(told).isEmpty();
    }

    @Test
    void theListenerReadsWhatTheChangeStored() throws Exception {
        var seen = new ArrayList<List<Trip>>();
        engine.addChangeListener(() -> seen.add(engine.trips()));

        engine.addTrip(TRIP);

        assertThat(seen).containsExactly(List.of(TRIP));
    }

    @Test
    void aListenerThatThrowsIsLoggedAndNeitherFailsTheChangeNorKeepsTheOthersFromBeingTold() throws Exception {
        var after = new ArrayList<String>();
        engine.addChangeListener(() -> {
            throw new IllegalStateException("a listener that fails");
        });
        engine.addChangeListener(() -> after.add("told"));

        try (var log = LogCapture.of(Engine.class)) {
            engine.setBatteryLevel(new Percent(72));

            assertThat(log.records()).singleElement().satisfies(record -> {
                assertThat(record.getLevel()).isEqualTo(java.util.logging.Level.SEVERE);
                assertThat(record.getThrown()).isInstanceOf(IllegalStateException.class);
            });
        }
        assertThat(told).hasSize(1);
        assertThat(after).containsExactly("told");
        assertThat(new BatteryFile(folder.resolve("battery.json"), FILES, CLOCK).reading()).isPresent();
    }

    @Test
    void aListenerMustNotBeNull() {
        assertThatThrownBy(() -> engine.addChangeListener(null)).isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * Puts a folder that is not empty where each stored file the changes write is, and keeps the keychain locked
     * without a way to unlock it, so every change fails.
     */
    private void breakTheStoredFiles() throws Exception {
        Files.delete(config);
        for (var name : Set.of("engine.json", "battery.json", "away.json", "trips.json", "secrets.json")) {
            Files.createDirectories(folder.resolve(name).resolve("in-the-way"));
        }
        keychain.locked = true;
        keychain.unlockFails = KeychainException.Kind.UNAVAILABLE;
    }
}
