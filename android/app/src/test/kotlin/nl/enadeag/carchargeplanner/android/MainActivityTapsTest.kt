package nl.enadeag.carchargeplanner.android

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import nl.enadeag.carchargeplanner.android.home.Plans
import nl.enadeag.carchargeplanner.android.store.FakeEngineAccess
import nl.enadeag.carchargeplanner.android.store.Saves
import nl.enadeag.carchargeplanner.android.store.StartState
import nl.enadeag.carchargeplanner.android.ui.NOT_READ_APART
import nl.enadeag.carchargeplanner.android.ui.notify.Notifications
import nl.enadeag.carchargeplanner.android.ui.notify.createNoticeChannels
import nl.enadeag.carchargeplanner.android.ui.text
import nl.enadeag.carchargeplanner.engine.config.EngineConfig
import nl.enadeag.carchargeplanner.engine.config.Percent
import nl.enadeag.carchargeplanner.engine.config.ProviderId
import nl.enadeag.carchargeplanner.engine.notify.BatteryCheck
import nl.enadeag.carchargeplanner.engine.trip.Trip
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf

/** A notification's tap, as it reaches the app's activity, with an engine in memory in place of the app's own. */
@RunWith(AndroidJUnit4::class)
class MainActivityTapsTest {

    private val zone = ZoneId.of("Europe/Amsterdam")
    private val access = FakeEngineAccess(StartState.Ready(EngineConfig.setUpDraft(zone, ProviderId("open-meteo"))))
    private val app = ApplicationProvider.getApplicationContext<CarChargePlannerApp>()
    private val manager = app.getSystemService(NotificationManager::class.java)
    private var scenario: ActivityScenario<MainActivity>? = null

    /** Puts the engine in memory into the app before the activity is made. */
    @get:Rule(order = 0)
    val engineInMemory = object : ExternalResource() {
        override fun before() {
            app.engine = access
            app.saves = Saves(access, app.scope)
        }
    }

    @get:Rule(order = 1)
    val compose = createEmptyComposeRule()

    @After
    fun close() {
        scenario?.close()
    }

    private fun tap(action: String?) = Intent(app, MainActivity::class.java).setAction(action)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    private fun battery() = tap(MainActivity.OPEN_BATTERY_ENTRY)

    private fun launch(intent: Intent) {
        scenario = ActivityScenario.launch(intent)
    }

    /** Gives the home its plan, as the engine's answer to its first question. */
    private fun planned() {
        compose.waitUntil(10_000) { access.outlooks.isNotEmpty() }
        access.outlooks.last().complete(Plans.planned(LocalDate.of(2026, 6, 1).atTime(9, 0).atZone(zone),
            listOf(listOf(Plans.window(zone, LocalDate.of(2026, 6, 1), 13, 15)))))
        compose.waitForIdle()
        compose.onNode(hasText(text(R.string.day_window)) and !NOT_READ_APART).assertExists()
    }

    private fun entry() = compose.onNode(isDialog())

    /** Posts a battery check for a trip, as the engine would, and gives back its notification. */
    private fun batteryCheck(departure: Instant): Notification {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        createNoticeChannels(app)
        val trip = Trip(departure, departure.plus(Duration.ofHours(8)), 240.0, false)
        Notifications(app, Clock.fixed(departure.minus(Duration.ofHours(2)), ZoneOffset.UTC))
            .announce(BatteryCheck(trip, Percent(40.0), zone))
        return shadowOf(manager).getNotification("battery_check:${departure.epochSecond}", 0)
    }

    private fun actionOf(notification: Notification): Intent =
        shadowOf(notification.actions[0].actionIntent).savedIntent

    @Test
    fun aBatteryChecksTapFromAClosedAppOpensTheBatteryEntryOnTheHome() {
        launch(battery())
        planned()

        entry().assertExists()
        compose.onNodeWithText(text(R.string.battery_entry_title)).assertExists()
    }

    @Test
    fun theTapIsFollowedOnceNotAgainWhenThePhoneTurns() {
        launch(battery())
        planned()
        compose.onNodeWithText(text(R.string.action_cancel)).performClick()
        compose.waitForIdle()
        entry().assertDoesNotExist()

        scenario!!.recreate()
        compose.waitForIdle()

        entry().assertDoesNotExist()
    }

    @Test
    fun theAppOpenedFromTheRecentAppsWithTheTapsIntentOpensNoEntry() {
        launch(battery().addFlags(Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY))
        planned()

        entry().assertDoesNotExist()
    }

    @Test
    fun aBatteryChecksActionTakesItsNotificationAwayAndNoOther() {
        val first = Instant.parse("2026-06-07T12:00:00Z")
        val second = first.plus(Duration.ofDays(1))
        val tapped = batteryCheck(first)
        batteryCheck(second)

        launch(actionOf(tapped))
        planned()

        entry().assertExists()
        assertThat(shadowOf(manager).getNotification("battery_check:${first.epochSecond}", 0)).isNull()
        assertThat(shadowOf(manager).getNotification("battery_check:${second.epochSecond}", 0)).isNotNull()
    }

    @Test
    fun theAppOpenedFromTheRecentAppsLeavesANewerNotificationUnderTheSameTag() {
        val departure = Instant.parse("2026-06-07T12:00:00Z")
        val intent = actionOf(batteryCheck(departure))

        launch(Intent(intent).addFlags(Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY))
        planned()

        assertThat(shadowOf(manager).getNotification("battery_check:${departure.epochSecond}", 0)).isNotNull()
    }

    @Test
    fun anIntentWhoseDataIsNoNoticeTakesNoNotificationAway() {
        val departure = Instant.parse("2026-06-07T12:00:00Z")
        val tag = "battery_check:${departure.epochSecond}"
        batteryCheck(departure)

        // the same words as a notice's tag, under another scheme
        launch(tap(null).setData(Uri.fromParts("other", tag, null)))
        planned()

        assertThat(shadowOf(manager).getNotification(tag, 0)).isNotNull()
    }

    @Test
    fun aDashboardsTapOpensNoEntry() {
        launch(tap(null))
        planned()

        entry().assertDoesNotExist()
    }

    @Test
    fun aBatteryChecksTapWhileTheHomeIsShownOpensTheEntryInTheActivityShown() {
        val controller = Robolectric.buildActivity(MainActivity::class.java, tap(null)).setup()
        planned()
        entry().assertDoesNotExist()

        controller.newIntent(battery())
        compose.waitForIdle()

        entry().assertExists()
        controller.pause().stop().destroy()
    }
}
