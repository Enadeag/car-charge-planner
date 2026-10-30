package nl.enadeag.carchargeplanner.engine.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import nl.enadeag.carchargeplanner.engine.setting.ConfigSetting;
import nl.enadeag.carchargeplanner.engine.setting.InvalidSetting;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ChargingHoursTest {

    private static final ZoneId AMSTERDAM = ZoneId.of("Europe/Amsterdam");
    private static final LocalDate DAY = LocalDate.of(2026, 7, 3);
    private static final ChargingHours EVENING_TO_NIGHT = new ChargingHours(LocalTime.of(20, 0), LocalTime.of(2, 0));

    private static Instant at(LocalDate day, int hour, int minute) {
        return day.atTime(hour, minute).atZone(AMSTERDAM).toInstant();
    }

    @Test
    void theStandardHoursAreFromSevenToTen() {
        assertThat(ChargingHours.STANDARD).isEqualTo(new ChargingHours(LocalTime.of(7, 0), LocalTime.of(22, 0)));
    }

    @ParameterizedTest
    @CsvSource({"06:59, false", "07:00, true", "12:00, true", "22:00, true", "22:01, false", "00:00, false"})
    void aChargeMayStartFromTheStartToTheEndBothIncluded(LocalTime time, boolean allowed) {
        assertThat(ChargingHours.STANDARD.allowStart(time)).isEqualTo(allowed);
    }

    @ParameterizedTest
    @CsvSource({"19:59, false", "20:00, true", "23:30, true", "00:00, true", "02:00, true", "02:01, false",
        "12:00, false"})
    void hoursThatEndBeforeTheyStartSpanMidnight(LocalTime time, boolean allowed) {
        assertThat(EVENING_TO_NIGHT.allowStart(time)).isEqualTo(allowed);
    }

    @Test
    void aMomentIsReadInTheHomesTimeZone() {
        // 21:30 in Amsterdam is 19:30 UTC
        assertThat(ChargingHours.STANDARD.allowStart(Instant.parse("2026-07-03T19:30:00Z"), AMSTERDAM)).isTrue();
        assertThat(ChargingHours.STANDARD.allowStart(Instant.parse("2026-07-03T21:30:00Z"), AMSTERDAM)).isFalse();
    }

    @Test
    void theNextStartIsTheMomentItselfWithinTheHours() {
        assertThat(ChargingHours.STANDARD.nextStart(at(DAY, 22, 0), AMSTERDAM)).isEqualTo(at(DAY, 22, 0));
    }

    @Test
    void theNextStartBeforeTheHoursIsTheirStartThatDay() {
        assertThat(ChargingHours.STANDARD.nextStart(at(DAY, 5, 10), AMSTERDAM)).isEqualTo(at(DAY, 7, 0));
        assertThat(EVENING_TO_NIGHT.nextStart(at(DAY, 3, 0), AMSTERDAM)).isEqualTo(at(DAY, 20, 0));
    }

    @Test
    void theNextStartAfterTheHoursIsTheirStartTheNextDay() {
        assertThat(ChargingHours.STANDARD.nextStart(at(DAY, 22, 5), AMSTERDAM)).isEqualTo(at(DAY.plusDays(1), 7, 0));
    }

    @Test
    void aStartThatTheSpringForwardSwitchSkipsMovesOnByTheGap() {
        var hours = new ChargingHours(LocalTime.of(2, 30), LocalTime.of(6, 0));
        var sunday = LocalDate.of(2026, 3, 29);
        // 02:30 does not exist that night; 01:00 UTC is 03:00 summer time
        assertThat(hours.nextStart(at(sunday, 1, 0), AMSTERDAM)).isEqualTo(Instant.parse("2026-03-29T01:30:00Z"));
    }

    @Test
    void hoursThatEndAtTheirStartAreRefusedAtTheChargingHours() {
        assertThatThrownBy(() -> new ChargingHours(LocalTime.of(7, 0), LocalTime.of(7, 0)))
                .isInstanceOfSatisfying(InvalidSetting.class, problem -> {
                    assertThat(problem.setting()).isEqualTo(ConfigSetting.CHARGING_HOURS);
                    assertThat(problem.rule()).isEqualTo(InvalidSetting.Rule.SAME_AS_START);
                });
    }

    @Test
    void timesTheConfigFileCannotHoldAsWholeMinutesAreRefused() {
        assertThatThrownBy(() -> new ChargingHours(LocalTime.of(7, 0, 30), LocalTime.of(22, 0)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("start");
        assertThatThrownBy(() -> new ChargingHours(LocalTime.of(7, 0), LocalTime.of(22, 0).plusNanos(1)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("end");
    }
}
