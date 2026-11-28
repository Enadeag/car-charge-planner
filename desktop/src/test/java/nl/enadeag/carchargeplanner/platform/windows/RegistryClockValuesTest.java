package nl.enadeag.carchargeplanner.platform.windows;

import static org.assertj.core.api.Assertions.assertThat;

import nl.enadeag.carchargeplanner.desktop.system.ClockFormat;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** What Windows' values of the time format stand for; reading them from the registry is in RegistryClockTest. */
class RegistryClockValuesTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', quoteCharacter = '"', value = {
        "HH:mm | 0 | TWENTY_FOUR_HOUR",
        "H:mm | \"\" | TWENTY_FOUR_HOUR",
        "h:mm tt | 1 | TWELVE_HOUR",
        "hh:mm tt | \"\" | TWELVE_HOUR",
        "tt h:mm | \"\" | TWELVE_HOUR",
        "'h' HH:mm | \"\" | TWENTY_FOUR_HOUR",
        "HH'h'mm | \"\" | TWENTY_FOUR_HOUR",
        "'Hour' h:mm | \"\" | TWELVE_HOUR",
        "\"\" | 1 | TWENTY_FOUR_HOUR",
        "\"\" | 0 | TWELVE_HOUR",
        "mm | 1 | TWENTY_FOUR_HOUR",
        "\"\" | \"\" | LANGUAGE",
        "\"\" | 2 | LANGUAGE"})
    void theShortTimeFormatSaysTheClockAndITimeWhenItSaysNothing(String shortTime, String time, ClockFormat clock) {
        assertThat(RegistryClock.of(shortTime, time)).isEqualTo(clock);
    }
}
