package io.github.enadeag.carchargeplanner.engine.away;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class RecurringAwayPeriodTest {

    private static final ZoneId TZ = ZoneId.of("Europe/Amsterdam");
    private static final LocalDate THURSDAY = LocalDate.of(2026, 7, 2);
    /** 02:00 winter time becomes 03:00 summer time. */
    private static final LocalDate SPRING_FORWARD = LocalDate.of(2026, 3, 29);

    private static RecurringAwayPeriod weekly(DayOfWeek weekday, int startHour, int startMinute, int endHour) {
        return new RecurringAwayPeriod(weekday, LocalTime.of(startHour, startMinute), LocalTime.of(endHour, 0));
    }

    @Test
    void onItsWeekdayItIsAwayFromTheStartToTheEnd() {
        assertThat(weekly(DayOfWeek.THURSDAY, 6, 0, 19).on(THURSDAY, TZ))
                .contains(AwayPeriod.on(THURSDAY, LocalTime.of(6, 0), LocalTime.of(19, 0), TZ));
    }

    @Test
    void onAnotherWeekdayItIsNotAway() {
        assertThat(weekly(DayOfWeek.THURSDAY, 6, 0, 19).on(THURSDAY.plusDays(1), TZ)).isEmpty();
    }

    @Test
    void acrossTheSpringForwardGapItLastsTheRealHours() {
        var period = weekly(DayOfWeek.SUNDAY, 1, 0, 4).on(SPRING_FORWARD, TZ).orElseThrow();
        assertThat(Duration.between(period.start(), period.end())).isEqualTo(Duration.ofHours(2));
    }

    @Test
    void aPeriodThatTheSpringForwardGapSwallowsIsNotAwayThatDay() {
        assertThat(weekly(DayOfWeek.SUNDAY, 2, 30, 3).on(SPRING_FORWARD, TZ)).isEmpty();
    }
}
