package nl.enadeag.carchargeplanner.desktop.system;

import static org.assertj.core.api.Assertions.assertThat;

import nl.enadeag.carchargeplanner.engine.config.ProviderId;
import nl.enadeag.carchargeplanner.engine.secrets.ApiKeys;
import nl.enadeag.carchargeplanner.engine.secrets.NoKeychain;
import nl.enadeag.carchargeplanner.engine.secrets.Secrets;
import nl.enadeag.carchargeplanner.engine.secrets.SecretsFile;
import nl.enadeag.carchargeplanner.engine.store.PrivateFiles;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

/**
 * The files the engine writes, with the desktop's privacy: readable by their owner only where files have modes, from
 * the moment they exist.
 */
@DisabledOnOs(OS.WINDOWS)
class PrivateFilesOnTheDesktopTest {

    private static final String SECRET = "SUPER-SECRET-VALUE-42";
    private static final ProviderId OPEN_METEO = new ProviderId("open-meteo");

    @TempDir
    Path folder;

    private final PrivateFiles files = new PrivateFiles(new PosixFilePrivacy());

    private static String mode(Path path) throws IOException {
        return PosixFilePermissions.toString(Files.getPosixFilePermissions(path));
    }

    private static void setMode(Path path, String mode) throws IOException {
        Files.setPosixFilePermissions(path, PosixFilePermissions.fromString(mode));
    }

    private ApiKeys keys() {
        return new ApiKeys(new NoKeychain(), new SecretsFile(folder.resolve("secrets.json"), files));
    }

    private static Secrets secrets(String value) {
        return new Secrets(Map.of(OPEN_METEO, Map.of("api_key", value)));
    }

    @Test
    void aFileMadeToAppendToIsOnlyForTheOwnerAndSoAreItsFolders() throws IOException {
        var file = folder.resolve("data").resolve("app.log");

        try (var out = files.append(file)) {
            out.write('x');
        }

        assertThat(mode(folder.resolve("data"))).isEqualTo("rwx------");
        assertThat(mode(file)).isEqualTo("rw-------");
    }

    @Test
    void missingFoldersAreMadeOnlyForTheOwnerAndTheFileToo() throws IOException {
        var file = folder.resolve("config").resolve("app").resolve("data.json");

        files.write(file, "{}");

        assertThat(mode(folder.resolve("config"))).isEqualTo("rwx------");
        assertThat(mode(folder.resolve("config").resolve("app"))).isEqualTo("rwx------");
        assertThat(mode(file)).isEqualTo("rw-------");
    }

    @Test
    void aFileOpenToOthersIsPrivateOnceRewrittenAndItsFolderIsLeftAsItWas() throws IOException {
        var file = Files.writeString(folder.resolve("engine.json"), "{}");
        setMode(file, "rw-r--r--");
        setMode(folder, "rwxr-xr-x");

        files.write(file, "{\"new\": true}");

        assertThat(mode(file)).isEqualTo("rw-------");
        assertThat(mode(folder)).isEqualTo("rwxr-xr-x");
    }

    @Test
    void aLeftoverTemporaryFileOpenToOthersDoesNotMakeTheFileOpen() throws IOException {
        var leftover = Files.writeString(folder.resolve("data.json.tmp"), "old leftover");
        setMode(leftover, "rw-rw-rw-");

        files.write(folder.resolve("data.json"), "{}");

        assertThat(mode(folder.resolve("data.json"))).isEqualTo("rw-------");
        assertThat(folder.resolve("data.json")).hasContent("{}");
    }

    @Test
    void anExistingFolderOpenToOthersIsMadePrivate() throws IOException {
        var app = Files.createDirectory(folder.resolve("app"));
        setMode(app, "rwxr-xr-x");

        files.tighten(app);

        assertThat(mode(app)).isEqualTo("rwx------");
    }

    @Test
    void theSecretsFileIsWrittenForItsOwnerOnly() throws Exception {
        keys().save(secrets(SECRET));

        assertThat(mode(folder.resolve("secrets.json"))).isEqualTo("rw-------");
        assertThat(keys().load().of(OPEN_METEO)).containsEntry("api_key", SECRET);
    }

    @Test
    void aLeftoverTemporarySecretsFileOpenToOthersDoesNotMakeTheFileOpen() throws Exception {
        var leftover = Files.writeString(folder.resolve("secrets.json.tmp"), "old leftover");
        setMode(leftover, "rw-rw-rw-");

        keys().save(secrets(SECRET));

        assertThat(mode(folder.resolve("secrets.json"))).isEqualTo("rw-------");
    }

    @Test
    void aSecretsFileOpenToOthersIsCorrectedWhenRead() throws Exception {
        keys().save(secrets(SECRET));
        setMode(folder.resolve("secrets.json"), "rw-r--r--");

        assertThat(keys().load().of(OPEN_METEO)).containsEntry("api_key", SECRET);
        assertThat(mode(folder.resolve("secrets.json"))).isEqualTo("rw-------");
    }
}
