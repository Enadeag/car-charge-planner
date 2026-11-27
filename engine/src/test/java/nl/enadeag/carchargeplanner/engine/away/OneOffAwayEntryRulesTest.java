package nl.enadeag.carchargeplanner.engine.away;

import static org.assertj.core.api.Assertions.assertThat;

import nl.enadeag.carchargeplanner.engine.away.OneOffAwayEntryRules.Checked;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class OneOffAwayEntryRulesTest {

    private static final ZoneId AMSTERDAM = ZoneId.of("Europe/Amsterdam");
    private static final LocalDate DAY = LocalDate.of(2026, 7, 3);
    /** In Amsterdam the clock goes from 02:00 to 03:00 on this day. */
    private static final LocalDate SPRING = LocalDate.of(2026, 3, 29);

    private static Instant at(LocalDate day, int hour, int minute) {
        return day.atTime(hour, minute).atZone(AMSTERDAM).toInstant();
    }

    private static Checked partOfDay(LocalDate day, String start, String end, Instant now) {
        return OneOffAwayEntryRules.partOfDay(day, LocalTime.parse(start), LocalTime.parse(end), now, AMSTERDAM);
    }

    @Test
    void theWholeDayIsTheDaysPeriodUntilItHasEnded() {
        var period = OneOffAwayPeriod.allDay(DAY, AMSTERDAM);

        assertThat(OneOffAwayEntryRules.allDay(DAY, at(DAY, 23, 59), AMSTERDAM)).isEqualTo(new Checked.Valid(period));
        assertThat(OneOffAwayEntryRules.allDay(DAY, period.end(), AMSTERDAM))
                .isEqualTo(new Checked.Refused(OneOffAwayProblem.PASSED));
    }

    @Test
    void partOfTheDayIsReadInTheHomesZone() {
        assertThat(partOfDay(DAY, "09:00", "17:30", at(DAY, 6, 0)))
                .isEqualTo(new Checked.Valid(new OneOffAwayPeriod(at(DAY, 9, 0), at(DAY, 17, 30))));
    }

    @ParameterizedTest
    @CsvSource({"16:00, 10:00", "10:00, 10:00"})
    void anEndAtOrBeforeTheStartIsRefused(String start, String end) {
        assertThat(partOfDay(DAY, start, end, at(DAY, 6, 0)))
                .isEqualTo(new Checked.Refused(OneOffAwayProblem.END_NOT_AFTER_START));
    }

    @ParameterizedTest
    // a start in the gap counts as the time after it: 02:30 is 03:30, after 03:00, and 02:00 is 03:00 itself
    @CsvSource({"02:30, 03:00", "02:00, 03:00"})
    void aStartTheClockSkipsThatComesToTheEndOrAfterItIsRefused(String start, String end) {
        assertThat(partOfDay(SPRING, start, end, at(SPRING.minusDays(1), 12, 0)))
                .isEqualTo(new Checked.Refused(OneOffAwayProblem.END_NOT_AFTER_START));
    }

    @Test
    void anEndBeforeTheStartIsRefusedAlsoWhenTheGapPutsItsMomentAfterTheStart() {
        // 02:30 is 03:30 as a moment, after 03:15, but as typed the end is before the start
        assertThat(partOfDay(SPRING, "03:15", "02:30", at(SPRING.minusDays(1), 12, 0)))
                .isEqualTo(new Checked.Refused(OneOffAwayProblem.END_NOT_AFTER_START));
    }

    @Test
    void aPeriodThatEndsAtNowHasPassedAndOneEndingAMinuteLaterHasNot() {
        assertThat(partOfDay(DAY, "09:00", "17:00", at(DAY, 17, 0)))
                .isEqualTo(new Checked.Refused(OneOffAwayProblem.PASSED));
        assertThat(partOfDay(DAY, "09:00", "17:01", at(DAY, 17, 0))).isInstanceOf(Checked.Valid.class);
    }

    @Test
    void anEndBeforeTheStartIsTheProblemAlsoWhenThePeriodHasPassed() {
        assertThat(partOfDay(DAY, "17:00", "09:00", at(DAY, 20, 0)))
                .isEqualTo(new Checked.Refused(OneOffAwayProblem.END_NOT_AFTER_START));
    }

    @Test
    void theEntryOpensFromNineToFive() {
        assertThat(OneOffAwayEntryRules.FIRST_START).isEqualTo(LocalTime.of(9, 0));
        assertThat(OneOffAwayEntryRules.FIRST_END).isEqualTo(LocalTime.of(17, 0));
    }
}
