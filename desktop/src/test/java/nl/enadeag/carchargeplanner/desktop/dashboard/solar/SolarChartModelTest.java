package nl.enadeag.carchargeplanner.desktop.dashboard.solar;

import static org.assertj.core.api.Assertions.assertThat;

import nl.enadeag.carchargeplanner.desktop.dashboard.Models;
import java.util.List;
import java.util.OptionalDouble;
import org.junit.jupiter.api.Test;

class SolarChartModelTest {

    @Test
    void theStepAtAnHourIsTheOneThatHoldsIt() {
        var today = Models.curve(3.6, List.of(), OptionalDouble.empty());

        assertThat(today.stepAt(13.4).orElseThrow().startHour()).isEqualTo(13);
    }

    @Test
    void aStepOutsideTheStepsIsTheNearestOne() {
        var today = Models.curve(3.6, List.of(), OptionalDouble.empty());

        assertThat(today.stepAt(2).orElseThrow()).isEqualTo(today.steps().getFirst());
        assertThat(today.stepAt(23).orElseThrow()).isEqualTo(today.steps().getLast());
        assertThat(new SolarChartModel(6, 20, 1, "1 kW", List.of(), 0, List.of(), OptionalDouble.empty(), List.of(), "")
                .stepAt(10)).isEmpty();
    }
}
