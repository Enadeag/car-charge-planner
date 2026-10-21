package nl.enadeag.carchargeplanner.desktop.dashboard.away;

import static org.assertj.core.api.Assertions.assertThat;

import nl.enadeag.carchargeplanner.desktop.dashboard.away.OneOffAwayCheck.Checked;
import nl.enadeag.carchargeplanner.desktop.text.TextKey;
import nl.enadeag.carchargeplanner.engine.away.OneOffAwayPeriod;
import java.time.Instant;
import java.time.InstantSource;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class OneOffAwayCheckTest {

    private static final ZoneId BRUSSELS = ZoneId.of("Europe/Brussels");
    private static final LocalDate DAY = LocalDate.of(2026, 7, 3);

    private static Instant at(LocalDate day, int hour, int minute) {
        return day.atTime(hour, minute).atZone(BRUSSELS).toInstant();
    }

    private static OneOffAwayCheck onDayAt(LocalDate day, Instant now) {
        return new OneOffAwayCheck(day, BRUSSELS, InstantSource.fixed(now));
    }

    private static OneOffAwayCheck morning() {
        return onDayAt(DAY, at(DAY, 6, 0));
    }

    @Test
    void theWholeDayRunsFromMidnightToMidnightInTheHomesZone() {
        assertThat(morning().wholeDay()).isEqualTo(new Checked.Valid(OneOffAwayPeriod.allDay(DAY, BRUSSELS)));
    }

    @Test
    void theWholeDayIsStillValidInTheEveningOfThatDayButNotAfterItEnded() {
        assertThat(onDayAt(DAY, at(DAY, 23, 59)).wholeDay()).isInstanceOf(Checked.Valid.class);
        assertThat(onDayAt(DAY, at(DAY.plusDays(1), 0, 0)).wholeDay()).isEqualTo(new Checked.Refused(TextKey.ONE_OFF_PASSED));
    }

    @ParameterizedTest
    @CsvSource({"10:00, 16:30, 10:00, 16:30", "9:05, 17:00, 09:05, 17:00", "  7:30 , 8:00 , 07:30, 08:00",
        "0:00, 23:59, 00:00, 23:59"})
    void timesAreReadOnA24HourClockWithOrWithoutALeadingZeroAndSpacesAround(String start, String end, String from,
            String to) {
        var expected = OneOffAwayPeriod.on(DAY, LocalTime.parse(from), LocalTime.parse(to), BRUSSELS);

        assertThat(morning().partOfDay(start, end)).isEqualTo(new Checked.Valid(expected));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "ten", "24:00", "10:60", "10", "10.30", "1000", "-1:00", "10:5", "10:00 am", "1 0:00"})
    void aStartThatIsNotATimeIsRefusedAsSuch(String typed) {
        assertThat(morning().partOfDay(typed, "17:00")).isEqualTo(new Checked.Refused(TextKey.PROBLEM_NOT_A_TIME));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "noon", "24:00", "25:00", "10:61"})
    void anEndThatIsNotATimeIsRefusedAsSuch(String typed) {
        assertThat(morning().partOfDay("9:00", typed)).isEqualTo(new Checked.Refused(TextKey.PROBLEM_NOT_A_TIME));
    }

    @Test
    void aTimeThatIsNotATimeIsReportedBeforeAnEndBeforeTheStart() {
        assertThat(morning().partOfDay("17:00", "ten")).isEqualTo(new Checked.Refused(TextKey.PROBLEM_NOT_A_TIME));
    }

    @ParameterizedTest
    @CsvSource({"17:00, 9:00", "10:00, 10:00", "23:59, 0:00", "10:01, 10:00"})
    void anEndAtOrBeforeTheStartIsRefused(String start, String end) {
        assertThat(morning().partOfDay(start, end)).isEqualTo(new Checked.Refused(TextKey.ONE_OFF_END_BEFORE_START));
    }

    @Test
    void twoTimesThatAreTheSameMomentWhenTheClockIsPutForwardAreRefusedAsEndBeforeStart() {
        var springForward = LocalDate.of(2026, 3, 29);
        var check = onDayAt(springForward, at(springForward.minusDays(1), 12, 0));

        assertThat(check.partOfDay("2:30", "3:30")).isEqualTo(new Checked.Refused(TextKey.ONE_OFF_END_BEFORE_START));
    }

    @Test
    void aPartOfTheDayThatEndedIsRefusedAsPassedAlsoWhenItStartedEarlier() {
        var check = onDayAt(DAY, at(DAY, 12, 0));

        assertThat(check.partOfDay("10:00", "11:59")).isEqualTo(new Checked.Refused(TextKey.ONE_OFF_PASSED));
        assertThat(check.partOfDay("10:00", "12:00")).isEqualTo(new Checked.Refused(TextKey.ONE_OFF_PASSED));
    }

    @Test
    void aPartOfTheDayThatEndsAMinuteAfterNowIsValidAlsoWhenItHasStarted() {
        var check = onDayAt(DAY, at(DAY, 12, 0));

        assertThat(check.partOfDay("10:00", "12:01"))
                .isEqualTo(new Checked.Valid(OneOffAwayPeriod.on(DAY, LocalTime.of(10, 0), LocalTime.of(12, 1), BRUSSELS)));
    }

    @Test
    void theTimesAreReadInTheHomesZoneNotInUtc() {
        var check = new OneOffAwayCheck(DAY, ZoneId.of("America/New_York"), InstantSource.fixed(at(DAY, 6, 0)));

        assertThat(check.partOfDay("9:00", "10:00")).isEqualTo(new Checked.Valid(OneOffAwayPeriod.on(DAY,
                LocalTime.of(9, 0), LocalTime.of(10, 0), ZoneId.of("America/New_York"))));
    }

    @Test
    void theClockIsReadWhenTheEntryIsChecked() {
        var now = new Instant[] {at(DAY, 6, 0)};
        var check = new OneOffAwayCheck(DAY, BRUSSELS, () -> now[0]);
        assertThat(check.partOfDay("10:00", "11:00")).isInstanceOf(Checked.Valid.class);

        now[0] = at(DAY, 11, 0);

        assertThat(check.partOfDay("10:00", "11:00")).isEqualTo(new Checked.Refused(TextKey.ONE_OFF_PASSED));
    }
}
