package nl.enadeag.carchargeplanner.android

import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.LocalDate
import java.time.ZoneId
import nl.enadeag.carchargeplanner.android.home.Plans
import nl.enadeag.carchargeplanner.android.store.FakeEngineAccess
import nl.enadeag.carchargeplanner.android.store.Saves
import nl.enadeag.carchargeplanner.android.store.StartState
import nl.enadeag.carchargeplanner.android.ui.text
import nl.enadeag.carchargeplanner.engine.config.EngineConfig
import nl.enadeag.carchargeplanner.engine.config.ProviderId
import org.assertj.core.api.Assertions.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.runner.RunWith

/** The app's activity on Robolectric once it is set up, with an engine in memory in place of the app's own. */
@RunWith(AndroidJUnit4::class)
class MainActivityOnTheHomeTest {

    private val zone = ZoneId.of("Europe/Amsterdam")
    private val access = FakeEngineAccess(StartState.Ready(EngineConfig.setUpDraft(zone, ProviderId("open-meteo"))))

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

    @Test
    fun backOnTheDashboardClosesTheApp() {
        compose.waitUntil(10_000) { access.outlooks.isNotEmpty() }
        access.outlooks.single().complete(Plans.planned(LocalDate.of(2026, 6, 1).atTime(9, 0).atZone(zone),
            listOf(listOf(Plans.window(zone, LocalDate.of(2026, 6, 1), 13, 15)))))
        compose.waitForIdle()
        compose.onNodeWithText(text(R.string.day_window)).assertExists()

        compose.activity.onBackPressedDispatcher.onBackPressed()

        assertThat(compose.activity.isFinishing).isTrue()
    }
}
