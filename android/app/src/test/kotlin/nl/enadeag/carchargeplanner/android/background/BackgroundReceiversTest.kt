package nl.enadeag.carchargeplanner.android.background

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Intent
import android.os.Bundle
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
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
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowBroadcastPendingResult
import org.robolectric.util.ReflectionHelpers
import org.robolectric.util.ReflectionHelpers.ClassParameter

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
            Clock.fixed(now, ZoneOffset.UTC), background.missed, background.oneRoundAtATime)
        app.schedule = BackgroundSchedule({ access }, background.work, background.alarm, MutableStateFlow(true),
            background.oneRoundAtATime) {
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

    /**
     * Hands [intent] to [receiver] as Android does, with the result Android waits for when it goes on in the background.
     *
     * @return that result
     */
    private fun receive(receiver: BroadcastReceiver, intent: Intent): BroadcastReceiver.PendingResult {
        val result = ReflectionHelpers.callStaticMethod<BroadcastReceiver.PendingResult>(
            ShadowBroadcastPendingResult::class.java, "create", ClassParameter.from(Int::class.javaPrimitiveType, 0),
            ClassParameter.from(String::class.java, null), ClassParameter.from(Bundle::class.java, null),
            ClassParameter.from(Boolean::class.javaPrimitiveType, false))
        ReflectionHelpers.callInstanceMethod<Unit>(receiver, "setPendingResult",
            ClassParameter.from(BroadcastReceiver.PendingResult::class.java, result))
        receiver.onReceive(app, intent)
        return result
    }

    /** Waits until the receiver told Android it is done; fails after ten seconds. */
    private fun finished(result: BroadcastReceiver.PendingResult) {
        Shadow.extract<ShadowBroadcastPendingResult>(result).future.get(10, TimeUnit.SECONDS)
    }

    @Test
    fun theAlarmsReceiverTellsAndroidItIsDoneOnceTheNextAlarmIsSet() {
        finished(receive(NoticeAlarmReceiver(), Intent()))

        assertThat(alarms.scheduledAlarms.map { it.triggerAtMs }).containsExactly(due.toEpochMilli())
    }

    @Test
    fun theBootReceiverTellsAndroidItIsDoneOnceTheRoundNowIsAsked() {
        finished(receive(BootReceiver(), Intent(Intent.ACTION_BOOT_COMPLETED)))

        assertThat(background.done).containsExactly("once wifi")
    }

    @Test
    fun theAlarmsReceiverGivesUpOnARoundThatRunsTooLongAndIsDoneInTime() {
        runBlocking { background.oneRoundAtATime.lock() }
        try {
            finished(receive(NoticeAlarmReceiver(50.milliseconds), Intent()))
        } finally {
            background.oneRoundAtATime.unlock()
        }

        assertThat(access.rounds).isEmpty()
        assertThat(alarms.scheduledAlarms).isEmpty()
    }

    @Test
    fun theBootReceiverGivesUpOnARoundThatRunsTooLongAndStillAsksTheRoundNow() {
        runBlocking { background.oneRoundAtATime.lock() }
        try {
            finished(receive(BootReceiver(50.milliseconds), Intent(Intent.ACTION_MY_PACKAGE_REPLACED)))
        } finally {
            background.oneRoundAtATime.unlock()
        }

        assertThat(access.rounds).isEmpty()
        assertThat(background.done).containsExactly("once wifi")
    }

    @Test
    fun theBootReceiverIgnoresAnyOtherIntent() {
        val receiver = BootReceiver()

        receive(receiver, Intent("nl.example.SOMETHING"))
        receive(receiver, Intent())

        // nothing goes on after onReceive: no round can still come
        assertThat(shadowOf(receiver).wentAsync()).isFalse()
        assertThat(access.rounds).isEmpty()
        assertThat(background.done).isEmpty()
    }

    @Test
    fun theReceiversGiveUpBeforeAndroidCountsThemAsNotResponding() {
        // Android's shortest time for a broadcast, on its foreground queue
        assertThat(RECEIVER_ROUND_LIMIT).isLessThan(10.seconds)
    }
}
