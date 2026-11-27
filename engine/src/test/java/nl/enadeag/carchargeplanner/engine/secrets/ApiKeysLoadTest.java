package nl.enadeag.carchargeplanner.engine.secrets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import nl.enadeag.carchargeplanner.LogCapture;
import java.nio.file.Files;
import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class ApiKeysLoadTest extends ApiKeysFixture {

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

    @Test
    void theFileWithoutAKeychainIsTheOneItWasGiven() {
        var file = file();
        assertThat(new ApiKeys(new NoKeychain(), file,
                KeyFallback.PRIVATE_FILE, Clock.systemUTC()).file()).isSameAs(file);
    }

    @Test
    void theFallbackIsRequired() {
        assertThatThrownBy(() -> new ApiKeys(keychain, file(), null, Clock.systemUTC()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("fallback");
    }
}
