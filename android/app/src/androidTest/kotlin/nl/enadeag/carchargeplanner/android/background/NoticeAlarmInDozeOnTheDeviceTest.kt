package nl.enadeag.carchargeplanner.android.background

import android.app.NotificationManager
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.util.Optional
import nl.enadeag.carchargeplanner.android.CarChargePlannerApp
import nl.enadeag.carchargeplanner.android.store.EngineAccess
import nl.enadeag.carchargeplanner.android.ui.notify.Notifications
import nl.enadeag.carchargeplanner.engine.ForecastUse
import nl.enadeag.carchargeplanner.engine.Outlook
import nl.enadeag.carchargeplanner.engine.config.Percent
import nl.enadeag.carchargeplanner.engine.config.Phases
import nl.enadeag.carchargeplanner.engine.notify.Announcement
import nl.enadeag.carchargeplanner.engine.notify.AnnouncementKind
import nl.enadeag.carchargeplanner.engine.plan.Window
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The app's real alarm, set about ten seconds ahead while the device is forced into Doze and the app into the rare
 * standby bucket: it must still go off, and its round must post the notice. Only this test sees Doze and the standby
 * buckets; the Robolectric tests check the alarm's kind and time. The emulator job runs it on Android 10 (API 29),
 * which does not have the limits of Android 12 and later; the check on the maintainer's phone covers those.
 */
@RunWith(AndroidJUnit4::class)
class NoticeAlarmInDozeOnTheDeviceTest {

    private val app = ApplicationProvider.getApplicationContext<CarChargePlannerApp>()
    private val manager = app.getSystemService(NotificationManager::class.java)

    /** Runs a shell command as the test's instrumentation, which may change the device's power state. */
    private fun shell(command: String): String {
        val output = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
        return java.io.FileInputStream(output.fileDescriptor).bufferedReader().use { it.readText() }
            .also { output.close() }
    }

    /** The engine's access, whose round tells the notice and plans nothing more, as the seam of this test. */
    private class RoundThatTells(own: EngineAccess, private val notifications: Notifications,
        private val notice: Announcement) : EngineAccess by own {

        override suspend fun round(use: ForecastUse): Outlook {
            notifications.announce(notice)
            return Outlook.NoForecast("the test's round plans nothing")
        }

        override suspend fun nextNoticeDue(outlook: Outlook): Instant? = null
    }

    @Test
    fun theAlarmGoesOffAndTheNoticeShowsWhileTheDeviceDozesAndTheAppIsRare() {
        val pkg = app.packageName
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            shell("pm grant $pkg android.permission.POST_NOTIFICATIONS")
        }
        val start = Instant.now().plus(Duration.ofMinutes(10))
        val window = Window(start, start.plus(Duration.ofHours(2)), 10, 2.3, 7.3, 0.6)
        val notice = Announcement(AnnouncementKind.SOLAR_WINDOW, window, ZoneId.systemDefault(), Phases.ONE,
            Percent(30.0), Optional.empty())
        val tag = "solar_window:${start.epochSecond}"
        val own = app.engine
        app.engine = RoundThatTells(own, Notifications(app), notice)
        try {
            shell("dumpsys battery unplug")
            shell("am set-standby-bucket $pkg rare")
            shell("dumpsys deviceidle enable deep")
            val idle = shell("dumpsys deviceidle force-idle deep")
            assertTrue("the device did not go into Doze: $idle", idle.contains("forced", ignoreCase = true))

            app.alarm.set(Instant.now().plusSeconds(10))

            val end = System.nanoTime() + Duration.ofSeconds(90).toNanos()
            while (manager.activeNotifications.none { it.tag == tag } && System.nanoTime() < end) {
                Thread.sleep(500)
            }
            assertTrue("no notice within 90 seconds", manager.activeNotifications.any { it.tag == tag })
        } finally {
            shell("dumpsys deviceidle unforce")
            shell("dumpsys battery reset")
            shell("am set-standby-bucket $pkg active")
            app.alarm.cancel()
            app.engine = own
            manager.cancel(tag, 0)
        }
    }
}
