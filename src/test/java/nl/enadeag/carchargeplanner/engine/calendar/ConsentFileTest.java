package nl.enadeag.carchargeplanner.engine.calendar;

import static org.assertj.core.api.Assertions.assertThat;

import nl.enadeag.carchargeplanner.engine.store.StoredJson;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ConsentFileTest {

    private static final Instant NOW = Instant.parse("2026-10-22T08:00:00Z");
    private static final Consents BOTH = new Consents(Map.of(
            Consent.READ_CALENDAR, ConsentRecord.givenNow(Consent.READ_CALENDAR, NOW),
            Consent.SEND_LOCATIONS, new ConsentRecord(Consent.SEND_LOCATIONS, NOW.plusSeconds(60), 3)));

    @TempDir
    Path folder;

    private Path path() {
        return folder.resolve("data").resolve("calendar-consents.json");
    }

    @Test
    void savedConsentsAreReadBackWithTheirDatesAndTextVersions() throws IOException {
        new ConsentFile(path()).save(BOTH);

        assertThat(new ConsentFile(path()).consents()).isEqualTo(BOTH);
        var written = StoredJson.parse(Files.readString(path()));
        assertThat(written.path("version").intValue()).isEqualTo(1);
        assertThat(written.path("consents").get(1).path("textVersion").intValue()).isEqualTo(3);
    }

    @Test
    void withoutAFileNoConsentIsGiven() {
        assertThat(new ConsentFile(path()).consents()).isEqualTo(Consents.NONE);
    }

    @Test
    void withdrawingTheLastConsentRemovesTheFile() throws IOException {
        var file = new ConsentFile(path());
        file.save(BOTH);

        file.save(Consents.NONE);

        assertThat(path()).doesNotExist();
        assertThat(file.consents()).isEqualTo(Consents.NONE);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "not json",
        "[]",
        "{\"consents\": {}}",
        "{\"consents\": [{\"consent\": \"READ_EMAIL\", \"givenAt\": \"2026-10-22T08:00:00Z\", \"textVersion\": 1}]}",
        "{\"consents\": [{\"consent\": \"READ_CALENDAR\", \"givenAt\": \"yesterday\", \"textVersion\": 1}]}",
        "{\"consents\": [{\"consent\": \"READ_CALENDAR\", \"givenAt\": \"2026-10-22T08:00:00Z\", \"textVersion\": 0}]}",
        "{\"consents\": [{\"consent\": \"READ_CALENDAR\", \"givenAt\": \"2026-10-22T08:00:00Z\"}]}",
        "{\"consents\": [{\"consent\": \"READ_CALENDAR\", \"givenAt\": \"2026-10-22T08:00:00Z\", \"textVersion\": 1.5}]}",
        "{\"consents\": [{\"consent\": 1, \"givenAt\": \"2026-10-22T08:00:00Z\", \"textVersion\": 1}]}",
        "{\"consents\": [{\"consent\": \"READ_CALENDAR\", \"givenAt\": 5, \"textVersion\": 1}]}",
        "{\"consents\": [{\"consent\": \"READ_CALENDAR\", \"givenAt\": \"2026-10-22T08:00:00Z\", \"textVersion\": 1},"
                + " {\"consent\": \"READ_CALENDAR\", \"givenAt\": \"2026-10-22T08:00:00Z\", \"textVersion\": 1}]}",
        "{\"version\": 2, \"consents\": []}",
    })
    void aFileThatCannotBeUsedCountsAsNoConsent(String content) throws IOException {
        Files.createDirectories(path().getParent());
        Files.writeString(path(), content);

        assertThat(new ConsentFile(path()).consents()).isEqualTo(Consents.NONE);
    }

    @Test
    void aConsentIsReadInAnyCase() throws IOException {
        Files.createDirectories(path().getParent());
        Files.writeString(path(), "{\"consents\": [{\"consent\": \"read_calendar\", \"givenAt\": "
                + "\"2026-10-22T08:00:00Z\", \"textVersion\": 1}]}");

        assertThat(new ConsentFile(path()).consents().counting()).containsExactly(Consent.READ_CALENDAR);
    }
}
