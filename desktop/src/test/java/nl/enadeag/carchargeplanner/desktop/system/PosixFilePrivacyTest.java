package nl.enadeag.carchargeplanner.desktop.system;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import nl.enadeag.carchargeplanner.engine.store.FilePrivacy;
import nl.enadeag.carchargeplanner.engine.store.FilePrivacyContractTest;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

/**
 * The desktop's file privacy against the contract every one meets, and the file modes it keeps others out with: what
 * the contract cannot check without POSIX types.
 */
class PosixFilePrivacyTest extends FilePrivacyContractTest {

    private final PosixFilePrivacy privacy = new PosixFilePrivacy();

    @Override
    protected FilePrivacy privacy() {
        return privacy;
    }

    private static String mode(Path path) throws IOException {
        return PosixFilePermissions.toString(Files.getPosixFilePermissions(path));
    }

    private static void setMode(Path path, String mode) throws IOException {
        Files.setPosixFilePermissions(path, PosixFilePermissions.fromString(mode));
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void aNewFileIsReadableAndWritableByItsOwnerOnly() throws IOException {
        assertThat(mode(newFile(folder.resolve("data.json")))).isEqualTo("rw-------");
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void aNewFolderAndTheMissingOnesAboveItAreTheOwnersOnly() throws IOException {
        var app = newFolder(folder.resolve("config").resolve("app"));

        assertThat(mode(app)).isEqualTo("rwx------");
        assertThat(mode(app.getParent())).isEqualTo("rwx------");
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void aFileOthersCanReadIsMadeTheOwnersOnly() throws IOException {
        var file = Files.writeString(folder.resolve("secrets.json"), "{}");
        setMode(file, "rw-r--r--");

        assertThat(privacy.tighten(file)).isTrue();
        assertThat(mode(file)).isEqualTo("rw-------");
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void aFolderOthersCanEnterIsMadeTheOwnersOnly() throws IOException {
        var app = Files.createDirectory(folder.resolve("app"));
        setMode(app, "rwxr-xr-x");

        assertThat(privacy.tighten(app)).isTrue();
        assertThat(mode(app)).isEqualTo("rwx------");
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void aFileTheOwnerCannotWriteIsMadeWritableAgain() throws IOException {
        var file = Files.writeString(folder.resolve("data.json"), "{}");
        setMode(file, "r--------");

        assertThat(privacy.tighten(file)).isTrue();
        assertThat(mode(file)).isEqualTo("rw-------");
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void whereFilesHaveModesAMissingPathIsRefused() {
        assertThatThrownBy(() -> privacy.tighten(folder.resolve("missing.json")))
                .isInstanceOf(NoSuchFileException.class);
    }

    @Test
    void onAFileSystemWithoutModesNothingIsAddedOrChanged() throws IOException {
        // a zip file system has no file modes, as Windows has none
        try (var modeless = FileSystems.newFileSystem(folder.resolve("modeless.zip"), Map.of("create", "true"))) {
            var file = Files.writeString(modeless.getPath("data.json"), "{}");

            assertThat(privacy.newFileAttributes(file)).isEmpty();
            assertThat(privacy.newFolderAttributes(modeless.getPath("app"))).isEmpty();
            assertThat(privacy.tighten(file)).isFalse();
            assertThat(privacy.tighten(modeless.getPath("missing.json"))).isFalse();
        }
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    void onWindowsNothingIsAddedOrChanged() throws IOException {
        var file = Files.writeString(folder.resolve("data.json"), "{}");

        assertThat(privacy.newFileAttributes(file)).isEmpty();
        assertThat(privacy.newFolderAttributes(folder)).isEmpty();
        assertThat(privacy.tighten(file)).isFalse();
    }
}
