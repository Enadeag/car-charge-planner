package nl.enadeag.carchargeplanner.engine.secrets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import nl.enadeag.carchargeplanner.engine.config.ProviderId;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class ApiKeysLockedTest extends ApiKeysFixture {

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
    void anUnlockedKeychainAsksForNoPromptAndTheOwnerIsNotToldAboutOne() throws SecretsProblem {
        var lockable = new LockableKeychain();
        var prompted = new AtomicInteger();

        keys(lockable).applyChanges(NEW_OPEN_METEO_KEY, prompted::incrementAndGet);

        assertThat(prompted).hasValue(0);
        assertThat(lockable.prompts).hasValue(0);
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
    void stoppingTheUnlockingTellsTheKeychain() {
        var lockable = new LockableKeychain();

        keys(lockable).stopUnlocking();

        assertThat(lockable.stops).hasValue(1);
    }
}
