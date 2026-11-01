package nl.enadeag.carchargeplanner.android

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class SunnyDayPlanTest {

    private val zone = ZoneId.of("Europe/Amsterdam")

    /**
     * The plan the engine makes for a summer's day, as it made it when the skeleton was written: it shows that the
     * Android build plans as the engine does on the JVM, so a deliberate change to the planner updates it in the same
     * pull request. `OnTheDeviceTest` checks the same values on the device, so such a change updates both.
     */
    @Test
    fun aSummersDayHasTheWindowTheEngineGivesIt() {
        val windows = SunnyDayPlan.windows(LocalDate.parse("2026-06-15"), zone)

        assertThat(windows).hasSize(1)
        val window = windows[0]
        assertThat(window.start()).isEqualTo(Instant.parse("2026-06-15T07:00:00Z"))
        assertThat(window.end()).isEqualTo(Instant.parse("2026-06-15T15:00:00Z"))
        assertThat(window.amps()).isEqualTo(10)
        assertThat(window.powerKw()).isEqualTo(2.3, within(1e-9))
        assertThat(window.solarKwh()).isEqualTo(18.35936687607709, within(1e-9))
        assertThat(window.importKwh()).isEqualTo(0.040633123922911185, within(1e-9))
    }

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
