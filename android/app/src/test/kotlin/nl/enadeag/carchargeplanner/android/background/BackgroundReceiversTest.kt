package nl.enadeag.carchargeplanner.android.background

import android.app.AlarmManager
import android.content.Intent
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.flow.MutableStateFlow
import nl.enadeag.carchargeplanner.android.CarChargePlannerApp
import nl.enadeag.carchargeplanner.android.form.DRAFT
import nl.enadeag.carchargeplanner.android.store.FakeEngineAccess
import nl.enadeag.carchargeplanner.android.store.StartState
import nl.enadeag.carchargeplanner.engine.ForecastUse
import nl.enadeag.carchargeplanner.engine.Outlook
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

/** The alarm's receiver and the boot receiver, reached as Android reaches them, on the app's parts in memory. */
@RunWith(AndroidJUnit4::class)
class BackgroundReceiversTest {

    private val app = ApplicationProvider.getApplicationContext<CarChargePlannerApp>()
    private val alarms = shadowOf(app.getSystemService(AlarmManager::class.java))
    private val now = Instant.parse("2026-06-07T09:00:00Z")
    private val due = now.plus(Duration.ofHours(2))
    private val access = FakeEngineAccess(StartState.Ready(DRAFT))
    private val background = FakeBackground()
    private var allowed = true

    @Before
    fun partsInMemory() {
        access.roundOutlook = Outlook.NoForecast("Open-Meteo could not be reached")
        access.noticeDue = due
        app.rounds = BackgroundRounds({ access }, { _, _ -> }, AndroidNoticeAlarm(app), background.work, { allowed },
            Clock.fixed(now, ZoneOffset.UTC))
        app.schedule = BackgroundSchedule({ access }, background.work, background.alarm, MutableStateFlow(true)) {
            allowed
        }
    }

    /** Delivers the broadcasts sent and waits until the work they started in the app's scope has ended. */
    private fun deliver(until: () -> Boolean) {
        val end = System.nanoTime() + Duration.ofSeconds(10).toNanos()
        while (!until() && System.nanoTime() < end) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(10)
        }
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun theAlarmMakesARoundOnTheStoredForecastOnlyAndSetsTheNextAlarm() {
        AndroidNoticeAlarm(app).set(now)
        alarms.fireAlarm(alarms.scheduledAlarms.single())

        deliver { alarms.scheduledAlarms.isNotEmpty() && access.dueAskedFor.isNotEmpty() }

        assertThat(access.rounds).containsExactly(ForecastUse.STORED_ONLY)
        assertThat(alarms.scheduledAlarms.map { it.triggerAtMs }).containsExactly(due.toEpochMilli())
    }

    @Test
    fun afterThePhoneStartsARoundOnTheStoredForecastSetsTheAlarmAndOneRoundIsAskedAtOnce() {
        app.sendBroadcast(Intent(Intent.ACTION_BOOT_COMPLETED).setClass(app, BootReceiver::class.java))

        deliver { background.done.isNotEmpty() }

        assertThat(access.rounds).containsExactly(ForecastUse.STORED_ONLY)
        assertThat(alarms.scheduledAlarms.map { it.triggerAtMs }).containsExactly(due.toEpochMilli())
        assertThat(background.done).containsExactly("once wifi")
    }

    @Test
    fun afterAnUpdateOfTheAppTheSameHappens() {
        app.sendBroadcast(Intent(Intent.ACTION_MY_PACKAGE_REPLACED).setClass(app, BootReceiver::class.java))

        deliver { background.done.isNotEmpty() }

        assertThat(access.rounds).containsExactly(ForecastUse.STORED_ONLY)
        assertThat(background.done).containsExactly("once wifi")
    }

    @Test
    fun afterThePhoneStartsWhileNotificationsAreNotAllowedNothingIsScheduled() {
        allowed = false
        AndroidNoticeAlarm(app).set(due)

        app.sendBroadcast(Intent(Intent.ACTION_BOOT_COMPLETED).setClass(app, BootReceiver::class.java))
        deliver { background.done.contains("work cancelled") && alarms.scheduledAlarms.isEmpty() }

        assertThat(access.rounds).isEmpty()
        assertThat(alarms.scheduledAlarms).isEmpty()
        assertThat(background.done).containsExactly("work cancelled")
    }

    @Test
    fun theBootReceiverIgnoresAnyOtherIntent() {
        app.sendBroadcast(Intent("nl.example.SOMETHING").setClass(app, BootReceiver::class.java))
        app.sendBroadcast(Intent().setClass(app, BootReceiver::class.java))

        // nothing comes to wait for: give a round that should not start the time to show
        shadowOf(Looper.getMainLooper()).idle()
        Thread.sleep(300)

        assertThat(access.rounds).isEmpty()
        assertThat(background.done).isEmpty()
    }
}
