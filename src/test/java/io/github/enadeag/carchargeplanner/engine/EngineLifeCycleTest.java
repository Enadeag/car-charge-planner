package io.github.enadeag.carchargeplanner.engine;

import static io.github.enadeag.carchargeplanner.engine.forecast.FakeProvider.steps;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.enadeag.carchargeplanner.LogCapture;
import io.github.enadeag.carchargeplanner.engine.away.AwayFile;
import io.github.enadeag.carchargeplanner.engine.away.AwayPeriod;
import io.github.enadeag.carchargeplanner.engine.battery.BatteryFile;
import io.github.enadeag.carchargeplanner.engine.config.ConfigFile;
import io.github.enadeag.carchargeplanner.engine.config.Percent;
import io.github.enadeag.carchargeplanner.engine.forecast.FakeProvider;
import io.github.enadeag.carchargeplanner.engine.forecast.ForecastCache;
import io.github.enadeag.carchargeplanner.engine.forecast.ForecastService;
import io.github.enadeag.carchargeplanner.engine.forecast.ForecastStep;
import io.github.enadeag.carchargeplanner.engine.forecast.Providers;
import io.github.enadeag.carchargeplanner.engine.notify.AnnouncedWindows;
import io.github.enadeag.carchargeplanner.engine.notify.Announcer;
import io.github.enadeag.carchargeplanner.engine.secrets.ApiKeys;
import io.github.enadeag.carchargeplanner.engine.secrets.KeyChanges;
import io.github.enadeag.carchargeplanner.engine.secrets.MemoryKeychain;
import io.github.enadeag.carchargeplanner.engine.secrets.Secrets;
import io.github.enadeag.carchargeplanner.engine.secrets.SecretsFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** The engine's own schedule: when it plans, and that it keeps planning. */
class EngineLifeCycleTest {

    private static final Instant NOW = Instant.parse("2026-07-03T06:00:00Z");
    private static final AwayPeriod AWAY = new AwayPeriod(NOW.plus(Duration.ofHours(2)), NOW.plus(Duration.ofHours(12)));

    @TempDir
    Path folder;

    private final ManualScheduler planner = new ManualScheduler();
    private final List<Outlook> told = new CopyOnWriteArrayList<>();

    /** A change the owner makes through the engine. */
    @FunctionalInterface
    private interface Change {
        void make(Engine engine) throws Exception;
    }

    private Engine engine(ScheduledExecutorService on) throws Exception {
        var config = Files.copy(Path.of("docs", "example-engine.json"), folder.resolve("engine.json"));
        var example = Files.readString(config);
        Files.writeString(config, example.substring(0, example.indexOf("\"forecast\""))
                + "\"forecast\": {\"primary\": \"main\", \"fallback\": \"\", \"settings\": {}}}");
        var sunny = new ArrayList<ForecastStep>();
        for (int hour = 0; hour < 48; hour++) {
            var start = NOW.plus(Duration.ofHours(hour));
            sunny.add(new ForecastStep(start, start.plus(Duration.ofHours(1)), 4.0));
        }
        var providers = new Providers(List.of(new FakeProvider("main", "Main", steps(sunny))));
        var apiKeys = new ApiKeys(new MemoryKeychain(), new SecretsFile(folder.resolve("secrets.json")));
        return new Engine(new ConfigFile(config),
                new ForecastService(providers, new ForecastCache(folder.resolve("cache")), apiKeys), providers, apiKeys,
                new AwayFile(folder.resolve("away.json")), new BatteryFile(folder.resolve("battery.json")),
                Clock.fixed(NOW, ZoneOffset.UTC),
                new Announcer(new AnnouncedWindows(folder.resolve("announced.json")), announcement -> { }), on);
    }

    private Engine started() throws Exception {
        var engine = engine(planner);
        engine.start(told::add);
        planner.runPending();
        return engine;
    }

    private static Percent battery(Outlook outlook) {
        return ((Outlook.Planned) outlook).batteryPct();
    }

