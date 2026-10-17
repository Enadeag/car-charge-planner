package io.github.enadeag.carchargeplanner.engine.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

class PrivateFilesTest {

    @TempDir
    Path folder;

    private static String mode(Path path) throws IOException {
        return PosixFilePermissions.toString(Files.getPosixFilePermissions(path));
    }

    private static void setMode(Path path, String mode) throws IOException {
        Files.setPosixFilePermissions(path, PosixFilePermissions.fromString(mode));
    }

    @Test
    void theWholeTextReplacesTheFileAndNoTemporaryFileIsLeft() throws IOException {
        var file = folder.resolve("data.json");
        Files.writeString(file, "a much longer old content than the new one");

        PrivateFiles.write(file, "{\"ü\": 1}");

        assertThat(file).hasContent("{\"ü\": 1}");
        assertThat(folder.resolve("data.json.tmp")).doesNotExist();
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void missingFoldersAreMadeOnlyForTheOwnerAndTheFileToo() throws IOException {
        var file = folder.resolve("config").resolve("app").resolve("data.json");

        PrivateFiles.write(file, "{}");

        assertThat(mode(folder.resolve("config"))).isEqualTo("rwx------");
        assertThat(mode(folder.resolve("config").resolve("app"))).isEqualTo("rwx------");
        assertThat(mode(file)).isEqualTo("rw-------");
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void aFileOpenToOthersIsPrivateOnceRewrittenAndItsFolderIsLeftAsItWas() throws IOException {
        var file = Files.writeString(folder.resolve("engine.json"), "{}");
        setMode(file, "rw-r--r--");
        setMode(folder, "rwxr-xr-x");

        PrivateFiles.write(file, "{\"new\": true}");

        assertThat(mode(file)).isEqualTo("rw-------");
        assertThat(mode(folder)).isEqualTo("rwxr-xr-x");
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void aLeftoverTemporaryFileOpenToOthersDoesNotMakeTheFileOpen() throws IOException {
        var leftover = Files.writeString(folder.resolve("data.json.tmp"), "old leftover");
        setMode(leftover, "rw-rw-rw-");

        PrivateFiles.write(folder.resolve("data.json"), "{}");

        assertThat(mode(folder.resolve("data.json"))).isEqualTo("rw-------");
        assertThat(folder.resolve("data.json")).hasContent("{}");
    }

    @Test
    void aFileThatCannotBeWrittenIsLeftAsItWas() throws IOException {
        var file = Files.writeString(folder.resolve("data.json"), "old");
        Files.createDirectory(folder.resolve("data.json.tmp"));

        assertThatThrownBy(() -> PrivateFiles.write(file, "new")).isInstanceOf(IOException.class);
        assertThat(file).hasContent("old");
    }

    @Test
    void aFileWhereTheFolderShouldBeIsReported() throws IOException {
        Files.writeString(folder.resolve("data"), "a file where the folder should be");

        assertThatThrownBy(() -> PrivateFiles.write(folder.resolve("data").resolve("away.json"), "{}"))
                .isInstanceOf(IOException.class);
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void anExistingFolderOpenToOthersIsMadePrivate() throws IOException {
        var app = Files.createDirectory(folder.resolve("app"));
        setMode(app, "rwxr-xr-x");

        PrivateFiles.tighten(app);

        assertThat(mode(app)).isEqualTo("rwx------");
    }

    @Test
    void aMissingFolderIsNotMadeByTightening() {
        PrivateFiles.tighten(folder.resolve("missing"));

        assertThat(folder.resolve("missing")).doesNotExist();
    }
}
