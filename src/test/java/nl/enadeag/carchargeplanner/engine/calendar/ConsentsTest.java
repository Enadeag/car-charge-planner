package nl.enadeag.carchargeplanner.engine.calendar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ConsentsTest {

    private static final Instant MONDAY = Instant.parse("2026-10-19T08:00:00Z");
    private static final Instant NOW = Instant.parse("2026-10-22T08:00:00Z");
    private static final ConsentRecord READ = ConsentRecord.givenNow(Consent.READ_CALENDAR, MONDAY);
    private static final ConsentRecord SEND = ConsentRecord.givenNow(Consent.SEND_LOCATIONS, MONDAY);
    /** A consent given to another version of the text than the one the app shows now. */
    private static final ConsentRecord OTHER_TEXT = new ConsentRecord(Consent.SEND_LOCATIONS, MONDAY,
            Consent.SEND_LOCATIONS.textVersion() + 1);

    private static Consents of(ConsentRecord... records) {
        var given = new EnumMap<Consent, ConsentRecord>(Consent.class);
        for (var record : records) {
            given.put(record.consent(), record);
        }
        return new Consents(given);
    }

    @Test
    void theCalendarMayBeReadOnlyWithBothConsents() {
        assertThat(Consents.NONE.allCount()).isFalse();
        assertThat(of(READ).allCount()).isFalse();
        assertThat(of(SEND).allCount()).isFalse();
        assertThat(of(READ, SEND).allCount()).isTrue();
    }

    @Test
    void aConsentToAnotherVersionOfItsTextDoesNotCount() {
        var consents = of(READ, OTHER_TEXT);

        assertThat(consents.counts(Consent.SEND_LOCATIONS)).isFalse();
        assertThat(consents.allCount()).isFalse();
        assertThat(consents.counting()).containsExactly(Consent.READ_CALENDAR);
        assertThat(consents.record(Consent.SEND_LOCATIONS)).contains(OTHER_TEXT);
    }

    @Test
    void aConsentGivenNowIsStampedWithTheMomentAndTheCurrentTextVersion() {
        var given = Consents.NONE.given(Set.of(Consent.READ_CALENDAR, Consent.SEND_LOCATIONS), NOW);

        assertThat(given).isEqualTo(of(new ConsentRecord(Consent.READ_CALENDAR, NOW, 1),
                new ConsentRecord(Consent.SEND_LOCATIONS, NOW, 1)));
    }

    @Test
    void aConsentThatCountsKeepsItsDate() {
        assertThat(of(READ, SEND).given(Set.of(Consent.READ_CALENDAR, Consent.SEND_LOCATIONS), NOW))
                .isEqualTo(of(READ, SEND));
    }

    @Test
    void aConsentToAnotherTextIsGivenAnewWhenChosen() {
        assertThat(of(READ, OTHER_TEXT).given(Set.of(Consent.READ_CALENDAR, Consent.SEND_LOCATIONS), NOW))
                .isEqualTo(of(READ, ConsentRecord.givenNow(Consent.SEND_LOCATIONS, NOW)));
    }

    @Test
    void aConsentThatCountsAndIsNotChosenIsWithdrawn() {
        assertThat(of(READ, SEND).given(Set.of(Consent.SEND_LOCATIONS), NOW)).isEqualTo(of(SEND));
        assertThat(of(READ, SEND).given(Set.of(), NOW)).isEqualTo(Consents.NONE);
    }

    @Test
    void aConsentToAnotherTextThatIsNotChosenStaysAsItIs() {
        assertThat(of(READ, OTHER_TEXT).given(Set.of(Consent.READ_CALENDAR), NOW)).isEqualTo(of(READ, OTHER_TEXT));
    }

    @Test
    void aRecordFiledUnderTheOtherConsentIsRefused() {
        assertThatThrownBy(() -> new Consents(Map.of(Consent.SEND_LOCATIONS, READ)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aRecordNeedsItsConsentItsMomentAndAVersionFromOne() {
        assertThatThrownBy(() -> new ConsentRecord(null, NOW, 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ConsentRecord(Consent.READ_CALENDAR, null, 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ConsentRecord(Consent.READ_CALENDAR, NOW, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(new ConsentRecord(Consent.READ_CALENDAR, NOW, 1).textVersion()).isEqualTo(1);
    }
}
