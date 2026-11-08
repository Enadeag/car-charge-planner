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

/** The work that asks the providers: every hour, and one round now. */
interface RoundWork {

    /**
     * Schedules the hourly round, or brings the scheduled one up to the network it waits for; its period carries on.
     *
     * @param wifiOnly whether it waits for a network without a data limit
     */
    fun hourly(wifiOnly: Boolean)

    /**
     * Schedules one round as soon as there is a network. While one only waits, nothing more is scheduled: it reads the
     * settings when it runs. While one runs, which may have read them before the change that asks for this, exactly one
     * more waits after it. Never more than one waits.
     *
     * @param wifiOnly whether it waits for a network without a data limit
     */
    suspend fun once(wifiOnly: Boolean)

    /**
     * Brings the round now that waits, if one does, up to the network it waits for; one that runs is left alone.
     *
     * @param wifiOnly whether it waits for a network without a data limit
     */
    suspend fun onceWaitsFor(wifiOnly: Boolean)

    /** Cancels the hourly round and the round now. */
    fun cancel()

    /**
     * Whether the hourly round is scheduled.
     *
     * @return true while it waits or runs
     */
    suspend fun hourlyScheduled(): Boolean
}

/**
 * How many rounds in a row gave no outlook, kept on the phone so it outlives the process between two alarms; the alarm
 * after such rounds backs off with it ([BackgroundRules.retryAt]).
 */
interface MissedRounds {

    /**
     * The rounds in a row that gave no outlook.
     *
     * @return the count; 0 after a round that gave one, and when it cannot be read
     */
    suspend fun missedRounds(): Int

    /**
     * Keeps the count; a write that fails is logged and leaves the one before.
     *
     * @param count the rounds in a row that gave no outlook, 0 after one that gave one
     */
    suspend fun missedRounds(count: Int)
}

/**
 * The rounds of the work in the background: the hourly round and the round now, which ask the providers, and the
 * alarm's and the start's, which plan from the stored forecast only. Each round tells the owner what is due, keeps the
 * Problems notification's episodes and sets the alarm for the next notice, under the one lock of the process's rounds.
 * While notifications are not allowed, there is no round: the work and the alarm are cancelled, until the app is opened
 * again with notifications allowed. Without a settings file the work and the alarm are cancelled after the round.
 *
 * @param access the engine's reads and writes, asked anew for each round so a test can put another in place
 * @param afterRound the Problems notification's episodes, after each round
 * @param alarm the alarm for the next notice
 * @param work the work that asks the providers
 * @param allowed whether notifications are allowed now
 * @param clock the moment the alarm is set from
 * @param missed the rounds in a row that gave no outlook, which the alarm after them backs off with
 * @param oneAtATime held by every round, with what follows it, so never two run at once in the process; the same lock
 *     as the [BackgroundSchedule]'s
 */
class BackgroundRounds(private val access: () -> EngineAccess,
    private val afterRound: suspend (Outlook, ForecastUse) -> Unit, private val alarm: NoticeAlarm,
    private val work: RoundWork, private val allowed: () -> Boolean, private val clock: Clock,
    private val missed: MissedRounds, private val oneAtATime: Mutex) {

    private companion object {
        val LOG = LoggerFactory.getLogger(BackgroundRounds::class.java)
    }

    /**
     * Makes one round. A failure is logged by its kind only and ends the round. The alarm is set also when the round
     * fails or is stopped: for the next notice once the round gave its outlook, else from
     * [BackgroundRules.MIN_ALARM_GAP] up to [BackgroundRules.MAX_RETRY_GAP] from now, longer with each such round in a
     * row, so the alarm's own round tries again and the alarm never stops coming.
     *
     * @param use where the forecast comes from: the providers for the hourly round and the round now, the stored
     *     forecast only for the alarm's and the start's
     */
    suspend fun run(use: ForecastUse): Unit = oneAtATime.withLock {
        if (allowed()) {
            plan(use)
        } else {
            try {
                stopAll(alarm, work)
            } catch (failure: RuntimeException) {
                LOG.warn("The work could not be cancelled: {}", failure.javaClass.simpleName)
            }
        }
    }

    private suspend fun plan(use: ForecastUse) {
        var outlook: Outlook? = null
        try {
            outlook = access().round(use)
            afterRound(outlook, use)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: RuntimeException) {
            LOG.warn("A round of kind {} failed: {}", use, failure.javaClass.simpleName)
        } finally {
            withContext(NonCancellable) { follow(outlook) }
        }
    }

    /**
     * Sets the alarm for the next notice, or takes everything away when the app is no longer set up, and keeps the
     * count of rounds in a row without an outlook.
     *
     * @param outlook the round's outlook; null when it gave none, as it failed or was stopped before
     */
    private suspend fun follow(outlook: Outlook?) {
        try {
            val inRow = if (outlook == null) missed.missedRounds() + 1 else 0
            missed.missedRounds(inRow)
            if (outlook is Outlook.NotSetUp) {
                stopAll(alarm, work)
            } else {
                val now = clock.instant()
                val at = if (outlook == null) BackgroundRules.retryAt(now, inRow)
                    else BackgroundRules.alarmAt(access().nextNoticeDue(outlook), now)
                if (at == null) alarm.cancel() else alarm.set(at)
            }
        } catch (failure: RuntimeException) {
            LOG.warn("The alarm could not be set: {}", failure.javaClass.simpleName)
        }
    }
}

/**
 * Cancels the alarm, then the work: in that order, as cancelling the work may stop the round that calls this.
 *
 * @param alarm the alarm for the next notice
 * @param work the work that asks the providers
 */
internal fun stopAll(alarm: NoticeAlarm, work: RoundWork) {
    alarm.cancel()
    work.cancel()
}
