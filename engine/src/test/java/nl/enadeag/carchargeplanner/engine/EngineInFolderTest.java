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
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EngineInFolderTest {

    private static final PrivateFiles FILES = new PrivateFiles(FilePrivacy.NONE);
    private static final Path EXAMPLE = Path.of("docs", "example-engine.json");
    /** The morning the recorded Open-Meteo answer starts on. */
    private static final Instant NOW = Instant.parse("2026-10-06T07:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
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
        return new ApiKeys(new NoKeychain(), new SecretsFile(folder.resolve("secrets.json"), FILES, CLOCK),
                KeyFallback.PRIVATE_FILE, Clock.systemUTC());
    }

    @Test
    void theSettingsFileIsReadFromTheDataFolder() throws IOException, ConfigProblem {
        Files.copy(EXAMPLE, folder.resolve("engine.json"));

        assertThat(inFolder().config()).isEqualTo(new ConfigFile(EXAMPLE, FILES, CLOCK).read());
    }

    @Test
    void aSettingsFileKeptElsewhereIsReadFromThere() throws IOException, ConfigProblem {
        var configFile = folder.resolve("config").resolve("planner.json");
        var data = folder.resolve("data");
        Files.createDirectories(configFile.getParent());
        Files.copy(EXAMPLE, configFile);
        var engine = Engine.inFolder(data, configFile, SUNNY, FILES, apiKeys(), notice -> { },
                CLOCK, new ManualScheduler());

        assertThat(engine.config()).isEqualTo(new ConfigFile(EXAMPLE, FILES, CLOCK).read());
        assertThat(data.resolve("engine.json")).doesNotExist();
    }

    @Test
    void aSettingsFileKeptElsewhereIsSavedThereAndNotInTheDataFolder() throws IOException, ConfigProblem {
        var configFile = folder.resolve("config").resolve("planner.json");
        var data = folder.resolve("data");
        var example = new ConfigFile(EXAMPLE, FILES, CLOCK).read();
        var engine = Engine.inFolder(data, configFile, SUNNY, FILES, apiKeys(), notice -> { },
                CLOCK, new ManualScheduler());

        engine.saveConfig(example);

        assertThat(new ConfigFile(configFile, FILES, CLOCK).read()).isEqualTo(example);
        assertThat(data.resolve("engine.json")).doesNotExist();
    }

    // The away and trip files have no such test: the engine leaves a damaged one as it is, so none can set one aside.
    @Test
    void aSettingsFileSetAsideIsNamedWithTheEnginesClock() throws IOException {
        Files.writeString(folder.resolve("engine.json"), "{oops");

        var aside = inFolder().setConfigAside();

        assertThat(aside).hasFileName("engine.json.damaged-20261006-070000").hasContent("{oops");
    }

    @Test
    void aDamagedBatteryFileIsSetAsideUnderTheEnginesClock() throws IOException {
        Files.writeString(folder.resolve("battery.json"), "{oops");

        inFolder().setBatteryLevel(new Percent(60));

        assertThat(folder.resolve("battery.json.damaged-20261006-070000")).hasContent("{oops");
    }

    @Test
    void aDamagedCachedForecastIsSetAsideUnderTheEnginesClock() throws IOException {
        Files.copy(EXAMPLE, folder.resolve("engine.json"));
        Files.createDirectories(folder.resolve("forecasts"));
        Files.writeString(folder.resolve("forecasts").resolve("forecast-open-meteo.json"), "{oops");

        inFolder().round(ForecastUse.ASK_PROVIDERS);

        assertThat(folder.resolve("forecasts").resolve("forecast-open-meteo.json.damaged-20261006-070000"))
                .hasContent("{oops");
    }

    @Test
    void aDamagedFileOfWhatWasToldAboutTheTripsIsSetAsideUnderTheEnginesClock() throws IOException {
        Files.writeString(folder.resolve("trip-notices.json"), "{oops");

        withATripTooLongForAFullBattery().round(ForecastUse.ASK_PROVIDERS);

        assertThat(folder.resolve("trip-notices.json.damaged-20261006-070000")).hasContent("{oops");
    }

    @Test
    void aDamagedFileOfTheAnnouncedWindowsIsSetAsideUnderTheEnginesClock() throws IOException {
        var engine = withATripTooLongForAFullBattery();
        var windowDue = engine.nextNoticeDue(engine.round(ForecastUse.ASK_PROVIDERS)).orElseThrow();
        Files.writeString(folder.resolve("announced.json"), "{oops");

        inFolder(windowDue).round(ForecastUse.ASK_PROVIDERS);

        var at = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss", Locale.ROOT).withZone(ZoneOffset.UTC);
        assertThat(folder.resolve("announced.json.damaged-" + at.format(windowDue))).hasContent("{oops");
    }

    /** The engine on the example settings, with a trip that makes a round ask whether the assumed level is right. */
    private Engine withATripTooLongForAFullBattery() throws IOException {
        Files.copy(EXAMPLE, folder.resolve("engine.json"));
        var engine = inFolder();
        engine.addTrip(new Trip(NOW.plus(Duration.ofHours(24)), NOW.plus(Duration.ofHours(32)), 300, false));
        return engine;
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
        var engine = inFolder();
        // longer than a full battery reaches, so the round asks whether the assumed level is right
        engine.addTrip(new Trip(NOW.plus(Duration.ofHours(24)), NOW.plus(Duration.ofHours(32)), 300, false));

        var outlook = engine.round(ForecastUse.ASK_PROVIDERS);

        assertThat(folder.resolve("forecasts")).isNotEmptyDirectory();
        assertThat(folder.resolve("trip-notices.json")).exists();

        var windowDue = engine.nextNoticeDue(outlook).orElseThrow();
        inFolder(windowDue).round(ForecastUse.ASK_PROVIDERS);

        assertThat(folder.resolve("announced.json")).exists();
    }
}
