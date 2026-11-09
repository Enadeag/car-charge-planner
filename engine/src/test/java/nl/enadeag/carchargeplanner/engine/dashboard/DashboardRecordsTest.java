package nl.enadeag.carchargeplanner.engine.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class DashboardRecordsTest {

    private static final Instant NOON = Instant.parse("2026-06-01T10:00:00Z");
    private static final LocalDate DAY = LocalDate.of(2026, 6, 1);

    private static DayChart chart(double from, double to, double scale, double baseload) {
        return new DayChart(from, to, scale, List.of(), baseload, List.of(), Optional.empty(), List.of(),
                Optional.empty());
    }

    @Test
    void aChartWithinItsRangesIsMade() {
        var chart = chart(0, 0.5, 0.1, 0);

        assertThat(chart.fromHour()).isEqualTo(0);
        assertThat(chart.baseloadKw()).isEqualTo(0);
    }

    @ParameterizedTest
    @CsvSource({"-1, 20, 1, 0.3", "6, 6, 1, 0.3", "NaN, 20, 1, 0.3", "6, NaN, 1, 0.3", "6, 20, 0, 0.3",
        "6, 20, NaN, 0.3", "6, 20, Infinity, 0.3", "6, 20, 1, -0.1", "6, 20, 1, NaN", "6, 20, 1, Infinity"})
    void aChartRefusesHoursThatDoNotRunForwardsFromZeroAndAScaleOrBaseloadOutOfRange(double from, double to,
            double scale, double baseload) {
        assertThatThrownBy(() -> chart(from, to, scale, baseload)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aChartKeepsItsOwnLists() {
        var steps = new ArrayList<>(List.of(new DayChart.Step(6, 7, 1, NOON)));
        var chart = new DayChart(6, 20, 1, steps, 0.3, List.of(), Optional.empty(), List.of(), Optional.empty());

        steps.clear();

        assertThat(chart.steps()).hasSize(1);
    }

    @ParameterizedTest
    @CsvSource({"2, 2, 1", "3, 2, 1", "2, 3, -0.1", "2, 3, NaN"})
    void aStepRunsForwardsAndItsPowerIsNotNegative(double start, double end, double kw) {
        assertThatThrownBy(() -> new DayChart.Step(start, end, kw, NOON)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aStepWithoutPowerIsMade() {
        assertThat(new DayChart.Step(2, 2.25, 0, NOON).kw()).isEqualTo(0);
    }

    @ParameterizedTest
    @CsvSource({"3, 3", "4, 3", "NaN, 3"})
    void aWindowRunsForwards(double start, double end) {
        assertThatThrownBy(() -> new DayChart.WindowBand(start, end, NOON, NOON))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @CsvSource({"0", "-1", "NaN"})
    void aPeakHasPower(double kw) {
        assertThatThrownBy(() -> new DayChart.Peak(kw, NOON)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aDaysNameIsOfThatDay() {
        var chart = chart(6, 20, 1, 0.3);

        assertThat(new DashboardDay(DAY, new DayName.Other(DAY), chart).name()).isEqualTo(new DayName.Other(DAY));
        assertThatThrownBy(() -> new DashboardDay(DAY, new DayName.Today(DAY.plusDays(1)), chart))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aDashboardKeepsItsOwnDays() {
        var days = new ArrayList<>(List.of(new DashboardDay(DAY, new DayName.Today(DAY), chart(6, 20, 1, 0.3))));
        var dashboard = new DashboardView(days);

        days.clear();

        assertThat(dashboard.days()).hasSize(1);
    }
}
