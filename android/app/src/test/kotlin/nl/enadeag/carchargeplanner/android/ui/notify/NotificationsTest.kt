package nl.enadeag.carchargeplanner.android.ui.notify

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.res.Configuration
import android.content.res.Resources
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Locale
import java.util.Optional
import nl.enadeag.carchargeplanner.android.MainActivity
import nl.enadeag.carchargeplanner.android.R
import nl.enadeag.carchargeplanner.engine.config.Percent
import nl.enadeag.carchargeplanner.engine.config.Phases
import nl.enadeag.carchargeplanner.engine.notify.Announcement
import nl.enadeag.carchargeplanner.engine.notify.AnnouncementKind
import nl.enadeag.carchargeplanner.engine.notify.BatteryCheck
import nl.enadeag.carchargeplanner.engine.notify.TripShort
import nl.enadeag.carchargeplanner.engine.plan.Window
import nl.enadeag.carchargeplanner.engine.trip.Trip
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** The notifications as Android gets them, on Robolectric's notification manager. */
@RunWith(AndroidJUnit4::class)
class NotificationsTest {

    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val manager = app.getSystemService(NotificationManager::class.java)
    private val amsterdam = ZoneId.of("Europe/Amsterdam")
    private val start = Instant.parse("2026-06-07T09:15:00Z")
    private val end = Instant.parse("2026-06-07T12:30:00Z")
    private val window = Window(start, end, 10, 2.3, 7.3, 0.6)
    private val departure = Instant.parse("2026-06-07T12:00:00Z")
    private val trip = Trip(departure, departure.plus(Duration.ofHours(8)), 240.0, false)

    /** Fifteen minutes before the window starts. */
    private val now = start.minus(Duration.ofMinutes(15))
    private val notifications = Notifications(app, Clock.fixed(now, ZoneOffset.UTC))

    private val solar = Announcement(AnnouncementKind.SOLAR_WINDOW, window, amsterdam, Phases.ONE, Percent(30.0),
        Optional.empty())

    @Before
    fun allowNotificationsOnA24HourClock() {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        Settings.System.putString(app.contentResolver, Settings.System.TIME_12_24, "24")
        createNoticeChannels(app)
    }

    private fun posted(tag: String): Notification = shadowOf(manager).getNotification(tag, 0)

    private fun Notification.title() = extras.getCharSequence(Notification.EXTRA_TITLE).toString()

    private fun Notification.text() = extras.getCharSequence(Notification.EXTRA_TEXT).toString()

    /** What a tap on the notification, or one of its actions, starts. */
    private fun started(notification: Notification, action: Int? = null): Intent {
        val tap = if (action == null) notification.contentIntent else notification.actions[action].actionIntent
        val shadow = shadowOf(tap)
        assertThat(shadow.isActivity).isTrue()
        assertThat(shadow.isImmutable).isTrue()
        return shadow.savedIntent
    }

    @Test
    fun aWindowIsPostedUnderItsTagInItsChannelWithItsWords() {
        notifications.announce(solar)

        val posted = posted("solar_window:${start.epochSecond}")
        assertThat(posted.channelId).isEqualTo("charge_windows")
        assertThat(posted.title()).isEqualTo("Charge window 11:15 to 14:30")
        assertThat(posted.text()).startsWith("Set the charge current to 10 A (2.3 kW)")
        assertThat(posted.category).isEqualTo(Notification.CATEGORY_REMINDER)
        assertThat(posted.smallIcon.resId).isEqualTo(R.drawable.ic_stat_charge)
        assertThat(shadowOf(manager).allNotifications).hasSize(1)
    }

    @Test
    fun aWindowTimesOutAtItsEndAlertsOnceAndGoesWhenTapped() {
        notifications.announce(solar)

        val posted = posted("solar_window:${start.epochSecond}")
        assertThat(posted.timeoutAfter).isEqualTo(Duration.between(now, end).toMillis())
        assertThat(posted.flags and Notification.FLAG_ONLY_ALERT_ONCE).isNotZero()
        assertThat(posted.flags and Notification.FLAG_AUTO_CANCEL).isNotZero()
    }

