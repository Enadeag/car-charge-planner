package nl.enadeag.carchargeplanner.android

import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.ZoneId
import nl.enadeag.carchargeplanner.android.store.FakeEngineAccess
import nl.enadeag.carchargeplanner.android.store.Saves
import nl.enadeag.carchargeplanner.android.store.StartState
import nl.enadeag.carchargeplanner.android.ui.text
import nl.enadeag.carchargeplanner.engine.config.Coordinates
import nl.enadeag.carchargeplanner.engine.forecast.Place
import nl.enadeag.carchargeplanner.engine.forecast.PlaceResults
import org.assertj.core.api.Assertions.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment

/** The app's activity on Robolectric, with an engine in memory in place of the app's own. */
@RunWith(AndroidJUnit4::class)
class MainActivityTest {

    private val access = FakeEngineAccess(StartState.NotSetUp)

    /** Puts the engine in memory into the app before the activity is made. */
    @get:Rule(order = 0)
    val engineInMemory = object : ExternalResource() {
        override fun before() {
            val app = ApplicationProvider.getApplicationContext<CarChargePlannerApp>()
            app.engine = access
            app.saves = Saves(access, app.scope)
        }
    }

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>()

    private fun field(label: Int) = compose.onNode(hasSetTextAction() and hasText(text(label), substring = true))

    private fun click(label: Int) = compose.onNodeWithText(text(label)).performScrollTo().performClick()

    @Test
    fun aChangeOfTheAppsLanguageReachesTheNumbersAndThePlaceSearchAndKeepsWhatWasTyped() {
        compose.waitUntil(10_000) { compose.onAllNodes(isHeading()).fetchSemanticsNodes().isNotEmpty() }
        click(R.string.action_next)
        field(R.string.place_field).performTextInput("Utrecht")

        // Android makes the activity again when the app's language changes, and keeps its view model
        RuntimeEnvironment.setQualifiers("nl")
        compose.activityRule.scenario.recreate()
        compose.waitForIdle()

        field(R.string.place_field).assert(hasText("Utrecht"))
        click(R.string.place_search)
        compose.waitForIdle()
        assertThat(access.searched).containsExactly("Utrecht" to "nl")
        val utrecht = Place("Utrecht", Coordinates(52.0907, 5.1214), ZoneId.of("Europe/Amsterdam"), "Utrecht",
            "Nederland", "")
        access.searches.single().complete(PlaceResults.Found(listOf(utrecht)))
        compose.waitForIdle()
        compose.onNodeWithText("Utrecht, Nederland").performScrollTo().performClick()
        click(R.string.coordinates_toggle)
        field(R.string.latitude).assert(hasText("52,0907"))
    }
}
