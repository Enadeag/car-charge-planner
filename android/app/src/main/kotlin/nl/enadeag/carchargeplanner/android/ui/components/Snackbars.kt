package nl.enadeag.carchargeplanner.android.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarDefaults
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarVisuals
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import nl.enadeag.carchargeplanner.android.R

/**
 * A snackbar that says what failed in the app's language and then the engine's English details, which a screen reader
 * reads as English. It has a button to close it and no action, and stays until the next notice or Close, as the
 * details may take long to read.
 *
 * @property message what failed, in the app's language
 * @property details the engine's words about it, in English
 */
internal class FailureVisuals(override val message: String, val details: String) : SnackbarVisuals {
    override val actionLabel: String? = null
    override val withDismissAction: Boolean = true
    override val duration: SnackbarDuration = SnackbarDuration.Indefinite
}

/**
 * Material's snackbar, with its action as Material draws it and, on every snackbar that asks for one, the app's own
 * Close, so a screen reader reads one name for it; the English details of a [FailureVisuals] are marked as English.
 * The snackbar host around it keeps its polite live region.
 *
 * @param data the snackbar to show
 */
@Composable
internal fun AppSnackbar(data: SnackbarData) {
    val visuals = data.visuals
    val action: (@Composable () -> Unit)? = visuals.actionLabel?.let { label ->
        {
            AppTextButton(onClick = data::performAction,
                colors = ButtonDefaults.textButtonColors(contentColor = SnackbarDefaults.actionColor)) {
                Text(label)
            }
        }
    }
    val close: (@Composable () -> Unit)? = if (visuals.withDismissAction) {
        {
            IconButton(onClick = data::dismiss,
                colors = IconButtonDefaults.iconButtonColors(contentColor = SnackbarDefaults.dismissActionContentColor)) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_close))
            }
        }
    } else {
        null
    }
    // Material's own padding around the snackbar, so it does not touch the screen's edge
    Snackbar(modifier = Modifier.padding(12.dp), action = action, dismissAction = close) {
        if (visuals is FailureVisuals) {
            val sentence = visuals.message + " " + stringResource(R.string.details_in_english, visuals.details)
            Text(inEnglish(sentence, visuals.details))
        } else {
            Text(visuals.message)
        }
    }
}
