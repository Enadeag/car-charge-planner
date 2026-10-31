package nl.enadeag.carchargeplanner.engine.secrets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import nl.enadeag.carchargeplanner.LogCapture;
import nl.enadeag.carchargeplanner.engine.config.ProviderId;
import nl.enadeag.carchargeplanner.engine.store.FilePrivacy;
import nl.enadeag.carchargeplanner.engine.store.PrivateFiles;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import tools.jackson.databind.json.JsonMapper;

class ApiKeysTest {

    private static final PrivateFiles FILES = new PrivateFiles(FilePrivacy.NONE);

    private static final ProviderId OPEN_METEO = new ProviderId("open-meteo");
    private static final Secrets KEYS = new Secrets(Map.of(
            OPEN_METEO, Map.of("api_key", "OM-SECRET-1"),
            new ProviderId("forecast-solar"), Map.of("api_key", "FS-SECRET-2")));

    @TempDir
    Path folder;

    private final MemoryKeychain keychain = new MemoryKeychain();
    private final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-07-03T07:00:00Z"));

    /** Refuses to store anything, as the Credential Manager does over ssh, without a logon session. */
    private static final class FailingWrites extends MemoryKeychain {
        @Override
        public void write(String name, String value) throws KeychainException {
            throw new KeychainException(KeychainException.Kind.UNAVAILABLE, "A specified logon session does not exist (1312)");
        }
    }

    /** Says it stored a value and keeps nothing. */
    private static final class Forgetful extends MemoryKeychain {
        @Override
        public void write(String name, String value) {
        }
    }

    /** Does not answer, as a Secret Service that hangs. */
    private static final class Unreadable extends MemoryKeychain {
        @Override
        public Optional<String> read(String name) throws KeychainException {
            throw new KeychainException(KeychainException.Kind.UNAVAILABLE, "did not answer");
        }
    }

    private SecretsFile file() {
        return new SecretsFile(folder.resolve("secrets.json"), FILES);
    }

    private ApiKeys keys(Keychain keychain) {
        return new ApiKeys(keychain, file(), now::get);
    }

    private void later(Duration time) {
        now.set(now.get().plus(time));
    }

    /** Counts what it is asked, as the system's keychain takes its time for each call. */
    private static class Counting extends MemoryKeychain {
        final AtomicInteger calls = new AtomicInteger();

        @Override
        public Optional<String> read(String name) throws KeychainException {
            calls.incrementAndGet();
            return super.read(name);
        }

        @Override
        public void write(String name, String value) throws KeychainException {
            calls.incrementAndGet();
            super.write(name, value);
        }
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
        var keys = keys(keychain);

        keys.moveFileIntoKeychain();

        assertThat(keys.load()).isEqualTo(KEYS);
        assertThat(keys.storage()).isEqualTo(KeyStorage.KEYCHAIN);
        assertThat(file().path()).doesNotExist();
        assertThat(keychain.entries).containsEntry("forecast/open-meteo/api_key", "OM-SECRET-1");
        assertThat(keys(keychain).load()).isEqualTo(KEYS);
    }

