package nl.enadeag.carchargeplanner.android.ui.start

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import nl.enadeag.carchargeplanner.android.R
import nl.enadeag.carchargeplanner.android.setup.SetUpAgain
import nl.enadeag.carchargeplanner.android.ui.components.EnglishDetails
import nl.enadeag.carchargeplanner.android.ui.components.Heading
import nl.enadeag.carchargeplanner.android.ui.components.Page
import nl.enadeag.carchargeplanner.android.ui.components.AppButton
import nl.enadeag.carchargeplanner.android.ui.components.AppTextButton

/** While the settings file is read as the app opens. */
@Composable
internal fun ReadingScreen() {
    Page {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            Text(stringResource(R.string.reading_settings), modifier = Modifier.padding(start = 8.dp))
        }
    }
}

/**
 * A settings file that cannot be used: what is wrong in the engine's English words, and "Set up again", which asks
 * first and keeps the file under another name. A move that fails says why and may be tried again.
 *
 * @param again the details and the move of the file
 */
@Composable
internal fun UnusableScreen(again: SetUpAgain) {
    val state by again.state.collectAsStateWithLifecycle()
    val file = again.unusable.fileName
    Page {
        Heading(stringResource(R.string.unusable_title))
        Text(stringResource(R.string.unusable_text, file))
        EnglishDetails(again.unusable.details)
        when (val now = state) {
            SetUpAgain.State.Moving, is SetUpAgain.State.Moved -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                Text(stringResource(R.string.set_up_again_moving), modifier = Modifier.padding(start = 8.dp))
            }
            is SetUpAgain.State.Failed -> Column(verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Assertive }) {
                Text(stringResource(R.string.set_up_again_failed), color = MaterialTheme.colorScheme.error)
                EnglishDetails(now.detail)
                AppButton(onClick = { again.ask() }) {
                    Text(stringResource(R.string.action_try_again))
                }
            }
            SetUpAgain.State.Offered, SetUpAgain.State.Confirming -> AppButton(onClick = { again.ask() }) {
                Text(stringResource(R.string.set_up_again))
            }
        }
    }
    if (state == SetUpAgain.State.Confirming) {
        AlertDialog(
            onDismissRequest = { again.cancel() },
            title = { Text(stringResource(R.string.set_up_again_question)) },
            text = { Text(stringResource(R.string.set_up_again_explained, file)) },
            confirmButton = {
                AppTextButton(onClick = { again.confirm() }) {
                    Text(stringResource(R.string.set_up_again))
                }
            },
            dismissButton = {
                AppTextButton(onClick = { again.cancel() }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

/** A settings file of a newer version of the app: the owner updates the app; nothing is offered to reset it. */
@Composable
internal fun NewerVersionScreen() {
    Page {
        Heading(stringResource(R.string.newer_title))
        Text(stringResource(R.string.newer_text))
    }
}
