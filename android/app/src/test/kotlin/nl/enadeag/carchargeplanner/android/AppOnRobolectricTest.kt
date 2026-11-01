package nl.enadeag.carchargeplanner.android

import android.app.Application
import android.os.StrictMode
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.assertj.core.api.Assertions.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The app as Android starts it, on Robolectric: the Application the manifest names, and the one screen until set-up
 * and Settings come. JUnit 4, which Robolectric runs on, through the vintage engine beside the JUnit 5 tests.
 */
@RunWith(AndroidJUnit4::class)
class AppOnRobolectricTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun theAppShowsItsNameAsAHeading() {
        compose.onNode(hasText("Car charge planner") and isHeading()).assertIsDisplayed()
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
}
