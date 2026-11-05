package nl.enadeag.carchargeplanner.android.ui.home

import android.text.format.DateFormat
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
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TimePickerDialog
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.LocalTime
import nl.enadeag.carchargeplanner.android.R
import nl.enadeag.carchargeplanner.android.home.AwayEntry
import nl.enadeag.carchargeplanner.android.ui.components.AppOutlinedButton
import nl.enadeag.carchargeplanner.android.ui.components.AppTextButton
import nl.enadeag.carchargeplanner.android.ui.components.DiscardChangesDialog
import nl.enadeag.carchargeplanner.android.ui.components.SaveButton
import nl.enadeag.carchargeplanner.android.ui.components.SaveFailure

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
 * entry, with the English details, in a live region read at once. Save shows its progress while it runs. The content
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
    val busy = saving == AwayEntry.SaveState.Saving
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
            // the dialog's own window: a control can take the focus only once that window has it, which comes after
            // the dialog is drawn, and again when the picker or the question over it closes
            val windowFocused = LocalWindowInfo.current.isWindowFocused
            LaunchedEffect(windowFocused) {
                if (windowFocused) {
                    when (returnTo) {
                        Picked.START -> from
                        Picked.END -> until
                        null -> choice
                    }.requestFocus()
                }
            }
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Kinds(draft.kind, choice, enabled = !busy) {
                    // the times' buttons may go with the choice, so the focus comes back to the choice
                    returnTo = null
                    entry.choose(it)
                }
                if (draft.kind == AwayEntry.Kind.PART_OF_DAY) {
                    TimeButton(stringResource(R.string.one_off_from), words.time(draft.start), from, null, !busy) {
                        returnTo = Picked.START
                        picking = Picked.START
                    }
                    TimeButton(stringResource(R.string.one_off_until), words.time(draft.end), until, problemText,
                        !busy) {
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
                SaveFailure(stringResource(R.string.one_off_not_saved), (saving as? AwayEntry.SaveState.Failed)?.detail)
            }
        },
        confirmButton = { SaveButton(busy, { entry.save() }) },
        dismissButton = {
            AppTextButton(onClick = { entry.cancel() }, enabled = !busy) {
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

/**
 * One of part of the day's times: a button with its name and the time, which opens the picker; it may take the focus
 * also in touch mode, so the focus comes back to it when the picker closes.
 */
@Composable
private fun TimeButton(name: String, time: String, focus: FocusRequester, problem: String?, enabled: Boolean,
    onClick: () -> Unit) {
    AppOutlinedButton(onClick = onClick, enabled = enabled,
        modifier = Modifier.fillMaxWidth().focusProperties { canFocus = true }.focusRequester(focus).semantics {
            if (problem != null) {
                error(problem)
            }
        }) {
        Column(Modifier.fillMaxWidth()) {
            Text(name, style = MaterialTheme.typography.labelMedium)
            Text(time, style = MaterialTheme.typography.titleMedium)
        }
    }
}

/**
 * Material's time picker in its dialog, on the phone's 12- or 24-hour clock, with OK and Cancel; Back, or a tap beside
 * it, cancels. A text button switches between the dial and typing the time, which stays as it was when the phone
 * turns. Its content scrolls, so OK stays reachable at a large font size.
 *
 * @param title which time it is, the dialog's heading
 * @param time the time it opens on
 * @param chosen gets the time on OK
 * @param closed called as it closes, after OK or not
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeDialog(title: String, time: LocalTime, chosen: (LocalTime) -> Unit, closed: () -> Unit) {
    val state = rememberTimePickerState(time.hour, time.minute, DateFormat.is24HourFormat(LocalContext.current))
    var typing by rememberSaveable { mutableStateOf(false) }
    TimePickerDialog(
        onDismissRequest = closed,
        title = { Text(title, modifier = Modifier.semantics { heading() }) },
        confirmButton = {
            AppTextButton(onClick = {
                chosen(LocalTime.of(state.hour, state.minute))
                closed()
            }) {
                Text(stringResource(R.string.action_ok))
            }
        },
        dismissButton = {
            AppTextButton(onClick = closed) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    ) {
        Column(Modifier.verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
            if (typing) TimeInput(state) else TimePicker(state)
            // Material's mode toggle is an icon; this one says in words what it switches to. It sits under the
            // picker, not in the dialog's `modeToggleButton` slot: that slot shares one row, as wide as the dial, with
            // Cancel and OK, which has no room for all three at a large font size or in Dutch
            AppTextButton(onClick = { typing = !typing }) {
                Text(stringResource(if (typing) R.string.one_off_clock else R.string.one_off_keyboard))
            }
        }
    }
}

/** The text of why Save refused the period. */
private fun problemId(problem: AwayEntry.Problem): Int = when (problem) {
    AwayEntry.Problem.END_NOT_AFTER_START -> R.string.one_off_end_before_start
    AwayEntry.Problem.PASSED -> R.string.one_off_passed
}
