package nl.enadeag.carchargeplanner.platform.windows;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import nl.enadeag.carchargeplanner.desktop.system.PosixFilePrivacy;
import nl.enadeag.carchargeplanner.engine.config.ProviderId;
import nl.enadeag.carchargeplanner.engine.secrets.ApiKeys;
import nl.enadeag.carchargeplanner.engine.secrets.KeyStorage;
import nl.enadeag.carchargeplanner.engine.secrets.KeychainException;
import nl.enadeag.carchargeplanner.engine.secrets.Secrets;
import nl.enadeag.carchargeplanner.engine.secrets.SecretsFile;
import nl.enadeag.carchargeplanner.engine.store.PrivateFiles;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

/**
 * Against the real Credential Manager, under a name of its own that it removes again. Over ssh there is no logon
 * session (error 1312): the tests that need one are skipped then, and the one about its absence runs instead.
 */
@EnabledOnOs(OS.WINDOWS)
class CredentialManagerKeychainTest {

    private static final PrivateFiles FILES = new PrivateFiles(new PosixFilePrivacy());

    private static final List<String> NAMES = List.of("probe", "forecast/open-meteo/api_key", "index");

    @TempDir
    Path folder;

    private final CredentialManagerKeychain keychain =
            new CredentialManagerKeychain("car-charge-planner-test-" + UUID.randomUUID());
    private boolean loggedOn;

    @BeforeEach
    void findOutWhetherThereIsALogonSession() throws KeychainException {
        try {
            keychain.write("probe", "x");
            loggedOn = true;
        } catch (KeychainException e) {
            if (!e.getMessage().contains("error 1312")) {
                throw e;
            }
        }
    }

    @AfterEach
    void removeWhatTheTestWrote() throws KeychainException {
        if (!loggedOn) {
            return;
        }
        for (var name : NAMES) {
            keychain.delete(name);
        }
    }

    @Test
    void aValueIsWrittenReadBackReplacedAndDeleted() throws KeychainException {
        assumeTrue(loggedOn, "no logon session, as over ssh");
        assertThat(keychain.read("forecast/open-meteo/api_key")).isEmpty();
        keychain.write("forecast/open-meteo/api_key", "FIRST-KEY");
        keychain.write("forecast/open-meteo/api_key", "SECOND-KEY-ëß");
        assertThat(keychain.read("forecast/open-meteo/api_key")).contains("SECOND-KEY-ëß");
        keychain.delete("forecast/open-meteo/api_key");
        keychain.delete("forecast/open-meteo/api_key"); // already gone: nothing happens
        assertThat(keychain.read("forecast/open-meteo/api_key")).isEmpty();
    }

    @Test
    void theKeysAreKeptInTheCredentialManager() throws Exception {
        assumeTrue(loggedOn, "no logon session, as over ssh");
        var keys = new ApiKeys(keychain, new SecretsFile(folder.resolve("secrets.json"), FILES));
        var secrets = new Secrets(Map.of(new ProviderId("open-meteo"), Map.of("api_key", "OM-SECRET-1")));

        keys.save(secrets);

        assertThat(keys.load()).isEqualTo(secrets);
        assertThat(keys.storage()).isEqualTo(KeyStorage.KEYCHAIN);
        assertThat(folder.resolve("secrets.json")).doesNotExist();
    }

    @Test
    void withoutALogonSessionThereIsNoKeychainAndTheKeysGoToTheFile() throws Exception {
        assumeFalse(loggedOn, "a logon session, so the Credential Manager works");
        assertThatThrownBy(() -> keychain.write("probe", "x")).isInstanceOfSatisfying(KeychainException.class,
                e -> assertThat(e.kind()).isEqualTo(KeychainException.Kind.UNAVAILABLE));
        var keys = new ApiKeys(keychain, new SecretsFile(folder.resolve("secrets.json"), FILES));
        var secrets = new Secrets(Map.of(new ProviderId("open-meteo"), Map.of("api_key", "OM-SECRET-1")));

        keys.save(secrets);

        assertThat(keys.storage()).isEqualTo(KeyStorage.FILE);
        assertThat(keys.load()).isEqualTo(secrets);
    }
}
