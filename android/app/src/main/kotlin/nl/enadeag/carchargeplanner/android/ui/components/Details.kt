package nl.enadeag.carchargeplanner.android.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import nl.enadeag.carchargeplanner.android.R

/** The engine's or the system's words about a problem, which stay English, in the sentence that says so. */
@Composable
internal fun EnglishDetails(details: String) {
    Text(stringResource(R.string.details_in_english, details), style = MaterialTheme.typography.bodyMedium)
}

/**
 * Why a save in a dialog failed, with the engine's English details.
 *
 * @param details the engine's words about it, in English; null while nothing failed, which shows nothing
 */
@Composable
internal fun SaveFailure(message: String, details: String?) {
    if (details != null) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(message, color = MaterialTheme.colorScheme.error)
            EnglishDetails(details)
        }
    }
}
