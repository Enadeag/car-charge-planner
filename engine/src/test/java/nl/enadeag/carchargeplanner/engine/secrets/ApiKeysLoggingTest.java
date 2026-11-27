package nl.enadeag.carchargeplanner.engine.secrets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import nl.enadeag.carchargeplanner.LogCapture;
import org.junit.jupiter.api.Test;

class ApiKeysLoggingTest extends ApiKeysFixture {

    @Test
    void whereTheKeysAreKeptIsLoggedWhenItChangesAndNeverAValue() throws SecretsProblem {
        var intermittent = new Intermittent();
        intermittent.away = true;
        var keys = keys(intermittent);
        try (var log = LogCapture.of(ApiKeys.class)) {
            keys.save(KEYS);
            keys.load();
            intermittent.away = false;
            keys.load();
            later(ApiKeys.RETRY_AFTER);
            keys.moveFileIntoKeychain();
            keys.load();

            assertThat(log.lines()).containsExactly(
                    "WARNING API keys are kept in " + file().path() + ", readable only by you: the system keychain "
                            + "cannot be used",
                    "INFO moved the API keys from " + file().path() + " into the system keychain",
                    "INFO API keys are kept in the system keychain again");
        }
    }

    @Test
    void keysMovedFromTheFileIntoTheKeychainAreLogged() throws SecretsProblem {
        file().write(KEYS);
        try (var log = LogCapture.of(ApiKeys.class)) {
            keys(keychain).moveFileIntoKeychain();

            assertThat(log.lines()).containsExactly(
                    "INFO moved the API keys from " + file().path() + " into the system keychain",
                    "INFO API keys are kept in the system keychain");
        }
    }

    @Test
    void aLockedKeychainIsLoggedOnceAndItsUnlockingTooButNeverAValue() throws SecretsProblem {
        var lockable = lockedWithTheKeys();
        var keys = keys(lockable);
        try (var log = LogCapture.of(ApiKeys.class)) {
            for (int minute = 0; minute < 3; minute++) {
                assertThatThrownBy(keys::load).isInstanceOf(SecretsProblem.class);
            }
            lockable.locked = false;
            keys.load();

            assertThat(log.lines()).containsExactly(
                    "WARNING API keys are kept in the system keychain, which is locked: they can be read once it is "
                            + "unlocked",
                    "INFO API keys are kept in the system keychain again");
            assertThat(String.join("\n", log.lines())).doesNotContain("OM-SECRET-1", "FS-SECRET-2");
        }
    }

    @Test
    void withoutAKeychainTheFirstLoadSaysTheKeysAreInTheFile() throws SecretsProblem {
        try (var log = LogCapture.of(ApiKeys.class)) {
            keys(new NoKeychain()).load();

            assertThat(log.lines()).containsExactly("WARNING API keys are kept in " + file().path()
                    + ", readable only by you: the system keychain cannot be used");
        }
    }

    @Test
    void whileLockedWithTheKeysInTheFileTheFileIsSaidToHoldThem() throws SecretsProblem {
        var lockable = new LockableKeychain();
        lockable.locked = true;
        file().write(KEYS);
        try (var log = LogCapture.of(ApiKeys.class)) {
            var keys = keys(lockable);
            keys.moveFileIntoKeychain();
            keys.load();

            assertThat(log.lines()).containsExactly("WARNING API keys are kept in " + file().path()
                    + ", readable only by you: the system keychain is locked");
        }
    }

    @Test
    void unlockingIsLoggedWithItsOutcome() throws SecretsProblem {
        var lockable = lockedWithTheKeys();
        try (var log = LogCapture.of(ApiKeys.class)) {
            keys(lockable).save(KEYS);
            lockable.locked = true;
            lockable.answer = UnlockOutcome.DISMISSED;
            keys(lockable).save(KEYS);
            lockable.unlockFails = KeychainException.Kind.REFUSED;
            assertThatThrownBy(() -> keys(lockable).save(KEYS)).isInstanceOf(SecretsProblem.class);

            assertThat(log.lines()).containsExactly(
                    "INFO the system keychain is locked: asking to unlock it to save the API keys",
                    "INFO the system keychain was unlocked",
                    "INFO API keys are kept in the system keychain",
                    "INFO the system keychain is locked: asking to unlock it to save the API keys",
                    "INFO the system keychain was not unlocked: the prompt was dismissed",
                    "WARNING API keys are kept in " + file().path() + ", readable only by you: the system keychain "
                            + "is locked, and was not unlocked when asked",
                    "INFO the system keychain is locked: asking to unlock it to save the API keys",
                    "WARNING the system keychain could not be unlocked: cannot unlock");
        }
    }
}
