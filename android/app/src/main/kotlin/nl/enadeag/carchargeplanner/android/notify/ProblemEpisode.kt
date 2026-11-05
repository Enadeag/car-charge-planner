package nl.enadeag.carchargeplanner.android.notify

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import nl.enadeag.carchargeplanner.engine.ForecastUse
import nl.enadeag.carchargeplanner.engine.Outlook

/** Whether an episode of the Problems notification is going on, kept where it outlives the process. */
interface EpisodeFlag {

    /**
     * Whether an episode is going on.
     *
     * @return true from the round that started it until one that saw it end; false when it cannot be read
     */
    suspend fun episodeStarted(): Boolean

    /**
     * Keeps whether an episode is going on; a write that fails is logged and leaves it as it was.
     *
     * @param started true as an episode starts, false as it ends
     */
    suspend fun episodeStarted(started: Boolean)
}

/** The Problems notification itself. */
interface ProblemNotice {

    /** Posts it, or leaves it when notifications are off or it cannot be posted; never throws. */
    fun show()

    /** Takes it away when it is shown; never throws. */
    fun remove()
}

/**
 * The episodes of the Problems notification: a settings file that cannot be used, or of a newer
 * version, or an API key a provider refused, which will not fix itself. Being offline, or a provider that is down, is
 * no problem of this kind. An episode starts on a round of the work in the background that asked the providers, with
 * one notification, which comes once per episode, also when the owner swiped it away; it ends, and the notification
 * goes, on the first such round, or the first plan the dashboard asks for, that no longer shows the problem. The
 * dashboard never starts one: the owner who looks at the app sees the problem there. An outlook without a forecast
 * for another reason cannot tell either way and changes nothing, as the forecast still could not be updated.
 *
 * @param flag where whether an episode is going on is kept
 * @param notice the notification
 */
class ProblemEpisode(private val flag: EpisodeFlag, private val notice: ProblemNotice) {

    /** What an outlook says about such a problem. */
    enum class Seen {

        /** It shows one: the notification is due. */
        PROBLEM,

        /** It shows none: a plan without a refused key, or no settings file at all. */
        NONE,

        /** It cannot tell: no forecast, but no provider refused its key. */
        UNKNOWN,
    }

    private val lock = Mutex()

    /**
     * Starts or ends an episode after a round of the work in the background (#362).
     *
     * @param outlook what the round gave
     * @param use where the round took its forecast from: only a round that asked the providers counts, as one on the
     *     stored forecast only asks nobody and so cannot see a refused key
     */
    suspend fun afterRound(outlook: Outlook, use: ForecastUse) {
        if (use != ForecastUse.ASK_PROVIDERS) {
            return
        }
        lock.withLock {
            when (seen(outlook)) {
                Seen.PROBLEM -> if (!flag.episodeStarted()) {
                    flag.episodeStarted(true)
                    notice.show()
                }
                Seen.NONE -> end()
                Seen.UNKNOWN -> Unit
            }
        }
    }

    /**
     * Ends an episode when the dashboard's plan no longer shows its problem; it never starts one.
     *
     * @param outlook the plan the dashboard asked for, which asks the providers as their rules allow
     */
    suspend fun dashboardSaw(outlook: Outlook) {
        if (seen(outlook) == Seen.NONE) {
            lock.withLock { end() }
        }
    }

    private suspend fun end() {
        notice.remove()
        if (flag.episodeStarted()) {
            flag.episodeStarted(false)
        }
    }

    companion object {

        /**
         * What an outlook says about a problem that only the owner can fix.
         *
         * @param outlook the outlook
         * @return [Seen.PROBLEM] for a settings file that cannot be used or a refused API key, also on a plan whose
         *     fallback answered; [Seen.NONE] for a plan without one and for no settings file; [Seen.UNKNOWN] for no
         *     forecast without a refused key
         */
        fun seen(outlook: Outlook): Seen = when (outlook) {
            is Outlook.ConfigUnusable -> Seen.PROBLEM
            is Outlook.NoForecast -> if (outlook.keyRefusedBy().isEmpty()) Seen.UNKNOWN else Seen.PROBLEM
            is Outlook.Planned -> if (outlook.forecastSource().keyRefusedBy().isEmpty()) Seen.NONE else Seen.PROBLEM
            is Outlook.NotSetUp -> Seen.NONE
        }
    }
}
