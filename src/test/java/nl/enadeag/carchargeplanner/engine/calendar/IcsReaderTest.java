package nl.enadeag.carchargeplanner.engine.calendar;

import static nl.enadeag.carchargeplanner.engine.calendar.RecordedCalendars.AMSTERDAM;
import static nl.enadeag.carchargeplanner.engine.calendar.RecordedCalendars.GOOGLE;
import static nl.enadeag.carchargeplanner.engine.calendar.RecordedCalendars.NEXTCLOUD;
import static nl.enadeag.carchargeplanner.engine.calendar.RecordedCalendars.OCTOBER;
import static nl.enadeag.carchargeplanner.engine.calendar.RecordedCalendars.OUTLOOK;
import static nl.enadeag.carchargeplanner.engine.calendar.RecordedCalendars.text;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import nl.enadeag.carchargeplanner.engine.config.Coordinates;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class IcsReaderTest {

    private static final String VISIT = "4k2n8c0l5g7mvq1hb3s9e6t2rf@google.com";
    private static final String WORKSHOP = "040000008200E00074C5B7101A82E00800000000A1B2C3D4E5F6A70100000000000000"
            + "0010000000F1E2D3C4B5A69788796A5B4C3D2E1F00";
    private static final String SWIMMING = "6b0f3c2e-9a41-4d7b-8e15-2f6c9d0a7b31";
    private static final String NIGHT_SHIFT = "A1F0C3D2-7E64-4B59-9C8A-0D1E2F3A4B5C";

    private final IcsReader reader = new IcsReader();

    private static EventTime.Timed at(String start, String end) {
        return new EventTime.Timed(Instant.parse(start), Instant.parse(end));
    }

    private static EventTime.AllDay allDay(String first, String last) {
        return new EventTime.AllDay(LocalDate.parse(first), LocalDate.parse(last));
    }

    private static LocalDate date(String text) {
        return LocalDate.parse(text);
    }

    @Test
    void googlesExportGivesEachOccurrenceOnTheDaysInOrderOfItsStart() throws CalendarProblem {
        var read = reader.read(text(GOOGLE), OCTOBER);

        // the visit repeats at 09:00 local time: 07:00 UTC before the clock change and 08:00 after it; the one of
        // 12 October is cancelled, the one of 2 November an exception date, and the one of 9 November moved
        assertThat(read.events()).extracting(CalendarEvent::uid, CalendarEvent::occurrence, CalendarEvent::time)
                .containsExactly(
                        tuple(VISIT, date("2026-10-19"), at("2026-10-19T07:00:00Z", "2026-10-19T08:00:00Z")),
                        tuple("7p3q9r1s2t4u5v6w8x0y1z2a3b@google.com", date("2026-10-25"),
                                at("2026-10-24T23:00:00Z", "2026-10-25T02:00:00Z")),
                        tuple(VISIT, date("2026-10-26"), at("2026-10-26T08:00:00Z", "2026-10-26T09:00:00Z")),
                        tuple("5m6n7o8p9q0r1s2t3u4v5w6x7y@google.com", date("2026-10-27"),
                                at("2026-10-27T13:00:00Z", "2026-10-27T13:30:00Z")),
                        tuple("1c2d3e4f5g6h7i8j9k0l1m2n3o@google.com", date("2026-10-28"),
                                allDay("2026-10-28", "2026-10-28")),
                        tuple("9z8y7x6w5v4u3t2s1r0q9p8o7n@google.com", date("2026-10-30"),
                                allDay("2026-10-30", "2026-11-01")),
                        tuple(VISIT, date("2026-11-09"), at("2026-11-10T10:00:00Z", "2026-11-10T11:00:00Z")));
        assertThat(read.unreadable()).isZero();
    }

    @Test
    void outlooksWindowsTimeZoneNamesFollowTheirOwnDefinition() throws CalendarProblem {
        var read = reader.read(text(OUTLOOK), OCTOBER);

        // the online meeting is at 14:00 in Outlook's "UTC" time zone

        assertThat(read.events()).extracting(CalendarEvent::uid, CalendarEvent::occurrence, CalendarEvent::time)
                .containsExactly(
                        tuple(WORKSHOP, date("2026-10-20"), at("2026-10-20T06:30:00Z", "2026-10-20T10:00:00Z")),
                        tuple("040000008200E00074C5B7101A82E00800000000C3D4E5F6A7B8C90100000000000000"
                                + "00100000002B3C4D5E6F708192A3B4C5D6E7F80911", date("2026-10-26"),
                                at("2026-10-26T14:00:00Z", "2026-10-26T15:00:00Z")),
                        tuple(WORKSHOP, date("2026-10-27"), at("2026-10-27T07:30:00Z", "2026-10-27T11:00:00Z")),
                        tuple("040000008200E00074C5B7101A82E00800000000B2C3D4E5F6A7B80100000000000000"
                                + "00100000001A2B3C4D5E6F708192A3B4C5D6E7F800", date("2026-10-29"),
                                allDay("2026-10-29", "2026-10-30")),
                        tuple(WORKSHOP, date("2026-11-10"), at("2026-11-10T12:00:00Z", "2026-11-10T15:30:00Z")));
    }

    @Test
    void nextcloudsExportKeepsTheEventsCoordinatesAndTheNightOfTheClockChange() throws CalendarProblem {
        var read = reader.read(text(NEXTCLOUD), OCTOBER);

        // the night shift starts at 02:00 summer time, the first of the two, and ends at 04:00 winter time: three hours
        assertThat(read.events()).extracting(CalendarEvent::uid, CalendarEvent::occurrence, CalendarEvent::time,
                CalendarEvent::coordinates).containsExactly(
                        tuple(SWIMMING, date("2026-10-23"), at("2026-10-23T05:30:00Z", "2026-10-23T06:00:00Z"),
                                Optional.of(new Coordinates(51.0234, 5.0187))),
                        tuple(SWIMMING, date("2026-10-24"), at("2026-10-24T05:30:00Z", "2026-10-24T06:00:00Z"),
                                Optional.of(new Coordinates(51.0234, 5.0187))),
                        tuple(NIGHT_SHIFT, date("2026-10-25"), at("2026-10-25T00:00:00Z", "2026-10-25T03:00:00Z"),
                                Optional.of(new Coordinates(51.1102, 4.9318))),
                        tuple("d4c3b2a1-0f9e-4d8c-b7a6-958473625140", date("2026-10-26"),
                                allDay("2026-10-26", "2026-10-26"), Optional.empty()),
                        tuple(SWIMMING, date("2026-10-26"), at("2026-10-26T06:30:00Z", "2026-10-26T07:00:00Z"),
                                Optional.of(new Coordinates(51.0234, 5.0187))),
                        tuple(SWIMMING, date("2026-10-27"), at("2026-10-27T17:30:00Z", "2026-10-27T18:00:00Z"),
                                Optional.of(new Coordinates(51.0234, 5.0187))),
                        tuple(SWIMMING, date("2026-10-28"), at("2026-10-28T06:30:00Z", "2026-10-28T07:00:00Z"),
                                Optional.of(new Coordinates(51.0234, 5.0187))));
    }

    @Test
    void anOccurrenceHoldsItsOwnLocationTitleAndDescription() throws CalendarProblem {
        var moved = reader.read(text(GOOGLE), OCTOBER).events().getLast();

        assertThat(moved.location()).contains("Dorpsstraat 10, 1234 AC Zonnedorp, Nederland");
        assertThat(moved.title()).isEqualTo("Weekly visit");
        assertThat(moved.description()).isEqualTo("Moved to Tuesday.");
        assertThat(moved.coordinates()).isEmpty();
    }

    @Test
    void anEventWithoutALocationHasNone() throws CalendarProblem {
        var call = reader.read(text(GOOGLE), OCTOBER).events().get(3);

        assertThat(call.title()).isEqualTo("Call");
        assertThat(call.location()).isEmpty();
    }

    @Test
    void anAppleLocationOnSeveralLinesIsKeptWhole() throws CalendarProblem {
        var nightShift = reader.read(text(NEXTCLOUD), OCTOBER).events().get(2);

        assertThat(nightShift.location()).contains("Station Zeedorp\nStationsplein 1\n4321 ZY Zeedorp");
    }

    @Test
    void onlyEventsThatTouchTheCoveredDaysAreGiven() throws CalendarProblem {
        // from Saturday 31 October to Monday 2 November: the weekend away began the day before and goes on
        var days = new CoveredDays(date("2026-10-31"), date("2026-11-02"), AMSTERDAM);

        var read = reader.read(text(GOOGLE), days);

        assertThat(read.events()).extracting(CalendarEvent::occurrence).containsExactly(date("2026-10-30"));
    }

    @Test
    void aTimeWithoutATimeZoneCountsInTheHomesTimeZone() throws CalendarProblem {
        var text = calendar("""
                BEGIN:VEVENT
                UID:floating-1
                DTSTART:20261021T080000
                DTEND:20261021T090000
                END:VEVENT
                """);

        var read = reader.read(text, OCTOBER);

        assertThat(read.events()).extracting(CalendarEvent::time)
                .containsExactly(at("2026-10-21T06:00:00Z", "2026-10-21T07:00:00Z"));
    }

    @Test
    void anEventWithoutAUidIsCountedAndLeftOut() throws CalendarProblem {
        var text = calendar("""
                BEGIN:VEVENT
                DTSTART:20261021T080000Z
                DTEND:20261021T090000Z
                END:VEVENT
                BEGIN:VEVENT
                UID:with-uid
                DTSTART:20261022T080000Z
                DTEND:20261022T090000Z
                END:VEVENT
                """);

        var read = reader.read(text, OCTOBER);

        assertThat(read.events()).extracting(CalendarEvent::uid).containsExactly("with-uid");
        assertThat(read.unreadable()).isEqualTo(1);
    }

    @Test
    void textThatIsNotACalendarIsRefusedWithoutQuotingIt() {
        assertThatThrownBy(() -> reader.read("<html><title>Private meeting</title></html>", OCTOBER))
                .isInstanceOfSatisfying(CalendarProblem.class, problem -> {
                    assertThat(problem.kind()).isEqualTo(CalendarProblem.Kind.NOT_A_CALENDAR);
                    assertThat(problem.getMessage()).doesNotContain("Private meeting");
                });
    }

    private static String calendar(String events) {
        return "BEGIN:VCALENDAR\r\nVERSION:2.0\r\nPRODID:-//Test//EN\r\n" + events.replace("\n", "\r\n")
                + "END:VCALENDAR\r\n";
    }
}
