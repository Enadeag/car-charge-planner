package nl.enadeag.carchargeplanner.desktop.settings.form;

import static org.assertj.core.api.Assertions.assertThat;

import nl.enadeag.carchargeplanner.desktop.text.Language;
import nl.enadeag.carchargeplanner.desktop.text.Messages;
import nl.enadeag.carchargeplanner.desktop.text.TextKey;
import nl.enadeag.carchargeplanner.engine.calendar.CalendarProblem;
import nl.enadeag.carchargeplanner.engine.calendar.CalendarRead;
import java.time.Instant;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class CalendarReadTextTest {

    private static final Messages ENGLISH = new Messages(Language.ENGLISH);
    private static final Instant NOW = Instant.parse("2026-10-22T08:00:00Z");

    @Test
    void aReadingSaysHowManyEventsItReadAndHowManyItCouldNot() {
        assertThat(CalendarReadText.of(new CalendarRead.Read(NOW, 7, 2), ENGLISH))
                .isEqualTo(ENGLISH.text(TextKey.CALENDAR_READ, 7, 2));
    }

    @Test
    void aFailureGivesTheEnginesWords() {
        var failed = new CalendarRead.Failed(NOW, CalendarProblem.Kind.REFUSED,
                "the calendar's server answered with HTTP status 404");

        assertThat(CalendarReadText.of(failed, ENGLISH)).isEqualTo(ENGLISH.text(TextKey.CALENDAR_READ_FAILED,
                "the calendar's server answered with HTTP status 404"));
    }

    static Stream<Arguments> reasons() {
        return Stream.of(Arguments.of(CalendarRead.Reason.NO_CONSENT, TextKey.CALENDAR_NO_CONSENT),
                Arguments.of(CalendarRead.Reason.NO_SOURCE, TextKey.CALENDAR_NO_SOURCE),
                Arguments.of(CalendarRead.Reason.NO_PLAN, TextKey.CALENDAR_NO_PLAN));
    }

    @ParameterizedTest
    @MethodSource("reasons")
    void eachReasonNotToReadHasItsOwnSentence(CalendarRead.Reason reason, TextKey sentence) {
        assertThat(CalendarReadText.of(new CalendarRead.NotRead(reason), ENGLISH)).isEqualTo(ENGLISH.text(sentence));
    }
}
