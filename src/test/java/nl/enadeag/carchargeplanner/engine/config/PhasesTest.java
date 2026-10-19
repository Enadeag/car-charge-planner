package nl.enadeag.carchargeplanner.engine.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class PhasesTest {

    @ParameterizedTest
    @CsvSource({"1, ONE", "3, THREE"})
    void eachNumberOfPhasesHasItsPhases(int count, Phases phases) {
        assertThat(Phases.of(count)).isEqualTo(phases);
        assertThat(phases.count()).isEqualTo(count);
    }

    @ParameterizedTest
    @ValueSource(ints = {Integer.MIN_VALUE, -1, 0, 2, 4, Integer.MAX_VALUE})
    void anyOtherNumberIsRefused(int count) {
        assertThatThrownBy(() -> Phases.of(count))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("phases must be 1 or 3");
    }
}
