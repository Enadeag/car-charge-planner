package nl.enadeag.carchargeplanner.engine.secrets;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import nl.enadeag.carchargeplanner.engine.secrets.KeychainWithFallback.Edit;
import nl.enadeag.carchargeplanner.engine.secrets.KeychainWithFallback.SaveResult;
import org.junit.jupiter.api.Test;

/** What a save says of where it ended, which {@link ApiKeys} only compares against locked. */
class KeychainWithFallbackTest extends ApiKeysFixture {

    private static final Edit CHANGE = Edit.changing(KeyChanges.entering(KEYS));

    private KeychainWithFallback over(Keychain chain, KeyFallback fallback) {
        var memory = new KeychainMemory(CLOCK, ApiKeys.RETRY_AFTER, file());
        return new KeychainWithFallback(chain, new FallbackFile(file(), fallback), memory, fallback);
    }

    private KeychainWithFallback over(Keychain chain) {
        return over(chain, KeyFallback.PRIVATE_FILE);
    }

    @Test
    void aSaveIntoAUsableKeychainIsSaved() throws SecretsProblem {
        assertThat(over(keychain).save(Edit.replacing(KEYS))).isEqualTo(SaveResult.SAVED);
        assertThat(over(keychain).save(CHANGE)).isEqualTo(SaveResult.SAVED);
    }

    @Test
    void aSaveThatFoldsTheFileIntoTheKeychainIsSaved() throws SecretsProblem {
        file().write(KEYS);

        assertThat(over(keychain).save(CHANGE)).isEqualTo(SaveResult.SAVED);
        assertThat(file().exists()).isFalse();
    }

    @Test
    void aSaveThatKeepsAFileItCouldNotReadIsSaved() throws Exception {
        Files.writeString(file().path(), "not json");

        assertThat(over(keychain).save(CHANGE)).isEqualTo(SaveResult.SAVED);
        assertThat(file().exists()).isTrue();
    }

    @Test
    void aSaveIntoTheFileWithoutAUsableKeychainIsSaved() throws SecretsProblem {
        assertThat(over(new Unreadable()).save(CHANGE)).isEqualTo(SaveResult.SAVED);
        assertThat(file().read()).isEqualTo(KEYS);
    }

    @Test
    void aSaveTheEmptyKeychainRefusesGoesToTheFileAndIsSaved() throws SecretsProblem {
        assertThat(over(new FailingWrites()).save(CHANGE)).isEqualTo(SaveResult.SAVED);
        assertThat(file().read()).isEqualTo(KEYS);
    }

    @Test
    void aSaveAfterTheDismissedPromptIsSavedInTheFileOrLockedWithoutOne() throws SecretsProblem {
        assertThat(over(keychain).dismissed(CHANGE)).isEqualTo(SaveResult.SAVED);
        assertThat(file().read()).isEqualTo(KEYS);

        assertThat(over(keychain, KeyFallback.NONE).dismissed(CHANGE)).isEqualTo(SaveResult.LOCKED);
    }

    @Test
    void aSaveIntoALockedKeychainIsLocked() throws SecretsProblem {
        var locked = new LockableKeychain();
        locked.locked = true;

        assertThat(over(locked).save(CHANGE)).isEqualTo(SaveResult.LOCKED);
        assertThat(file().exists()).isFalse();
    }
}
