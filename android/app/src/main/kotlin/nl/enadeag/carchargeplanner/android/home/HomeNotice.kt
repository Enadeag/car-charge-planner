package nl.enadeag.carchargeplanner.android.home

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import nl.enadeag.carchargeplanner.android.store.SaveOutcome
import nl.enadeag.carchargeplanner.android.store.Saves
import nl.enadeag.carchargeplanner.android.store.StartState

/**
 * The outcome of a Settings save that no screen showed yet, such as one that ended after the owner left Settings: the
 * home shows it until the owner closes it, so a failed save is never silent (#315). A settings file that turned out
 * unusable, or of a newer version, is handed on, so the app shows its screen instead. Called from the main thread.
 *
 * @param saves the app's saves, whose outcomes the home takes while it is shown
 * @param scope where the outcomes are taken, the home's
 * @param unusable gets the state of a settings file a save found unusable
 */
class HomeNotice(private val saves: Saves, scope: CoroutineScope,
    private val unusable: (StartState.WithoutSettings) -> Unit) {

    private val noticeNow = MutableStateFlow<SaveOutcome?>(null)

    /** The outcome the home shows; null for none. Never [SaveOutcome.SettingsUnusable], which leads to its screen. */
    val notice: StateFlow<SaveOutcome?> = noticeNow.asStateFlow()

    init {
        scope.launch {
            saves.lastOutcome.filterNotNull().collect { show(it) }
        }
    }

    /** Closes the outcome shown, as the owner read it. */
    fun close() {
        noticeNow.value = null
    }

    private fun show(outcome: SaveOutcome) {
        if (outcome !is SaveOutcome.SettingsUnusable) {
            noticeNow.value = outcome
        }
        saves.shown(outcome)
        if (outcome is SaveOutcome.SettingsUnusable) {
            unusable(outcome.state)
        }
    }
}
