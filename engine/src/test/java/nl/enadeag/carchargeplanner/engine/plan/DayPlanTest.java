package nl.enadeag.carchargeplanner.engine.plan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import nl.enadeag.carchargeplanner.engine.config.Percent;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class DayPlanTest {

    private static DayPlan withDrivingShort(double drivingShortPct) {
        return new DayPlan(LocalDate.of(2026, 7, 3), new Percent(40), List.of(), new Percent(40), new Percent(80),
                new Percent(0), 50, drivingShortPct, List.of());
    }

    @ParameterizedTest
    @ValueSource(doubles = {0, 10, 150})
    void theDrivingTheBatteryCannotHoldIsZeroOrMore(double drivingShortPct) {
        assertThat(withDrivingShort(drivingShortPct).drivingShortPct()).isEqualTo(drivingShortPct);
    }

    @ParameterizedTest
    @ValueSource(doubles = {-1e-9, -10, Double.NaN})
    void aNegativeOrUnknownDrivingTheBatteryCannotHoldIsRefused(double drivingShortPct) {
        assertThatThrownBy(() -> withDrivingShort(drivingShortPct)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(String.valueOf(drivingShortPct));
    }
}
