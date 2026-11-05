package nl.enadeag.carchargeplanner.android.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarVisuals
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import nl.enadeag.carchargeplanner.android.R

/**
 * A snackbar that says what failed in the app's language and then the engine's English details, which a screen reader
 * reads as English. It has a button to close it and no action.
 *
 * @property message what failed, in the app's language
 * @property details the engine's words about it, in English
 * @property duration how long it stays
 */
internal class FailureVisuals(override val message: String, val details: String,
    override val duration: SnackbarDuration = SnackbarDuration.Long) : SnackbarVisuals {
    override val actionLabel: String? = null
    override val withDismissAction: Boolean = true
}

/**
 * Material's snackbar, with the English details of a [FailureVisuals] marked as English; any other kind is Material's
 * own. The snackbar host around it keeps its polite live region.
 *
 * @param data the snackbar to show
 */
@Composable
internal fun AppSnackbar(data: SnackbarData) {
    val visuals = data.visuals
    if (visuals !is FailureVisuals) {
        Snackbar(data)
        return
    }
    Snackbar(dismissAction = {
        IconButton(onClick = data::dismiss) {
            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_close))
        }
    }) {
        val sentence = visuals.message + " " + stringResource(R.string.details_in_english, visuals.details)
        Text(inEnglish(sentence, visuals.details))
    }
}
