package nl.enadeag.carchargeplanner.android.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.ZoneId
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import nl.enadeag.carchargeplanner.android.R
import nl.enadeag.carchargeplanner.android.form.NumberText
import nl.enadeag.carchargeplanner.android.start.AppFlow
import nl.enadeag.carchargeplanner.android.store.FakeEngineAccess
import nl.enadeag.carchargeplanner.android.store.Saves
import nl.enadeag.carchargeplanner.android.store.StartState
import nl.enadeag.carchargeplanner.engine.config.Coordinates
import nl.enadeag.carchargeplanner.engine.config.EngineConfig
import nl.enadeag.carchargeplanner.engine.config.ProviderId
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The app's screens as `AppFlow` moves between them, on Robolectric with an engine in memory. */
@RunWith(AndroidJUnit4::class)
class AppScreenTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val amsterdam = ZoneId.of("Europe/Amsterdam")
    private val draft = EngineConfig.setUpDraft(amsterdam, ProviderId("open-meteo"))
    private val access = FakeEngineAccess(StartState.Ready(draft.withSystem(
        draft.system().withLocation(Coordinates(52.0907, 5.1214), amsterdam), draft.typical())))
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    @After
    fun stop() {
        scope.cancel()
    }

    @Test
    fun theHomesSettingsOpensTheListAndUpComesBackToTheHome() {
        val flow = AppFlow(access, Saves(access, scope), scope, scope, SavedStateHandle(),
            NumberText(Locale.ENGLISH), { amsterdam }, { "en" }, Dispatchers.Main.immediate)
        compose.setContent { AppTheme { AppScreen(flow) } }
        compose.waitForIdle()

        compose.onNodeWithContentDescription(text(R.string.home_settings)).performClick()
        compose.waitForIdle()
        compose.onNode(isHeading() and hasText(text(R.string.settings_title))).assertExists()
        assertThat(flow.screen.value).isInstanceOf(AppFlow.Screen.Settings::class.java)
        compose.onNodeWithContentDescription(text(R.string.action_up)).performClick()
        compose.waitForIdle()

        compose.onNode(isHeading() and hasText(text(R.string.home_title))).assertExists()
        assertThat(flow.screen.value).isInstanceOf(AppFlow.Screen.Home::class.java)
    }
}
