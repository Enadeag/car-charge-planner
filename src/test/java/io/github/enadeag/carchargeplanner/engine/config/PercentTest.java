package io.github.enadeag.carchargeplanner.engine.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PercentTest {

    @ParameterizedTest
    @ValueSource(doubles = {0, 0.5, 30, 99.99, 100})
    void fromZeroToAHundredIsAPercentage(double value) {
        assertThat(new Percent(value).value()).isEqualTo(value);
    }

    @ParameterizedTest
    @ValueSource(doubles = {-0.001, -1, 100.001, 101, Double.NaN, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY})
    void anythingElseIsRefused(double value) {
        assertThatThrownBy(() -> new Percent(value))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("a percentage must be from 0 to 100, not " + value);
    }

    @Test
    void percentagesWithTheSameValueAreEqual() {
        assertThat(new Percent(30)).isEqualTo(new Percent(30.0)).isNotEqualTo(new Percent(31));
    }
}
