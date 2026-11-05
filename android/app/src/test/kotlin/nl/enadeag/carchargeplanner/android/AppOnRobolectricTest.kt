package nl.enadeag.carchargeplanner.android

import android.app.Application
import android.app.NotificationManager
import android.os.Looper
import android.os.StrictMode
import android.os.strictmode.DiskReadViolation
import android.os.strictmode.DiskWriteViolation
import android.system.Os
import android.system.OsConstants
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.catchThrowable
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowLog

/**
 * The app as Android starts it, on Robolectric: the Application the manifest names, and the activity, which opens on
 * set-up without a settings file. JUnit 4, which Robolectric runs on, through the vintage engine beside the JUnit 5
 * tests.
 */
@RunWith(AndroidJUnit4::class)
class AppOnRobolectricTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun withoutASettingsFileTheAppOpensOnSetUpsWelcome() {
        val welcome = isHeading() and hasText("Welcome", substring = true)

        compose.waitUntil(10_000) { compose.onAllNodes(welcome).fetchSemanticsNodes().isNotEmpty() }

        compose.onNode(welcome).assertIsDisplayed()
    }

    @Test
    fun theManifestNamesTheAppsOwnApplicationWhichMakesTheEngineAndTheSaves() {
        val app = ApplicationProvider.getApplicationContext<Application>()

        assertThat(app).isInstanceOfSatisfying(CarChargePlannerApp::class.java) { own ->
            assertThat(own.engine.providers()).hasSize(2)
            assertThat(own.saves.lastOutcome.value).isNull()
        }
    }

    @Test
    fun theDebugBuildsMainThreadPolicyDetectsDiskReadsDiskWritesAndTheNetworkOnly() {
        val literal = StrictMode.ThreadPolicy.Builder()
            .detectDiskReads()
            .detectDiskWrites()
            .detectNetwork()
            .penaltyListener(Runnable::run) {}
            .build()

        assertThat(CarChargePlannerApp.mainThreadPolicy().toString()).isEqualTo(literal.toString())
    }

    @Test
    fun onlyTheDebugBuildSetsAMainThreadPolicy() {
        val set = mutableListOf<StrictMode.ThreadPolicy>()

        CarChargePlannerApp.applyMainThreadPolicy(debug = false, set = { set += it })
        assertThat(set).isEmpty()

        CarChargePlannerApp.applyMainThreadPolicy(debug = true, set = { set += it })
        assertThat(set).hasSize(1)
    }

    @Test
    fun theAppSetsThePolicyOnlyInTheDebugBuild() {
        val expected = if (BuildConfig.DEBUG) CarChargePlannerApp.mainThreadPolicy() else StrictMode.ThreadPolicy.LAX

        assertThat(StrictMode.getThreadPolicy().toString()).isEqualTo(expected.toString())
    }

    @Test
    fun aDiskWriteOfTheAppsOwnCodeOnTheMainThreadCrashesTheDebugBuild() {
        assumeTrue(BuildConfig.DEBUG)
        val policy = StrictMode.allowThreadDiskWrites()
        StrictMode.allowThreadDiskReads()
        val file = try {
            val cache = ApplicationProvider.getApplicationContext<Application>().cacheDir.apply { mkdirs() }
            File(cache, "strictmode-write")
        } finally {
            StrictMode.setThreadPolicy(policy)
        }

        val descriptor = Os.open(file.path, OsConstants.O_WRONLY or OsConstants.O_CREAT, 384)
        // opening for writing is a read of the disk and then a write, each its own crash
        val crashes = try {
            List(2) { catchThrowable { shadowOf(Looper.getMainLooper()).idle() } }
        } finally {
            val allowed = StrictMode.allowThreadDiskWrites()
            StrictMode.allowThreadDiskReads()
            try {
                Os.close(descriptor)
                file.delete()
            } finally {
                StrictMode.setThreadPolicy(allowed)
            }
        }

        assertThat(crashes.map { it?.cause?.javaClass?.name }).containsExactly(
            DiskReadViolation::class.java.name,
            DiskWriteViolation::class.java.name,
        )
    }

    @Test
    fun aDiskReadOfTheAppsOwnCodeOnTheMainThreadCrashesTheDebugBuildAfterTheReadNotInsideIt() {
        assumeTrue(BuildConfig.DEBUG)
        ShadowLog.clear()

        // a real read through Android's file calls, which StrictMode sees; this test's frames are the app's own
        Os.access("/", OsConstants.F_OK)
        val crash = catchThrowable { shadowOf(Looper.getMainLooper()).idle() }

        assertThat(crash).isInstanceOf(IllegalStateException::class.java)
            .hasCauseInstanceOf(DiskReadViolation::class.java)
        assertThat(crash.stackTrace).noneMatch { it.className.startsWith(StrictMode::class.java.name) }
        assertThat(ShadowLog.getLogsForTag("StrictMode")).isEmpty()
    }

    @Test
    fun theEngineIsNotStartedSoNothingIsPlannedOrAnnounced() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        // the process's platform parts are made once, so a later test's files folder may not be there yet, and
        // asking for it makes it, which the debug build's StrictMode refuses on the main thread
        val policy = StrictMode.allowThreadDiskWrites()
        val files = try {
            app.filesDir
        } finally {
            StrictMode.setThreadPolicy(policy)
        }

        assertThat(Thread.getAllStackTraces().keys.map { it.name }).doesNotContain("planner")
        assertThat(File(files, "announced.json")).doesNotExist()
        assertThat(File(files, "trip-notices.json")).doesNotExist()
    }

    @Test
    fun theAppMakesItsNotificationChannelsAsItStartsBeforeAnyNotification() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val manager = app.getSystemService(NotificationManager::class.java)

        assertThat(manager.notificationChannels.map { it.id })
            .containsExactlyInAnyOrder("charge_windows", "battery_trip_checks", "problems")
        assertThat(shadowOf(manager).allNotifications).isEmpty()
    }
}
