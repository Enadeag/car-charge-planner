package nl.enadeag.carchargeplanner.android.background

import android.app.AlarmManager
import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.Instant
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowAlarmManager

/** The alarm on Robolectric's alarm manager: its kind, its time, and that there is only ever one. */
@RunWith(AndroidJUnit4::class)
class AndroidNoticeAlarmTest {

    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val manager = shadowOf(app.getSystemService(AlarmManager::class.java))
    private val alarm = AndroidNoticeAlarm(app)
    private val at = Instant.parse("2026-06-07T09:00:00Z")

    @Test
    fun theAlarmIsAnInexactOneOnTheWallClockThatWakesThePhoneAlsoWhileItDozes() {
        alarm.set(at)

        val set = manager.scheduledAlarms.single()
        assertThat(set.type).isEqualTo(AlarmManager.RTC_WAKEUP)
        assertThat(set.triggerAtMs).isEqualTo(at.toEpochMilli())
        assertThat(set.isAllowWhileIdle).isTrue()
        assertThat(set.windowLengthMs).isEqualTo(ShadowAlarmManager.WINDOW_HEURISTIC)
        assertThat(set.intervalMs).isZero()
    }

    @Test
    fun theAlarmReachesTheAppsOwnReceiverThroughAnImmutableIntent() {
        alarm.set(at)

        val operation = shadowOf(manager.scheduledAlarms.single().operation)
        assertThat(operation.isBroadcast).isTrue()
        assertThat(operation.isImmutable).isTrue()
        assertThat(operation.savedIntent.component?.className).isEqualTo(NoticeAlarmReceiver::class.java.name)
        assertThat(operation.savedIntent.component?.packageName).isEqualTo(app.packageName)
    }

    @Test
    fun aNewAlarmReplacesTheOneBefore() {
        alarm.set(at)
        alarm.set(at.plusSeconds(3600))

        assertThat(manager.scheduledAlarms.map { it.triggerAtMs }).containsExactly(at.plusSeconds(3600).toEpochMilli())
    }

    @Test
    fun aCancelTakesTheAlarmAway() {
        alarm.set(at)
        alarm.cancel()

        assertThat(manager.scheduledAlarms).isEmpty()
    }

    @Test
    fun aCancelWithoutAnAlarmChangesNothing() {
        alarm.cancel()

        assertThat(manager.scheduledAlarms).isEmpty()
    }
}
