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
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AnnouncedWindowsTest {

    private static final PrivateFiles FILES = new PrivateFiles(FilePrivacy.NONE);

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
        new AnnouncedWindows(path(), FILES).save(List.of(WINDOW, later));

        assertThat(new AnnouncedWindows(path(), FILES).load()).containsExactly(WINDOW, later);
    }

    /** The shape the file has had since windows were first announced, so an update does not announce them again. */
    @Test
    void aFileInTheShapeKeptSoFarIsRead() throws IOException {
        Files.createDirectories(path().getParent());
        Files.writeString(path(), "[{\"start\":\"2026-07-03T10:00:00Z\",\"end\":\"2026-07-03T12:00:00Z\",\"amps\":16,"
                + "\"powerKw\":3.68,\"solarKwh\":6.9,\"importKwh\":0.46}]");

        assertThat(new AnnouncedWindows(path(), FILES).load()).containsExactly(WINDOW);
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

        assertThat(new AnnouncedWindows(path(), FILES).load()).isEmpty();
    }

    @Test
    void aDamagedWindowIsNamedByItsPlaceInTheFile() throws IOException {
        Files.createDirectories(path().getParent());
        Files.writeString(path(), "[{\"start\":\"2026-07-03T10:00:00Z\",\"end\":\"2026-07-03T12:00:00Z\",\"amps\":16,"
                + "\"powerKw\":3.68,\"solarKwh\":6.9,\"importKwh\":0.46}, {\"start\":\"2026-07-03T13:00:00Z\"}]");
        try (var log = LogCapture.of(StoredFile.class)) {
            assertThat(new AnnouncedWindows(path(), FILES).load()).isEmpty();

            assertThat(log.lines()).containsExactly("WARNING " + path() + " cannot be used: window 2 is not a whole window");
        }
    }
}
