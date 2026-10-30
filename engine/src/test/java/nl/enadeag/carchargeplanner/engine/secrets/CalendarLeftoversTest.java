package nl.enadeag.carchargeplanner.engine.secrets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.STRING;

import nl.enadeag.carchargeplanner.LogCapture;
import nl.enadeag.carchargeplanner.engine.config.ProviderId;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CalendarLeftoversTest {

    private static final String ICAL_LINK = "https://calendar.example/private-0123456789abcdef/basic.ics";
    private static final String ROUTE_KEY = "GEO-SECRET-3";
    private static final Secrets FORECAST_KEYS = new Secrets(Map.of(
            new ProviderId("forecast-solar"), Map.of("api_key", "FS-SECRET-2")));
    private static final Secrets WITH_LEFTOVERS = new Secrets(Map.of(
            new ProviderId("forecast-solar"), Map.of("api_key", "FS-SECRET-2"),
            new ProviderId("calendar"), Map.of("ical-link", ICAL_LINK),
            new ProviderId("geoapify"), Map.of("api-key", ROUTE_KEY)));
    private static final String CONSENTS = """
            {"version": 1, "consents": [{"consent": "READ_CALENDAR", "givenAt": "2026-10-22T08:00:00Z", \
            "textVersion": 1}, {"consent": "SEND_LOCATIONS", "givenAt": "2026-10-22T08:00:00Z", "textVersion": 1}]}""";

    @TempDir
    Path folder;

    private final LockableKeychain keychain = new LockableKeychain();

    private ApiKeys keys() {
        return new ApiKeys(keychain, new SecretsFile(folder.resolve("secrets.json")));
    }

    private Path consents() {
        return folder.resolve("calendar-consents.json");
    }

    @Test
    void theConsentsFileAndTheCalendarsSecretsAreRemovedAndTheOtherKeysStay() throws Exception {
        Files.writeString(consents(), CONSENTS);
        keys().save(WITH_LEFTOVERS);

        try (var log = LogCapture.of(CalendarLeftovers.class)) {
            CalendarLeftovers.remove(keys(), folder);

            assertThat(log.lines()).hasSize(2).allMatch(line -> line.startsWith("INFO "))
                    .noneMatch(line -> line.contains(ICAL_LINK) || line.contains(ROUTE_KEY));
        }
        assertThat(consents()).doesNotExist();
        assertThat(keys().load()).isEqualTo(FORECAST_KEYS);
        assertThat(keychain.entries.values()).doesNotContain(ICAL_LINK, ROUTE_KEY);
    }

    @Test
    void theSecretsKeptInTheFileWithoutAKeychainAreRemovedToo() throws Exception {
        var file = new SecretsFile(folder.resolve("secrets.json"));
        file.write(WITH_LEFTOVERS);

        CalendarLeftovers.remove(new ApiKeys(new NoKeychain(), file), folder);

        assertThat(file.read()).isEqualTo(FORECAST_KEYS);
    }

    @Test
    void withoutLeftoversNothingChangesAndNothingIsLogged() throws Exception {
        keys().save(FORECAST_KEYS);
        var index = keychain.entries.get(KeychainEntries.INDEX);

        try (var log = LogCapture.of(CalendarLeftovers.class)) {
            CalendarLeftovers.remove(keys(), folder);

            assertThat(log.lines()).isEmpty();
        }
        assertThat(keys().load()).isEqualTo(FORECAST_KEYS);
        assertThat(keychain.entries.get(KeychainEntries.INDEX)).isEqualTo(index);
        try (var files = Files.list(folder)) {
            assertThat(files).isEmpty();
        }
    }

    @Test
    void aLockedKeychainIsLoggedAndLeftForTheNextStartWithoutAPromptWhileTheConsentsFileGoes() throws Exception {
        Files.writeString(consents(), CONSENTS);
        keys().save(WITH_LEFTOVERS);
        keychain.locked = true;

        try (var log = LogCapture.of(CalendarLeftovers.class)) {
            CalendarLeftovers.remove(keys(), folder);

            assertThat(log.lines()).hasSize(2);
            assertThat(log.lines()).last(STRING).startsWith("WARNING ").contains("next start");
        }
        assertThat(keychain.prompts).hasValue(0);
        assertThat(consents()).doesNotExist();
        keychain.locked = false;
        CalendarLeftovers.remove(keys(), folder);
        assertThat(keys().load()).isEqualTo(FORECAST_KEYS);
    }

    @Test
    void theSecretsInTheFileGoWhileTheKeychainIsLockedWhichIsLoggedForTheNextStartWithoutAPrompt() throws Exception {
        var file = new SecretsFile(folder.resolve("secrets.json"));
        file.write(WITH_LEFTOVERS);
        keychain.locked = true;

        try (var log = LogCapture.of(CalendarLeftovers.class)) {
            CalendarLeftovers.remove(new ApiKeys(keychain, file), folder);

            assertThat(log.lines()).singleElement().asString().startsWith("WARNING ").contains("next start");
        }
        assertThat(file.read()).isEqualTo(FORECAST_KEYS);
        assertThat(keychain.prompts).hasValue(0);
    }

    @Test
    void aConsentsFileThatCannotBeRemovedIsLoggedAndTheSecretsStillGo() throws IOException, SecretsProblem {
        Files.createDirectories(consents());
        Files.writeString(consents().resolve("in-the-way"), "");
        keys().save(WITH_LEFTOVERS);

        try (var log = LogCapture.of(CalendarLeftovers.class)) {
            CalendarLeftovers.remove(keys(), folder);

            assertThat(log.lines()).first(STRING).startsWith("WARNING ").contains(consents().toString());
        }
        assertThat(keys().load()).isEqualTo(FORECAST_KEYS);
    }
}
