package nl.enadeag.carchargeplanner.android.home

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import nl.enadeag.carchargeplanner.android.store.EngineAccess
import nl.enadeag.carchargeplanner.android.store.LatestAnswer
import nl.enadeag.carchargeplanner.engine.Outlook
import nl.enadeag.carchargeplanner.engine.away.OneOffAwayPeriod
import nl.enadeag.carchargeplanner.engine.config.ProviderId
import org.slf4j.LoggerFactory

/**
 * The dashboard's plan: asked when the home opens, unless it comes back from Settings where nothing changed, again on
 * Refresh or a pull, again when the owner comes back to the app and the plan shown is older than [STALE_AFTER], again
 * when a Settings save changes something while the home is shown, and again after the owner entered the battery
 * level or changed a one-off away period, only the latest answer counting. Refresh keeps a plan in view while the new
 * one is made; a Settings change does not, as the plan shown no longer fits. A settings file that is gone or cannot be
 * used is handed on, so the app shows set-up or the file's problem instead. Called from the main thread.
 *
 * @param access the engine's plan and the one-off away periods
 * @param scope where the plan is asked, the screen's
 * @param clock tells how old the plan shown is
 * @param changes counts the Settings saves that changed something; each new count asks for the plan again
 * @param kept what the home showed before Settings opened, kept when no save changed anything since; null asks
 * @param seen gets each outlook the engine gave, before the home shows it, so a Problems notification that it no
 *     longer shows goes; a failure in it is logged and changes nothing the home shows
 * @param lost called when the outlook says the app is not set up or its settings file cannot be used
 */
class HomeOutlook(private val access: EngineAccess, scope: CoroutineScope, private val clock: Clock,
    changes: StateFlow<Int> = MutableStateFlow(0), kept: Kept? = null,
    private val seen: suspend (Outlook) -> Unit = {}, private val lost: () -> Unit) {

    companion object {
        /** How old a plan may be when the owner comes back to the app before a new one is asked. */
        val STALE_AFTER: Duration = Duration.ofMinutes(15)

        private val LOG = LoggerFactory.getLogger(HomeOutlook::class.java)
    }

    /** What the home shows. */
    sealed interface State {

        /** The first plan is being made, which may fetch a forecast first. */
        data object Asking : State

        /**
         * There is a plan with days.
         *
         * @property dashboard what the home shows of it
         */
        data class Planned(val dashboard: Dashboard) : State

        /**
         * No forecast provider had a forecast, or the plan failed otherwise; the owner may try again.
         *
         * @property reason the engine's words, in English
         */
        data class NoForecast(val reason: String) : State

        /**
         * The forecast has no days to plan.
         *
         * @property footer the forecast's credit, source and freshness
         * @property zone the home's time zone, in which the footer's times are told
         */
        data class NoDays(val footer: Footer, val zone: ZoneId) : State
    }

    /**
     * An answer the home showed, and when it came, so a home made again shows it as it was.
     *
     * @property state the answer; never [State.Asking]
     * @property at when it came, by the home's clock
     * @throws IllegalArgumentException when [state] is [State.Asking], which is no answer
     */
    data class Kept(val state: State, val at: Instant) {

        init {
            if (state == State.Asking) {
                throw IllegalArgumentException("a kept answer is an answer, not Asking")
            }
        }
    }

    private val latest = LatestAnswer<State?>(scope)
    private val stateNow = MutableStateFlow(kept?.state ?: State.Asking)
    private val refreshingNow = MutableStateFlow(false)
    private var answeredAt: Instant? = kept?.at

    /** True from a question until its answer; an answer that leads away from the home ends it too. */
    private var pending = false

    /** What the home shows now. */
    val state: StateFlow<State> = stateNow.asStateFlow()

    /** True while a new plan is made with the one before still in view. */
    val refreshing: StateFlow<Boolean> = refreshingNow.asStateFlow()

    init {
        if (kept == null) {
            ask()
        }
        scope.launch {
            changes.drop(1).collect { ask() }
        }
    }

    /**
     * Asks for the plan again, as on Refresh or a pull: a plan shown stays in view until the new one comes. While a
     * plan is asked already it does nothing, so the one on its way is not started over.
     */
    fun refresh() {
        when {
            pending -> return
            stateNow.value is State.Planned -> ask(keepShown = true)
            else -> ask()
        }
    }

    /**
     * Asks for the plan again at once, as after the owner changed what it starts from, such as the battery level: a
     * plan shown stays in view until the new one comes. Unlike [refresh], a plan asked already is asked anew, as it may
     * have been made before the change.
     */
    fun planAgain() {
        ask(keepShown = stateNow.value is State.Planned)
    }

    /**
     * Shows the one-off away periods as the engine stored them after the owner added or removed one, at once, then
     * asks for the plan again as [planAgain] does, the plan shown staying in view until the new one comes.
     *
     * @param periods the stored periods after the change, in order of their start
     */
    fun awayChanged(periods: List<OneOffAwayPeriod>) {
        (stateNow.value as? State.Planned)?.let { stateNow.value = State.Planned(it.dashboard.withAway(periods)) }
        planAgain()
    }

    /**
     * Tells that the owner came back to the app: a plan older than [STALE_AFTER] is asked again, unless one is asked
     * already.
     */
    fun returned() {
        val at = answeredAt ?: return
        if (Duration.between(at, clock.instant()) > STALE_AFTER) {
            refresh()
        }
    }

    /**
     * What the home shows now, for a home made again.
     *
     * @return the answer and when it came; null while the first is asked
     */
    fun kept(): Kept? = answeredAt?.let { at -> stateNow.value.takeIf { it != State.Asking }?.let { Kept(it, at) } }

    private fun ask(keepShown: Boolean = false) {
        if (keepShown) {
            refreshingNow.value = true
        } else {
            stateNow.value = State.Asking
            refreshingNow.value = false
        }
        pending = true
        latest.ask({ answer() }) { state ->
            pending = false
            refreshingNow.value = false
            if (state == null) {
                answeredAt = null
                lost()
            } else {
                stateNow.value = state
                answeredAt = clock.instant()
            }
        }
    }

    /** The home's next state; null when the app is not set up or its settings file cannot be used. */
    private suspend fun answer(): State? = try {
        when (val outlook = access.outlook().also { tell(it) }) {
            is Outlook.Planned -> if (outlook.days().isEmpty()) State.NoDays(Footer.of(outlook), outlook.zone())
                else State.Planned(Dashboard.of(outlook, access.awayPeriods(), ::providerName))
            is Outlook.NoForecast -> State.NoForecast(outlook.reason())
            is Outlook.NotSetUp, is Outlook.ConfigUnusable -> null
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: RuntimeException) {
        LOG.warn("The plan could not be made", failure)
        State.NoForecast("The plan could not be made.")
    }

    private suspend fun tell(outlook: Outlook) {
        try {
            seen(outlook)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: RuntimeException) {
            LOG.warn("The plan could not be passed on to the notifications", failure)
        }
    }

    /** A provider's name as the engine knows it; its id when the engine does not know it. */
    private fun providerName(id: ProviderId): String =
        access.providers().firstOrNull { it.id() == id }?.name() ?: id.value()
}
