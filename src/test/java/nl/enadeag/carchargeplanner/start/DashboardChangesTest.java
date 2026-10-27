package nl.enadeag.carchargeplanner.start;

import static org.assertj.core.api.Assertions.assertThat;

import nl.enadeag.carchargeplanner.LogCapture;
import nl.enadeag.carchargeplanner.desktop.app.Background;
import nl.enadeag.carchargeplanner.engine.Engine;
import nl.enadeag.carchargeplanner.engine.ManualScheduler;
import nl.enadeag.carchargeplanner.engine.away.AwayFile;
import nl.enadeag.carchargeplanner.engine.away.OneOffAwayPeriod;
import nl.enadeag.carchargeplanner.engine.battery.BatteryFile;
import nl.enadeag.carchargeplanner.engine.calendar.CalendarFeed;
import nl.enadeag.carchargeplanner.engine.calendar.CalendarFetcher;
import nl.enadeag.carchargeplanner.engine.calendar.ConsentFile;
import nl.enadeag.carchargeplanner.engine.calendar.IcsReader;
import nl.enadeag.carchargeplanner.engine.battery.BatteryReading;
import nl.enadeag.carchargeplanner.engine.config.ConfigFile;
import nl.enadeag.carchargeplanner.engine.config.Percent;
import nl.enadeag.carchargeplanner.engine.forecast.ForecastCache;
import nl.enadeag.carchargeplanner.engine.forecast.ForecastService;
import nl.enadeag.carchargeplanner.engine.forecast.Providers;
import nl.enadeag.carchargeplanner.engine.forecast.ReplayHttp;
import nl.enadeag.carchargeplanner.engine.notify.AnnouncedWindows;
import nl.enadeag.carchargeplanner.engine.notify.Announcer;
import nl.enadeag.carchargeplanner.engine.notify.ToldTrips;
import nl.enadeag.carchargeplanner.engine.secrets.ApiKeys;
import nl.enadeag.carchargeplanner.engine.secrets.NoKeychain;
import nl.enadeag.carchargeplanner.engine.secrets.SecretsFile;
import nl.enadeag.carchargeplanner.engine.trip.Trip;
import nl.enadeag.carchargeplanner.engine.trip.TripFile;
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
    private static final OneOffAwayPeriod AWAY = new OneOffAwayPeriod(NOW.plus(Duration.ofHours(2)), NOW.plus(Duration.ofHours(12)));

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
                new AwayFile(folder.resolve("away.json")), new TripFile(folder.resolve("trips.json")),
                new BatteryFile(folder.resolve("battery.json")),
                new ConsentFile(folder.resolve("calendar-consents.json")),
                new CalendarFeed(new CalendarFetcher(new ReplayHttp(List.of())), new IcsReader()),
                Clock.fixed(NOW, ZoneOffset.UTC),
                new Announcer(new AnnouncedWindows(folder.resolve("announced.json")),
                        new ToldTrips(folder.resolve("trip-notices.json")), _ -> { }),
                new ManualScheduler());
        changes = new DashboardChanges(engine, new Background(handedOn::add, Runnable::run));
    }

    private void runTheSavingThread() {
        handedOn.forEach(Runnable::run);
        handedOn.clear();
    }

    @Test
    void theChangesAreSavedOnTheSavingThreadInTheOrderTheyWereMade() {
        changes.setBatteryLevel(new Percent(60));
        changes.addAwayPeriod(AWAY);
        changes.setBatteryLevel(new Percent(72));
        changes.removeAwayPeriod(AWAY);
        assertThat(Files.exists(folder.resolve("battery.json"))).isFalse();

        runTheSavingThread();

        assertThat(new BatteryFile(folder.resolve("battery.json")).reading().map(BatteryReading::level))
                .contains(new Percent(72));
        assertThat(Files.exists(folder.resolve("away.json"))).isTrue();
        assertThat(new AwayFile(folder.resolve("away.json")).periods()).isEmpty();
    }

    @Test
    void tripsAreAddedChangedAndRemovedOnTheSavingThreadInOrder() {
        var trip = new Trip(NOW.plus(Duration.ofHours(2)), NOW.plus(Duration.ofHours(12)), 300, false);
        var changed = new Trip(trip.departure(), trip.returnTime(), 300, true, true);
        var other = new Trip(NOW.plus(Duration.ofDays(1)), NOW.plus(Duration.ofDays(2)), 80, false);
        changes.addTrip(trip);
        changes.addTrip(other);
        changes.changeTrip(trip, changed);
        changes.removeTrip(other);
        assertThat(Files.exists(folder.resolve("trips.json"))).isFalse();

        runTheSavingThread();

        assertThat(new TripFile(folder.resolve("trips.json")).trips(NOW)).containsExactly(changed);
    }

    @Test
    void aChangeThatCannotBeSavedDoesNotStopTheNextOne() throws Exception {
        Files.createDirectory(folder.resolve("battery.json"));

        try (var log = LogCapture.of(Background.class)) {
            changes.setBatteryLevel(new Percent(72));
            changes.addAwayPeriod(AWAY);
            runTheSavingThread();

            assertThat(log.lines()).containsExactly(
                    "WARNING failed in the background: saving the battery level (UncheckedIOException)");
        }
        assertThat(new AwayFile(folder.resolve("away.json")).periods()).containsExactly(AWAY);
    }
}
