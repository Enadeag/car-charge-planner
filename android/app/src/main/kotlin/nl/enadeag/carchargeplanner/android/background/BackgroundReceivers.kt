package nl.enadeag.carchargeplanner.android.background

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import nl.enadeag.carchargeplanner.engine.ForecastUse

/**
 * How long a receiver's round may take, waiting for a round that runs included. It stays below the shortest time
 * Android gives a broadcast, 10 seconds on its foreground queue, after which it counts the receiver as not responding,
 * also under `goAsync()`. A round that runs sets the alarm itself, so a receiver that gives up loses nothing.
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
        val host = context.applicationContext as BackgroundHost
        val pending = goAsync()
        host.scope.launch {
            try {
                withTimeoutOrNull(limit) { host.rounds.run(ForecastUse.STORED_ONLY) }
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
        val host = context.applicationContext as BackgroundHost
        val pending = goAsync()
        host.scope.launch {
            try {
                withTimeoutOrNull(limit) { host.rounds.run(ForecastUse.STORED_ONLY) }
                host.schedule.afterBoot()
            } finally {
                pending.finish()
            }
        }
    }
}
