package nl.enadeag.carchargeplanner.desktop.text;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class MessagesTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "ENGLISH | 1   | A trip can last at most 1 day.",
        "ENGLISH | 366 | A trip can last at most 366 days.",
        "DUTCH   | 1   | Een rit kan hoogstens 1 dag duren.",
        "DUTCH   | 366 | Een rit kan hoogstens 366 dagen duren.",
    })
    void aTripsLongestLengthTakesTheFormOfItsNumber(Language language, long days, String expected) {
        assertThat(new Messages(language).text(TextKey.TRIP_TOO_LONG, days)).isEqualTo(expected);
    }
}