    @Test
    fun theLockScreenShowsOnlyThatAChargeWindowComesSoon() {
        notifications.announce(solar)

        val posted = posted("solar_window:${start.epochSecond}")
        assertThat(posted.visibility).isEqualTo(Notification.VISIBILITY_PRIVATE)
        assertThat(posted.publicVersion.title()).isEqualTo("Charge window soon")
        assertThat(posted.publicVersion.extras.getCharSequence(Notification.EXTRA_TEXT)).isNull()
    }

    @Test
    fun aTapOpensTheAppsActivityStraightAndAsksForNothingMore() {
        notifications.announce(solar)

        val intent = started(posted("solar_window:${start.epochSecond}"))
        assertThat(intent.component?.className).isEqualTo(MainActivity::class.java.name)
        assertThat(intent.component?.packageName).isEqualTo(app.packageName)
        assertThat(intent.action).isNull()
    }

    @Test
    fun aBatteryCheckOpensTheBatteryEntryByItsTapAndItsOneActionAndAlertsAgainWhenAskedAgain() {
        notifications.announce(BatteryCheck(trip, Percent(40.0), amsterdam))

        val posted = posted("battery_check:${departure.epochSecond}")
        assertThat(posted.channelId).isEqualTo("battery_trip_checks")
        assertThat(posted.publicVersion.title()).isEqualTo("Check the battery level")
        assertThat(posted.timeoutAfter).isEqualTo(Duration.between(now, departure).toMillis())
        assertThat(posted.flags and Notification.FLAG_ONLY_ALERT_ONCE).isZero()
        assertThat(posted.actions.map { it.title.toString() }).containsExactly("Enter battery level")
        for (intent in listOf(started(posted), started(posted, action = 0))) {
            assertThat(intent.component?.className).isEqualTo(MainActivity::class.java.name)
            assertThat(intent.action).isEqualTo(MainActivity.OPEN_BATTERY_ENTRY)
        }
    }

    @Test
    fun aBatteryCheckAskedAgainForTheSameTripReplacesTheFirst() {
        notifications.announce(BatteryCheck(trip, Percent(40.0), amsterdam))
        notifications.announce(BatteryCheck(trip, Percent(70.0), amsterdam))

        assertThat(shadowOf(manager).allNotifications).hasSize(1)
        assertThat(posted("battery_check:${departure.epochSecond}").text()).contains("about 70%")
    }

    @Test
    fun aShortTripOpensTheDashboard() {
        notifications.announce(TripShort(trip, 11.2, amsterdam))

        val posted = posted("trip_short:${departure.epochSecond}")
        assertThat(posted.channelId).isEqualTo("battery_trip_checks")
        assertThat(started(posted).action).isNull()
        assertThat(posted.actions).isNull()
    }

    @Test
    fun aNoticeWhoseWindowEndedIsNotPosted() {
        Notifications(app, Clock.fixed(end, ZoneOffset.UTC)).announce(solar)

        assertThat(shadowOf(manager).allNotifications).isEmpty()
    }

    @Test
    fun withoutAndroid13sPermissionNothingIsPosted() {
        shadowOf(app).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)

        notifications.announce(solar)
        notifications.show()

