package io.github.enadeag.carchargeplanner.engine.secrets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.enadeag.carchargeplanner.engine.config.ProviderId;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import tools.jackson.databind.json.JsonMapper;

class ApiKeysTest {

    private static final ProviderId OPEN_METEO = new ProviderId("open-meteo");
    private static final Secrets KEYS = new Secrets(Map.of(
            OPEN_METEO, Map.of("api_key", "OM-SECRET-1"),
            new ProviderId("forecast-solar"), Map.of("api_key", "FS-SECRET-2")));

    @TempDir
    Path folder;

    private final MemoryKeychain keychain = new MemoryKeychain();

    /** Refuses to store anything, as the Credential Manager does over ssh, without a logon session. */
    private static final class FailingWrites extends MemoryKeychain {
        @Override
        public void write(String name, String value) throws KeychainException {
            throw new KeychainException("A specified logon session does not exist (1312)");
        }
    }

    /** Says it stored a value and keeps nothing. */
    private static final class Forgetful extends MemoryKeychain {
        @Override
        public void write(String name, String value) {
        }
    }

    private static final class Unreadable extends MemoryKeychain {
        @Override
        public Optional<String> read(String name) throws KeychainException {
            throw new KeychainException("locked");
        }
    }

    private SecretsFile file() {
        return new SecretsFile(folder.resolve("secrets.json"));
    }

    private ApiKeys keys(Keychain keychain) {
        return new ApiKeys(keychain, file());
    }

    @Test
    void eachValueIsAnEntryNamedByProviderAndKeyAndAnIndexListsTheNames() throws Exception {
        keys(keychain).save(KEYS);

        assertThat(keychain.entries).containsEntry("forecast/open-meteo/api_key", "OM-SECRET-1")
                .containsEntry("forecast/forecast-solar/api_key", "FS-SECRET-2");
        assertThat(new JsonMapper().readValue(keychain.entries.get("index"), String[].class))
                .containsExactly("forecast/forecast-solar/api_key", "forecast/open-meteo/api_key");
        try (var files = Files.list(folder)) {
            assertThat(files).isEmpty();
        }
    }

    @Test
    void savedSecretsAreReadBackAndWhatIsLeftOutIsForgotten() throws SecretsProblem {
        var keys = keys(keychain);
        keys.save(KEYS);
        assertThat(keys.load()).isEqualTo(KEYS);

        keys.save(new Secrets(Map.of(OPEN_METEO, Map.of("api_key", "OM-NEW", "empty", ""))));

        assertThat(keys.load()).isEqualTo(new Secrets(Map.of(OPEN_METEO, Map.of("api_key", "OM-NEW"))));
        assertThat(keychain.entries).containsOnlyKeys("index", "forecast/open-meteo/api_key");
    }

    @Test
    void aFileFoundWhileAKeychainIsUsableMovesIntoItAndIsRemoved() throws SecretsProblem {
        file().write(KEYS);

        assertThat(keys(keychain).load()).isEqualTo(KEYS);
        assertThat(file().path()).doesNotExist();
        assertThat(keychain.entries).containsEntry("forecast/open-meteo/api_key", "OM-SECRET-1");
        assertThat(keys(keychain).load()).isEqualTo(KEYS);
    }

    static Stream<Keychain> keychainsThatDoNotKeepValues() {
        return Stream.of(new FailingWrites(), new Forgetful());
    }

    @ParameterizedTest
    @MethodSource("keychainsThatDoNotKeepValues")
    void theFileStaysAndIsUsedWhenTheMoveDoesNotWork(Keychain broken) throws SecretsProblem {
        file().write(KEYS);
        var keys = keys(broken);

        assertThat(keys.load()).isEqualTo(KEYS);
        assertThat(file().path()).exists();
        assertThat(keys.load()).isEqualTo(KEYS);
        assertThat(keys.storage()).isEqualTo(KeyStorage.FILE);
    }

    @Test
    void whenTheKeychainRefusesASaveTheKeysGoToTheFile() throws SecretsProblem {
        var keys = keys(new FailingWrites());
        keys.save(KEYS);

        assertThat(file().path()).exists();
        assertThat(keys.load()).isEqualTo(KEYS);
    }

    @Test
    void aKeychainThatCannotBeReadCountsAsNone() throws SecretsProblem {
        var keys = keys(new Unreadable());
        assertThat(keys.load()).isEqualTo(Secrets.NONE);

        keys.save(KEYS);

        assertThat(keys.load()).isEqualTo(KEYS);
        assertThat(keys.storage()).isEqualTo(KeyStorage.FILE);
    }

    @Test
    void withoutAKeychainTheFileIsUsedAndTheAppCanSaySo() throws SecretsProblem {
        var keys = keys(new NoKeychain());
        keys.save(KEYS);

        assertThat(keys.load()).isEqualTo(KEYS);
        assertThat(keys.storage()).isEqualTo(KeyStorage.FILE);
    }

    @Test
    void withAKeychainAndNoFileTheKeychainIsWhereTheKeysAre() {
        assertThat(keys(keychain).storage()).isEqualTo(KeyStorage.KEYCHAIN);
    }

    @Test
    void aDamagedIndexIsReportedWithoutAValueAndASaveRepairsIt() throws SecretsProblem {
        keychain.entries.put("index", "not a list");
        var keys = keys(keychain);

        assertThatThrownBy(keys::load).isInstanceOf(SecretsProblem.class).hasMessageContaining("could not be read");
        keys.save(KEYS);
        assertThat(keys.load()).isEqualTo(KEYS);
    }

    @Test
    void anIndexNameWithoutAProviderIdIsADamagedIndex() {
        keychain.entries.put("index", "[\"forecast/Open Meteo/api_key\"]");
        keychain.entries.put("forecast/Open Meteo/api_key", "OM-SECRET-1");

        assertThatThrownBy(keys(keychain)::load).isInstanceOf(SecretsProblem.class)
                .hasMessageContaining("could not be read").hasMessageNotContaining("OM-SECRET-1");
    }

    @Test
    void namesThatCouldNotBeSplitUpAgainAreRefused() {
        var odd = new Secrets(Map.of(OPEN_METEO, Map.of("api/key", "x")));
        assertThatThrownBy(() -> keys(keychain).save(odd)).isInstanceOf(SecretsProblem.class);
        assertThat(keychain.entries).isEmpty();
    }
}
