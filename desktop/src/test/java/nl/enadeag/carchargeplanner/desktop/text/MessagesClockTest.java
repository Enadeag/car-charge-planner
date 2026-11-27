package nl.enadeag.carchargeplanner.desktop.text;

import static org.assertj.core.api.Assertions.assertThat;

import nl.enadeag.carchargeplanner.desktop.system.ClockFormat;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

/** The times on the system's 12- or 24-hour clock, as the app writes them and as the owner may type them. */
class MessagesClockTest {

    private static final ZoneId BRUSSELS = ZoneId.of("Europe/Brussels");

    /** The narrow no-break space CLDR puts between an English time and its marker. */
    private static final String NNBSP = " ";

    static Stream<Arguments> written() {
        return Stream.of(
                Arguments.of(Language.ENGLISH, ClockFormat.LANGUAGE, "07:05", "19:30", "00:00", "12:00"),
                Arguments.of(Language.ENGLISH, ClockFormat.TWENTY_FOUR_HOUR, "07:05", "19:30", "00:00", "12:00"),
                Arguments.of(Language.ENGLISH, ClockFormat.TWELVE_HOUR, "7:05" + NNBSP + "am", "7:30" + NNBSP + "pm",
                        "12:00" + NNBSP + "am", "12:00" + NNBSP + "pm"),
                Arguments.of(Language.DUTCH, ClockFormat.LANGUAGE, "07:05", "19:30", "00:00", "12:00"),
                Arguments.of(Language.DUTCH, ClockFormat.TWENTY_FOUR_HOUR, "07:05", "19:30", "00:00", "12:00"),
                Arguments.of(Language.DUTCH, ClockFormat.TWELVE_HOUR, "7:05 a.m.", "7:30 p.m.", "12:00 a.m.",
                        "12:00 p.m."));
    }

    @ParameterizedTest
    @MethodSource("written")
    void aTimeIsWrittenOnTheClockAskedForWithTheLanguagesMarker(Language language, ClockFormat clock, String morning,
            String evening, String midnight, String noon) {
        var messages = new Messages(language, clock);

        assertThat(messages.time(LocalTime.of(7, 5))).isEqualTo(morning);
        assertThat(messages.time(LocalTime.of(19, 30))).isEqualTo(evening);
        assertThat(messages.time(LocalTime.MIDNIGHT)).isEqualTo(midnight);
        assertThat(messages.time(LocalTime.NOON)).isEqualTo(noon);
    }

    @ParameterizedTest
    @MethodSource("written")
    void aMomentIsWrittenAsItsTimeInTheHomesZone(Language language, ClockFormat clock, String morning) {
        var moment = LocalTime.of(7, 5).atDate(java.time.LocalDate.of(2026, 7, 1)).atZone(BRUSSELS).toInstant();

        assertThat(new Messages(language, clock).time(moment, BRUSSELS)).isEqualTo(morning);
    }

    @ParameterizedTest
    @CsvSource({"LANGUAGE, false", "TWENTY_FOUR_HOUR, false", "TWELVE_HOUR, true"})
    void onlyA12HourClockMakesTheTimeFieldsWider(ClockFormat clock, boolean twelveHour) {
        for (var language : Language.values()) {
            assertThat(new Messages(language, clock).twelveHour()).isEqualTo(twelveHour);
        }
    }

    static Stream<Arguments> accepted() {
        return Stream.of(
                Arguments.of("19:30", "19:30"), Arguments.of("07:30", "07:30"), Arguments.of("7:30", "07:30"),
                Arguments.of("0:00", "00:00"), Arguments.of("23:59", "23:59"), Arguments.of(" 19:30 ", "19:30"),
                Arguments.of("7:30 PM", "19:30"), Arguments.of("7:30 pm", "19:30"), Arguments.of("7:30pm", "19:30"),
                Arguments.of("7:30 p.m.", "19:30"), Arguments.of("7:30 P.M.", "19:30"), Arguments.of("7:30p.m.", "19:30"),
                Arguments.of("7:30 P. M.", "19:30"), Arguments.of("7:30 Pm", "19:30"), Arguments.of("07:30 pm", "19:30"),
                Arguments.of("7:30" + NNBSP + "pm", "19:30"), Arguments.of("7:30 pm", "19:30"),
                Arguments.of("7:30 am", "07:30"), Arguments.of("7:30 a.m.", "07:30"), Arguments.of("7:30 AM", "07:30"),
                Arguments.of("7 pm", "19:00"), Arguments.of("7pm", "19:00"), Arguments.of("12 AM", "00:00"),
                Arguments.of("12 PM", "12:00"), Arguments.of("12:15 am", "00:15"), Arguments.of("12:15 pm", "12:15"),
                Arguments.of("11:59 pm", "23:59"), Arguments.of("1 am", "01:00"), Arguments.of("pm 7:30", "19:30"));
    }

    static Stream<Arguments> acceptedInEveryLanguageAndClock() {
        return accepted().flatMap(typed -> Stream.of(Language.values()).flatMap(language -> Stream.of(
                ClockFormat.values()).map(clock -> Arguments.of(language, clock, typed.get()[0], typed.get()[1]))));
    }

    @ParameterizedTest
    @MethodSource("acceptedInEveryLanguageAndClock")
    void eitherClockIsReadWhateverTheSystemsClock(Language language, ClockFormat clock, String typed, String time) {
        assertThat(new Messages(language, clock).readTime(typed)).contains(LocalTime.parse(time));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "seven", "noon", "pm", "7", "19", "07", "24:00", "25:00", "7:60", "7:5", "7:300",
        "7.30", "7:30:00", "1 0:00", "7 : 30", "-1:00", "13:00 pm", "13 pm", "0:30 am", "00 am", "0 pm",
        "7:30 xm", "7:30 pm pm", "am 7:30 pm", "7:30 p", "7:30 m", "7:30 a.m.m", "7:30 o'clock", "७:३०"})
    void aTextThatIsNotATimeOnEitherClockIsNone(String typed) {
        for (var language : Language.values()) {
            for (var clock : ClockFormat.values()) {
                assertThat(new Messages(language, clock).readTime(typed)).as("%s in %s, %s", typed, language, clock)
                        .isEmpty();
            }
        }
    }

    @ParameterizedTest
    @EnumSource(Language.class)
    void theLanguagesOwnMarkerIsReadInItsOwnCase(Language language) {
        var messages = new Messages(language, ClockFormat.TWELVE_HOUR);
        var written = messages.time(LocalTime.of(21, 45));

        assertThat(messages.readTime(written.toUpperCase(messages.locale()))).contains(LocalTime.of(21, 45));
    }
}
