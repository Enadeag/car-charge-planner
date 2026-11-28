package nl.enadeag.carchargeplanner.engine.plan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import nl.enadeag.carchargeplanner.engine.config.Percent;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class DayPlanTest {

    private static DayPlan withDriving(double drivingPct, double drivingShortPct) {
        return new DayPlan(LocalDate.of(2026, 7, 3), new Percent(40), List.of(), List.of(), new Percent(40),
                new Percent(80), new Percent(0), drivingPct, drivingShortPct, List.of());
    }

    private static DayPlan withDepartures(double highestPct, double... departurePcts) {
        var departures = Arrays.stream(departurePcts).mapToObj(Percent::new).toList();
        return new DayPlan(LocalDate.of(2026, 7, 3), new Percent(40), departures, List.of(), new Percent(highestPct),
                new Percent(80), new Percent(0), 50, 0, List.of());
    }

    @ParameterizedTest
    @CsvSource({"50, 0", "50, 10", "50, 50", "160, 120"})
    void theDrivingTheBatteryCannotHoldIsFromZeroToTheDaysDriving(double drivingPct, double drivingShortPct) {
        assertThat(withDriving(drivingPct, drivingShortPct).drivingShortPct()).isEqualTo(drivingShortPct);
    }

    @ParameterizedTest
    @CsvSource({"50, -1e-9", "50, -10", "50, NaN", "50, 50.000001", "0, 1e-9", "50, 150"})
    void aDrivingTheBatteryCannotHoldOutsideThatIsRefused(double drivingPct, double drivingShortPct) {
        assertThatThrownBy(() -> withDriving(drivingPct, drivingShortPct))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(String.valueOf(drivingShortPct));
    }

    @Test
    void aDepartureAtTheHighestLevelIsKept() {
        assertThat(withDepartures(80, 50, 80).departureLevelPcts()).containsExactly(new Percent(50), new Percent(80));
    }

    @Test
    void aDepartureLevelAboveTheHighestLevelIsRefused() {
        assertThatThrownBy(() -> withDepartures(80, 50, 80.000001))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("80.000001");
    }
}
