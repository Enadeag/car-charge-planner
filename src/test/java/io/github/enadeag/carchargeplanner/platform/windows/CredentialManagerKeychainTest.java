package io.github.enadeag.carchargeplanner.platform.windows;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import io.github.enadeag.carchargeplanner.engine.config.ProviderId;
import io.github.enadeag.carchargeplanner.engine.secrets.ApiKeys;
import io.github.enadeag.carchargeplanner.engine.secrets.KeyStorage;
import io.github.enadeag.carchargeplanner.engine.secrets.KeychainException;
import io.github.enadeag.carchargeplanner.engine.secrets.Secrets;
import io.github.enadeag.carchargeplanner.engine.secrets.SecretsFile;
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
 * session (error 1312) and the test is skipped; it runs in a session where the user is logged in.
 */
@EnabledOnOs(OS.WINDOWS)
class CredentialManagerKeychainTest {

    private static final List<String> NAMES = List.of("probe", "forecast/open-meteo/api_key", "index");

    @TempDir
    Path folder;

    private final CredentialManagerKeychain keychain =
            new CredentialManagerKeychain("car-charge-planner-test-" + UUID.randomUUID());
    private boolean loggedOn;

    @BeforeEach
    void skipWithoutALogonSession() throws KeychainException {
        try {
            keychain.write("probe", "x");
            loggedOn = true;
        } catch (KeychainException e) {
            assumeFalse(e.getMessage().contains("error 1312"), "no logon session, as over ssh: " + e.getMessage());
            throw e;
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
        var keys = new ApiKeys(keychain, new SecretsFile(folder.resolve("secrets.json")));
        var secrets = new Secrets(Map.of(new ProviderId("open-meteo"), Map.of("api_key", "OM-SECRET-1")));

        keys.save(secrets);

        assertThat(keys.load()).isEqualTo(secrets);
        assertThat(keys.storage()).isEqualTo(KeyStorage.KEYCHAIN);
        assertThat(folder.resolve("secrets.json")).doesNotExist();
    }
}
