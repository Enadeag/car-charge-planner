package nl.enadeag.carchargeplanner.android.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import nl.enadeag.carchargeplanner.android.R
import nl.enadeag.carchargeplanner.android.home.BatteryEntry
import nl.enadeag.carchargeplanner.android.home.SaveState
import nl.enadeag.carchargeplanner.android.ui.components.AppTextButton
import nl.enadeag.carchargeplanner.android.ui.components.DiscardChangesDialog
import nl.enadeag.carchargeplanner.android.ui.components.EntryField
import nl.enadeag.carchargeplanner.android.ui.components.FieldKind
import nl.enadeag.carchargeplanner.android.ui.components.FocusOnWindow
import nl.enadeag.carchargeplanner.android.ui.components.SaveAction
import nl.enadeag.carchargeplanner.android.ui.components.SaveFailure

/**
 * The entry of the battery level the car shows, while it is open: Material's dialog with a number field, whose
 * keyboard's Done saves as Save does, and Cancel. The field takes the focus when the entry opens, again when Save
 * finds a problem, which shows under it in a live region, and when the question over the entry closes. A save that
 * failed says so in the entry, with the English details, in a live region read at once; while it runs, Save is held,
 * keeping the focus, and shows its progress, and the field takes no typing. Back, or a tap beside the dialog, asks
 * "Discard changes?" when a level was typed.
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
    val refusals by entry.refusals.collectAsStateWithLifecycle()
    val asked by entry.discardAsked.collectAsStateWithLifecycle()
    val held by entry.busy.collectAsStateWithLifecycle()
    val savingNow = saving == SaveState.Saving
    val field = remember { FocusRequester() }
    AlertDialog(
        onDismissRequest = { entry.back() },
        title = {
            Text(stringResource(R.string.battery_entry_title), modifier = Modifier.semantics { heading() })
        },
        text = {
            // in the dialog's own window, also when the question over it closes
            FocusOnWindow(refusals) { field }
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.battery_entry_text))
                EntryField(stringResource(R.string.battery_entry_field), stringResource(R.string.battery_entry_hint),
                    text, { entry.edit(it) }, FieldKind.WHOLE, problems[BatteryEntry.Field.LEVEL], typical = false,
                    focus = field, imeAction = ImeAction.Done, onAction = { entry.save() }, announceProblem = true,
                    readOnly = savingNow)
                SaveFailure(stringResource(R.string.battery_not_saved), (saving as? SaveState.Failed)?.detail)
            }
        },
        confirmButton = { SaveAction(saving = savingNow, held = held) { entry.save() } },
        dismissButton = {
            AppTextButton(onClick = { entry.cancel() }, enabled = !savingNow) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
    if (asked) {
        DiscardChangesDialog(stringResource(R.string.battery_discard_text), entry::keepEditing, entry::discard)
    }
}
