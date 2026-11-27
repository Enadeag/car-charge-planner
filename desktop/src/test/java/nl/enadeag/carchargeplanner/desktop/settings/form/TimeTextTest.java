package nl.enadeag.carchargeplanner.desktop.settings.form;

import static org.assertj.core.api.Assertions.assertThat;

import nl.enadeag.carchargeplanner.desktop.system.ClockFormat;
import nl.enadeag.carchargeplanner.desktop.text.Language;
import nl.enadeag.carchargeplanner.desktop.text.Messages;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class TimeTextTest {

    @Test
    void aTimeIsWrittenAsHoursAndMinutesOnTheLanguagesOwn24HourClock() {
        var english = new Messages(Language.ENGLISH);

        assertThat(TimeText.write(LocalTime.of(7, 30), english)).isEqualTo("07:30");
        assertThat(TimeText.write(LocalTime.of(19, 5), english)).isEqualTo("19:05");
    }

    @Test
    void onA12HourClockATimeIsWrittenWithTheLanguagesMarker() {
        assertThat(TimeText.write(LocalTime.of(19, 5), new Messages(Language.ENGLISH, ClockFormat.TWELVE_HOUR)))
                .isEqualTo("7:05 pm");
        assertThat(TimeText.write(LocalTime.of(7, 5), new Messages(Language.DUTCH, ClockFormat.TWELVE_HOUR)))
                .isEqualTo("7:05 a.m.");
    }

    @ParameterizedTest
    @EnumSource(ClockFormat.class)
    void whatAFieldShowsIsReadBackAsTheSameTimeOnEveryClock(ClockFormat clock) {
        for (var language : Language.values()) {
            var messages = new Messages(language, clock);
            for (var time : new LocalTime[] {LocalTime.MIDNIGHT, LocalTime.of(0, 1), LocalTime.of(11, 59),
                    LocalTime.NOON, LocalTime.of(13, 0), LocalTime.of(23, 59)}) {
                assertThat(TimeText.read(TimeText.write(time, messages), messages)).contains(time);
            }
        }
    }

    @Test
    void aTypedTimeIsReadOnEitherClockWithSpacesAroundItIgnored() {
        var english = new Messages(Language.ENGLISH);

        assertThat(TimeText.read(" 23:59 ", english)).contains(LocalTime.of(23, 59));
        assertThat(TimeText.read("7:30 pm", english)).contains(LocalTime.of(19, 30));
    }

    @Test
    void aTextThatIsNotATimeIsNone() {
        var english = new Messages(Language.ENGLISH);

        assertThat(TimeText.read("", english)).isEmpty();
        assertThat(TimeText.read("24:00", english)).isEmpty();
        assertThat(TimeText.read("noon", english)).isEmpty();
    }
}
