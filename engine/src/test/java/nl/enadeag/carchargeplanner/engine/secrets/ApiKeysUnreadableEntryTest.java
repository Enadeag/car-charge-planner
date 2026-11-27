package nl.enadeag.carchargeplanner.engine.secrets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import nl.enadeag.carchargeplanner.LogCapture;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ApiKeysUnreadableEntryTest extends ApiKeysFixture {

    @Test
    void aSaveWhileAnEntryCannotBeReadKeepsItAndTheOtherProvidersEntries() throws SecretsProblem {
        var keychain = withForecastSolarUnreadable();

        keys(keychain).applyChanges(NEW_SOLCAST_KEY, () -> { });

        assertThat(keychain.entries).containsEntry(OPEN_METEO_ENTRY, "OM-SECRET-1")
                .containsEntry(FORECAST_SOLAR_ENTRY, "FS-SECRET-2")
                .containsEntry("forecast/solcast/api_key", "SC-NEW");
        assertThat(index(keychain)).containsExactly(FORECAST_SOLAR_ENTRY, OPEN_METEO_ENTRY, "forecast/solcast/api_key");
        assertThat(file().path()).doesNotExist();
    }

    static Stream<Arguments> entriesKeptUnread() {
        return Stream.of(
                Arguments.of(new String[] {FORECAST_SOLAR_ENTRY}, "1 (" + FORECAST_SOLAR_ENTRY + ")"),
                Arguments.of(new String[] {OPEN_METEO_ENTRY, FORECAST_SOLAR_ENTRY},
                        "2 (" + FORECAST_SOLAR_ENTRY + ", " + OPEN_METEO_ENTRY + ")"));
    }

    @ParameterizedTest
    @MethodSource("entriesKeptUnread")
    void aSaveThatKeepsEntriesItCannotReadNamesThemOnceAndNeverAValue(String[] unreadable, String said)
            throws SecretsProblem {
        var keychain = holdingTheKeys(new UnreadableEntryKeychain(unreadable));
        try (var log = LogCapture.of(ApiKeys.class)) {
            keys(keychain).applyChanges(NEW_SOLCAST_KEY, () -> { });

            assertThat(log.lines()).containsExactly(
                    "WARNING the API keys were saved in the system keychain; entries it could not read, kept as they "
                            + "were: " + said,
                    "INFO API keys are kept in the system keychain");
        }
    }

    @Test
    void aSaveThatKeepsBothAnEntryItCannotReadAndAFileItCannotReadNamesTheEntry() throws IOException, SecretsProblem {
        Files.writeString(file().path(), "not JSON");
        var keychain = withForecastSolarUnreadable();
        try (var log = LogCapture.of(ApiKeys.class)) {
            keys(keychain).applyChanges(NEW_SOLCAST_KEY, () -> { });

            assertThat(log.lines()).startsWith("WARNING the API keys were saved in the system keychain; entries it "
                    + "could not read, kept as they were: 1 (" + FORECAST_SOLAR_ENTRY + ")");
        }
        assertThat(keychain.entries).containsEntry(FORECAST_SOLAR_ENTRY, "FS-SECRET-2");
        assertThat(file().path()).hasContent("not JSON");
    }

    @Test
    void afterASaveThatKeptAnEntryItCannotReadALoadStillSaysTheKeysCannotBeRead() throws SecretsProblem {
        var keys = keys(withForecastSolarUnreadable());

        keys.applyChanges(NEW_SOLCAST_KEY, () -> { });

        assertThatThrownBy(keys::load).isInstanceOfSatisfying(SecretsProblem.class,
                problem -> assertThat(problem.kind()).isEqualTo(SecretsProblem.Kind.FAILED))
                .hasMessageContaining("could not be read");
    }

    @Test
    void enteringAKeyWhoseEntryCannotBeReadReplacesIt() throws SecretsProblem {
        var keychain = withForecastSolarUnreadable();

        keys(keychain).applyChanges(KeyChanges.entering(new Secrets(Map.of(FORECAST_SOLAR,
                Map.of("api_key", "FS-NEW")))), () -> { });

        assertThat(keys(keychain).load()).isEqualTo(new Secrets(Map.of(OPEN_METEO, Map.of("api_key", "OM-SECRET-1"),
                FORECAST_SOLAR, Map.of("api_key", "FS-NEW"))));
    }

    @Test
    void removingAKeyWhoseEntryCannotBeReadDeletesTheEntryAndItsName() throws SecretsProblem {
        var keychain = withForecastSolarUnreadable();

        keys(keychain).applyChanges(new KeyChanges(Secrets.NONE, Map.of(FORECAST_SOLAR, Set.of("api_key"))),
                () -> { });

        assertThat(keychain.entries).containsOnlyKeys(KeychainEntries.INDEX, OPEN_METEO_ENTRY);
        assertThat(index(keychain)).containsExactly(OPEN_METEO_ENTRY);
        assertThat(keys(keychain).load()).isEqualTo(new Secrets(Map.of(OPEN_METEO, Map.of("api_key", "OM-SECRET-1"))));
    }

    @Test
    void whenNoEntryCanBeReadASaveKeepsThemAllAndAddsTheNewKey() throws SecretsProblem {
        var keychain = holdingTheKeys(new UnreadableEntryKeychain(FORECAST_SOLAR_ENTRY, OPEN_METEO_ENTRY));

        keys(keychain).applyChanges(NEW_SOLCAST_KEY, () -> { });

        assertThat(keychain.entries).containsEntry(OPEN_METEO_ENTRY, "OM-SECRET-1")
                .containsEntry(FORECAST_SOLAR_ENTRY, "FS-SECRET-2")
                .containsEntry("forecast/solcast/api_key", "SC-NEW");
        assertThat(index(keychain)).containsExactly(FORECAST_SOLAR_ENTRY, OPEN_METEO_ENTRY, "forecast/solcast/api_key");
    }

    @Test
    void aFileMovedIntoAKeychainWithAnEntryItCannotReadKeepsThatEntryAndTheOthers() throws SecretsProblem {
        var keychain = withForecastSolarUnreadable();
        file().write(new Secrets(Map.of(SOLCAST, Map.of("api_key", "SC-IN-FILE"))));

        keys(keychain).moveFileIntoKeychain();

        assertThat(file().path()).doesNotExist();
        assertThat(keychain.entries).containsEntry(OPEN_METEO_ENTRY, "OM-SECRET-1")
                .containsEntry(FORECAST_SOLAR_ENTRY, "FS-SECRET-2")
                .containsEntry("forecast/solcast/api_key", "SC-IN-FILE");
        assertThat(index(keychain)).containsExactly(FORECAST_SOLAR_ENTRY, OPEN_METEO_ENTRY, "forecast/solcast/api_key");
    }

    @Test
    void aRefusedDeleteOfAnEntryThatCannotBeReadFailsTheSaveWithoutTheFile() throws SecretsProblem {
        var keychain = withForecastSolarUnreadable();
        keychain.refusesDelete = true;
        var removed = new KeyChanges(Secrets.NONE, Map.of(FORECAST_SOLAR, Set.of("api_key")));

        assertThatThrownBy(() -> keys(keychain).applyChanges(removed, () -> { }))
                .isInstanceOfSatisfying(SecretsProblem.class,
                        problem -> assertThat(problem.kind()).isEqualTo(SecretsProblem.Kind.FAILED))
                .hasMessageNotContaining("SECRET");

        assertThat(file().path()).doesNotExist();
        assertThat(keychain.entries).containsEntry(OPEN_METEO_ENTRY, "OM-SECRET-1");
    }

    @Test
    void whenTheIndexCannotBeWrittenWhileAnEntryCannotBeReadTheSaveFailsWithoutTheFile() throws SecretsProblem {
        var refusesIndex = holdingTheKeys(new UnreadableEntryKeychain(FORECAST_SOLAR_ENTRY) {
            @Override
            public void write(String name, String value) throws KeychainException {
                if (name.equals(KeychainEntries.INDEX)) {
                    throw new KeychainException(KeychainException.Kind.REFUSED, "not kept");
                }
                super.write(name, value);
            }
        });
        var before = Map.copyOf(refusesIndex.entries);

        assertThatThrownBy(() -> keys(refusesIndex).applyChanges(NEW_SOLCAST_KEY, () -> { }))
                .isInstanceOfSatisfying(SecretsProblem.class,
                        problem -> assertThat(problem.kind()).isEqualTo(SecretsProblem.Kind.FAILED));

        assertThat(file().path()).doesNotExist();
        assertThat(refusesIndex.entries).containsAllEntriesOf(before);
    }

    @Test
    void anIndexTheKeychainRefusesToGiveSendsASaveToTheFile() throws SecretsProblem {
        var keys = keys(new UnreadableEntryKeychain(KeychainEntries.INDEX));

        keys.applyChanges(NEW_SOLCAST_KEY, () -> { });

        assertThat(file().read()).isEqualTo(new Secrets(Map.of(SOLCAST, Map.of("api_key", "SC-NEW"))));
    }

    @Test
    void removingAnotherKeyKeepsTheEntryThatCannotBeRead() throws SecretsProblem {
        var keychain = withForecastSolarUnreadable();

        keys(keychain).applyChanges(new KeyChanges(Secrets.NONE, Map.of(OPEN_METEO, Set.of("api_key"))), () -> { });

        assertThat(keychain.entries).containsOnlyKeys(KeychainEntries.INDEX, FORECAST_SOLAR_ENTRY)
                .containsEntry(FORECAST_SOLAR_ENTRY, "FS-SECRET-2");
        assertThat(index(keychain)).containsExactly(FORECAST_SOLAR_ENTRY);
    }

    @Test
    void anIndexRefusedAfterTheKeychainSaidItIsUsableSendsTheSaveToTheFileAndKeepsEveryEntry()
            throws SecretsProblem {
        var keychain = holdingTheKeys(new RefusesTheIndexAfterTheFirstRead());
        var before = Map.copyOf(keychain.entries);

        keys(keychain).applyChanges(NEW_SOLCAST_KEY, () -> { });

        assertThat(keychain.entries).isEqualTo(before);
        assertThat(file().read()).isEqualTo(new Secrets(Map.of(SOLCAST, Map.of("api_key", "SC-NEW"))));
    }

    @Test
    void aLockedEntryAfterOneThatCannotBeReadHasTheOwnerUnlockAndWritesNothingUnanswered() {
        var keychain = holdingTheKeys(new LockableWithAnUnreadableEntry());
        keychain.lockedEntry = OPEN_METEO_ENTRY;
        var before = Map.copyOf(keychain.entries);

        assertThatThrownBy(() -> keys(keychain).applyChanges(NEW_SOLCAST_KEY, () -> { }))
                .isInstanceOfSatisfying(SecretsProblem.class,
                        problem -> assertThat(problem.kind()).isEqualTo(SecretsProblem.Kind.LOCKED));

        assertThat(keychain.prompts).hasValue(1);
        assertThat(keychain.entries).isEqualTo(before);
        assertThat(file().path()).doesNotExist();
    }

    @Test
    void aSaveIntoALockedKeychainWithAnEntryItCannotReadGoesToTheFileWhenDismissedAndKeepsTheEntry()
            throws SecretsProblem {
        var keychain = holdingTheKeys(new LockableWithAnUnreadableEntry());
        keychain.locked = true;
        keychain.answer = UnlockOutcome.DISMISSED;
        var before = Map.copyOf(keychain.entries);

        keys(keychain).applyChanges(NEW_SOLCAST_KEY, () -> { });

        assertThat(file().read()).isEqualTo(new Secrets(Map.of(SOLCAST, Map.of("api_key", "SC-NEW"))));
        assertThat(keychain.entries).isEqualTo(before);
    }

    @Test
    void enteringAKeyWhoseEntryCannotBeReadWhileItsWriteIsRefusedFailsTheSaveWithoutTheFile() {
        var keychain = holdingTheKeys(new RefusesWrites(Set.of(FORECAST_SOLAR_ENTRY)));
        var before = Map.copyOf(keychain.entries);
        var entered = KeyChanges.entering(new Secrets(Map.of(FORECAST_SOLAR, Map.of("api_key", "FS-NEW"))));

        assertThatThrownBy(() -> keys(keychain).applyChanges(entered, () -> { }))
                .isInstanceOfSatisfying(SecretsProblem.class,
                        problem -> assertThat(problem.kind()).isEqualTo(SecretsProblem.Kind.FAILED));

        assertThat(file().path()).doesNotExist();
        assertThat(keychain.entries).isEqualTo(before);
    }

    @Test
    void aMoveIntoAKeychainWithAnEntryItCannotReadThatRefusesWritesKeepsTheFileAndTheEntries() throws SecretsProblem {
        var keychain = holdingTheKeys(new RefusesWrites(Set.of("forecast/solcast/api_key")));
        var before = Map.copyOf(keychain.entries);
        var inFile = new Secrets(Map.of(SOLCAST, Map.of("api_key", "SC-IN-FILE")));
        file().write(inFile);
        var keys = keys(keychain);

        keys.moveFileIntoKeychain();

        assertThat(file().read()).isEqualTo(inFile);
        assertThat(keychain.entries).isEqualTo(before);
        assertThat(keys.storage()).isEqualTo(KeyStorage.FILE);
    }

    @Test
    void aSaveWithoutChangesLeavesAnEntryThatCannotBeReadAndTheOthersAsTheyAre() throws SecretsProblem {
        var keychain = withForecastSolarUnreadable();
        var before = Map.copyOf(keychain.entries);

        keys(keychain).applyChanges(KeyChanges.NONE, () -> { });

        assertThat(keychain.entries).isEqualTo(before);
    }

    @Test
    void withTheFileAsFallbackAFailedSaveBesideAnEntryThatCannotBeReadSaysTheFileWasNotUsedEither() {
        var keychain = holdingTheKeys(new RefusesWrites(Set.of(FORECAST_SOLAR_ENTRY)));
        var entered = KeyChanges.entering(new Secrets(Map.of(FORECAST_SOLAR, Map.of("api_key", "FS-NEW"))));

        assertThatThrownBy(() -> keys(keychain).applyChanges(entered, () -> { }))
                .isInstanceOf(SecretsProblem.class).hasMessageContaining("not stored in the file either");
    }
}
