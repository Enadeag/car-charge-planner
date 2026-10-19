package nl.enadeag.carchargeplanner.host;

import static org.assertj.core.api.Assertions.assertThat;

import nl.enadeag.carchargeplanner.LogCapture;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The private file of API keys: beside the config on Linux, and on Windows in the local data folder, where a file from
 * before moves once. The folders stand in for {@code %APPDATA%} and {@code %LOCALAPPDATA%}, so this runs anywhere.
 */
class SecretsLocationTest {

    @TempDir
    Path folder;

    private Path config;
    private Path data;
    private Path old;

    @BeforeEach
    void folders() throws Exception {
        config = Files.createDirectories(folder.resolve("Roaming/car-charge-planner")).resolve("engine.json");
        data = folder.resolve("Local/car-charge-planner");
        old = config.resolveSibling("secrets.json");
    }

    /** A file from before, as private as the app wrote it. */
    private void oldFile() throws Exception {
        Files.writeString(old, "{\"version\": 1, \"open-meteo\": {\"api_key\": \"OM-SECRET-1\"}}");
        if (old.getFileSystem().supportedFileAttributeViews().contains("posix")) {
            Files.setPosixFilePermissions(old, PosixFilePermissions.fromString("rw-------"));
        }
    }

    @Test
    void onLinuxTheFileStaysBesideTheConfig() throws Exception {
        oldFile();

        assertThat(SecretsLocation.file(Os.LINUX, config, data)).isEqualTo(old);
        assertThat(SecretsLocation.moveFromOldPlace(Os.LINUX, config, data)).isEqualTo(old);
        assertThat(old).exists();
        assertThat(data).doesNotExist();
    }

    @Test
    void onWindowsTheFileIsInTheLocalDataFolder() {
        assertThat(SecretsLocation.file(Os.WINDOWS, config, data)).isEqualTo(data.resolve("secrets.json"));
        assertThat(SecretsLocation.moveFromOldPlace(Os.WINDOWS, config, data)).isEqualTo(data.resolve("secrets.json"));
        assertThat(data).as("nothing to move, nothing made").doesNotExist();
    }

    @Test
    void onWindowsAFileFromBeforeMovesOnceAsPrivateAsItWasAndNoCopyStays() throws Exception {
        oldFile();
        var content = Files.readString(old);
        var mode = old.getFileSystem().supportedFileAttributeViews().contains("posix")
                ? Files.getPosixFilePermissions(old) : null;
        var now = data.resolve("secrets.json");

        try (var log = LogCapture.of(SecretsLocation.class)) {
            assertThat(SecretsLocation.moveFromOldPlace(Os.WINDOWS, config, data)).isEqualTo(now);
            assertThat(log.lines()).containsExactly("INFO moved " + old + " to " + now + ", which stays on this "
                    + "computer");
        }

        assertThat(old).doesNotExist();
        assertThat(now).hasContent(content);
        if (mode != null) {
            assertThat(Files.getPosixFilePermissions(now)).isEqualTo(mode);
        }
        assertThat(SecretsLocation.moveFromOldPlace(Os.WINDOWS, config, data)).isEqualTo(now);
        assertThat(now).hasContent(content);
    }

    @Test
    void onWindowsAFileFromBeforeIsNotUsedOnceTheLocalFolderHasOne() throws Exception {
        oldFile();
        var now = Files.writeString(Files.createDirectories(data).resolve("secrets.json"), "{\"version\": 1}");

        try (var log = LogCapture.of(SecretsLocation.class)) {
            assertThat(SecretsLocation.moveFromOldPlace(Os.WINDOWS, config, data)).isEqualTo(now);
            assertThat(log.lines()).singleElement().asString().startsWith("WARNING").contains(old.toString());
        }

        assertThat(now).hasContent("{\"version\": 1}");
        assertThat(old).exists();
    }

    @Test
    void whenTheFileCannotBeMovedItIsUsedWhereItIsAndNotCopied() throws Exception {
        oldFile();
        Files.createDirectories(data.getParent());
        Files.writeString(data, "a file where the folder should be");

        assertThat(SecretsLocation.moveFromOldPlace(Os.WINDOWS, config, data)).isEqualTo(old);

        assertThat(old).exists();
        assertThat(data).isRegularFile();
    }
}
