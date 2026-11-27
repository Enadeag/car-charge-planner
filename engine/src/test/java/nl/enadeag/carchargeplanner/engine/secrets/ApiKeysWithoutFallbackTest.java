package nl.enadeag.carchargeplanner.engine.secrets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import nl.enadeag.carchargeplanner.LogCapture;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ApiKeysWithoutFallbackTest extends ApiKeysFixture {

    @Test
    void withoutAFallbackAKeychainThatCannotBeUsedMeansTheKeysCouldNotBeReadAndNoFileIsUsed() {
        var keys = keys(new NoKeychain(), KeyFallback.NONE);

        try (var log = LogCapture.of(ApiKeys.class)) {
            assertThatThrownBy(keys::load).isInstanceOfSatisfying(SecretsProblem.class,
                    problem -> assertThat(problem.kind()).isEqualTo(SecretsProblem.Kind.FAILED))
                    .hasMessageContaining("could not be read");
            assertThat(keys.storage()).isEqualTo(KeyStorage.UNAVAILABLE);

            assertThat(log.lines()).containsExactly("WARNING API keys can be neither read nor saved, as there is no "
                    + "file to keep them in: the system keychain cannot be used");
        }
        assertThat(file().path()).doesNotExist();
    }

    @Test
    void withoutAFallbackAKeychainThatCannotBeUsedKeepsSayingSoUntilItIsAskedAgain() throws SecretsProblem {
        var intermittent = holdingTheKeys(new Intermittent());
        intermittent.away = true;
        var keys = keys(intermittent, KeyFallback.NONE);
        assertThatThrownBy(keys::load).isInstanceOf(SecretsProblem.class);
        intermittent.away = false;

        later(ApiKeys.RETRY_AFTER.minusSeconds(1));
        assertThatThrownBy(keys::load).isInstanceOf(SecretsProblem.class).hasMessageContaining("could not be read");
        assertThat(keys.storage()).isEqualTo(KeyStorage.UNAVAILABLE);

        later(Duration.ofSeconds(1));
        assertThat(keys.load()).isEqualTo(KEYS);
        assertThat(keys.storage()).isEqualTo(KeyStorage.KEYCHAIN);
    }

    static Stream<Arguments> keychainsThatCannotStore() {
        return Stream.of(Arguments.of(new NoKeychain(), KeyStorage.UNAVAILABLE),
                Arguments.of(new FailingWrites(), KeyStorage.KEYCHAIN),
                Arguments.of(new RefusingWrites(), KeyStorage.KEYCHAIN));
    }

    @ParameterizedTest
    @MethodSource("keychainsThatCannotStore")
    void withoutAFallbackASaveTheKeychainCannotTakeFailsAndWritesNoFile(Keychain cannotStore, KeyStorage after) {
        var keys = keys(cannotStore, KeyFallback.NONE);

        assertThatThrownBy(() -> keys.applyChanges(NEW_SOLCAST_KEY, () -> { }))
                .isInstanceOfSatisfying(SecretsProblem.class,
                        problem -> assertThat(problem.kind()).isEqualTo(SecretsProblem.Kind.FAILED))
                .hasMessageNotContaining("SC-NEW");
        assertThatThrownBy(() -> keys.save(KEYS)).isInstanceOfSatisfying(SecretsProblem.class,
                problem -> assertThat(problem.kind()).isEqualTo(SecretsProblem.Kind.FAILED));

        assertThat(file().path()).doesNotExist();
        assertThat(keys.storage()).isEqualTo(after);
    }

    @Test
    void withoutAFallbackAFileFromBeforeIsNeverReadMovedWrittenOrDeleted() throws IOException, SecretsProblem {
        file().write(KEYS);
        var before = Files.readString(file().path());
        var keys = keys(keychain, KeyFallback.NONE);

        assertThat(keys.load()).isEqualTo(Secrets.NONE);
        keys.moveFileIntoKeychain();
        assertThat(keychain.entries).isEmpty();
        keys.applyChanges(NEW_SOLCAST_KEY, () -> { });
        keys.applyChanges(new KeyChanges(Secrets.NONE, Map.of(OPEN_METEO, Set.of("api_key"))), () -> { });

        assertThat(keys.load()).isEqualTo(new Secrets(Map.of(SOLCAST, Map.of("api_key", "SC-NEW"))));
        assertThat(keys.storage()).isEqualTo(KeyStorage.KEYCHAIN);
        assertThat(Files.readString(file().path())).isEqualTo(before);
    }

    @Test
    void withoutAFallbackADismissedPromptSavesNothingAndTheKeychainIsStillLocked() {
        var lockable = holdingTheKeys(new LockableKeychain());
        var before = Map.copyOf(lockable.entries);
        lockable.locked = true;
        lockable.answer = UnlockOutcome.DISMISSED;
        var keys = keys(lockable, KeyFallback.NONE);

        assertThatThrownBy(() -> keys.applyChanges(NEW_SOLCAST_KEY, () -> { }))
                .isInstanceOfSatisfying(SecretsProblem.class,
                        problem -> assertThat(problem.kind()).isEqualTo(SecretsProblem.Kind.LOCKED));

        assertThat(file().path()).doesNotExist();
        assertThat(lockable.entries).isEqualTo(before);
        assertThat(keys.storage()).isEqualTo(KeyStorage.LOCKED_KEYCHAIN);
    }

    @Test
    void withoutAFallbackAnIndexTheKeychainRefusesToGiveIsADamagedOne() {
        var refusedIndex = holdingTheKeys(new UnreadableEntryKeychain(KeychainEntries.INDEX));
        var before = Map.copyOf(refusedIndex.entries);
        var keys = keys(refusedIndex, KeyFallback.NONE);

        assertThatThrownBy(keys::load).isInstanceOfSatisfying(SecretsProblem.class,
                problem -> assertThat(problem.kind()).isEqualTo(SecretsProblem.Kind.FAILED))
                .hasMessageContaining("could not be read");
        assertThat(keys.storage()).isEqualTo(KeyStorage.KEYCHAIN);

        assertThat(file().path()).doesNotExist();
        assertThat(refusedIndex.entries).isEqualTo(before);
    }

    @Test
    void withoutAFallbackASaveOverARefusedIndexStartsANewOneAndLeavesTheOldEntries() throws SecretsProblem {
        var refusedIndex = holdingTheKeys(new UnreadableEntryKeychain(KeychainEntries.INDEX));
        var keys = keys(refusedIndex, KeyFallback.NONE);

        keys.applyChanges(NEW_SOLCAST_KEY, () -> { });

        assertThat(keys.load()).isEqualTo(new Secrets(Map.of(SOLCAST, Map.of("api_key", "SC-NEW"))));
        assertThat(index(refusedIndex)).containsExactly("forecast/solcast/api_key");
        assertThat(refusedIndex.entries).containsEntry(FORECAST_SOLAR_ENTRY, "FS-SECRET-2")
                .containsEntry(OPEN_METEO_ENTRY, "OM-SECRET-1");
        assertThat(file().path()).doesNotExist();
    }

    @Test
    void withoutAFallbackTheOldEntriesOfARefusedIndexAreReplacedWhenTheOwnerEntersThemAgain() throws SecretsProblem {
        var refusedIndex = holdingTheKeys(new UnreadableEntryKeychain(KeychainEntries.INDEX));
        var keys = keys(refusedIndex, KeyFallback.NONE);

        keys.applyChanges(KeyChanges.entering(new Secrets(Map.of(OPEN_METEO, Map.of("api_key", "OM-NEW")))),
                () -> { });

        assertThat(keys.load()).isEqualTo(new Secrets(Map.of(OPEN_METEO, Map.of("api_key", "OM-NEW"))));
        assertThat(refusedIndex.entries).containsEntry(OPEN_METEO_ENTRY, "OM-NEW");
    }

    @Test
    void withoutAFallbackAKeychainThatTurnsOutGoneWhenUnlockingFailsTheSaveAsOneThatCannotBeUsed() {
        var lockable = holdingTheKeys(new LockableKeychain());
        var before = Map.copyOf(lockable.entries);
        lockable.locked = true;
        lockable.unlockFails = KeychainException.Kind.UNAVAILABLE;

        assertThatThrownBy(() -> keys(lockable, KeyFallback.NONE).applyChanges(NEW_SOLCAST_KEY, () -> { }))
                .isInstanceOfSatisfying(SecretsProblem.class,
                        problem -> assertThat(problem.kind()).isEqualTo(SecretsProblem.Kind.FAILED))
                .hasMessageContaining("cannot be used");

        assertThat(file().path()).doesNotExist();
        assertThat(lockable.entries).isEqualTo(before);
    }

    @Test
    void withoutAFallbackAFailedSaveBesideAnEntryThatCannotBeReadNeverMentionsTheFile() {
        var keychain = holdingTheKeys(new RefusesWrites(Set.of(FORECAST_SOLAR_ENTRY)));
        var before = Map.copyOf(keychain.entries);
        var entered = KeyChanges.entering(new Secrets(Map.of(FORECAST_SOLAR, Map.of("api_key", "FS-NEW"))));

        assertThatThrownBy(() -> keys(keychain, KeyFallback.NONE).applyChanges(entered, () -> { }))
                .isInstanceOfSatisfying(SecretsProblem.class,
                        problem -> assertThat(problem.kind()).isEqualTo(SecretsProblem.Kind.FAILED))
                .hasMessageNotContaining("file").hasMessageNotContaining("FS-NEW");

        assertThat(file().path()).doesNotExist();
        assertThat(keychain.entries).isEqualTo(before);
    }

    @Test
    void withoutAFallbackASaveWhileTheKeychainIsLeftAloneAsksItAgainAndStoresTheKeys() throws SecretsProblem {
        var intermittent = new Intermittent();
        intermittent.away = true;
        var keys = keys(intermittent, KeyFallback.NONE);
        assertThatThrownBy(keys::load).isInstanceOf(SecretsProblem.class);
        intermittent.away = false;

        keys.applyChanges(NEW_SOLCAST_KEY, () -> { });

        assertThat(keys.load()).isEqualTo(new Secrets(Map.of(SOLCAST, Map.of("api_key", "SC-NEW"))));
        assertThat(keys.storage()).isEqualTo(KeyStorage.KEYCHAIN);
        assertThat(file().path()).doesNotExist();
    }

    @Test
    void withoutAFallbackAKeychainUnlockedWhenAskedTakesTheSaveAndNoFileIsWritten() throws SecretsProblem {
        var lockable = holdingTheKeys(new LockableKeychain());
        lockable.locked = true;
        var keys = keys(lockable, KeyFallback.NONE);

        keys.applyChanges(NEW_SOLCAST_KEY, () -> { });

        assertThat(lockable.prompts).hasValue(1);
        assertThat(keys.load()).isEqualTo(new Secrets(Map.of(OPEN_METEO, Map.of("api_key", "OM-SECRET-1"),
                FORECAST_SOLAR, Map.of("api_key", "FS-SECRET-2"), SOLCAST, Map.of("api_key", "SC-NEW"))));
        assertThat(file().path()).doesNotExist();
    }

    @Test
    void withoutAFallbackTwoSavesAfterARefusedIndexBothEndUpInTheNewIndex() throws SecretsProblem {
        var refusedIndex = holdingTheKeys(new UnreadableEntryKeychain(KeychainEntries.INDEX));
        var keys = keys(refusedIndex, KeyFallback.NONE);

        keys.applyChanges(NEW_SOLCAST_KEY, () -> { });
        keys.applyChanges(KeyChanges.entering(new Secrets(Map.of(OPEN_METEO, Map.of("api_key", "OM-NEW")))),
                () -> { });

        assertThat(index(refusedIndex)).containsExactly(OPEN_METEO_ENTRY, "forecast/solcast/api_key");
        assertThat(keys.load()).isEqualTo(new Secrets(Map.of(OPEN_METEO, Map.of("api_key", "OM-NEW"),
                SOLCAST, Map.of("api_key", "SC-NEW"))));
        assertThat(file().path()).doesNotExist();
    }
}
