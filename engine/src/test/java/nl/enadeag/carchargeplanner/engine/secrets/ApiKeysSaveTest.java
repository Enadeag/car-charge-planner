package nl.enadeag.carchargeplanner.engine.secrets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ApiKeysSaveTest extends ApiKeysFixture {

    @Test
    void whenTheKeychainRefusesASaveTheKeysGoToTheFile() throws SecretsProblem {
        var keys = keys(new FailingWrites());
        keys.save(KEYS);

        assertThat(file().path()).exists();
        assertThat(keys.load()).isEqualTo(KEYS);
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
    void anUpdateOverADamagedIndexKeepsOnlyWhatWasEntered() throws SecretsProblem {
        keychain.entries.put("index", "not a list");

        keys(keychain).applyChanges(NEW_OPEN_METEO_KEY, () -> { });

        assertThat(file().path()).doesNotExist();
        assertThat(keychain.entries).containsEntry("forecast/open-meteo/api_key", "OM-NEW");
        assertThat(keys(keychain).load()).isEqualTo(new Secrets(Map.of(OPEN_METEO, Map.of("api_key", "OM-NEW"))));
    }

    @Test
    void aSaveDoesNotReadTheKeysItReplaces() throws SecretsProblem {
        var unreadable = new EntryThatDoesNotAnswer();
        unreadable.entries.put("index", "[\"forecast/forecast-solar/api_key\"]");
        var changed = new Secrets(Map.of(OPEN_METEO, Map.of("api_key", "OM-NEW")));

        keys(unreadable).save(changed);

        assertThat(unreadable.entries).containsEntry("forecast/open-meteo/api_key", "OM-NEW");
        assertThat(file().path()).doesNotExist();
    }

    @Test
    void aRemovalTakesOnlyTheNamedSettingsOutOfTheKeychain() throws SecretsProblem {
        var keys = keys(keychain);
        keys.save(new Secrets(Map.of(OPEN_METEO, Map.of("api_key", "OM-SECRET-1", "other", "OM-OTHER"),
                FORECAST_SOLAR, Map.of("api_key", "FS-SECRET-2"))));

        keys.applyChanges(new KeyChanges(Secrets.NONE,
                Map.of(OPEN_METEO, Set.of("other"), FORECAST_SOLAR, Set.of("api_key", "absent"))), () -> { });

        assertThat(keys(keychain).load()).isEqualTo(new Secrets(Map.of(OPEN_METEO, Map.of("api_key", "OM-SECRET-1"))));
        assertThat(keychain.entries).containsOnlyKeys("index", "forecast/open-meteo/api_key");
    }

    static Stream<Arguments> changesToAReadableKeychainHoldingTheKeysThatRefusesWrites() {
        var entering = NEW_SOLCAST_KEY;
        var removing = new KeyChanges(Secrets.NONE, Map.of(OPEN_METEO, Set.of("api_key")));
        return Stream.of(Arguments.of(holdingTheKeys(new FailingWrites()), entering),
                Arguments.of(holdingTheKeys(new RefusingWrites()), entering),
                Arguments.of(holdingTheKeys(new FailingWrites()), removing),
                Arguments.of(holdingTheKeys(new RefusingWrites()), removing));
    }

    @ParameterizedTest
    @MethodSource("changesToAReadableKeychainHoldingTheKeysThatRefusesWrites")
    void aChangeIntoAReadableKeychainWithKeysThatRefusesWritesFailsAndWritesNoFileThatWouldHideThem(
            MemoryKeychain refusing, KeyChanges changes) throws SecretsProblem {
        var before = Map.copyOf(refusing.entries);
        var keys = keys(refusing);

        assertThatThrownBy(() -> keys.applyChanges(changes, () -> { }))
                .isInstanceOfSatisfying(SecretsProblem.class,
                        problem -> assertThat(problem.kind()).isEqualTo(SecretsProblem.Kind.FAILED))
                .hasMessageContaining("not stored in the file either")
                .hasMessageNotContaining("SC-NEW").hasMessageNotContaining("SECRET");

        assertThat(file().path()).doesNotExist();
        assertThat(refusing.entries).isEqualTo(before);
        assertThat(keys.load()).isEqualTo(KEYS);
        assertThat(keys.storage()).isEqualTo(KeyStorage.KEYCHAIN);
    }

    @Test
    void aChangeIntoAReadableKeychainWithKeysThatRefusesWritesLeavesAFileFromBeforeAsItIs()
            throws IOException, SecretsProblem {
        var refusing = holdingTheKeys(new RefusingWrites());
        file().write(new Secrets(Map.of(SOLCAST, Map.of("api_key", "SC-IN-FILE"))));
        var before = Files.readString(file().path());

        assertThatThrownBy(() -> keys(refusing).applyChanges(NEW_OPEN_METEO_KEY, () -> { }))
                .isInstanceOf(SecretsProblem.class);

        assertThat(Files.readString(file().path())).isEqualTo(before);
    }

    @Test
    void aChangeIntoAReadableKeychainThatRefusesWritesAndHoldsNoKeyGoesToTheFile() throws SecretsProblem {
        var refusing = new RefusingWrites();
        refusing.entries.put(KeychainEntries.INDEX, "[\"" + OPEN_METEO_ENTRY + "\"]"); // listed, but holds no value

        keys(refusing).applyChanges(NEW_SOLCAST_KEY, () -> { });

        assertThat(file().read()).isEqualTo(new Secrets(Map.of(SOLCAST, Map.of("api_key", "SC-NEW"))));
    }

    static Stream<MemoryKeychain> readableKeychainsHoldingTheKeysThatRefuseWrites() {
        return Stream.of(holdingTheKeys(new FailingWrites()), holdingTheKeys(new RefusingWrites()));
    }

    @ParameterizedTest
    @MethodSource("readableKeychainsHoldingTheKeysThatRefuseWrites")
    void aSaveThatReplacesEveryKeyStillGoesToTheFileWhenAKeychainWithKeysRefusesWrites(MemoryKeychain refusing)
            throws SecretsProblem {
        var replacing = new Secrets(Map.of(SOLCAST, Map.of("api_key", "SC-NEW")));

        keys(refusing).save(replacing);

        assertThat(file().read()).isEqualTo(replacing);
        assertThat(keys(refusing).load()).isEqualTo(replacing);
    }

    @Test
    void withoutAKeychainARemovalTakesTheSettingOutOfTheFile() throws SecretsProblem {
        file().write(KEYS);

        keys(new NoKeychain()).applyChanges(new KeyChanges(Secrets.NONE, Map.of(FORECAST_SOLAR, Set.of("api_key"))),
                () -> { });

        assertThat(file().read()).isEqualTo(new Secrets(Map.of(OPEN_METEO, Map.of("api_key", "OM-SECRET-1"))));
    }

    @Test
    void aKeychainsProvidersAndSettingsThatNoProviderKnowsSurviveASaveAndALoad() throws SecretsProblem {
        var lockable = new LockableKeychain();
        keys(lockable).save(WITH_UNKNOWN_PROVIDERS);

        keys(lockable).applyChanges(NEW_OPEN_METEO_KEY, () -> { });

        assertThat(keys(lockable).load()).isEqualTo(UNKNOWN_PROVIDERS_AFTER_A_CHANGE);
        assertThat(lockable.prompts).hasValue(0);
    }

    @Test
    void aFilesProvidersAndSettingsThatNoProviderKnowsSurviveASaveAndALoad() throws SecretsProblem {
        file().write(WITH_UNKNOWN_PROVIDERS);
        assertThat(keys(new NoKeychain()).load()).isEqualTo(WITH_UNKNOWN_PROVIDERS);

        keys(new NoKeychain()).applyChanges(NEW_OPEN_METEO_KEY, () -> { });

        assertThat(file().read()).isEqualTo(UNKNOWN_PROVIDERS_AFTER_A_CHANGE);
        assertThat(keys(new NoKeychain()).load()).isEqualTo(UNKNOWN_PROVIDERS_AFTER_A_CHANGE);
    }
}
