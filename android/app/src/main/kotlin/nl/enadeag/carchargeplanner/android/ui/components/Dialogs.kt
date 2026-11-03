package nl.enadeag.carchargeplanner.android.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import nl.enadeag.carchargeplanner.android.R

/**
 * "Discard changes?", with Keep editing and Discard, which is named for what it does and drawn as a warning. Back, or
 * a tap beside it, keeps editing.
 *
 * @param text what would be thrown away, under the question
 * @param onKeep stays with what was typed
 * @param onDiscard throws it away
 */
@Composable
internal fun DiscardChangesDialog(text: String, onKeep: () -> Unit, onDiscard: () -> Unit) {
    AlertDialog(
        onDismissRequest = onKeep,
        title = { Text(stringResource(R.string.discard_question)) },
        text = { Text(text) },
        confirmButton = {
            AppTextButton(onClick = onDiscard) {
                Text(stringResource(R.string.discard), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            AppTextButton(onClick = onKeep) {
                Text(stringResource(R.string.keep_editing))
            }
        },
    )
}
