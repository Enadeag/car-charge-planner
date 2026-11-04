package nl.enadeag.carchargeplanner.android

import android.app.Application
import android.os.Looper
import android.os.StrictMode
import android.os.strictmode.DiskReadViolation
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
    fun onlyTheDebugBuildCrashesOnTheDiskOrTheNetworkOnTheMainThread() {
        val expected = if (BuildConfig.DEBUG) CarChargePlannerApp.mainThreadPolicy() else StrictMode.ThreadPolicy.LAX

        assertThat(StrictMode.getThreadPolicy().toString()).isEqualTo(expected.toString())
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
}
