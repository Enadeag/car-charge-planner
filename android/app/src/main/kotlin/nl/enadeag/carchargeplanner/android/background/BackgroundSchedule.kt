package nl.enadeag.carchargeplanner.android.background

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import nl.enadeag.carchargeplanner.android.store.EngineAccess
import nl.enadeag.carchargeplanner.android.store.StartState
import org.slf4j.LoggerFactory

/**
 * Sets up the work in the background as the app is used: at each start of the screen, at Finish and after each change
 * the owner saved, the hourly round and one round now are scheduled once the app is set up and notifications are
 * allowed, so the first alarm is set at once and a changed place gets its forecast; both are cancelled, with the alarm,
 * when the settings file is gone or notifications are not allowed. A round now that only waits is not doubled, as it
 * reads the settings when it runs; one that runs already gets exactly one more after it ([RoundWork.once]). After the
 * phone starts or the app is updated, only the round now is asked: WorkManager keeps the hourly one itself. A failure
 * is logged by its kind only.
 *
 * @param access the engine's reads and writes, asked anew each time so a test can put another in place
 * @param work the work that asks the providers
 * @param alarm the alarm for the next notice
 * @param wifiOnly the "Wi-Fi only" switch
 * @param oneAtATime the lock of the [BackgroundRounds], which a cancel waits for, so a running round's alarm does not
 *     come back once cancelled
 * @param allowed whether notifications are allowed now
 */
class BackgroundSchedule(private val access: () -> EngineAccess, private val work: RoundWork,
    private val alarm: NoticeAlarm, private val wifiOnly: Flow<Boolean>, private val oneAtATime: Mutex,
    private val allowed: () -> Boolean) {

    private companion object {
        val LOG = LoggerFactory.getLogger(BackgroundSchedule::class.java)
    }

    /** At a start of the screen, at Finish and after a saved change: schedules, cancels or leaves the work. */
    suspend fun refresh(): Unit = guarded("refresh") {
        when (BackgroundRules.atStart(access().readSettings(), allowed())) {
            BackgroundRules.AtStart.SCHEDULE -> {
                val wifi = wifiOnly.first()
                work.hourly(wifi)
                work.once(wifi)
            }
            // after a running round, so its alarm does not come back once cancelled
            BackgroundRules.AtStart.CANCEL -> oneAtATime.withLock { stopAll(alarm, work) }
            BackgroundRules.AtStart.LEAVE -> Unit
        }
    }

    /**
     * After the phone started or the app was updated, once the round on the stored forecast has set the alarm: one round
     * now.
     */
    suspend fun afterBoot(): Unit = guarded("boot") {
        if (allowed() && access().readSettings() is StartState.Ready) {
            work.once(wifiOnly.first())
        }
    }

    /**
     * The "Wi-Fi only" switch changed: the hourly round, when scheduled, and a round now that waits, wait for the
     * network it now says; a round now that runs is left alone.
     *
     * @param on the switch as it is now
     */
    suspend fun wifiSwitched(on: Boolean): Unit = guarded("switch") {
        if (work.hourlyScheduled()) {
            work.hourly(on)
        }
        work.onceWaitsFor(on)
    }

    private suspend fun guarded(kind: String, step: suspend () -> Unit) {
        try {
            step()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: RuntimeException) {
            LOG.warn("The background work's {} failed: {}", kind, failure.javaClass.simpleName)
        }
    }
}
