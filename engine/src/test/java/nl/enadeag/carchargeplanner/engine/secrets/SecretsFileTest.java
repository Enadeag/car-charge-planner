package nl.enadeag.carchargeplanner.engine.secrets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import nl.enadeag.carchargeplanner.engine.config.ProviderId;
import nl.enadeag.carchargeplanner.engine.store.StoredJson;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Map;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** The private file the keys go to without a usable keychain. */
class SecretsFileTest {

    private static final String SECRET = "SUPER-SECRET-VALUE-42";
    private static final ProviderId OPEN_METEO = new ProviderId("open-meteo");

    @TempDir
    Path folder;

    private Path path() {
        return folder.resolve("secrets.json");
    }

    private ApiKeys withoutKeychain() {
        return new ApiKeys(new NoKeychain(), new SecretsFile(path()));
    }

    private static Secrets secrets(String providerId, String value) {
        return new Secrets(Map.of(new ProviderId(providerId), Map.of("api_key", value)));
    }

    private String mode(Path file) throws IOException {
        return PosixFilePermissions.toString(Files.getPosixFilePermissions(file));
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void secretsAreWrittenPrivateAndReadBack() throws Exception {
        var both = new Secrets(Map.of(OPEN_METEO, Map.of("api_key", SECRET), new ProviderId("forecast-solar"),
                Map.of("api_key", "other")));
        withoutKeychain().save(both);

        assertThat(mode(path())).isEqualTo("rw-------");
        assertThat(withoutKeychain().load()).isEqualTo(both);
        assertThat(folder.resolve("secrets.json.tmp")).doesNotExist();
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void aLeftoverTemporaryFileOpenToOthersDoesNotMakeTheFileOpen() throws Exception {
        var leftover = folder.resolve("secrets.json.tmp");
        Files.writeString(leftover, "old leftover");
        Files.setPosixFilePermissions(leftover, PosixFilePermissions.fromString("rw-rw-rw-"));

        withoutKeychain().save(secrets("open-meteo", SECRET));

        assertThat(mode(path())).isEqualTo("rw-------");
    }

    @Test
    void emptyValuesAreNotStored() throws Exception {
        withoutKeychain().save(new Secrets(Map.of(OPEN_METEO, Map.of("api_key", ""), new ProviderId("forecast-solar"), Map.of())));

        assertThat(withoutKeychain().load()).isEqualTo(Secrets.NONE);
        assertThat(Files.readString(path())).doesNotContain("api_key");
    }

    @Test
    void noFileMeansNoSecrets() throws SecretsProblem {
        assertThat(withoutKeychain().load()).isEqualTo(Secrets.NONE);
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void aFileOpenToOthersIsCorrectedWhenRead() throws Exception {
        withoutKeychain().save(secrets("open-meteo", SECRET));
        Files.setPosixFilePermissions(path(), PosixFilePermissions.fromString("rw-r--r--"));

        assertThat(withoutKeychain().load().of(OPEN_METEO)).containsEntry("api_key", SECRET);
        assertThat(mode(path())).isEqualTo("rw-------");
    }

    @Test
    void theFileHoldsTheKeysByProviderIdAsPlainTexts() throws Exception {
        var written = "{\"forecast-solar\":{\"api_key\":\"other\"},\"open-meteo\":{\"api_key\":\"" + SECRET + "\"}}";
        Files.writeString(path(), written);
        var secrets = withoutKeychain().load();
        assertThat(secrets.of(OPEN_METEO)).containsEntry("api_key", SECRET);

        withoutKeychain().save(secrets);

        var withVersion = "{\"version\":1," + written.substring(1);
        assertThat(StoredJson.parse(Files.readString(path()))).isEqualTo(StoredJson.parse(withVersion));
    }

    @Test
    void aBrokenFileIsReportedWithoutQuotingIt() throws IOException {
        Files.writeString(path(), "{\"open-meteo\": {\"api_key\": \"" + SECRET + "\" oops}}");

        assertThatThrownBy(() -> withoutKeychain().load()).isInstanceOf(SecretsProblem.class)
                .hasMessageContaining("not valid JSON").hasMessageNotContaining(SECRET);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"api_key\": 5}", "{\"open-meteo\": {\"api_key\": 5}}", "{\"open-meteo\": [\"x\"]}"})
    void aFileOfTheWrongShapeIsRefused(String text) throws IOException {
        Files.writeString(path(), text);
        assertThatThrownBy(() -> withoutKeychain().load()).isInstanceOf(SecretsProblem.class).hasMessageContaining("texts only");
    }

    @Test
    void aFileWithAWrongProviderIdIsRefusedWithoutQuotingItsValues() throws IOException {
        Files.writeString(path(), "{\"Open Meteo\": {\"api_key\": \"" + SECRET + "\"}}");

        assertThatThrownBy(() -> withoutKeychain().load()).isInstanceOf(SecretsProblem.class)
                .hasMessageContaining("\"Open Meteo\" is not a provider id").hasMessageNotContaining(SECRET);
    }

    @Test
    void namesWithOddCharactersAreNotWritten() {
        var evil = new Secrets(Map.of(OPEN_METEO, Map.of("api key\n[evil]", "x")));
        assertThatThrownBy(() -> withoutKeychain().save(evil)).isInstanceOf(SecretsProblem.class);
        assertThat(path()).doesNotExist();
    }

    @Test
    void aValueWithQuotesAndNewlinesSurvives() throws SecretsProblem {
        var awkward = "a\"b\\c\nd";
        withoutKeychain().save(secrets("open-meteo", awkward));
        assertThat(withoutKeychain().load().of(OPEN_METEO)).containsEntry("api_key", awkward);
    }

    @Test
    @DisabledOnOs(OS.WINDOWS) // a read-only folder does not stop writing there on Windows
    void aFailedWriteLeavesTheOldFileAndNoTemporaryOne() throws Exception {
        withoutKeychain().save(secrets("open-meteo", "old"));
        Files.setPosixFilePermissions(folder, PosixFilePermissions.fromString("r-x------"));
        try {
            assertThatThrownBy(() -> withoutKeychain().save(secrets("open-meteo", SECRET)))
                    .isInstanceOf(SecretsProblem.class).hasMessageNotContaining(SECRET);
        } finally {
            Files.setPosixFilePermissions(folder, PosixFilePermissions.fromString("rwx------"));
        }
        assertThat(withoutKeychain().load().of(OPEN_METEO)).containsEntry("api_key", "old");
        assertThat(folder.resolve("secrets.json.tmp")).doesNotExist();
    }

    @Test
    void secretsNeverReachTheLog() throws SecretsProblem, IOException {
        var logged = new StringBuilder();
        var capture = new Handler() {
            @Override
            public void publish(LogRecord record) {
                logged.append(record.getMessage()).append('\n');
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        };
        var root = Logger.getLogger("");
        var previous = root.getLevel();
        root.addHandler(capture);
        root.setLevel(Level.ALL);
        try {
            withoutKeychain().save(secrets("open-meteo", SECRET));
            withoutKeychain().load();
            new ApiKeys(new MemoryKeychain(), new SecretsFile(path())).load(); // the move into a keychain
            Files.writeString(path(), "{\"open-meteo\": {\"api_key\": \"" + SECRET + "\" oops}}");
            assertThatThrownBy(() -> withoutKeychain().load()).isInstanceOf(SecretsProblem.class);
            withoutKeychain().save(secrets("open-meteo", "new")); // keeps the damaged file aside
        } finally {
            root.removeHandler(capture);
            root.setLevel(previous);
        }
        assertThat(logged.toString()).doesNotContain(SECRET);
    }

    @Test
    void aFileOfANewerVersionIsRefusedAsWrittenByANewerApp() throws IOException {
        Files.writeString(path(), "{\"version\": 2, \"open-meteo\": {\"api_key\": \"" + SECRET + "\"}}");

        assertThatThrownBy(() -> withoutKeychain().load()).isInstanceOf(SecretsProblem.class)
                .hasMessageContaining("written by a newer version of the app").hasMessageNotContaining(SECRET);
    }

    @Test
    void aFileWithoutAVersionIsRead() throws Exception {
        Files.writeString(path(), "{\"open-meteo\": {\"api_key\": \"" + SECRET + "\"}}");

        assertThat(withoutKeychain().load().of(OPEN_METEO)).containsEntry("api_key", SECRET);
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void aBrokenFileIsKeptAsideAsPrivateAsItWasWhenNewKeysAreSaved() throws Exception {
        var broken = "{\"open-meteo\": {\"api_key\": \"" + SECRET + "\" oops}}";
        Files.writeString(path(), broken);
        Files.setPosixFilePermissions(path(), PosixFilePermissions.fromString("rw-------"));

        withoutKeychain().save(secrets("open-meteo", "new"));

        assertThat(withoutKeychain().load().of(OPEN_METEO)).containsEntry("api_key", "new");
        try (var kept = Files.list(folder)) {
            assertThat(kept.filter(file -> file.getFileName().toString().startsWith("secrets.json.damaged-")))
                    .singleElement().satisfies(file -> {
                        assertThat(file).hasContent(broken);
                        assertThat(mode(file)).isEqualTo("rw-------");
                    });
        }
    }

    @Test
    void aBrokenFileIsKeptAsideWhenTheKeysGoToTheKeychain() throws Exception {
        var broken = "{\"open-meteo\": oops}";
        Files.writeString(path(), broken);
        var keys = new ApiKeys(new MemoryKeychain(), new SecretsFile(path()));

        keys.save(secrets("open-meteo", SECRET));

        assertThat(path()).doesNotExist();
        assertThat(keys.load().of(OPEN_METEO)).containsEntry("api_key", SECRET);
        try (var kept = Files.list(folder)) {
            assertThat(kept.filter(file -> file.getFileName().toString().startsWith("secrets.json.damaged-")))
                    .singleElement().satisfies(file -> assertThat(file).hasContent(broken));
        }
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void makingTheFilePrivateSaysWhetherItHadToChangeItsMode() throws Exception {
        var file = new SecretsFile(path());
        file.write(secrets("open-meteo", SECRET));
        Files.setPosixFilePermissions(path(), PosixFilePermissions.fromString("rw-r--r--"));

        assertThat(file.makePrivate()).isTrue();
        assertThat(mode(path())).isEqualTo("rw-------");
        assertThat(file.makePrivate()).isFalse();
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    void withoutFileModesThereIsNothingToMakePrivate() throws Exception {
        var file = new SecretsFile(path());
        file.write(secrets("open-meteo", SECRET));

        assertThat(file.makePrivate()).isFalse();
    }

    @Test
    void aBadNameIsRefusedBeforeTheFileIsWritten() {
        var evil = new Secrets(Map.of(OPEN_METEO, Map.of("api key", "x")));

        assertThatThrownBy(() -> new SecretsFile(path()).write(evil)).isInstanceOf(SecretsProblem.class)
                .hasMessage("A setting name has characters that are not allowed");
        assertThat(folder.resolve("secrets.json")).doesNotExist();
        assertThat(folder.resolve("secrets.json.tmp")).doesNotExist();
    }

    @Test
    void whatTheParserQuotesIsNotShownForTheSecretsFile() throws IOException {
        // the parser's own message would quote the bare word, which is the key
        Files.writeString(path(), "{\"open-meteo\": {\"api_key\": SUPERSECRETVALUE42}}");

        assertThatThrownBy(() -> withoutKeychain().load()).isInstanceOf(SecretsProblem.class)
                .hasMessage(path() + ": not valid JSON (line 1, column 28). Fix it or delete it and enter the keys again.");
    }

    @Test
    void aFileThatCannotBeUsedIsToldToBeFixedOrDeleted() throws IOException {
        Files.writeString(path(), "[]");

        assertThatThrownBy(() -> withoutKeychain().load()).isInstanceOf(SecretsProblem.class)
                .hasMessageEndingWith(". Fix it or delete it and enter the keys again.")
                .hasMessageStartingWith(path() + ": ");
    }

    @Test
    void aFileThatCannotBeReadIsReportedAsSuch() throws IOException {
        Files.createDirectory(path());
        try {
            assertThatThrownBy(() -> withoutKeychain().load()).isInstanceOf(SecretsProblem.class)
                    .hasMessageStartingWith("Could not read " + path() + " (")
                    .hasMessageNotContaining("Fix it");
        } finally {
            if (path().getFileSystem().supportedFileAttributeViews().contains("posix")) {
                Files.setPosixFilePermissions(path(), PosixFilePermissions.fromString("rwx------"));
            }
        }
    }
}