        assertThat(shadowOf(manager).allNotifications).isEmpty()
    }

    @Test
    fun whileNotificationsAreBlockedNothingIsPosted() {
        shadowOf(manager).setNotificationsEnabled(false)

        notifications.announce(solar)
        notifications.show()

        assertThat(shadowOf(manager).allNotifications).isEmpty()
    }

    @Test
    fun aNotificationThatCannotBeMadeIsNotThrown() {
        val broken = object : ContextWrapper(app) {
            override fun getResources(): Resources = throw IllegalStateException("no resources")
            override fun getApplicationContext(): Context = this
        }

        Notifications(broken, Clock.fixed(now, ZoneOffset.UTC)).announce(solar)

        assertThat(shadowOf(manager).allNotifications).isEmpty()
    }

    @Test
    fun theWordsAreInTheAppsLanguageWhenPosted() {
        val dutch = app.createConfigurationContext(Configuration(app.resources.configuration).apply {
            setLocale(Locale.forLanguageTag("nl"))
        })

        Notifications(dutch, Clock.fixed(now, ZoneOffset.UTC)).announce(solar)

        assertThat(posted("solar_window:${start.epochSecond}").title()).isEqualTo("Laadvenster 11:15 tot 14:30")
    }

    @Test
    fun thePhonesClockIsReadWhenANoticeIsPosted() {
        notifications.announce(solar)
        Settings.System.putString(app.contentResolver, Settings.System.TIME_12_24, "12")

        notifications.announce(TripShort(trip, 11.2, amsterdam))

        assertThat(posted("solar_window:${start.epochSecond}").title()).isEqualTo("Charge window 11:15 to 14:30")
        assertThat(posted("trip_short:${departure.epochSecond}").text().replace('\u202f', ' '))
            .contains("on Sunday at 2:00 PM")
    }

    @Test
    fun theProblemsNotificationIsOneQuietErrorThatOpensTheDashboardUntilItIsTakenAway() {
        notifications.show()

        val posted = posted("problems")
        assertThat(posted.channelId).isEqualTo("problems")
        assertThat(posted.category).isEqualTo(Notification.CATEGORY_ERROR)
        assertThat(posted.title()).isEqualTo("Forecast not updated")
        assertThat(posted.text()).isEqualTo("The forecast could not be updated. Open the app for details.")
        assertThat(posted.timeoutAfter).isZero()
        assertThat(posted.visibility).isEqualTo(Notification.VISIBILITY_PRIVATE)
        assertThat(started(posted).action).isNull()

        notifications.remove()

        assertThat(shadowOf(manager).allNotifications).isEmpty()
    }

    @Test
    fun theChannelsHaveTheirNamesDescriptionsAndImportance() {
        val channels = manager.notificationChannels.associateBy { it.id }

        assertThat(channels.keys).containsExactlyInAnyOrder("charge_windows", "battery_trip_checks", "problems")
        assertThat(channels.getValue("charge_windows").name).isEqualTo("Charge windows")
        assertThat(channels.getValue("charge_windows").importance).isEqualTo(NotificationManager.IMPORTANCE_DEFAULT)
        assertThat(channels.getValue("charge_windows").description).startsWith("A notice shortly before")
        assertThat(channels.getValue("battery_trip_checks").name).isEqualTo("Battery and trip checks")
        assertThat(channels.getValue("battery_trip_checks").importance)
            .isEqualTo(NotificationManager.IMPORTANCE_DEFAULT)
        assertThat(channels.getValue("problems").name).isEqualTo("Problems")
        assertThat(channels.getValue("problems").importance).isEqualTo(NotificationManager.IMPORTANCE_LOW)
        assertThat(channels.getValue("problems").sound).isNull()
    }

    @Test
    @Config(qualifiers = "nl")
    fun theChannelsNamesFollowTheAppsLanguageWhenTheAppStartsAgain() {
        createNoticeChannels(app)

        assertThat(manager.notificationChannels.map { it.name.toString() })
            .containsExactlyInAnyOrder("Laadvensters", "Controles van accuniveau en ritten", "Problemen")
    }

    @Test
    fun theSettingsOfTheAppsNotificationsOpenForThisApp() {
        openNotificationSettings(app.let { context ->
            object : ContextWrapper(context) {
                override fun startActivity(intent: Intent) = context.startActivity(intent
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        })

        val intent = shadowOf(app).nextStartedActivity
        assertThat(intent.action).isEqualTo(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        assertThat(intent.getStringExtra(Settings.EXTRA_APP_PACKAGE)).isEqualTo(app.packageName)
    }
}
