package nl.enadeag.carchargeplanner.android.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
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
 * @param details the words, in English
 * @param modifier such as a live region, for details that come in while the screen is shown
 */
@Composable
internal fun EnglishDetails(details: String, modifier: Modifier = Modifier) {
    Text(inEnglish(stringResource(R.string.details_in_english, details), details),
        style = MaterialTheme.typography.bodyMedium, modifier = modifier)
}

/**
 * A sentence with English words in it marked as English.
 *
 * @param sentence the whole sentence, in the app's language
 * @param english the English words in it; the last place they occur is marked
 * @return the sentence, with the English words in a span of their own language
 */
internal fun inEnglish(sentence: String, english: String): AnnotatedString = buildAnnotatedString {
    append(sentence)
    val start = sentence.lastIndexOf(english)
    if (start >= 0 && english.isNotEmpty()) {
        addStyle(SpanStyle(localeList = LocaleList("en")), start, start + english.length)
    }
}

/**
 * Why a save in a dialog failed, with the engine's English details, in a live region a screen reader reads at once.
 * The region stays while there is no failure, so one that comes in later is read.
 *
 * @param message what failed, in the app's language
 * @param details the engine's words about it, in English; null while nothing failed, which shows nothing
 */
@Composable
internal fun SaveFailure(message: String, details: String?) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.semantics(mergeDescendants = true) {
        liveRegion = LiveRegionMode.Assertive
    }) {
        if (details != null) {
            Text(message, color = MaterialTheme.colorScheme.error)
            EnglishDetails(details)
        }
    }
}
