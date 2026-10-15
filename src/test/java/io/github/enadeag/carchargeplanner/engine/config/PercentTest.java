package io.github.enadeag.carchargeplanner.engine.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.exc.MismatchedInputException;
import tools.jackson.databind.exc.ValueInstantiationException;
import tools.jackson.databind.json.JsonMapper;

class PercentTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

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

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {"30 | 30.0", "30.0 | 30.0", "0 | 0.0", "100 | 100.0", "12.5 | 12.5"})
    void inJsonAPercentageIsItsNumber(String json, double value) {
        assertThat(JSON.readValue(json, Percent.class)).isEqualTo(new Percent(value));
        assertThat(JSON.writeValueAsString(new Percent(value))).isEqualTo(Double.toString(value));
    }

    /** As Jackson reads any other number of the config file. */
    @Test
    void inJsonANumberInQuotesIsReadToo() {
        assertThat(JSON.readValue("\"30\"", Percent.class)).isEqualTo(new Percent(30));
    }

    @ParameterizedTest
    @ValueSource(strings = {"-1", "100.5", "\"101\"", "\"NaN\""})
    void inJsonANumberOutOfRangeIsRefused(String json) {
        assertThatThrownBy(() -> JSON.readValue(json, Percent.class))
                .isInstanceOf(ValueInstantiationException.class)
                .hasRootCauseMessage("a percentage must be from 0 to 100, not " + Double.parseDouble(json.replace("\"", "")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"\"thirty\"", "true", "[30]", "{\"value\": 30}"})
    void inJsonSomethingElseThanANumberIsRefused(String json) {
        assertThatThrownBy(() -> JSON.readValue(json, Percent.class)).isInstanceOf(MismatchedInputException.class);
    }

    @Test
    void inJsonAnEmptyTextIsRefused() {
        assertThatThrownBy(() -> JSON.readValue("\"\"", Percent.class))
                .isInstanceOf(ValueInstantiationException.class)
                .hasRootCauseMessage("a percentage must be a number");
    }
}
