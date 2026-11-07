package nl.enadeag.carchargeplanner.android.ui.home

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalResources
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import nl.enadeag.carchargeplanner.android.R
import nl.enadeag.carchargeplanner.android.ui.components.FailureVisuals

/**
 * The row whose Remove was pressed, a one-off away period or a trip, and the rows as they were then, which drawing
 * never reads, so the focus moves on once the list no longer has it; a Remove that failed forgets it, as its row stays
 * in the list.
 *
 * @param K what names a row
 * @property row the row going; null when none is
 * @property rows the rows before it went, in order
 */
internal class Going<K>(var row: K? = null, var rows: List<K> = listOf())

/**
 * The row an Undo is adding back, a one-off away period or a trip, which drawing never reads: the list on the page in
 * view puts the focus on it once it shows; an Undo that failed forgets it.
 *
 * @param K what names a row
 * @property row the row; null when no Undo is under way
 */
internal class Restoring<K>(var row: K? = null)

/**
 * What a change to a list of the dashboard came to, as its snackbar says it.
 *
 * @param K what names a row of the list
 */
internal sealed interface Said<out K> {

    /**
     * Something was added or changed.
     *
     * @property text the snackbar's sentence, such as "Saved: Tomorrow, all day."
     */
    data class Saved(val text: String) : Said<Nothing>

    /**
     * A row was removed; Undo adds it back.
     *
     * @param K what names a row
     * @property text the snackbar's sentence, such as "Removed: Tomorrow, all day."
     * @property row the row removed, which Undo adds back
     */
    data class Removed<K>(val text: String, val row: K) : Said<K>

    /**
     * A Remove failed, so the row stays.
     *
     * @property text the snackbar's sentence
     * @property detail the system's or the engine's words, in English
     */
    data class NotRemoved(val text: String, val detail: String) : Said<Nothing>

    /**
     * A change or an Undo failed, so the list stays as it was.
     *
     * @property text the snackbar's sentence
     * @property detail the system's or the engine's words, in English
     */
    data class NotSaved(val text: String, val detail: String) : Said<Nothing>
}

/**
 * What the changes to a list of the dashboard came to, the one-off away periods' or the trips', each said once in a
 * snackbar, which a screen reader reads when it comes: an added or changed row; a removed one, with Undo, which adds it
 * back; a Remove or another change that failed, with the English details and a button to close it.
 *
 * @param K what names a row
 * @param N what a change comes to
 * @param notices what the last change came to, until it was said
 * @param shown tells the changes that the notice was said
 * @param snackbars where the snackbar shows
 * @param going the Remove that was pressed, forgotten when it failed
 * @param restoring gets the row an Undo adds back, so the list puts the focus on it, forgotten when it failed
 * @param said what a notice says
 * @param undo adds a removed row back, as the snackbar's Undo
 */
@Composable
internal fun <K, N : Any> ChangeOutcome(notices: StateFlow<N?>, shown: () -> Unit, snackbars: SnackbarHostState,
    going: Going<K>, restoring: Restoring<K>, said: (N) -> Said<K>, undo: (K) -> Unit) {
    val resources = LocalResources.current
    val words by rememberUpdatedState(said)
    LaunchedEffect(notices) {
        notices.collect { notice ->
            if (notice == null) {
                return@collect
            }
            shown()
            val saying = words(notice)
            // a change that failed leaves the list as it was, so no later change of the list moves the focus for it
            when (saying) {
                is Said.NotRemoved -> going.row = null
                is Said.NotSaved -> restoring.row = null
                else -> Unit
            }
            snackbars.currentSnackbarData?.dismiss()
            launch {
                when (saying) {
                    is Said.Saved -> snackbars.showSnackbar(saying.text)
                    is Said.Removed -> {
                        // it stays until the next notice or Close: Material takes one with an action away after about
                        // 10 s, also with TalkBack, which is too soon to find Undo
                        val result = snackbars.showSnackbar(saying.text, resources.getString(R.string.action_undo),
                            withDismissAction = true, duration = SnackbarDuration.Indefinite)
                        if (result == SnackbarResult.ActionPerformed) {
                            restoring.row = saying.row
                            undo(saying.row)
                        }
                    }
                    is Said.NotRemoved -> snackbars.showSnackbar(FailureVisuals(saying.text, saying.detail))
                    is Said.NotSaved -> snackbars.showSnackbar(FailureVisuals(saying.text, saying.detail))
                }
            }
        }
    }
}
