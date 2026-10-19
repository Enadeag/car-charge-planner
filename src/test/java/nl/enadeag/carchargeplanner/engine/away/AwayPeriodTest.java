package nl.enadeag.carchargeplanner.engine.away;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class AwayPeriodTest {

    private static final ZoneId TZ = ZoneId.of("Europe/Amsterdam");
    private static final LocalDate DAY = LocalDate.of(2026, 7, 3);

    @Test
    void allDaySpansLocalMidnightToMidnight() {
        var period = AwayPeriod.allDay(DAY, TZ);
        assertThat(period.start()).isEqualTo(DAY.atStartOfDay(TZ).toInstant());
        assertThat(period.end()).isEqualTo(DAY.plusDays(1).atStartOfDay(TZ).toInstant());
    }

    @Test
    void allDayOnTheSpringForwardDayIs23Hours() {
        var period = AwayPeriod.allDay(LocalDate.of(2026, 3, 29), TZ);
        assertThat(Duration.between(period.start(), period.end())).isEqualTo(Duration.ofHours(23));
    }

    @Test
    void partOfADayIsReadInTheHomesTimeZone() {
        var period = AwayPeriod.on(DAY, LocalTime.of(9, 0), LocalTime.of(17, 30), TZ);
        assertThat(period.start()).isEqualTo(DAY.atTime(9, 0).atZone(TZ).toInstant());
        assertThat(period.end()).isEqualTo(DAY.atTime(17, 30).atZone(TZ).toInstant());
    }

    @ParameterizedTest
    @CsvSource({"16:00, 10:00", "10:00, 10:00"})
    void partOfADayMustEndAfterItStarts(LocalTime start, LocalTime end) {
        assertThatThrownBy(() -> AwayPeriod.on(DAY, start, end, TZ)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aPeriodMustEndAfterItStarts() {
        var moment = DAY.atStartOfDay(TZ).toInstant();
        assertThatThrownBy(() -> new AwayPeriod(moment, moment)).isInstanceOf(IllegalArgumentException.class);
    }
}
