package nl.enadeag.carchargeplanner.android

import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The smoke test on a device or emulator, which the emulator job runs on Android 8 (API 26), the app's minimum: the
 * app starts, and the engine plans there with the phone's `java.time` and core library desugaring as it does on the
 * JVM. The instrumented tests of the app's own parts come with those parts.
 */
@RunWith(AndroidJUnit4::class)
class OnTheDeviceTest {

    /** The screen opens: [MainActivity] plans the day and writes the car to JSON and back before it shows them. */
    @Test
    fun theAppStartsAndShowsItsScreen() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            assertEquals(Lifecycle.State.RESUMED, scenario.state)
        }
    }

    /**
     * The summer's day of `SunnyDayPlanTest`, planned on the device in the time zone of Amsterdam: the same window as
     * on the JVM, so the engine's time zone rules and arithmetic behave alike on the phone. The plan uses
     * `PlanningPreferences.RECOMMENDED`, which checks its durations with `Duration.toSecondsPart`: Android 8 lacks
     * that method, and desugaring adds it.
     */
    @Test
    fun aSummersDayHasTheWindowItHasOnTheJvm() {
        val windows = SunnyDayPlan.windows(LocalDate.parse("2026-06-15"), ZoneId.of("Europe/Amsterdam"))

        assertEquals(1, windows.size)
        val window = windows[0]
        assertEquals(Instant.parse("2026-06-15T07:00:00Z"), window.start())
        assertEquals(Instant.parse("2026-06-15T15:00:00Z"), window.end())
        assertEquals(10, window.amps())
        assertEquals(18.35936687607709, window.solarKwh(), 1e-9)
    }
}
