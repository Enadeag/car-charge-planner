package nl.enadeag.carchargeplanner.engine.forecast;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class StepRulesTest {

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
        assertThat(StepRules.problems(List.of(step(8, 1), step(9, 0), step(10, 2.5)))).isEmpty();
        assertThat(StepRules.problems(List.of())).isEmpty();
    }

    @Test
    void aStepMustEndAfterItStarts() {
        assertThat(StepRules.problems(List.of(new ForecastStep(hour(8), hour(8), 1)))).singleElement().asString().contains("end after");
        assertThat(StepRules.problems(List.of(new ForecastStep(hour(9), hour(8), 1)))).singleElement().asString().contains("end after");
    }

    @Test
    void localStepsThroughTheRepeatedHourAreInOrder() {
        var start = LocalDate.of(2026, 10, 25).atStartOfDay(AMSTERDAM).toInstant();
        var steps = new ArrayList<ForecastStep>();
        for (int i = 0; i < 25; i++) {
            steps.add(new ForecastStep(start.plus(Duration.ofHours(i)), start.plus(Duration.ofHours(i + 1)), 0));
        }
        assertThat(StepRules.problems(steps)).isEmpty();
    }

    @Test
    void stepsMustBeInOrderWithoutOverlap() {
        assertThat(StepRules.problems(List.of(step(8, 1), step(9, 1)))).isEmpty();
        assertThat(StepRules.problems(List.of(step(8, 1, 2), step(9, 1)))).singleElement().asString().contains("overlaps");
        assertThat(StepRules.problems(List.of(step(9, 1), step(8, 1)))).singleElement().asString().contains("overlaps");
    }

    @ParameterizedTest
    @ValueSource(doubles = {-0.1, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
    void powerMustBeFiniteAndNotNegative(double powerKw) {
        assertThat(StepRules.problems(List.of(step(8, powerKw)))).hasSize(1);
    }

    @Test
    void fifteenThirtyAndSixtyMinuteStepsAreAllFine() {
        var steps = List.of(new ForecastStep(hour(8), hour(8, 15), 1), new ForecastStep(hour(8, 15), hour(8, 45), 1),
                new ForecastStep(hour(8, 45), hour(9, 45), 1));
        assertThat(StepRules.problems(steps)).isEmpty();
    }

    @Test
    void requireValidGivesTheStepsOrABadResponseWithTheReason() throws ProviderError {
        assertThat(StepRules.requireValid(List.of(step(8, 1)))).containsExactly(step(8, 1));
        assertThatThrownBy(() -> StepRules.requireValid(List.of(step(8, -1))))
                .isInstanceOf(ProviderError.BadResponse.class).hasMessageContaining("negative");
    }
}
