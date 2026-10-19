package nl.enadeag.carchargeplanner.desktop.settings.form;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalTime;
import org.junit.jupiter.api.Test;

class TimeTextTest {

    @Test
    void aTimeIsWrittenAsHoursAndMinutesOnA24HourClock() {
        assertThat(TimeText.write(LocalTime.of(7, 30))).isEqualTo("07:30");
        assertThat(TimeText.write(LocalTime.of(19, 5))).isEqualTo("19:05");
    }

    @Test
    void aTypedTimeIsReadWithSpacesAroundItIgnored() {
        assertThat(TimeText.read("07:30")).contains(LocalTime.of(7, 30));
        assertThat(TimeText.read(" 23:59 ")).contains(LocalTime.of(23, 59));
    }

    @Test
    void aTextThatIsNotATimeIsNone() {
        assertThat(TimeText.read("")).isEmpty();
        assertThat(TimeText.read("7:30")).isEmpty();
        assertThat(TimeText.read("24:00")).isEmpty();
        assertThat(TimeText.read("12:60")).isEmpty();
        assertThat(TimeText.read("noon")).isEmpty();
    }
}