    @Test
    void theEnginePlansAtTheStartAndThenEveryMinute() throws Exception {
        started();
        assertThat(told).hasSize(1);

        planner.advance(Duration.ofSeconds(59));
        assertThat(told).hasSize(1);

        planner.advance(Duration.ofSeconds(1));
        assertThat(told).hasSize(2);
        assertThat(told).allMatch(Outlook.Planned.class::isInstance);
    }

    static Stream<Arguments> changes() {
        return Stream.of(
                Arguments.of("battery level", (Change) engine -> engine.setBatteryLevel(new Percent(72))),
                Arguments.of("away period added", (Change) engine -> engine.addAwayPeriod(AWAY)),
                Arguments.of("away period removed", (Change) engine -> engine.removeAwayPeriod(AWAY)),
                Arguments.of("config", (Change) engine -> engine.saveConfig(engine.config())),
                Arguments.of("API keys", (Change) engine -> engine.saveApiKeys(new Secrets(Map.of()))),
                Arguments.of("API key changes", (Change) engine -> engine.saveApiKeys(KeyChanges.entering(Secrets.NONE), () -> { })));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("changes")
    void theEnginePlansAgainSoonAfterAChange(String name, Change change) throws Exception {
        var engine = started();

        change.make(engine);
        planner.runPending();

        assertThat(told).hasSize(2);
    }

    @Test
    void thePlanAfterABatteryLevelStartsFromIt() throws Exception {
        var engine = started();

        engine.setBatteryLevel(new Percent(72));
        planner.runPending();

        assertThat(battery(told.getLast())).isEqualTo(new Percent(72));
    }

    @Test
    void theEnginePlansAgainAlsoWhenAChangeCouldNotBeSaved() throws Exception {
        var engine = started();
        Files.createDirectory(folder.resolve("battery.json"));

        assertThatThrownBy(() -> engine.setBatteryLevel(new Percent(72))).isInstanceOf(java.io.IOException.class);
        planner.runPending();

        assertThat(told).hasSize(2);
    }

    @Test
    void aChangeBeforeTheStartWaitsForTheFirstPlan() throws Exception {
        var engine = engine(planner);

        engine.setBatteryLevel(new Percent(72));
        planner.runPending();
        engine.start(told::add);
        planner.runPending();

        assertThat(told).hasSize(1);
        assertThat(battery(told.getFirst())).isEqualTo(new Percent(72));
    }

    @Test
    void thePlanningKeepsGoingAfterAnError() throws Exception {
        var engine = engine(planner);
        var first = new boolean[] {true};
        Consumer<Outlook> failsOnce = outlook -> {
            if (first[0]) {
                first[0] = false;
                throw new StackOverflowError("a listener that fails once");
            }
            told.add(outlook);
        };
        try (var log = LogCapture.of(PlanSchedule.class)) {
            engine.start(failsOnce);
            planner.runPending();

            planner.advance(PlanSchedule.EVERY);

            assertThat(told).hasSize(1);
            assertThat(log.records()).singleElement().satisfies(record -> {
                assertThat(record.getMessage()).isEqualTo("planning failed; trying again at the next round");
                assertThat(record.getThrown()).isInstanceOf(StackOverflowError.class);
            });
        }
    }

    @Test
    void afterTheCloseNothingIsPlannedButChangesAreStillSaved() throws Exception {
        var engine = started();

        engine.close();
        engine.setBatteryLevel(new Percent(72));
        planner.advance(Duration.ofMinutes(5));

        assertThat(told).hasSize(1);
        assertThat(new BatteryFile(folder.resolve("battery.json")).reading()).isPresent();
    }

    @Test
    void theEngineStartsOnce() throws Exception {
        var engine = started();

        assertThatThrownBy(() -> engine.start(told::add)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void theCloseStopsTheEnginesThread() throws Exception {
        var thread = Executors.newSingleThreadScheduledExecutor();
        var engine = engine(thread);
        var outlooks = new LinkedBlockingQueue<Outlook>();
        engine.start(outlooks::add);
        assertThat(outlooks.poll(10, TimeUnit.SECONDS)).isInstanceOf(Outlook.Planned.class);

        engine.close();

        assertThat(thread.isTerminated()).isTrue();
    }
}
