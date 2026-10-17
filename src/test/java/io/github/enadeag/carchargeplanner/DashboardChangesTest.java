package io.github.enadeag.carchargeplanner;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.enadeag.carchargeplanner.desktop.Background;
import io.github.enadeag.carchargeplanner.engine.Engine;
import io.github.enadeag.carchargeplanner.engine.ManualScheduler;
import io.github.enadeag.carchargeplanner.engine.away.AwayFile;
import io.github.enadeag.carchargeplanner.engine.away.AwayPeriod;
import io.github.enadeag.carchargeplanner.engine.battery.BatteryFile;
import io.github.enadeag.carchargeplanner.engine.battery.BatteryReading;
import io.github.enadeag.carchargeplanner.engine.config.ConfigFile;
import io.github.enadeag.carchargeplanner.engine.config.Percent;
import io.github.enadeag.carchargeplanner.engine.forecast.ForecastCache;
import io.github.enadeag.carchargeplanner.engine.forecast.ForecastService;
import io.github.enadeag.carchargeplanner.engine.forecast.Providers;
import io.github.enadeag.carchargeplanner.engine.notify.AnnouncedWindows;
import io.github.enadeag.carchargeplanner.engine.notify.Announcer;
import io.github.enadeag.carchargeplanner.engine.secrets.ApiKeys;
import io.github.enadeag.carchargeplanner.engine.secrets.NoKeychain;
import io.github.enadeag.carchargeplanner.engine.secrets.SecretsFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DashboardChangesTest {

    private static final Instant NOW = Instant.parse("2026-07-03T06:00:00Z");
    private static final AwayPeriod AWAY = new AwayPeriod(NOW.plus(Duration.ofHours(2)), NOW.plus(Duration.ofHours(12)));

    @TempDir
    Path folder;

    /** The saving thread, run by hand: what it was handed, in order. */
    private final List<Runnable> handedOn = new ArrayList<>();
    private DashboardChanges changes;

    @BeforeEach
    void makeTheEngine() {
        var providers = new Providers(List.of());
        var apiKeys = new ApiKeys(new NoKeychain(), new SecretsFile(folder.resolve("secrets.json")));
        var engine = new Engine(new ConfigFile(folder.resolve("engine.json")),
                new ForecastService(providers, new ForecastCache(folder.resolve("cache")), apiKeys), providers, apiKeys,
                new AwayFile(folder.resolve("away.json")), new BatteryFile(folder.resolve("battery.json")),
                Clock.fixed(NOW, ZoneOffset.UTC),
                new Announcer(new AnnouncedWindows(folder.resolve("announced.json")), announcement -> { }),
                new ManualScheduler());
        changes = new DashboardChanges(engine, new Background(handedOn::add, Runnable::run));
    }

    private void runTheSavingThread() {
        handedOn.forEach(Runnable::run);
        handedOn.clear();
    }

    @Test
    void theChangesAreSavedOnTheSavingThreadInTheOrderTheyWereMade() {
        changes.enterBatteryLevel(new Percent(60));
        changes.addAwayPeriod(AWAY);
        changes.enterBatteryLevel(new Percent(72));
        changes.removeAwayPeriod(AWAY);
        assertThat(Files.exists(folder.resolve("battery.json"))).isFalse();

        runTheSavingThread();

        assertThat(new BatteryFile(folder.resolve("battery.json")).reading().map(BatteryReading::level))
                .contains(new Percent(72));
        assertThat(Files.exists(folder.resolve("away.json"))).isTrue();
        assertThat(new AwayFile(folder.resolve("away.json")).periods()).isEmpty();
    }

    @Test
    void aChangeThatCannotBeSavedDoesNotStopTheNextOne() throws Exception {
        Files.createDirectory(folder.resolve("battery.json"));

        try (var log = LogCapture.of(Background.class)) {
            changes.enterBatteryLevel(new Percent(72));
            changes.addAwayPeriod(AWAY);
            runTheSavingThread();

            assertThat(log.lines()).containsExactly(
                    "WARNING failed in the background: saving the battery level (UncheckedIOException)");
        }
        assertThat(new AwayFile(folder.resolve("away.json")).periods()).containsExactly(AWAY);
    }
}
