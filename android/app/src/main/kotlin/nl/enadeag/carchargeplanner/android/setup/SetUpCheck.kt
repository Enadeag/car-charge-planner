package nl.enadeag.carchargeplanner.android.setup

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import nl.enadeag.carchargeplanner.android.store.EngineAccess
import nl.enadeag.carchargeplanner.android.store.LatestAnswer
import nl.enadeag.carchargeplanner.engine.config.EngineConfig
import nl.enadeag.carchargeplanner.engine.forecast.ConnectionResult

/**
 * Set-up's last step: one forecast from the primary provider for the system as entered, so the owner knows it works
 * before Finish; a failure only says so and never blocks Finish. Only the latest answer counts. What the provider
 * answered is never kept in the saved state, so after the system ended the app the step asks again.
 *
 * @param access the engine's connection check
 * @param scope where the check runs, the screen's
 */
class SetUpCheck(private val access: EngineAccess, scope: CoroutineScope) {

    /** Where the check is. */
    sealed interface State {

        /** Not asked yet. */
        data object NotAsked : State

        /** The provider is being asked. */
        data object Asking : State

        /**
         * The provider gave a forecast.
         *
         * @property days how many days it covers
         */
        data class Works(val days: Int) : State

        /**
         * It did not; Finish is still possible.
         *
         * @property detail the engine's words, in English
         */
        data class Failed(val detail: String) : State
    }

    private val latest = LatestAnswer<ConnectionResult>(scope)
    private val stateNow = MutableStateFlow<State>(State.NotAsked)

    /** Where the check is now. */
    val state: StateFlow<State> = stateNow.asStateFlow()

    /**
     * Asks the config's primary provider for one forecast, nothing saved; a check that runs is dropped.
     *
     * @param config the whole config as entered
     */
    fun check(config: EngineConfig) {
        stateNow.value = State.Asking
        val primary = config.forecast().primary()
        latest.ask({ access.checkConnection(primary, config.system(), config.forecast().settingsOf(primary)) }) {
            stateNow.value = if (it.worked()) State.Works(it.days()) else State.Failed(it.message())
        }
    }

    /** Stops a check that runs, as the owner went back; its answer is dropped and nothing is asked. */
    fun cancel() {
        latest.cancel()
        stateNow.value = State.NotAsked
    }
}
