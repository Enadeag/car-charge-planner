package nl.enadeag.carchargeplanner.engine;

import static org.assertj.core.api.Assertions.assertThat;

import nl.enadeag.carchargeplanner.engine.away.OneOffAwayPeriod;
import nl.enadeag.carchargeplanner.engine.config.ConfigFile;
import nl.enadeag.carchargeplanner.engine.config.ConfigProblem;
import nl.enadeag.carchargeplanner.engine.config.Percent;
import nl.enadeag.carchargeplanner.engine.forecast.HttpGet;
import nl.enadeag.carchargeplanner.engine.forecast.HttpGet.Reply;
import nl.enadeag.carchargeplanner.engine.forecast.Recorded;
import nl.enadeag.carchargeplanner.engine.forecast.ReplayHttp;
import nl.enadeag.carchargeplanner.engine.secrets.ApiKeys;
import nl.enadeag.carchargeplanner.engine.secrets.KeyFallback;
import nl.enadeag.carchargeplanner.engine.secrets.NoKeychain;
import nl.enadeag.carchargeplanner.engine.secrets.SecretsFile;
import nl.enadeag.carchargeplanner.engine.store.FilePrivacy;
import nl.enadeag.carchargeplanner.engine.store.PrivateFiles;
import nl.enadeag.carchargeplanner.engine.trip.Trip;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EngineInFolderTest {

    private static final PrivateFiles FILES = new PrivateFiles(FilePrivacy.NONE);
    private static final Path EXAMPLE = Path.of("docs", "example-engine.json");
    /** The morning the recorded Open-Meteo answer starts on. */
    private static final Instant NOW = Instant.parse("2026-10-06T07:00:00Z");
    private static final HttpGet SUNNY = ReplayHttp.always(new Reply(200, Recorded.answer("open_meteo_3days.json")));

    @TempDir
    Path folder;

    private Engine inFolder(Instant now) {
        return Engine.inFolder(folder, SUNNY, FILES, apiKeys(), notice -> { }, Clock.fixed(now, ZoneOffset.UTC),
                new ManualScheduler());
    }

    private Engine inFolder() {
        return inFolder(NOW);
    }

    private ApiKeys apiKeys() {
        return new ApiKeys(new NoKeychain(), new SecretsFile(folder.resolve("secrets.json"), FILES),
                KeyFallback.PRIVATE_FILE, Clock.systemUTC());
    }

    @Test
    void theConfigFileIsReadFromTheDataFolder() throws IOException, ConfigProblem {
        Files.copy(EXAMPLE, folder.resolve("engine.json"));

        assertThat(inFolder().config()).isEqualTo(new ConfigFile(EXAMPLE, FILES).read());
    }

    @Test
    void aConfigFileKeptElsewhereIsSavedThereAndNotInTheDataFolder() throws IOException, ConfigProblem {
        var configFile = folder.resolve("config").resolve("planner.json");
        var data = folder.resolve("data");
        var example = new ConfigFile(EXAMPLE, FILES).read();
        var engine = Engine.inFolder(data, configFile, SUNNY, FILES, apiKeys(), notice -> { },
                Clock.fixed(NOW, ZoneOffset.UTC), new ManualScheduler());

        engine.saveConfig(example);

        assertThat(new ConfigFile(configFile, FILES).read()).isEqualTo(example);
        assertThat(data.resolve("engine.json")).doesNotExist();
    }

    @Test
    void anAwayPeriodIsKeptInAwayJson() throws IOException {
        var period = new OneOffAwayPeriod(NOW.plus(Duration.ofHours(2)), NOW.plus(Duration.ofHours(12)));

        inFolder().addAwayPeriod(period);

        assertThat(folder.resolve("away.json")).exists();
        assertThat(inFolder().awayPeriods()).containsExactly(period);
    }

    @Test
    void aTripIsKeptInTripsJson() throws IOException {
        var trip = new Trip(NOW.plus(Duration.ofHours(2)), NOW.plus(Duration.ofHours(12)), 300, false);

        inFolder().addTrip(trip);

        assertThat(folder.resolve("trips.json")).exists();
        assertThat(inFolder().trips()).containsExactly(trip);
    }

    @Test
    void theBatteryLevelIsKeptInBatteryJson() throws IOException {
        inFolder().setBatteryLevel(new Percent(60));

        assertThat(folder.resolve("battery.json")).exists();
    }

    @Test
    void aRoundKeepsTheForecastAndWhatItToldInTheDataFolder() throws IOException {
        Files.copy(EXAMPLE, folder.resolve("engine.json"));
        // longer than a full battery reaches, so the round asks whether the assumed level is right
        var engine = inFolder();
        engine.addTrip(new Trip(NOW.plus(Duration.ofHours(24)), NOW.plus(Duration.ofHours(32)), 300, false));

        var outlook = engine.round(ForecastUse.ASK_PROVIDERS);

        assertThat(folder.resolve("forecasts")).isNotEmptyDirectory();
        assertThat(folder.resolve("trip-notices.json")).exists();

        var windowDue = engine.nextNoticeDue(outlook).orElseThrow();
        inFolder(windowDue).round(ForecastUse.ASK_PROVIDERS);

        assertThat(folder.resolve("announced.json")).exists();
    }
}
