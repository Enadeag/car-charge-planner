package nl.enadeag.carchargeplanner.android.background

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import nl.enadeag.carchargeplanner.android.CarChargePlannerApp
import nl.enadeag.carchargeplanner.engine.ForecastUse

/**
 * How long a receiver's round may take, waiting for a round that runs included, well within the time Android gives a
 * broadcast. A round that runs sets the alarm itself, so a receiver that gives up loses nothing.
 */
internal val RECEIVER_ROUND_LIMIT = 8.seconds

/**
 * The receiver of the alarm: one round from the stored forecast only, which reads no API key and asks nobody, then the
 * next alarm. Not exported: only the app's own alarm reaches it.
 *
 * @param limit how long the round may take
 */
class NoticeAlarmReceiver internal constructor(private val limit: Duration) : BroadcastReceiver() {

    /** As Android makes it, with [RECEIVER_ROUND_LIMIT]. */
    constructor() : this(RECEIVER_ROUND_LIMIT)

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as CarChargePlannerApp
        val pending = goAsync()
        app.scope.launch {
            try {
                withTimeoutOrNull(limit) { app.rounds.run(ForecastUse.STORED_ONLY) }
            } finally {
                pending.finish()
            }
        }
    }
}

/**
 * After the phone started or the app was updated, when Android has forgotten the alarm: one round from the stored
 * forecast now, which sets the alarm again, then one round that asks the providers as soon as there is a network.
 * Exported for the system's two broadcasts only; any other intent is ignored.
 *
 * @param limit how long the round from the stored forecast may take; the round that asks the providers is asked after
 *     it all the same
 */
class BootReceiver internal constructor(private val limit: Duration) : BroadcastReceiver() {

    /** As Android makes it, with [RECEIVER_ROUND_LIMIT]. */
    constructor() : this(RECEIVER_ROUND_LIMIT)

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
                withTimeoutOrNull(limit) { app.rounds.run(ForecastUse.STORED_ONLY) }
                app.schedule.afterBoot()
            } finally {
                pending.finish()
            }
        }
    }
}
