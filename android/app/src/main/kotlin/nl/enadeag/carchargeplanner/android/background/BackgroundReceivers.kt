package nl.enadeag.carchargeplanner.android.background

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.launch
import nl.enadeag.carchargeplanner.android.CarChargePlannerApp
import nl.enadeag.carchargeplanner.engine.ForecastUse

/**
 * Where the alarm goes off: one round from the stored forecast only, which reads no API key and asks nobody, then the
 * next alarm. Not exported: only the app's own alarm reaches it.
 */
class NoticeAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as CarChargePlannerApp
        val pending = goAsync()
        app.scope.launch {
            try {
                app.rounds.run(ForecastUse.STORED_ONLY)
            } finally {
                pending.finish()
            }
        }
    }
}

/**
 * After the phone started or the app was updated, when Android has forgotten the alarm: one round from the stored
 * forecast at once, which sets the alarm again, then one round that asks the providers as soon as there is a network.
 * Exported for the system's two broadcasts only; any other intent is ignored.
 */
class BootReceiver : BroadcastReceiver() {

    private companion object {
        val SYSTEM_ACTIONS = setOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED)
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in SYSTEM_ACTIONS) {
            return
        }
        val app = context.applicationContext as CarChargePlannerApp
        val pending = goAsync()
        app.scope.launch {
            try {
                app.rounds.run(ForecastUse.STORED_ONLY)
                app.schedule.afterBoot()
            } finally {
                pending.finish()
            }
        }
    }
}
