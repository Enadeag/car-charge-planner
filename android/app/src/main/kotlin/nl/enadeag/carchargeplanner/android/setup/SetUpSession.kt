package nl.enadeag.carchargeplanner.android.setup

import androidx.lifecycle.SavedStateHandle
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import nl.enadeag.carchargeplanner.android.form.LocationForm
import nl.enadeag.carchargeplanner.android.form.NumberText
import nl.enadeag.carchargeplanner.android.form.forget
import nl.enadeag.carchargeplanner.android.store.EngineAccess
import nl.enadeag.carchargeplanner.android.store.SaveOutcome
import nl.enadeag.carchargeplanner.android.store.Saves

/**
 * Set-up as its screen goes through it: the steps and their forms, the place search, the check of the forecast on the
 * last step, and Finish with what its save came to. The check runs on entering the last step, also after the system
 * ended the app there, and Back stops it. Back waits while Finish saves. Called from the main thread.
 *
 * @param handle the set-up screen's saved state
 * @param numbers how the screen's language writes numbers, which follows a change of the app's language
 * @param phoneZone the phone's time zone as it is now: the home has it until a place is chosen, and typed coordinates
 *     get it
 * @param language the app's language now, as the place search takes it, such as "nl"
 * @param access the engine's place search and connection check
 * @param saves the app's saves, which outlive the screen; the session shows each outcome they give
 * @param scope where the search and the check run, the screen's
 * @param ended gets a save's outcome that ends set-up: [SaveOutcome.Saved], or a settings file that cannot be used
 */
class SetUpSession(handle: SavedStateHandle, numbers: NumberText, phoneZone: () -> ZoneId, language: () -> String,
    private val access: EngineAccess, private val saves: Saves, scope: CoroutineScope,
    private val ended: (SaveOutcome) -> Unit) {

    companion object {
        /** Every key of set-up in the handle: the step, the place search and the forms' texts. */
        private val KEYS = listOf("setUp") + SetUpFlow.PREFIXES

        /**
         * Forgets everything set-up kept in the handle, as it ends, so a later set-up starts on its welcome with its
         * own draft, and Settings, whose forms share the prefixes, shows nothing of it.
         *
         * @param handle the screen's saved state
         */
        fun forget(handle: SavedStateHandle) = handle.forget(KEYS)
    }

    /** Where Finish is. */
    sealed interface Finishing {

        /** Not pressed, or its save came to nothing that stays on the screen. */
        data object Idle : Finishing

        /** The whole config is being saved; Finish waits. */
        data object Saving : Finishing

        /**
         * The save failed and nothing was stored; the owner may try again.
         *
         * @property detail the system's or the engine's words, in English
         */
        data class Failed(val detail: String) : Finishing
    }

    private val draft = SetUpFlow.draft(phoneZone())

    /** The steps and their forms. */
    val flow = SetUpFlow(handle, numbers, draft, phoneZone)

    /** The check of the forecast on the last step. */
    val check = SetUpCheck(access, scope)

    /** The place search of the location step, with the primary provider's search. */
    val places = PlaceSearch(handle, access, scope, draft.forecast().primary(), language = language)

    private val finishingNow = MutableStateFlow<Finishing>(Finishing.Idle)
    private val refusedNow = MutableStateFlow(false)
    private val problemsShownNow = MutableStateFlow(0)

    /** Where Finish is now. */
    val finishing: StateFlow<Finishing> = finishingNow.asStateFlow()

    /** True after Finish went back to a step with a refused value, until the owner leaves that step. */
    val refused: StateFlow<Boolean> = refusedNow.asStateFlow()

    /** How many times Next or Finish showed problems, so the step can move the focus to the first. */
    val problemsShown: StateFlow<Int> = problemsShownNow.asStateFlow()

    init {
        if (flow.step.value == SetUpStep.CHECK) {
            check.check(flow.config())
        }
        scope.launch {
            saves.lastOutcome.filterNotNull().collect { show(it) }
        }
    }

    /**
     * The name of the forecast provider set-up asks, for the welcome and the check.
     *
     * @return such as "Open-Meteo"
     */
    fun providerName(): String {
        val primary = draft.forecast().primary()
        return access.providers().firstOrNull { it.id() == primary }?.name() ?: primary.value()
    }

    /**
     * Checks the step's values and goes on when none is refused; on to the last step, the check starts.
     *
     * @return true when it went on; false when a value is refused, whose problems show
     */
    fun next(): Boolean {
        refusedNow.value = false
        if (!flow.next()) {
            problemsShownNow.value++
            return false
        }
        if (flow.step.value == SetUpStep.CHECK) {
            check.check(flow.config())
        }
        return true
    }

    /**
     * Goes to the step before, keeping what was entered; a check that runs stops. While Finish saves, it waits.
     *
     * @return false on the first step, where Back leaves set-up
     */
    fun back(): Boolean {
        if (finishingNow.value == Finishing.Saving) {
            return true
        }
        if (flow.step.value == SetUpStep.CHECK) {
            check.cancel()
            finishingNow.value = Finishing.Idle
        }
        refusedNow.value = false
        return flow.back()
    }

    /** Asks for the forecast again, with the values as entered now. */
    fun checkAgain() = check.check(flow.config())

    /**
     * Saves the whole config, once; a refused value sends the owner back to its step, where the step says why.
     */
    fun finish() {
        if (finishingNow.value == Finishing.Saving) {
            return
        }
        // before the save starts, as its outcome may come at once
        finishingNow.value = Finishing.Saving
        if (flow.finish(saves) == null) {
            check.cancel()
            finishingNow.value = Finishing.Idle
            refusedNow.value = true
            problemsShownNow.value++
        }
    }

    /**
     * Takes a place the owner chose from the search: its coordinates and time zone go into the location.
     *
     * @param result the place, as the search showed it
     */
    fun choose(result: PlaceSearch.Result) {
        flow.location.choose(result.place)
        places.choose(result)
    }

    /**
     * Puts a coordinate the owner typed, so no place from the search is chosen any longer.
     *
     * @param field the latitude or the longitude
     * @param text what the field holds now
     */
    fun typeCoordinate(field: LocationForm.Field, text: String) {
        flow.location.edit(field, text)
        places.forgetChosen()
    }

    private fun show(outcome: SaveOutcome) {
        when (outcome) {
            is SaveOutcome.Failed -> finishingNow.value = Finishing.Failed(outcome.detail)
            SaveOutcome.KeysNotSaved -> finishingNow.value = Finishing.Failed("The API keys could not be stored.")
            SaveOutcome.Saved, is SaveOutcome.SettingsUnusable -> finishingNow.value = Finishing.Idle
        }
        saves.shown(outcome)
        if (outcome == SaveOutcome.Saved || outcome is SaveOutcome.SettingsUnusable) {
            ended(outcome)
        }
    }
}
