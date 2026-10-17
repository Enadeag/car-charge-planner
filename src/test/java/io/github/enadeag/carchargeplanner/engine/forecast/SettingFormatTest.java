package io.github.enadeag.carchargeplanner.engine.forecast;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class SettingFormatTest {

    @ParameterizedTest
    @CsvSource({
        "abcDEF0123456789, true",
        "'  abc123  ', true",
        "'', true",
        "abc-123, false",
        "abc/123, false",
        "abc?x, false",
        "abc 123, false",
        "ключ, false",
        "é1, false",
        "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa, true",
        "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa, false",
    })
    void lettersAndDigitsAreOnlyTheseAtMostSixtyFour(String value, boolean accepted) {
        assertThat(SettingFormat.LETTERS_AND_DIGITS.accepts(value)).isEqualTo(accepted);
    }
}
