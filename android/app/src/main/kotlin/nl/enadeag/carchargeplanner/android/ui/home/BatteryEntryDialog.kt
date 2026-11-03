package nl.enadeag.carchargeplanner.android.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import nl.enadeag.carchargeplanner.android.R
import nl.enadeag.carchargeplanner.android.home.BatteryEntry
import nl.enadeag.carchargeplanner.android.ui.components.AppTextButton
import nl.enadeag.carchargeplanner.android.ui.components.EnglishDetails
import nl.enadeag.carchargeplanner.android.ui.components.EntryField
import nl.enadeag.carchargeplanner.android.ui.components.FieldKind

/**
 * The entry of the battery level the car shows, while it is open: Material's dialog with a number field, whose
 * keyboard's Done saves as Save does, and Cancel. The field takes the focus when the entry opens, again when Save
 * finds a problem, which shows under it, and when the question over the entry closes. A save that failed says so in the entry, with the English details, in a live
 * region read at once; Save shows its progress while it runs. Back, or a tap beside the dialog, asks "Discard
 * changes?" when a level was typed.
 *
 * @param entry what is typed, the save and what it came to
 */
@Composable
internal fun BatteryEntryDialog(entry: BatteryEntry) {
    val open by entry.open.collectAsStateWithLifecycle()
    if (!open) {
        return
    }
    val text by entry.text.collectAsStateWithLifecycle()
    val problems by entry.problems.collectAsStateWithLifecycle()
    val saving by entry.saving.collectAsStateWithLifecycle()
    val problemsShown by entry.problemsShown.collectAsStateWithLifecycle()
    val asked by entry.discardAsked.collectAsStateWithLifecycle()
    val busy = saving == BatteryEntry.SaveState.Saving
    val field = remember { FocusRequester() }
    AlertDialog(
        onDismissRequest = { entry.back() },
        title = {
            Text(stringResource(R.string.battery_entry_title), modifier = Modifier.semantics { heading() })
        },
        text = {
            // the dialog's own window: the field can take the focus only once that window has it, which comes after
            // the dialog is drawn, and again when the question over it closes
            val windowFocused = LocalWindowInfo.current.isWindowFocused
            LaunchedEffect(problemsShown, windowFocused) {
                if (windowFocused) {
                    field.requestFocus()
                }
            }
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.battery_entry_text))
                EntryField(stringResource(R.string.battery_entry_field), stringResource(R.string.battery_entry_hint),
                    text, { entry.edit(it) }, FieldKind.WHOLE, problems[BatteryEntry.Field.LEVEL], typical = false,
                    focus = field, imeAction = ImeAction.Done, onAction = { entry.save() })
                SaveFailure(saving)
            }
        },
        confirmButton = {
            AppTextButton(onClick = { entry.save() }, enabled = !busy) {
                if (busy) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Text(stringResource(R.string.settings_saving), modifier = Modifier.padding(start = 8.dp))
                } else {
                    Text(stringResource(R.string.action_save))
                }
            }
        },
        dismissButton = {
            AppTextButton(onClick = { entry.cancel() }, enabled = !busy) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
    if (asked) {
        DiscardDialog(entry)
    }
}

/** Why the save failed, with the English details, in a live region read at once; it stays while empty. */
@Composable
private fun SaveFailure(saving: BatteryEntry.SaveState) {
    // the live region stays while empty, so a failure that comes in later is read
    Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.semantics(mergeDescendants = true) {
        liveRegion = LiveRegionMode.Assertive
    }) {
        if (saving is BatteryEntry.SaveState.Failed) {
            Text(stringResource(R.string.battery_not_saved), color = MaterialTheme.colorScheme.error)
            EnglishDetails(saving.detail)
        }
    }
}

/** "Discard changes?" over the entry, as Settings asks it, with Keep editing and Discard drawn as a warning. */
@Composable
private fun DiscardDialog(entry: BatteryEntry) {
    AlertDialog(
        onDismissRequest = { entry.keepEditing() },
        title = { Text(stringResource(R.string.settings_discard_question)) },
        text = { Text(stringResource(R.string.battery_discard_text)) },
        confirmButton = {
            AppTextButton(onClick = { entry.discard() }) {
                Text(stringResource(R.string.settings_discard), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            AppTextButton(onClick = { entry.keepEditing() }) {
                Text(stringResource(R.string.settings_keep_editing))
            }
        },
    )
}
