package nl.enadeag.carchargeplanner.engine.secrets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import nl.enadeag.carchargeplanner.LogCapture;
import nl.enadeag.carchargeplanner.engine.config.ProviderId;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class ApiKeysMoveFileTest extends ApiKeysFixture {

    @Test
    void aFileFoundWhileAKeychainIsUsableMovesIntoItAndIsRemoved() throws SecretsProblem {
        file().write(KEYS);
        var keys = keys(keychain);

        keys.moveFileIntoKeychain();

        assertThat(keys.load()).isEqualTo(KEYS);
        assertThat(keys.storage()).isEqualTo(KeyStorage.KEYCHAIN);
        assertThat(file().path()).doesNotExist();
        assertThat(keychain.entries).containsEntry("forecast/open-meteo/api_key", "OM-SECRET-1");
        assertThat(keys(keychain).load()).isEqualTo(KEYS);
    }

    @Test
    void aMoveWithoutAUsableKeychainLeavesItAloneForAWhile() throws SecretsProblem {
        file().write(KEYS);
        var intermittent = new Intermittent();
        intermittent.away = true;
        var keys = keys(intermittent);
        try (var log = LogCapture.of(ApiKeys.class)) {
            keys.moveFileIntoKeychain();
            intermittent.away = false;

            later(ApiKeys.RETRY_AFTER.minusSeconds(1));
            keys.moveFileIntoKeychain();
            assertThat(file().path()).exists();

            later(Duration.ofSeconds(1));
            keys.moveFileIntoKeychain();
            assertThat(file().path()).doesNotExist();
            assertThat(log.lines()).startsWith("WARNING API keys are kept in " + file().path()
                    + ", readable only by you: the system keychain cannot be used");
        }
    }

    @Test
    void aFileThatCannotBeMovedIsLoggedAndNotTriedAgainForAWhile() throws IOException {
        Files.writeString(file().path(), "{\"open-meteo\": {\"api key\": \"OM-SECRET-1\"}}");
        var counting = new Counting();
        var keys = keys(counting);
        try (var log = LogCapture.of(ApiKeys.class)) {
            keys.moveFileIntoKeychain();
            var asked = counting.calls.get();

            later(ApiKeys.RETRY_AFTER.minusSeconds(1));
            keys.moveFileIntoKeychain();

            assertThat(counting.calls.get()).isEqualTo(asked);
            assertThat(log.lines()).containsExactly("WARNING API keys are kept in " + file().path()
                    + ", readable only by you: the file could not be read, or holds a name that is not allowed");
        }
    }

    @Test
    void aKeychainThatLocksDuringTheMoveKeepsTheFile() throws SecretsProblem {
        file().write(KEYS);
        var lockable = new LocksAfterTheFirstQuestion();
        try (var log = LogCapture.of(ApiKeys.class)) {
            keys(lockable).moveFileIntoKeychain();

            assertThat(log.lines()).containsExactly("WARNING API keys are kept in " + file().path()
                    + ", readable only by you: the system keychain is locked");
        }
        assertThat(file().read()).isEqualTo(KEYS);
    }

    static Stream<String> filesWhoseKeysCannotBeRead() {
        return Stream.of("{\"open-meteo\": {\"api key\": \"OM-SECRET-1\"}}", "not JSON");
    }

    @ParameterizedTest
    @MethodSource("filesWhoseKeysCannotBeRead")
    void aChangeSavedOverAFileWhoseKeysCannotBeReadKeepsThatFileAndSaysWhy(String content)
            throws IOException, SecretsProblem {
        Files.writeString(file().path(), content);
        var keys = keys(keychain);

        try (var log = LogCapture.of(ApiKeys.class)) {
            keys.applyChanges(NEW_OPEN_METEO_KEY, () -> { });

            assertThat(log.lines()).containsExactly("WARNING API keys are kept in " + file().path()
                    + ", readable only by you: the file could not be read, or holds a name that is not allowed",
                    "WARNING the API keys were saved in the system keychain, but " + file().path()
                    + " is kept: its keys could not be read, so they were not moved");
        }
        assertThat(file().path()).hasContent(content);
        assertThat(keychain.entries).containsEntry("forecast/open-meteo/api_key", "OM-NEW");
        assertThatThrownBy(keys::load).isInstanceOf(SecretsProblem.class);
        assertThat(keys.storage()).isEqualTo(KeyStorage.FILE);
    }

    @Test
    void aFileKeptAtASaveMovesInOnceItCanBeReadAndTheSavedChangeStays() throws IOException, SecretsProblem {
        Files.writeString(file().path(), "{\"open-meteo\": {\"api key\": \"OM-SECRET-1\"}}");
        var keys = keys(keychain);
        keys.applyChanges(NEW_OPEN_METEO_KEY, () -> { });

        Files.writeString(file().path(), "{\"forecast-solar\": {\"api_key\": \"FS-SECRET-2\"}}");
        keys.moveFileIntoKeychain();

        var both = new Secrets(Map.of(OPEN_METEO, Map.of("api_key", "OM-NEW"),
                new ProviderId("forecast-solar"), Map.of("api_key", "FS-SECRET-2")));
        assertThat(keys.load()).isEqualTo(both);
        assertThat(keys(keychain).load()).isEqualTo(both);
        assertThat(file().path()).doesNotExist();
    }

    @Test
    void aMoveWithoutAFileChangesNothing() throws SecretsProblem {
        var counting = new Counting();
        var keys = keys(counting);

        keys.moveFileIntoKeychain();

        assertThat(counting.calls).hasValue(0);
        assertThat(keys.load()).isEqualTo(Secrets.NONE);
    }

    @Test
    void aMoveWhileTheKeychainIsLockedWaitsForNothingAndMovesOnceItIsUnlocked() throws SecretsProblem {
        var lockable = new LockableKeychain();
        lockable.locked = true;
        file().write(KEYS);
        var keys = keys(lockable);

        keys.moveFileIntoKeychain();
        assertThat(file().path()).exists();
        assertThat(lockable.prompts).hasValue(0);

        lockable.locked = false; // as by the owner, outside the app; no wait before the next move
        keys.moveFileIntoKeychain();

        assertThat(file().path()).doesNotExist();
        assertThat(keys.load()).isEqualTo(KEYS);
    }

    static Stream<Keychain> keychainsThatDoNotKeepValues() {
        return Stream.of(new FailingWrites(), new Forgetful());
    }

    @ParameterizedTest
    @MethodSource("keychainsThatDoNotKeepValues")
    void theFileStaysAndIsUsedWhenTheMoveDoesNotWork(Keychain broken) throws SecretsProblem {
        file().write(KEYS);
        var keys = keys(broken);

        keys.moveFileIntoKeychain();

        assertThat(keys.load()).isEqualTo(KEYS);
        assertThat(file().path()).exists();
        assertThat(keys.load()).isEqualTo(KEYS);
        assertThat(keys.storage()).isEqualTo(KeyStorage.FILE);
    }

    @Test
    void aSaveIntoAWorkingKeychainRemovesTheFileTheKeysWereKeptInBefore() throws SecretsProblem {
        file().write(KEYS);
        var changed = new Secrets(Map.of(OPEN_METEO, Map.of("api_key", "OM-NEW")));

        keys(keychain).save(changed);

        assertThat(file().path()).doesNotExist();
        assertThat(keychain.entries).containsEntry("forecast/open-meteo/api_key", "OM-NEW");
    }

    @Test
    void theFileStaysWhenTheKeychainDoesNotGiveBackWhatWasMovedIntoIt() throws SecretsProblem {
        file().write(KEYS);
        var keys = keys(new ForgetsAfterOneRead());

        keys.moveFileIntoKeychain();

        assertThat(keys.load()).isEqualTo(KEYS);
        assertThat(file().read()).isEqualTo(KEYS);
    }

    @Test
    void aFailedMoveIsLoggedAndTheKeychainIsLeftAloneForAWhile() throws SecretsProblem {
        file().write(KEYS);
        var forgetful = new Counting() {
            @Override
            public void write(String name, String value) {
                calls.incrementAndGet();
            }
        };
        var keys = keys(forgetful);
        try (var log = LogCapture.of(ApiKeys.class)) {
            keys.moveFileIntoKeychain();
            var asked = forgetful.calls.get();

            later(ApiKeys.RETRY_AFTER.minusSeconds(1));
            keys.moveFileIntoKeychain();
            assertThat(keys.load()).isEqualTo(KEYS);
            assertThat(forgetful.calls.get()).isEqualTo(asked);
            assertThat(log.lines()).containsExactly("WARNING API keys are kept in " + file().path()
                    + ", readable only by you: they could not be moved into the system keychain");
        }
    }

    @Test
    void aFileWithABadNameIsNotMovedIntoTheKeychain() throws IOException {
        Files.writeString(file().path(), "{\"open-meteo\": {\"api key\": \"OM-SECRET-1\"}}");
        var keys = keys(keychain);

        keys.moveFileIntoKeychain();

        assertThatThrownBy(keys::load).isInstanceOf(SecretsProblem.class)
                .hasMessage("A setting name has characters that are not allowed");
        assertThat(keychain.entries).isEmpty();
        assertThat(file().path()).exists();
    }

    @Test
    void aRemovalOfAKeyKeptOnlyInTheFileMovesTheRestIntoAUsableKeychainAndDropsTheFile() throws SecretsProblem {
        file().write(KEYS);

        keys(keychain).applyChanges(new KeyChanges(Secrets.NONE, Map.of(FORECAST_SOLAR, Set.of("api_key"))), () -> { });

        assertThat(file().path()).doesNotExist();
        assertThat(keychain.entries).containsOnlyKeys(KeychainEntries.INDEX, OPEN_METEO_ENTRY);
        assertThat(keys(keychain).load()).isEqualTo(new Secrets(Map.of(OPEN_METEO, Map.of("api_key", "OM-SECRET-1"))));
    }
}
