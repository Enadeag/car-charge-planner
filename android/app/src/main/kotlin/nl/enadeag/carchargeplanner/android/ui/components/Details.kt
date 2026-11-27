package nl.enadeag.carchargeplanner.android.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.unit.dp
import nl.enadeag.carchargeplanner.android.R

/**
 * The engine's or the system's words about a problem, which stay English, in the sentence that says so: a screen
 * reader reads them as English (a `LocaleSpan`), the sentence around them in the app's language.
 *
 */
@Composable
internal fun EnglishDetails(details: String) {
    Text(inEnglish(stringResource(R.string.details_in_english, details), details),
        style = MaterialTheme.typography.bodyMedium)
}

/**
 * @param english the English words in it; the last place they occur is marked
 */
internal fun inEnglish(sentence: String, english: String): AnnotatedString = buildAnnotatedString {
    append(sentence)
    val start = sentence.lastIndexOf(english)
    if (start >= 0 && english.isNotEmpty()) {
        addStyle(SpanStyle(localeList = LocaleList("en")), start, start + english.length)
    }
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
