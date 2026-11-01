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

/** The smoke test on a device or emulator; the emulator job runs it on Android 8 (API 26), the app's minimum. */
@RunWith(AndroidJUnit4::class)
class OnTheDeviceTest {

    @Test
    fun theAppStartsAndResumes() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            assertEquals(Lifecycle.State.RESUMED, scenario.state)
        }
    }

    /**
     * The values of `SunnyDayPlanTest`, on purpose. `PlanningPreferences.RECOMMENDED` calls `Duration.toSecondsPart`,
     * which Android 8 lacks and desugaring adds.
     */
    @Test
    fun aSummersDayHasTheChargeWindowItHasOnTheJvm() {
        val windows = SunnyDayPlan.windows(LocalDate.parse("2026-06-15"), ZoneId.of("Europe/Amsterdam"))

        assertEquals(1, windows.size)
        val chargeWindow = windows[0]
        assertEquals(Instant.parse("2026-06-15T07:00:00Z"), chargeWindow.start())
        assertEquals(Instant.parse("2026-06-15T15:00:00Z"), chargeWindow.end())
        assertEquals(10, chargeWindow.amps())
        assertEquals(18.35936687607709, chargeWindow.solarKwh(), 1e-9)
    }
}
