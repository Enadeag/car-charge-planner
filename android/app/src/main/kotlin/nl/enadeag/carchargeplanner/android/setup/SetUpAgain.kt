package nl.enadeag.carchargeplanner.android.setup

import java.io.IOException
import java.nio.file.Path
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import nl.enadeag.carchargeplanner.android.store.EngineAccess
import nl.enadeag.carchargeplanner.android.store.StartState
import org.slf4j.LoggerFactory

/**
 * "Set up again" for a settings file that cannot be used: after the owner confirms, the file is moved aside to
 * `engine.json.damaged-<time>`, so what it held is kept, and set-up starts. Only for an unusable file: one of a newer
 * version is never moved, the owner updates the app instead.
 *
 * @param unusable what the start found, whose details the screen shows
 * @param access the engine's move of the file
 * @param scope the app's scope, so turning the phone does not stop the move halfway
 */
class SetUpAgain(val unusable: StartState.Unusable, private val access: EngineAccess,
    private val scope: CoroutineScope) {

    private companion object {
        val LOG = LoggerFactory.getLogger(SetUpAgain::class.java)
    }

    /** Where "Set up again" is. */
    sealed interface State {

        /** The details and the offer to set up again. */
        data object Offered : State

        /** The owner is asked whether to move the file aside and start over. */
        data object Confirming : State

        /** The file is being moved. */
        data object Moving : State

        /**
         * The file is kept aside; set-up starts.
         *
         * @property keptAs where the file is now, for the message that names it
         */
        data class Moved(val keptAs: Path) : State

        /**
         * The file could not be moved, or the move failed otherwise, and is left as it was; the owner may try again.
         *
         * @property detail the system's or the engine's words, in English
         */
        data class Failed(val detail: String) : State
    }

    private val stateNow = MutableStateFlow<State>(State.Offered)

    /** Where it is now. */
    val state: StateFlow<State> = stateNow.asStateFlow()

    /** Asks the owner to confirm, after they chose "Set up again". */
    fun ask() {
        if (stateNow.value == State.Offered || stateNow.value is State.Failed) {
            stateNow.value = State.Confirming
        }
    }

    /** Back to the offer, without moving anything. */
    fun cancel() {
        if (stateNow.value == State.Confirming) {
            stateNow.value = State.Offered
        }
    }

    /**
     * Moves the file aside, as the owner confirmed.
     *
     * @return the move; null when the owner was not asked to confirm, so nothing is moved
     */
    fun confirm(): Job? {
        if (stateNow.value != State.Confirming) {
            return null
        }
        stateNow.value = State.Moving
        return scope.launch {
            stateNow.value = try {
                State.Moved(access.setSettingsAside())
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: IOException) {
                State.Failed(failure.message ?: failure.javaClass.simpleName)
            } catch (failure: RuntimeException) {
                LOG.warn("The settings file could not be set aside", failure)
                State.Failed(failure.message ?: failure.javaClass.simpleName)
            }
        }
    }
}