    @Test
    void aLoadOnlyReadsTheFileAndLeavesItWhereItIs() throws SecretsProblem {
        file().write(KEYS);
        var keys = keys(keychain);
        try (var log = LogCapture.of(ApiKeys.class)) {
            assertThat(keys.load()).isEqualTo(KEYS);

            assertThat(log.lines()).containsExactly("WARNING API keys are kept in " + file().path()
                    + ", readable only by you: they have not been moved into the system keychain");
        }
        assertThat(keys.storage()).isEqualTo(KeyStorage.FILE);
        assertThat(file().path()).exists();
        assertThat(keychain.entries).isEmpty();
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

    static Stream<String> filesWhoseKeysCannotBeRead() {
        return Stream.of("{\"open-meteo\": {\"api key\": \"OM-SECRET-1\"}}", "not JSON");
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

    @Test
    void aChangeSavedAfterTheOwnerUnlockedTheKeychainKeepsTheKeysThatWereOnlyInTheFile() throws SecretsProblem {
        var lockable = lockedWithTheKeys();
        var other = new ProviderId("other");
        file().write(new Secrets(Map.of(other, Map.of("api_key", "OTHER-IN-FILE"))));
        var keys = keys(lockable);

        keys.applyChanges(NEW_OPEN_METEO_KEY, () -> { });

        assertThat(lockable.prompts).hasValue(1);
        assertThat(file().path()).doesNotExist();
        assertThat(keys.load()).isEqualTo(new Secrets(Map.of(OPEN_METEO, Map.of("api_key", "OM-NEW"),
                FORECAST_SOLAR, Map.of("api_key", "FS-SECRET-2"), other, Map.of("api_key", "OTHER-IN-FILE"))));
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

    /** A keychain that stops and starts answering, as a Secret Service that hangs for a while. */
    private static final class Intermittent extends MemoryKeychain {
        private volatile boolean away;

        @Override
        public Optional<String> read(String name) throws KeychainException {
            if (away) {
                throw new KeychainException(KeychainException.Kind.UNAVAILABLE, "did not answer");
            }
            return super.read(name);
        }
    }

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
    void keysReadFromTheKeychainAreKeptSoAPlanEveryMinuteDoesNotAskItAgain() throws SecretsProblem {
        var counting = new Counting();
        keys(counting).save(KEYS);
        var keys = keys(counting);
        keys.load();
        var asked = counting.calls.get();

        for (int minute = 0; minute < 60; minute++) {
            later(Duration.ofMinutes(1));
            assertThat(keys.load()).isEqualTo(KEYS);
            assertThat(keys.storage()).isEqualTo(KeyStorage.KEYCHAIN);
        }

        assertThat(counting.calls.get()).isEqualTo(asked);
    }

    @Test
    void aSaveReplacesTheKeysThatWereKeptWithoutReadingThemBack() throws SecretsProblem {
        var counting = new Counting();
        var keys = keys(counting);
        keys.load();
        var changed = new Secrets(Map.of(OPEN_METEO, Map.of("api_key", "OM-NEW")));

        keys.save(changed);
        var asked = counting.calls.get();

        assertThat(keys.load()).isEqualTo(changed);
        assertThat(counting.calls.get()).isEqualTo(asked);
    }

    @Test
    void aKeychainThatCannotBeUsedIsLeftAloneForAWhileAndThenAskedAgain() throws SecretsProblem {
        var intermittent = new Intermittent();
        intermittent.entries.put("index", "[\"forecast/open-meteo/api_key\"]");
        intermittent.entries.put("forecast/open-meteo/api_key", "OM-SECRET-1");
        intermittent.away = true;
        var keys = keys(intermittent);
        assertThat(keys.load()).isEqualTo(Secrets.NONE);
        intermittent.away = false;

        later(ApiKeys.RETRY_AFTER.minusSeconds(1));
        assertThat(keys.load()).isEqualTo(Secrets.NONE);
        assertThat(keys.storage()).isEqualTo(KeyStorage.FILE);

        later(Duration.ofSeconds(1));
        assertThat(keys.load()).isEqualTo(new Secrets(Map.of(OPEN_METEO, Map.of("api_key", "OM-SECRET-1"))));
        assertThat(keys.storage()).isEqualTo(KeyStorage.KEYCHAIN);
    }

    @Test
    void aKeychainThatCannotBeReadKeepsSayingSoUntilItIsAskedAgain() throws SecretsProblem {
        var counting = new Counting();
        counting.entries.put("index", "not a list");
        var keys = keys(counting);
        assertThatThrownBy(keys::load).isInstanceOf(SecretsProblem.class);
        var asked = counting.calls.get();

        assertThatThrownBy(keys::load).isInstanceOf(SecretsProblem.class).hasMessageContaining("could not be read");
        assertThat(counting.calls.get()).isEqualTo(asked);

        counting.entries.put("index", "[]");
        later(ApiKeys.RETRY_AFTER);
        assertThat(keys.load()).isEqualTo(Secrets.NONE);
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

    /** A keychain holding the keys, then locked. */
    private LockableKeychain lockedWithTheKeys() throws SecretsProblem {
        var lockable = new LockableKeychain();
        keys(lockable).save(KEYS);
        lockable.locked = true;
        return lockable;
    }

    @Test
    void aLockedKeychainMeansTheKeysAreNotAvailableYetAndNoFileIsUsed() throws SecretsProblem {
        var lockable = lockedWithTheKeys();
        var keys = keys(lockable);

        assertThatThrownBy(keys::load).isInstanceOfSatisfying(SecretsProblem.class,
                problem -> assertThat(problem.kind()).isEqualTo(SecretsProblem.Kind.LOCKED));
        assertThat(keys.storage()).isEqualTo(KeyStorage.LOCKED_KEYCHAIN);
        assertThat(file().path()).doesNotExist();

        lockable.locked = false; // no wait: the next plan finds the keys
        assertThat(keys.load()).isEqualTo(KEYS);
        assertThat(keys.storage()).isEqualTo(KeyStorage.KEYCHAIN);
    }

    @Test
    void whileTheKeychainIsLockedKeysKeptInTheFileBeforeAreUsed() throws SecretsProblem {
        var lockable = new LockableKeychain();
        lockable.locked = true;
        file().write(KEYS);
        var keys = keys(lockable);

        assertThat(keys.load()).isEqualTo(KEYS);
        assertThat(keys.storage()).isEqualTo(KeyStorage.FILE);
        assertThat(file().path()).exists();
    }

    @Test
    void aSaveWhileLockedHasTheOwnerUnlockTheKeychainAndStoresTheKeysThere() throws SecretsProblem {
        var lockable = lockedWithTheKeys();
        var keys = keys(lockable);
        var changed = new Secrets(Map.of(OPEN_METEO, Map.of("api_key", "OM-NEW")));

        keys.save(changed);

        assertThat(lockable.prompts).hasValue(1);
        assertThat(lockable.entries).containsEntry("forecast/open-meteo/api_key", "OM-NEW")
                .doesNotContainKey("forecast/forecast-solar/api_key");
        assertThat(file().path()).doesNotExist();
        assertThat(keys.load()).isEqualTo(changed);
        assertThat(keys.storage()).isEqualTo(KeyStorage.KEYCHAIN);
    }

    @Test
    void whenTheOwnerDismissesThePromptTheKeysGoToTheFileAndMoveIntoTheKeychainOnceItIsUnlocked()
            throws SecretsProblem {
        var lockable = lockedWithTheKeys();
        lockable.answer = UnlockOutcome.DISMISSED;
        var keys = keys(lockable);
        var changed = new Secrets(Map.of(OPEN_METEO, Map.of("api_key", "OM-NEW")));

        keys.save(changed);

        assertThat(file().read()).isEqualTo(changed);
        assertThat(keys.storage()).isEqualTo(KeyStorage.FILE);
        assertThat(keys.load()).isEqualTo(changed);

        lockable.locked = false;
        later(ApiKeys.RETRY_AFTER);
        keys.moveFileIntoKeychain();
        // laid over what the keychain held, so a key that was not in the file is not lost
        assertThat(keys.load()).isEqualTo(new Secrets(Map.of(OPEN_METEO, Map.of("api_key", "OM-NEW"),
                new ProviderId("forecast-solar"), Map.of("api_key", "FS-SECRET-2"))));
        assertThat(file().path()).doesNotExist();
        assertThat(lockable.entries).containsEntry("forecast/open-meteo/api_key", "OM-NEW");
    }

    @Test
    void whenTheKeychainCannotBeUnlockedByTheAppNothingIsSavedAndTheOwnerIsToldItIsLocked() throws SecretsProblem {
        var lockable = lockedWithTheKeys();
        lockable.unlockFails = KeychainException.Kind.REFUSED;
        var before = Map.copyOf(lockable.entries);

        assertThatThrownBy(() -> keys(lockable).save(new Secrets(Map.of(OPEN_METEO, Map.of("api_key", "OM-NEW")))))
                .isInstanceOfSatisfying(SecretsProblem.class,
                        problem -> assertThat(problem.kind()).isEqualTo(SecretsProblem.Kind.LOCKED))
                .hasMessageContaining("Unlock it and save again").hasMessageNotContaining("OM-NEW");

        assertThat(file().path()).doesNotExist();
        assertThat(lockable.entries).isEqualTo(before);
    }

    @Test
    void whenTheSecretServiceTurnsOutToBeGoneAtUnlockingTheKeysGoToTheFile() throws SecretsProblem {
        var lockable = lockedWithTheKeys();
        lockable.unlockFails = KeychainException.Kind.UNAVAILABLE;

        keys(lockable).save(KEYS);

        assertThat(file().read()).isEqualTo(KEYS);
    }

    @Test
    void aSaveThatWaitsForTheOwnerHoldsUpNoLoadAndStopsWaitingWhenAsked() throws Exception {
        var lockable = lockedWithTheKeys();
        lockable.answer = null;
        var keys = keys(lockable);
        var outcome = new AtomicReference<Throwable>();
        var saving = new Thread(() -> {
            try {
                keys.save(new Secrets(Map.of(OPEN_METEO, Map.of("api_key", "OM-NEW"))));
            } catch (SecretsProblem | RuntimeException e) {
                outcome.set(e);
            }
        });
        saving.setDaemon(true);
        saving.start();
        assertThat(lockable.prompting.await(10, TimeUnit.SECONDS)).as("the prompt shows").isTrue();

        assertThatThrownBy(keys::load).isInstanceOf(SecretsProblem.class); // answers at once: the keychain is locked
        keys.stopUnlocking();
        saving.join(10_000);
        assertThat(saving.isAlive()).as("the save ended").isFalse();

        assertThat(outcome.get()).isInstanceOfSatisfying(SecretsProblem.class,
                problem -> assertThat(problem.kind()).isEqualTo(SecretsProblem.Kind.LOCKED));
        assertThat(file().path()).doesNotExist();
        assertThat(lockable.entries).doesNotContainValue("OM-NEW");
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

    private static final ProviderId FORECAST_SOLAR = new ProviderId("forecast-solar");
    private static final KeyChanges NEW_OPEN_METEO_KEY =
            KeyChanges.entering(new Secrets(Map.of(OPEN_METEO, Map.of("api_key", "OM-NEW"))));

    @Test
    void aKeyEnteredWhileLockedIsAddedOnceUnlockedAndTheOtherStoredKeysStay() throws SecretsProblem {
        var lockable = lockedWithTheKeys();
        var keys = keys(lockable);
        var prompted = new AtomicInteger();

        keys.applyChanges(NEW_OPEN_METEO_KEY, prompted::incrementAndGet);

        assertThat(prompted).hasValue(1);
        assertThat(keys.load()).isEqualTo(new Secrets(Map.of(OPEN_METEO, Map.of("api_key", "OM-NEW"),
                FORECAST_SOLAR, Map.of("api_key", "FS-SECRET-2"))));
        assertThat(lockable.entries).containsEntry("forecast/forecast-solar/api_key", "FS-SECRET-2");
    }

    @Test
    void aKeyEnteredWhileLockedGoesAloneToTheFileWhenDismissedAndTheKeychainsOtherKeysStay() throws SecretsProblem {
        var lockable = lockedWithTheKeys();
        lockable.answer = UnlockOutcome.DISMISSED;
        var before = Map.copyOf(lockable.entries);
        var keys = keys(lockable);

        keys.applyChanges(NEW_OPEN_METEO_KEY, () -> { });

        assertThat(file().read()).isEqualTo(new Secrets(Map.of(OPEN_METEO, Map.of("api_key", "OM-NEW"))));
        assertThat(lockable.entries).as("the locked keychain is not touched").isEqualTo(before);

        lockable.locked = false;
        later(ApiKeys.RETRY_AFTER);
        keys.moveFileIntoKeychain();
        assertThat(keys.load()).as("moved in over what the keychain holds").isEqualTo(new Secrets(Map.of(
                OPEN_METEO, Map.of("api_key", "OM-NEW"), FORECAST_SOLAR, Map.of("api_key", "FS-SECRET-2"))));
        assertThat(file().path()).doesNotExist();
    }

    @Test
    void aKeyEnteredWithoutAKeychainIsAddedToTheFileAndItsOtherKeysStay() throws SecretsProblem {
        file().write(KEYS);
        var keys = keys(new NoKeychain());

        keys.applyChanges(NEW_OPEN_METEO_KEY, () -> { });

        assertThat(file().read()).isEqualTo(new Secrets(Map.of(OPEN_METEO, Map.of("api_key", "OM-NEW"),
                FORECAST_SOLAR, Map.of("api_key", "FS-SECRET-2"))));
    }

    @Test
    void onlyAKeyTheOwnerEmptiedIsRemoved() throws SecretsProblem {
        var keys = keys(keychain);
        keys.save(KEYS);

        keys.applyChanges(new KeyChanges(Secrets.NONE, Map.of(FORECAST_SOLAR, Set.of("api_key"))), () -> { });

        assertThat(keys.load()).isEqualTo(new Secrets(Map.of(OPEN_METEO, Map.of("api_key", "OM-SECRET-1"))));
        assertThat(keychain.entries).doesNotContainKey("forecast/forecast-solar/api_key");
    }

    @Test
    void anUnlockedKeychainAsksForNoPromptAndTheOwnerIsNotToldAboutOne() throws SecretsProblem {
        var lockable = new LockableKeychain();
        var prompted = new AtomicInteger();

        keys(lockable).applyChanges(NEW_OPEN_METEO_KEY, prompted::incrementAndGet);

        assertThat(prompted).hasValue(0);
        assertThat(lockable.prompts).hasValue(0);
    }

    @Test
    void aSaveIntoAWorkingKeychainRemovesTheFileTheKeysWereKeptInBefore() throws SecretsProblem {
        file().write(KEYS);
        var changed = new Secrets(Map.of(OPEN_METEO, Map.of("api_key", "OM-NEW")));

        keys(keychain).save(changed);

        assertThat(file().path()).doesNotExist();
        assertThat(keychain.entries).containsEntry("forecast/open-meteo/api_key", "OM-NEW");
    }

    /** Keeps each value for one read only, so what it holds as a whole differs from what was written. */
    private static final class ForgetsAfterOneRead extends MemoryKeychain {
        @Override
        public Optional<String> read(String name) throws KeychainException {
            return name.equals(KeychainEntries.INDEX) ? super.read(name) : Optional.ofNullable(entries.remove(name));
        }
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
    void aKeychainThatCannotBeReadStillHoldsTheKeys() {
        keychain.entries.put("index", "not a list");
        var keys = keys(keychain);

        assertThat(keys.storage()).isEqualTo(KeyStorage.KEYCHAIN);
    }

    @Test
    void anIndexNameOutsideTheAppsOwnIsADamagedIndex() {
        keychain.entries.put("index", "[\"other/open-meteo/api_key\"]");

        assertThatThrownBy(keys(keychain)::load).isInstanceOf(SecretsProblem.class).hasMessageContaining("could not be read");
    }

    @Test
    void aSaveIntoTheFileLeavesTheKeychainAloneForAWhile() throws SecretsProblem {
        var intermittent = new Intermittent();
        intermittent.away = true;
        var keys = keys(intermittent);
        keys.save(KEYS);
        intermittent.away = false;

        later(ApiKeys.RETRY_AFTER.minusSeconds(1));
        keys.moveFileIntoKeychain();
        assertThat(keys.load()).isEqualTo(KEYS);
        assertThat(file().path()).exists();
        assertThat(intermittent.entries).isEmpty();

        later(Duration.ofSeconds(1));
        keys.moveFileIntoKeychain();
        assertThat(keys.load()).isEqualTo(KEYS);
        assertThat(file().path()).doesNotExist();
    }

    /** Answers the first question, about the index, and is locked from then on. */
    private static final class LocksAfterTheFirstQuestion extends LockableKeychain {
        @Override
        public Optional<String> read(String name) throws KeychainException {
            var value = super.read(name);
            locked = true;
            return value;
        }
    }

    @Test
    void aKeychainThatLocksBetweenTheTwoQuestionsIsLocked() {
        var lockable = new LocksAfterTheFirstQuestion();
        lockable.entries.put("index", "[\"forecast/open-meteo/api_key\"]");
        lockable.entries.put("forecast/open-meteo/api_key", "OM-SECRET-1");
        var keys = keys(lockable);

        assertThatThrownBy(keys::load).isInstanceOfSatisfying(SecretsProblem.class,
                problem -> assertThat(problem.kind()).isEqualTo(SecretsProblem.Kind.LOCKED));
        assertThat(keys.storage()).isEqualTo(KeyStorage.LOCKED_KEYCHAIN);
    }

    /** Usable, but refuses its first write as locked, as a keychain that holds none of the app's items yet. */
    private static final class LockedForTheFirstItem extends LockableKeychain {
        private boolean refused;

        @Override
        public void write(String name, String value) throws KeychainException {
            if (!refused) {
                refused = true;
                locked = true;
            }
            super.write(name, value);
        }

        @Override
        public Optional<String> read(String name) throws KeychainException {
            return locked && name.equals(KeychainEntries.INDEX) ? Optional.empty() : super.read(name);
        }
    }

    @Test
    void aWriteRefusedAsLockedHasTheOwnerUnlockTheKeychain() throws SecretsProblem {
        var lockable = new LockedForTheFirstItem();

        keys(lockable).save(KEYS);

        assertThat(lockable.prompts).hasValue(1);
        assertThat(lockable.entries).containsEntry("forecast/open-meteo/api_key", "OM-SECRET-1");
        assertThat(file().path()).doesNotExist();
    }

    @Test
    void aBadNameIsRefusedBeforeTheOwnerIsAskedToUnlock() throws SecretsProblem {
        var lockable = lockedWithTheKeys();
        var before = Map.copyOf(lockable.entries);
        var prompted = new AtomicInteger();
        var bad = KeyChanges.entering(new Secrets(Map.of(OPEN_METEO, Map.of("api key", "x"))));

        assertThatThrownBy(() -> keys(lockable).applyChanges(bad, prompted::incrementAndGet)).isInstanceOf(SecretsProblem.class);

        assertThat(prompted).hasValue(0);
        assertThat(lockable.prompts).hasValue(0);
        assertThat(lockable.entries).isEqualTo(before);
        assertThat(file().path()).doesNotExist();
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
    void anUpdateOverADamagedIndexKeepsOnlyWhatWasEntered() throws SecretsProblem {
        keychain.entries.put("index", "not a list");

        keys(keychain).applyChanges(NEW_OPEN_METEO_KEY, () -> { });

        assertThat(file().path()).doesNotExist();
        assertThat(keychain.entries).containsEntry("forecast/open-meteo/api_key", "OM-NEW");
        assertThat(keys(keychain).load()).isEqualTo(new Secrets(Map.of(OPEN_METEO, Map.of("api_key", "OM-NEW"))));
    }

    /** Lists a key it cannot give, as an entry that went missing behind the index's back. */
    private static final class UnreadableEntry extends MemoryKeychain {
        @Override
        public Optional<String> read(String name) throws KeychainException {
            if (name.equals("forecast/forecast-solar/api_key")) {
                throw new KeychainException(KeychainException.Kind.UNAVAILABLE, "did not answer");
            }
            return super.read(name);
        }
    }

    @Test
    void aSaveDoesNotReadTheKeysItReplaces() throws SecretsProblem {
        var unreadable = new UnreadableEntry();
        unreadable.entries.put("index", "[\"forecast/forecast-solar/api_key\"]");
        var changed = new Secrets(Map.of(OPEN_METEO, Map.of("api_key", "OM-NEW")));

        keys(unreadable).save(changed);

        assertThat(unreadable.entries).containsEntry("forecast/open-meteo/api_key", "OM-NEW");
        assertThat(file().path()).doesNotExist();
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

    @Test
    void removingTakesOnlyTheNamedSettingsOutOfTheKeychain() throws SecretsProblem {
        var keys = keys(keychain);
        keys.save(new Secrets(Map.of(OPEN_METEO, Map.of("api_key", "OM-SECRET-1", "other", "OM-OTHER"),
                FORECAST_SOLAR, Map.of("api_key", "FS-SECRET-2"))));

        assertThat(keys.remove(Map.of(OPEN_METEO, Set.of("other"), FORECAST_SOLAR, Set.of("api_key", "absent"))))
                .isTrue();

        assertThat(keys(keychain).load()).isEqualTo(new Secrets(Map.of(OPEN_METEO, Map.of("api_key", "OM-SECRET-1"))));
        assertThat(keychain.entries).containsOnlyKeys("index", "forecast/open-meteo/api_key");
    }

    @Test
    void removingWhatIsNotStoredWritesNothing() throws SecretsProblem {
        var failing = new FailingWrites();
        failing.entries.put(KeychainEntries.INDEX, "[]");

        assertThat(keys(failing).remove(Map.of(OPEN_METEO, Set.of("api_key")))).isFalse();
        assertThat(file().path()).doesNotExist();
    }

    @Test
    void withoutAKeychainRemovingTakesTheSettingsOutOfTheFile() throws SecretsProblem {
        file().write(KEYS);

        assertThat(keys(new NoKeychain()).remove(Map.of(FORECAST_SOLAR, Set.of("api_key")))).isTrue();

        assertThat(file().read()).isEqualTo(new Secrets(Map.of(OPEN_METEO, Map.of("api_key", "OM-SECRET-1"))));
    }

    @Test
    void removingFromALockedKeychainNeverPromptsAndRemovesNothing() throws SecretsProblem {
        var lockable = lockedWithTheKeys();

        assertThatThrownBy(() -> keys(lockable).remove(Map.of(OPEN_METEO, Set.of("api_key"))))
                .isInstanceOfSatisfying(SecretsProblem.class,
                        problem -> assertThat(problem.kind()).isEqualTo(SecretsProblem.Kind.LOCKED));

        assertThat(lockable.prompts).hasValue(0);
        lockable.locked = false;
        assertThat(keys(lockable).load()).isEqualTo(KEYS);
    }

    /** Locked after it said it holds the keys, so only the save finds it locked. */
    @Test
    void aKeychainThatLocksBeforeTheRemovalIsWrittenNeverPromptsEither() throws SecretsProblem {
        var lockable = new LockableKeychain();
        var keys = keys(lockable);
        keys.save(KEYS);
        keys.load();
        lockable.locked = true;

        assertThatThrownBy(() -> keys.remove(Map.of(OPEN_METEO, Set.of("api_key"))))
                .isInstanceOfSatisfying(SecretsProblem.class,
                        problem -> assertThat(problem.kind()).isEqualTo(SecretsProblem.Kind.LOCKED));
        assertThat(lockable.prompts).hasValue(0);
    }

    @Test
    void theFileWithoutAKeychainIsTheOneItWasGiven() {
        var file = file();
        assertThat(new ApiKeys(new NoKeychain(), file).file()).isSameAs(file);
    }
}
