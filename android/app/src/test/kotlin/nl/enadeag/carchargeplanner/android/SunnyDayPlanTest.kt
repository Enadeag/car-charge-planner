package nl.enadeag.carchargeplanner.android

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class SunnyDayPlanTest {

    private val zone = ZoneId.of("Europe/Amsterdam")

    /** A summer's day, and the days the clocks go forward and back. */
    @ParameterizedTest
    @ValueSource(strings = ["2026-06-15", "2026-03-29", "2026-10-25"])
    fun theDayHasChargeWindowsAndEachLiesInItsSunnyHours(date: String) {
        val day = LocalDate.parse(date)

        val windows = SunnyDayPlan.windows(day, zone)

        assertThat(windows).isNotEmpty()
        windows.forEach { window ->
            val start = window.start().atZone(zone)
            val end = window.end().atZone(zone)
            assertThat(start.toLocalDate()).isEqualTo(day)
            assertThat(end.toLocalDate()).isEqualTo(day)
            assertThat(start.toLocalTime()).isAfterOrEqualTo(LocalTime.of(6, 0))
            assertThat(end.toLocalTime()).isBeforeOrEqualTo(LocalTime.of(20, 0))
            assertThat(window.solarShare()).isPositive()
        }
    }
}
