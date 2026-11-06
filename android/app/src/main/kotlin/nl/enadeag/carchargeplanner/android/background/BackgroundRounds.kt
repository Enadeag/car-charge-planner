package nl.enadeag.carchargeplanner.android.background

import java.time.Clock
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import nl.enadeag.carchargeplanner.android.store.EngineAccess
import nl.enadeag.carchargeplanner.engine.ForecastUse
import nl.enadeag.carchargeplanner.engine.Outlook
import org.slf4j.LoggerFactory

/** The one alarm that wakes the phone when the next notice is due. */
interface NoticeAlarm {

    /**
     * Sets the alarm, in place of the one set before.
     *
     * @param at when it goes off, as a moment, so a changed clock or time zone moves it along
     */
    fun set(at: Instant)

    /** Takes the alarm away; nothing when none is set. */
    fun cancel()
}

/** The work that asks the providers: every hour, and once at once. */
interface RoundWork {

    /**
     * Schedules the hourly round, or brings the scheduled one up to the network it waits for; its period carries on.
     *
     * @param wifiOnly whether it waits for a network without a data limit
     */
    fun hourly(wifiOnly: Boolean)

    /**
     * Schedules one round as soon as there is a network; one that waits or runs already stays, and no second comes.
     *
     * @param wifiOnly whether it waits for a network without a data limit
     */
    fun once(wifiOnly: Boolean)

    /** Cancels the hourly round and the one at once. */
    fun cancel()

    /**
     * Whether the hourly round is scheduled.
     *
     * @return true while it waits or runs
     */
    suspend fun hourlyScheduled(): Boolean
}

/** Held by every round of the work in the background, with what follows it, so never two run at once in the process. */
internal val ONE_ROUND_AT_A_TIME = Mutex()

/**
 * The rounds of the work in the background: the hourly round and the one at once, which ask the providers, and the
 * alarm's and the start's, which plan from the stored forecast only. Each round tells the owner what is due, keeps the
 * Problems notification's episodes and sets the alarm for the next notice, under one lock for the whole process. While
 * notifications are not allowed, there is no round: the work and the alarm are cancelled, until the app is opened
 * again with notifications allowed. Without a settings file the work and the alarm are cancelled after the round.
 *
 * @param access the engine's reads and writes, asked anew for each round so a test can put another in place
 * @param afterRound the Problems notification's episodes, after each round
 * @param alarm the alarm for the next notice
 * @param work the work that asks the providers
 * @param allowed whether notifications are allowed now
 * @param clock the moment the alarm is set from
 */
class BackgroundRounds(private val access: () -> EngineAccess,
    private val afterRound: suspend (Outlook, ForecastUse) -> Unit, private val alarm: NoticeAlarm,
    private val work: RoundWork, private val allowed: () -> Boolean, private val clock: Clock) {

    private companion object {
        val LOG = LoggerFactory.getLogger(BackgroundRounds::class.java)
    }

    /**
     * Makes one round. A failure is logged by its kind only and ends the round; a round that gave its outlook sets the
     * alarm also when it is stopped.
     *
     * @param use where the forecast comes from: the providers for the hourly round and the one at once, the stored
     *     forecast only for the alarm's and the start's
     */
    suspend fun run(use: ForecastUse) = ONE_ROUND_AT_A_TIME.withLock {
        var outlook: Outlook? = null
        try {
            if (!allowed()) {
                stop()
                return@withLock
            }
            outlook = access().round(use)
            afterRound(outlook, use)
        } catch (failure: RuntimeException) {
            if (failure is CancellationException) {
                throw failure
            }
            LOG.warn("A round of kind {} failed: {}", use, failure.javaClass.simpleName)
        } finally {
            outlook?.let { withContext(NonCancellable) { follow(it) } }
        }
    }

    /** Sets the alarm for the next notice, or takes everything away when the app is no longer set up. */
    private suspend fun follow(outlook: Outlook) {
        try {
            if (outlook is Outlook.NotSetUp) {
                stop()
                return
            }
            val at = BackgroundRules.alarmAt(access().nextNoticeDue(outlook), clock.instant())
            if (at == null) alarm.cancel() else alarm.set(at)
        } catch (failure: RuntimeException) {
            LOG.warn("The alarm could not be set: {}", failure.javaClass.simpleName)
        }
    }

    /** The alarm first, as cancelling the work may stop the round that runs this. */
    private fun stop() {
        alarm.cancel()
        work.cancel()
    }
}
