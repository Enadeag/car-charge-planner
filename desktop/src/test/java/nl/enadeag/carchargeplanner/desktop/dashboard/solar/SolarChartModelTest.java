package nl.enadeag.carchargeplanner.desktop.dashboard.solar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import nl.enadeag.carchargeplanner.desktop.dashboard.Models;
import nl.enadeag.carchargeplanner.engine.dashboard.DayChart;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import org.junit.jupiter.api.Test;

class SolarChartModelTest {

    private static DayChart withoutSteps() {
        return new DayChart(6, 20, 1, List.of(), 0, List.of(), Optional.empty(), List.of(), Optional.empty());
    }

    @Test
    void theStepAtAnHourIsTheOneThatHoldsIt() {
        var today = Models.curve(3.6, List.of(), OptionalDouble.empty());

        assertThat(today.stepAt(13.4).orElseThrow().startHour()).isEqualTo(13);
        assertThat(today.stepAt(14).orElseThrow().startHour()).as("the end of a step is the next one's").isEqualTo(14);
    }

    @Test
    void aStepOutsideTheStepsIsTheNearestOne() {
        var today = Models.curve(3.6, List.of(), OptionalDouble.empty());

        assertThat(today.stepAt(2).orElseThrow()).isEqualTo(today.chart().steps().getFirst());
        assertThat(today.stepAt(23).orElseThrow()).isEqualTo(today.chart().steps().getLast());
        assertThat(new SolarChartModel(withoutSteps(), "1 kW", List.of(), List.of(), "").stepAt(10)).isEmpty();
    }

    @Test
    void aStepReadsOutItsOwnWords() {
        var today = Models.curve(3.6, List.of(), OptionalDouble.empty());

        assertThat(today.readout(today.chart().steps().get(6))).isEqualTo(today.readouts().get(6));
        assertThat(today.readout(today.chart().steps().get(6))).startsWith("13:00");
    }

    @Test
    void theMiddleOfAStepIsHalfwayBetweenItsStartAndEnd() {
        assertThat(SolarChartModel.midHour(new DayChart.Step(13, 14.5, 2, Instant.EPOCH))).isEqualTo(13.75);
    }

    @Test
    void eachStepNeedsOneReadout() {
        var today = Models.curve(3.6, List.of(), OptionalDouble.empty());

        assertThatThrownBy(() -> new SolarChartModel(today.chart(), "4 kW", today.readouts().subList(1, 12),
                List.of(), "")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SolarChartModel(withoutSteps(), "1 kW", List.of("13:00"), List.of(), ""))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
