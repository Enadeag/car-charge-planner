package nl.enadeag.carchargeplanner.android.background

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import java.time.Instant

/**
 * The alarm on Android's alarm manager: inexact, allowed while the phone dozes, on the wall clock, and waking the
 * phone; no exact-alarm permission. One explicit, immutable intent to the app's own [NoticeAlarmReceiver], under one
 * request code, so each alarm replaces the one before.
 *
 * @param context the application's context
 */
class AndroidNoticeAlarm(private val context: Context) : NoticeAlarm {

    private companion object {
        const val REQUEST_CODE = 362
    }

    private val manager: AlarmManager = context.getSystemService(AlarmManager::class.java)

    override fun set(at: Instant) {
        manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.toEpochMilli(), intent())
    }

    override fun cancel() {
        manager.cancel(intent())
    }

    private fun intent(): PendingIntent = PendingIntent.getBroadcast(context, REQUEST_CODE,
        Intent(context, NoticeAlarmReceiver::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
}
