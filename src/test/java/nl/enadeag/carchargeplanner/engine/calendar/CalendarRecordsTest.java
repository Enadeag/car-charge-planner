package nl.enadeag.carchargeplanner.engine.calendar;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** The calendar's records refuse what their rules forbid, so one that exists is valid. */
class CalendarRecordsTest {

    private static final Instant NINE = Instant.parse("2026-10-21T09:00:00Z");
    private static final LocalDate DAY = LocalDate.of(2026, 10, 21);

    static Stream<Arguments> refused() {
        return Stream.of(
                Arguments.of("days that end before they begin",
                        (ThrowingCallable) () -> new CoveredDays(DAY, DAY.minusDays(1), RecordedCalendars.AMSTERDAM)),
                Arguments.of("an event that ends before it starts",
                        (ThrowingCallable) () -> new EventTime.Timed(NINE, NINE.minusSeconds(1))),
                Arguments.of("an all-day event whose last day is before its first",
                        (ThrowingCallable) () -> new EventTime.AllDay(DAY, DAY.minusDays(1))),
                Arguments.of("an event without a UID", (ThrowingCallable) () -> new CalendarEvent(" ", DAY,
                        new EventTime.AllDay(DAY, DAY), Optional.empty(), Optional.empty(), "", "")),
                Arguments.of("a negative count of events",
                        (ThrowingCallable) () -> new CalendarRead.Read(NINE, -1, 0)),
                Arguments.of("a negative count of unreadable events",
                        (ThrowingCallable) () -> new CalendarRead.Read(NINE, 0, -1)),
                Arguments.of("a negative count of unreadable events in a list",
                        (ThrowingCallable) () -> new IcsReader.EventList(List.of(), -1)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("refused")
    void aRecordRefusesWhatItsRuleForbids(String what, ThrowingCallable making) {
        assertThatThrownBy(making).isInstanceOf(IllegalArgumentException.class);
    }
}
