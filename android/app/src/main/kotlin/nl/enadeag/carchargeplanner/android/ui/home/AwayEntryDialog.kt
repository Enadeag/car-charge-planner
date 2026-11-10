package nl.enadeag.carchargeplanner.android.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import nl.enadeag.carchargeplanner.android.R
import nl.enadeag.carchargeplanner.android.home.AwayEntry
import nl.enadeag.carchargeplanner.android.home.SaveState
import nl.enadeag.carchargeplanner.android.ui.components.AppTextButton
import nl.enadeag.carchargeplanner.android.ui.components.DiscardChangesDialog
import nl.enadeag.carchargeplanner.android.ui.components.FocusOnWindow
import nl.enadeag.carchargeplanner.android.ui.components.SaveAction
import nl.enadeag.carchargeplanner.android.ui.components.SaveFailure
import nl.enadeag.carchargeplanner.android.ui.components.TimeButton
import nl.enadeag.carchargeplanner.android.ui.components.TimeDialog

/** Which of the entry's two times the picker is open for. */
private enum class Picked {
    /** The time the car leaves. */
    START,

    /** The time it is back. */
    END,
}

/**
 * The entry of a one-off away period, while it is open: Material's dialog with the day in its title, the choice of all
 * day or part of it, and for part of the day the two times, each a button that opens Material's time picker on the
 * phone's 12- or 24-hour clock, as a dial or typed; then Save and Cancel. The choice takes the focus when the entry opens, a time's button
 * when its picker closes, and what had it before when the question over the entry closes. Why Save refused the period
 * shows under the end time, or under the choice for all day, in a polite live region; a save that failed says so in the
 * entry, with the English details, in a live region read at once. While a change of the away periods runs, Save is
 * held, keeping the focus, and while its own save runs it shows its progress. The content
 * scrolls, so it stays reachable at a large font size. Back, or a tap beside the dialog, asks "Discard changes?" when
 * the choice or a time changed.
 *
 * @param entry what is entered, the save and what it came to
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AwayEntryDialog(entry: AwayEntry) {
    val shown by entry.draft.collectAsStateWithLifecycle()
    val draft = shown ?: return
    val problem by entry.problem.collectAsStateWithLifecycle()
    val saving by entry.saving.collectAsStateWithLifecycle()
    val asked by entry.discardAsked.collectAsStateWithLifecycle()
    val held by entry.busy.collectAsStateWithLifecycle()
    val savingNow = saving == SaveState.Saving
    val words = dashboardWords(draft.zone)
    var picking by rememberSaveable { mutableStateOf<Picked?>(null) }
    var returnTo by rememberSaveable { mutableStateOf<Picked?>(null) }
    val choice = remember { FocusRequester() }
    val from = remember { FocusRequester() }
    val until = remember { FocusRequester() }
    val problemText = problem?.let { stringResource(problemId(it)) }
    AlertDialog(
        onDismissRequest = { entry.back() },
        title = {
            Text(stringResource(R.string.one_off_entry_title, words.day(draft.day)),
                modifier = Modifier.semantics { heading() })
        },
        text = {
            // in the dialog's own window, also when the picker or the question over it closes
            FocusOnWindow {
                when (returnTo) {
                    Picked.START -> from
                    Picked.END -> until
                    null -> choice
                }
            }
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Kinds(draft.kind, choice, enabled = !savingNow) {
                    // the times' buttons may go with the choice, so the focus comes back to the choice
                    returnTo = null
                    entry.choose(it)
                }
                if (draft.kind == AwayEntry.Kind.PART_OF_DAY) {
                    TimeButton(stringResource(R.string.one_off_from), words.time(draft.start), from, null, !savingNow) {
                        returnTo = Picked.START
                        picking = Picked.START
                    }
                    TimeButton(stringResource(R.string.one_off_until), words.time(draft.end), until, problemText,
                        !savingNow) {
                        returnTo = Picked.END
                        picking = Picked.END
                    }
                }
                // under the end time, or the choice for all day; the live region stays while empty, so a refusal
                // that comes in is read while the focus stays on Save
                Column(Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }) {
                    problemText?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
                SaveFailure(stringResource(R.string.one_off_not_saved), (saving as? SaveState.Failed)?.detail)
            }
        },
        confirmButton = { SaveAction(saving = savingNow, held = held) { entry.save() } },
        dismissButton = {
            AppTextButton(onClick = { entry.cancel() }, enabled = !savingNow) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
    when (picking) {
        Picked.START -> TimeDialog(stringResource(R.string.one_off_from), draft.start, { entry.start(it) }) {
            picking = null
        }
        Picked.END -> TimeDialog(stringResource(R.string.one_off_until), draft.end, { entry.end(it) }) {
            picking = null
        }
        null -> Unit
    }
    if (asked) {
        DiscardChangesDialog(stringResource(R.string.one_off_discard_text), entry::keepEditing, entry::discard)
    }
}

/** The choice of all day or part of it, as radio buttons a screen reader reads as a group; the chosen takes focus. */
@Composable
private fun Kinds(chosen: AwayEntry.Kind, focus: FocusRequester, enabled: Boolean, onChoose: (AwayEntry.Kind) -> Unit) {
    Column(Modifier.selectableGroup()) {
        AwayEntry.Kind.entries.forEach { kind ->
            val selected = kind == chosen
            // it may take the focus also in touch mode, where a radio button takes none, so the focus starts there
            val focused = if (selected) Modifier.focusProperties { canFocus = true }.focusRequester(focus) else Modifier
            Row(verticalAlignment = Alignment.CenterVertically,
                modifier = focused.fillMaxWidth().heightIn(min = 48.dp)
                    .selectable(selected = selected, enabled = enabled, role = Role.RadioButton,
                        onClick = { onChoose(kind) })) {
                RadioButton(selected = selected, onClick = null, enabled = enabled)
                Text(stringResource(if (kind == AwayEntry.Kind.ALL_DAY) R.string.one_off_whole_day
                    else R.string.one_off_part_of_day), modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}

/** The text of why Save refused the period. */
private fun problemId(problem: AwayEntry.Problem): Int = when (problem) {
    AwayEntry.Problem.END_NOT_AFTER_START -> R.string.one_off_end_before_start
    AwayEntry.Problem.PASSED -> R.string.one_off_passed
}
