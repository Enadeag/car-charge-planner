package nl.enadeag.carchargeplanner.android.form

import java.time.LocalTime
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class TimeTextTest {

    @Test
    fun aTimeIsWrittenOnA24HourClockAndReadBack() {
        assertThat(TimeText.write(LocalTime.of(7, 5))).isEqualTo("07:05")
        assertThat(TimeText.read(" 22:30 ")).isEqualTo(LocalTime.of(22, 30))
    }

    @ParameterizedTest
    @ValueSource(strings = ["", "7:30", "24:00", "12:60", "7.30", "noon", "07:30:00"])
    fun aTextThatIsNoTimeSuchAs0730IsNone(text: String) {
        assertThat(TimeText.read(text)).isNull()
    }
}
