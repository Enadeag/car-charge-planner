package nl.enadeag.carchargeplanner.android.setup

import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import nl.enadeag.carchargeplanner.android.store.EngineAccess
import nl.enadeag.carchargeplanner.android.store.LatestAnswer
import nl.enadeag.carchargeplanner.engine.config.ProviderId
import nl.enadeag.carchargeplanner.engine.forecast.Place
import nl.enadeag.carchargeplanner.engine.forecast.PlaceResults
import org.slf4j.LoggerFactory

/**
 * Set-up's place search: what the owner typed, which survives the system ending the app, and the places found for it,
 * which do not. A search runs only when the owner asks for it, never per keystroke, and only the latest one counts.
 * The text goes to the provider's place search, with the language the names should come in.
 *
 * @param handle where the typed text and the label of the place chosen are kept
 * @param access the engine's place search
 * @param scope where a search runs, the screen's
 * @param provider the forecast provider whose place search is used
 * @param prefix sets this search's keys in the handle apart, as set-up and Settings share the screen's handle
 * @param language the app's language now, as the place search takes it, such as "nl"
 */
class PlaceSearch(private val handle: SavedStateHandle, private val access: EngineAccess, scope: CoroutineScope,
    private val provider: ProviderId, prefix: String = "setUp.place", private val language: () -> String) {

    companion object {

        /** The most places shown for one search. */
        const val MOST_RESULTS = 8

        /**
         * The longest text kept, far longer than any place's name; what is typed beyond it is left out, so the saved
         * state and the request to the provider stay small.
         */
        const val LONGEST_TEXT = 100

        private val LOG = LoggerFactory.getLogger(PlaceSearch::class.java)
    }

    /**
     * A place found, as the list shows it.
     *
     * @property place the place
     * @property label its name, region and country; with its finer area too when another place found reads alike
     */
    data class Result(val place: Place, val label: String)

    /** What the search shows below the field. */
    sealed interface State {

        /** Nothing was searched for since the text or the place last changed. */
        data object Idle : State

        /** The place search is being asked. */
        data object Searching : State

        /**
         * The search worked.
         *
         * @property text what was searched for
         * @property results at most [MOST_RESULTS] places, in the order the service ranks them; empty when none matched
         */
        data class Found(val text: String, val results: List<Result>) : State

        /** The forecast provider has no place search, so the owner types the coordinates. */
        data object NoSearch : State

        /**
         * The search did not work.
         *
         * @property problem why, such as too few characters or no network
         */
        data class Failed(val problem: PlaceResults.Problem) : State
    }

    private val textKey = "$prefix.text"
    private val chosenKey = "$prefix.chosen"
    private val latest = LatestAnswer<State>(scope)
    private val stateNow = MutableStateFlow<State>(State.Idle)
    private val chosenNow = MutableStateFlow(handle.get<String>(chosenKey))
    private val choicesNow = MutableStateFlow(0)

    /** What the search shows now. */
    val state: StateFlow<State> = stateNow.asStateFlow()

    /** The label of the place chosen from the results; null when none was, or coordinates were typed since. */
    val chosen: StateFlow<String?> = chosenNow.asStateFlow()

    /**
     * How many times a place was chosen since this search was made, so the screen can tell a new choice, whose
     * confirmation then takes the focus, from one it showed before, as before turning the phone.
     */
    val choices: StateFlow<Int> = choicesNow.asStateFlow()

    /**
     * What the owner typed.
     *
     * @return the text, empty before anything was typed
     */
    fun text(): String = handle.get<String>(textKey).orEmpty()

    /**
     * Puts what the owner typed, up to [LONGEST_TEXT] characters; nothing is searched for until they ask.
     *
     * @param text what the field holds now
     */
    fun edit(text: String) {
        handle[textKey] = text.take(LONGEST_TEXT)
    }

    /** Searches for the text typed, dropping a search that runs. */
    fun search() {
        val text = text().trim()
        stateNow.value = State.Searching
        latest.ask({ ask(text) }) { stateNow.value = it }
    }

    /** Drops a search that runs, as its page closes; what was typed stays. */
    fun cancel() {
        latest.cancel()
        stateNow.value = State.Idle
    }

    /**
     * Keeps the place the owner chose, whose label the step then shows, also in the field; the results go.
     *
     * @param result the place chosen, from [State.Found]
     */
    fun choose(result: Result) {
        cancel()
        edit(result.label)
        keepChosen(result.label)
        choicesNow.value++
    }

    /**
     * Forgets the place chosen, as the owner typed coordinates of their own; the field, which showed its label, is
     * emptied too, so it never names a place the coordinates are not of.
     */
    fun forgetChosen() {
        if (chosenNow.value != null) {
            keepChosen(null)
            handle.remove<String>(textKey)
        }
    }

    private fun keepChosen(label: String?) {
        if (label == null) handle.remove<String>(chosenKey) else handle[chosenKey] = label
        chosenNow.value = label
    }

    private suspend fun ask(text: String): State {
        val answer = try {
            access.searchPlaces(provider, text, language())
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: RuntimeException) {
            LOG.warn("The place search failed", failure)
            PlaceResults.Failed(PlaceResults.Problem.UNAVAILABLE)
        }
        return when (answer) {
            null -> State.NoSearch
            is PlaceResults.Failed -> State.Failed(answer.problem())
            is PlaceResults.Found -> State.Found(text, labelled(answer.places().take(MOST_RESULTS)))
        }
    }

    private fun labelled(places: List<Place>): List<Result> = places.map { place ->
        val alike = places.count { it.label() == place.label() } > 1
        Result(place, if (alike) place.detailedLabel() else place.label())
    }
}
