package nl.enadeag.carchargeplanner.engine.forecast;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import nl.enadeag.carchargeplanner.engine.config.Coordinates;
import nl.enadeag.carchargeplanner.engine.config.PanelArray;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class StepsTest {

    private static final ZoneId AMSTERDAM = ZoneId.of("Europe/Amsterdam");

    private static Instant hour(int hour) {
        return hour(hour, 0);
    }

    private static Instant hour(int hour, int minute) {
        return LocalDate.of(2026, 7, 3).atTime(hour, minute).atZone(AMSTERDAM).toInstant();
    }

    private static ForecastStep step(int hour, double powerKw, int hours) {
        return new ForecastStep(hour(hour), hour(hour).plus(Duration.ofHours(hours)), powerKw);
    }

    private static ForecastStep step(int hour, double powerKw) {
        return step(hour, powerKw, 1);
    }

    @Test
    void goodStepsPass() {
        assertThat(Steps.problems(List.of(step(8, 1), step(9, 0), step(10, 2.5)))).isEmpty();
        assertThat(Steps.problems(List.of())).isEmpty();
    }

    @Test
    void aStepMustEndAfterItStarts() {
        assertThat(Steps.problems(List.of(new ForecastStep(hour(8), hour(8), 1)))).singleElement().asString().contains("end after");
        assertThat(Steps.problems(List.of(new ForecastStep(hour(9), hour(8), 1)))).singleElement().asString().contains("end after");
    }

    @Test
    void localStepsThroughTheRepeatedHourAreInOrder() {
        var start = LocalDate.of(2026, 10, 25).atStartOfDay(AMSTERDAM).toInstant();
        var steps = new ArrayList<ForecastStep>();
        for (int i = 0; i < 25; i++) {
            steps.add(new ForecastStep(start.plus(Duration.ofHours(i)), start.plus(Duration.ofHours(i + 1)), 0));
        }
        assertThat(Steps.problems(steps)).isEmpty();
    }

    @Test
    void stepsMustBeInOrderWithoutOverlap() {
        assertThat(Steps.problems(List.of(step(8, 1), step(9, 1)))).isEmpty();
        assertThat(Steps.problems(List.of(step(8, 1, 2), step(9, 1)))).singleElement().asString().contains("overlaps");
        assertThat(Steps.problems(List.of(step(9, 1), step(8, 1)))).singleElement().asString().contains("overlaps");
    }

    @ParameterizedTest
    @ValueSource(doubles = {-0.1, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
    void powerMustBeFiniteAndNotNegative(double powerKw) {
        assertThat(Steps.problems(List.of(step(8, powerKw)))).hasSize(1);
    }

    @Test
    void fifteenThirtyAndSixtyMinuteStepsAreAllFine() {
        var steps = List.of(new ForecastStep(hour(8), hour(8, 15), 1), new ForecastStep(hour(8, 15), hour(8, 45), 1),
                new ForecastStep(hour(8, 45), hour(9, 45), 1));
        assertThat(Steps.problems(steps)).isEmpty();
    }

    @Test
    void requireValidGivesTheStepsOrABadResponseWithTheReason() throws ProviderError {
        assertThat(Steps.requireValid(List.of(step(8, 1)))).containsExactly(step(8, 1));
        assertThatThrownBy(() -> Steps.requireValid(List.of(step(8, -1))))
                .isInstanceOf(ProviderError.BadResponse.class).hasMessageContaining("negative");
    }

    @Test
    void stepsThatLineUpAreAddedStepByStep() {
        var a = List.of(step(8, 1.0), step(9, 2.0));
        var b = List.of(step(8, 0.5), step(9, 0.25));
        assertThat(Steps.addUp(List.of(a, b))).containsExactly(step(8, 1.5), step(9, 2.25));
    }

    @Test
    void stepsThatDoNotLineUpAreCutAndNoEnergyIsLost() {
        var a = List.of(step(8, 1.0, 2));
        var b = List.of(new ForecastStep(hour(8), hour(8, 30), 2.0), new ForecastStep(hour(8, 30), hour(10), 0.5));
        var total = Steps.addUp(List.of(a, b));

        assertThat(total).containsExactly(new ForecastStep(hour(8), hour(8, 30), 3.0), new ForecastStep(hour(8, 30), hour(10), 1.5));
        assertThat(total.stream().mapToDouble(s -> s.powerKw() * s.hours()).sum()).isCloseTo(2.0 + 1.0 + 0.75, within(1e-9));
    }

    @Test
    void aGapInOneArrayAddsNothingAndAGapInAllStaysAGap() {
        var a = List.of(step(8, 1.0), step(9, 1.0), step(12, 1.0));
        var b = List.of(step(8, 2.0), step(12, 2.0));
        assertThat(Steps.addUp(List.of(a, b))).containsExactly(step(8, 3.0), step(9, 1.0), step(12, 3.0));
    }

    @Test
    void theTotalCoversOnlyTheTimeEveryArrayCovers() {
        var a = List.of(step(8, 1.0), step(9, 1.0), step(10, 1.0));
        var b = List.of(step(9, 2.0));
        assertThat(Steps.addUp(List.of(a, b))).containsExactly(step(9, 3.0));
        assertThat(Steps.addUp(List.of(a, List.of()))).isEmpty();
        assertThat(Steps.addUp(List.of())).isEmpty();
    }

    @Test
    void oneForecastIsReturnedAsItIs() {
        var a = List.of(step(8, 1.0));
        assertThat(Steps.addUp(List.of(a))).isSameAs(a);
    }

    @Test
    void eachArrayIsAskedOnItsOwnAndInOrder() throws ProviderError {
        var east = new PanelArray("East", 3.0, 30, -90, 0.14);
        var west = new PanelArray("West", 2.5, 30, 90, 0.1);
        var system = new SystemDescription(new Coordinates(52.37, 4.9), AMSTERDAM, List.of(east, west));
        var asked = new ArrayList<PanelArray>();

        var total = Steps.eachArray(system, array -> {
            asked.add(array);
            return List.of(step(8, array.kwp()));
        });

        assertThat(total).containsExactly(step(8, 5.5));
        assertThat(asked).containsExactly(east, west);
    }
}
