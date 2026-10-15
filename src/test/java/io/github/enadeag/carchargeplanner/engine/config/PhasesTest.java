package io.github.enadeag.carchargeplanner.engine.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.exc.ValueInstantiationException;
import tools.jackson.databind.json.JsonMapper;

class PhasesTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

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

    @ParameterizedTest
    @CsvSource({"1, ONE", "3, THREE"})
    void inJsonThePhasesAreTheirNumber(String json, Phases phases) {
        assertThat(JSON.writeValueAsString(phases)).isEqualTo(json);
        assertThat(JSON.readValue(json, Phases.class)).isEqualTo(phases);
    }

    @ParameterizedTest
    @ValueSource(strings = {"2", "0"})
    void inJsonAnyOtherNumberIsRefused(String json) {
        assertThatThrownBy(() -> JSON.readValue(json, Phases.class))
                .isInstanceOf(ValueInstantiationException.class)
                .hasRootCauseMessage("phases must be 1 or 3");
    }

    @ParameterizedTest
    @ValueSource(strings = {"\"ONE\"", "\"one\""})
    void inJsonTheConstantsNameIsNotThePhases(String json) {
        assertThatThrownBy(() -> JSON.readValue(json, Phases.class)).isInstanceOf(RuntimeException.class);
    }
}
