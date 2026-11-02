package nl.enadeag.carchargeplanner.android.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.intl.LocaleList
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
