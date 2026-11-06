package nl.enadeag.carchargeplanner.android

import android.Manifest
import android.app.NotificationManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.nio.file.Path
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import nl.enadeag.carchargeplanner.android.home.Plans
import nl.enadeag.carchargeplanner.android.store.FakeEngineAccess
import nl.enadeag.carchargeplanner.android.store.Saves
import nl.enadeag.carchargeplanner.android.store.StartState
import nl.enadeag.carchargeplanner.android.ui.NOT_READ_APART
import nl.enadeag.carchargeplanner.android.ui.text
import nl.enadeag.carchargeplanner.engine.ForecastUse
import nl.enadeag.carchargeplanner.engine.Outlook
import nl.enadeag.carchargeplanner.engine.config.ConfigProblem
import nl.enadeag.carchargeplanner.engine.config.EngineConfig
import nl.enadeag.carchargeplanner.engine.config.ProviderId
import org.assertj.core.api.Assertions.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

/** The app's activity on Robolectric once it is set up, with an engine in memory in place of the app's own. */
@RunWith(AndroidJUnit4::class)
class MainActivityOnTheHomeTest {

    private val zone = ZoneId.of("Europe/Amsterdam")
    private val access = FakeEngineAccess(StartState.Ready(EngineConfig.setUpDraft(zone, ProviderId("open-meteo"))))

    /** Puts the engine in memory into the app before the activity is made. */
    @get:Rule(order = 0)
    val engineInMemory = object : ExternalResource() {
        override fun before() {
            app.engine = access
            app.saves = Saves(access, app.scope)
        }
    }

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>()

    private val app = ApplicationProvider.getApplicationContext<CarChargePlannerApp>()
    private val manager = app.getSystemService(NotificationManager::class.java)

    /** Gives the home a plan without a problem, as the engine's answer to its first question. */
    private fun planned() {
        compose.waitUntil(10_000) { access.outlooks.isNotEmpty() }
        access.outlooks.single().complete(Plans.planned(LocalDate.of(2026, 6, 1).atTime(9, 0).atZone(zone),
            listOf(listOf(Plans.window(zone, LocalDate.of(2026, 6, 1), 13, 15)))))
        compose.waitForIdle()
        compose.onNode(hasText(text(R.string.day_window)) and !NOT_READ_APART).assertExists()
    }

    @Test
    fun backOnTheDashboardClosesTheApp() {
        planned()

        compose.activity.onBackPressedDispatcher.onBackPressed()

        assertThat(compose.activity.isFinishing).isTrue()
    }

    @Test
    fun whileNotificationsAreNotAllowedTheHomeAsksForThem() {
        planned()

        compose.waitUntil(10_000) {
            compose.onAllNodesWithText(text(R.string.notification_card_title)).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText(text(R.string.notification_card_ask)).assertExists()
    }

    @Test
    fun settingsForecastPageHasTheWifiOnlySwitch() {
        planned()

        compose.onNodeWithContentDescription(text(R.string.home_settings)).performClick()
        compose.waitForIdle()
        compose.onNodeWithText(text(R.string.settings_forecast)).performClick()
        compose.waitForIdle()

        compose.onNode(hasText(text(R.string.forecast_wifi_only)) and
            SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch)).assertExists()
    }

    @Test
    fun aPlanWithoutTheProblemEndsTheProblemsEpisodeOnTheDashboard() {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val unusable = Outlook.ConfigUnusable(ConfigProblem.Kind.INVALID, Path.of("engine.json"), "detail")
        runBlocking { app.problems.afterRound(unusable, ForecastUse.ASK_PROVIDERS) }
        assertThat(shadowOf(manager).getNotification("problems", 0)).isNotNull()

        planned()

        compose.waitUntil(10_000) { shadowOf(manager).getNotification("problems", 0) == null }
        assertThat(runBlocking { app.phoneState.episodeStarted() }).isFalse()
    }
}
