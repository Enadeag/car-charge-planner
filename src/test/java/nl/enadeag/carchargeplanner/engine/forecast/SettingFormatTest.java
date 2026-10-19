package nl.enadeag.carchargeplanner.engine.forecast;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

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

    @ParameterizedTest
    @ValueSource(strings = {"key\r\nX-Injected: 1", "a\nb", "tab\there", "bell\u0007", "nul\u0000", "del\u007F",
        "next line\u0085"})
    void anyTextIsOneLineWithoutControlCharacters(String value) {
        assertThat(SettingFormat.TEXT.accepts(value)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "  spaces around  \n", "Ünïcode key/with=signs?", "😀"})
    void anyTextTakesEverythingElse(String value) {
        assertThat(SettingFormat.TEXT.accepts(value)).isTrue();
    }
}
