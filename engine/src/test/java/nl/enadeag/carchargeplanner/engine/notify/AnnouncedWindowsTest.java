package nl.enadeag.carchargeplanner.engine.notify;

import static org.assertj.core.api.Assertions.assertThat;

import nl.enadeag.carchargeplanner.LogCapture;
import nl.enadeag.carchargeplanner.engine.plan.Window;
import nl.enadeag.carchargeplanner.engine.store.FilePrivacy;
import nl.enadeag.carchargeplanner.engine.store.PrivateFiles;
import nl.enadeag.carchargeplanner.engine.store.StoredFile;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class AnnouncedWindowsTest {

    private static final PrivateFiles FILES = new PrivateFiles(FilePrivacy.NONE);

    private static final Clock CLOCK = Clock.systemUTC();

    private static final Window WINDOW = new Window(Instant.parse("2026-07-03T10:00:00Z"),
            Instant.parse("2026-07-03T12:00:00Z"), 16, 3.68, 6.9, 0.46);

    @TempDir
    Path folder;

    private Path path() {
        return folder.resolve("data").resolve("announced.json");
    }

    @Test
    void savedWindowsAreLoadedInTheirOrder() {
        var later = new Window(Instant.parse("2026-07-04T09:00:00Z"), Instant.parse("2026-07-04T10:30:00Z"), 6,
                1.38, 2.0, 0.07);
        new AnnouncedWindows(path(), FILES, CLOCK).save(List.of(WINDOW, later));

        assertThat(new AnnouncedWindows(path(), FILES, CLOCK).load()).containsExactly(WINDOW, later);
    }

    /** The shape the file has had since windows were first announced, so an update does not announce them again. */
    @Test
    void aFileInTheShapeKeptSoFarIsRead() throws IOException {
        Files.createDirectories(path().getParent());
        Files.writeString(path(), "[{\"start\":\"2026-07-03T10:00:00Z\",\"end\":\"2026-07-03T12:00:00Z\",\"amps\":16,"
                + "\"powerKw\":3.68,\"solarKwh\":6.9,\"importKwh\":0.46}]");

        assertThat(new AnnouncedWindows(path(), FILES, CLOCK).load()).containsExactly(WINDOW);
    }

    @ParameterizedTest
    @ValueSource(strings = {"not json", "{}", "[{\"start\":\"2026-07-03T10:00:00Z\"}]",
        "[{\"start\":\"later\",\"end\":\"2026-07-03T12:00:00Z\",\"amps\":16,\"powerKw\":3.68,\"solarKwh\":6.9,"
                + "\"importKwh\":0.46}]",
        "[{\"start\":\"2026-07-03T10:00:00Z\",\"end\":\"2026-07-03T12:00:00Z\",\"amps\":16.5,\"powerKw\":3.68,"
                + "\"solarKwh\":6.9,\"importKwh\":0.46}]"})
    void aDamagedFileHoldsNoWindows(String content) throws IOException {
        Files.createDirectories(path().getParent());
        Files.writeString(path(), content);

        assertThat(new AnnouncedWindows(path(), FILES, CLOCK).load()).isEmpty();
    }

    private List<Window> loaded(String start, String end, String amps, String powerKw, String solarKwh,
            String importKwh) throws IOException {
        Files.createDirectories(path().getParent());
        Files.writeString(path(), "[{\"start\":\"2026-07-03T10:00:00Z\",\"end\":\"2026-07-03T12:00:00Z\",\"amps\":16,"
                + "\"powerKw\":3.68,\"solarKwh\":6.9,\"importKwh\":0.46}, {\"start\":\"" + start + "\",\"end\":\"" + end
                + "\",\"amps\":" + amps + ",\"powerKw\":" + powerKw + ",\"solarKwh\":" + solarKwh + ",\"importKwh\":"
                + importKwh + "}]");
        return new AnnouncedWindows(path(), FILES, CLOCK).load();
    }

    @ParameterizedTest
    @CsvSource({"0, true", "-1, false"})
    void aWindowsCurrentIsNotNegative(String amps, boolean read) throws IOException {
        assertThat(loaded("2026-07-04T10:00:00Z", "2026-07-04T11:00:00Z", amps, "3", "3", "0")).hasSize(read ? 2 : 0);
    }

    @ParameterizedTest
    @CsvSource({"0, true", "1.7976931348623157E308, true", "-4.9E-324, false", "1e400, false"})
    void aWindowsPowerAndEnergiesAreFiniteAndNotNegative(String value, boolean read) throws IOException {
        int windows = read ? 2 : 0;
        assertThat(loaded("2026-07-04T10:00:00Z", "2026-07-04T11:00:00Z", "16", value, "3", "0")).hasSize(windows);
        assertThat(loaded("2026-07-04T10:00:00Z", "2026-07-04T11:00:00Z", "16", "3", value, "0")).hasSize(windows);
        assertThat(loaded("2026-07-04T10:00:00Z", "2026-07-04T11:00:00Z", "16", "3", "3", value)).hasSize(windows);
    }

    @ParameterizedTest
    @CsvSource({"1970-01-01T00:00:00Z, 1970-01-01T01:00:00Z, true",
        "1969-12-31T23:59:59.999999999Z, 1970-01-01T01:00:00Z, false",
        "9999-12-31T23:00:00Z, 9999-12-31T23:59:59.999999999Z, true",
        "9999-12-31T23:00:00Z, +10000-01-01T00:00:00Z, false",
        "2026-07-04T10:00:00Z, 2026-07-20T10:00:00Z, true",
        "2026-07-04T10:00:00Z, 2026-07-20T10:00:00.000000001Z, false",
        "1800-01-01T00:00:00Z, 2300-01-01T00:00:00Z, false"})
    void aWindowFallsWithinTheRangeOfMomentsAndLastsAtMostSixteenDays(String start, String end, boolean read)
            throws IOException {
        assertThat(loaded(start, end, "16", "3", "3", "0")).hasSize(read ? 2 : 0);
    }

    @Test
    void aWindowOutsideTheRulesIsNamedByItsPlaceAndTheLimit() throws IOException {
        try (var log = LogCapture.of(StoredFile.class)) {
            loaded("2026-07-04T10:00:00Z", "2026-07-04T11:00:00Z", "16", "3", "1e400", "0");
            loaded("1969-12-31T23:59:59Z", "2026-07-04T11:00:00Z", "16", "3", "3", "0");
            loaded("2026-07-04T10:00:00Z", "2026-07-24T11:00:00Z", "16", "3", "3", "0");

            assertThat(log.lines()).containsExactly(
                    "WARNING " + path() + " cannot be used: window 2 must have a current, a power and energies that "
                            + "are finite numbers of 0 or more",
                    "WARNING " + path() + " cannot be used: window 2 must start at 1970-01-01T00:00:00Z or later and "
                            + "end before +10000-01-01T00:00:00Z",
                    "WARNING " + path() + " cannot be used: window 2 must last at most 16 days");
        }
    }

    @Test
    void aDamagedWindowIsNamedByItsPlaceInTheFile() throws IOException {
        Files.createDirectories(path().getParent());
        Files.writeString(path(), "[{\"start\":\"2026-07-03T10:00:00Z\",\"end\":\"2026-07-03T12:00:00Z\",\"amps\":16,"
                + "\"powerKw\":3.68,\"solarKwh\":6.9,\"importKwh\":0.46}, {\"start\":\"2026-07-03T13:00:00Z\"}]");
        try (var log = LogCapture.of(StoredFile.class)) {
            assertThat(new AnnouncedWindows(path(), FILES, CLOCK).load()).isEmpty();

            assertThat(log.lines()).containsExactly("WARNING " + path() + " cannot be used: window 2 is not a whole window");
        }
    }
}
