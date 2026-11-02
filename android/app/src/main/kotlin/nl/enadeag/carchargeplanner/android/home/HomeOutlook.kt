package nl.enadeag.carchargeplanner.android.home

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import nl.enadeag.carchargeplanner.android.store.EngineAccess
import nl.enadeag.carchargeplanner.android.store.LatestAnswer
import nl.enadeag.carchargeplanner.engine.Outlook
import org.slf4j.LoggerFactory

/**
 * The plain home's plan: asked once when the home opens and again on Refresh, only the latest answer counting. A
 * settings file that is gone or cannot be used is handed on, so the app shows set-up or the file's problem instead.
 * Called from the main thread.
 *
 * @param access the engine's plan
 * @param scope where the plan is asked, the screen's
 * @param lost called when the outlook says the app is not set up or its settings file cannot be used
 */
class HomeOutlook(private val access: EngineAccess, scope: CoroutineScope, private val lost: () -> Unit) {

    private companion object {
        val LOG = LoggerFactory.getLogger(HomeOutlook::class.java)
    }

    /** What the home shows. */
    sealed interface State {

        /** The plan is being made, which may fetch a forecast first. */
        data object Asking : State

        /**
         * There is a plan.
         *
         * @property today what the home shows of it
         */
        data class Planned(val today: TodayPlan) : State

        /**
         * No forecast provider had a forecast, or the plan failed otherwise; the owner may try again.
         *
         * @property reason the engine's words, in English
         */
        data class NoForecast(val reason: String) : State
    }

    private val latest = LatestAnswer<Outlook?>(scope)
    private val stateNow = MutableStateFlow<State>(State.Asking)

    /** What the home shows now. */
    val state: StateFlow<State> = stateNow.asStateFlow()

    init {
        refresh()
    }

    /** Asks for the plan again, as on Refresh. */
    fun refresh() {
        stateNow.value = State.Asking
        latest.ask({ ask() }) { outlook ->
            when (outlook) {
                is Outlook.Planned -> stateNow.value = State.Planned(TodayPlan.of(outlook))
                is Outlook.NoForecast -> stateNow.value = State.NoForecast(outlook.reason())
                is Outlook.NotSetUp, is Outlook.ConfigUnusable -> lost()
                null -> stateNow.value = State.NoForecast("The plan could not be made.")
            }
        }
    }

    private suspend fun ask(): Outlook? = try {
        access.outlook()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: RuntimeException) {
        LOG.warn("The plan could not be made", failure)
        null
    }
}
