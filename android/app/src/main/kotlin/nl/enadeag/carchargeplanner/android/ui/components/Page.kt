package nl.enadeag.carchargeplanner.android.ui.components

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp

/** The widest a page's column grows on a tablet or a phone on its side, so lines stay easy to read. */
private val MOST_WIDTH = 600.dp

/**
 * A screen's page: one column of at most [MOST_WIDTH], centred, that scrolls, so every screen fits at any font size,
 * clear of the system bars and the keyboard.
 */
@Composable
internal fun Page(content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier = Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing),
            contentAlignment = Alignment.TopCenter) {
            Column(
                modifier = Modifier.widthIn(max = MOST_WIDTH).fillMaxWidth().verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                content = content,
            )
        }
    }
}

/**
 * A page's heading, which takes the focus when asked, so a screen reader starts there on a new step.
 *
 * @param focus moves the focus to it; null for a heading that never takes it
 */
@Composable
internal fun Heading(text: String, focus: FocusRequester? = null) {
    val focused = if (focus == null) Modifier else Modifier.focusRequester(focus).focusable()
    Text(text, style = MaterialTheme.typography.headlineSmall, modifier = focused.semantics { heading() })
}

@Composable
internal fun SectionHeading(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
}

/**
 * Which row's heading takes the focus next, after Add or Remove changed the rows, such as the panel arrays: the new
 * row's after Add, the one before after Remove, so the focus does not fall off the page with a button that went. When
 * the last row goes, the focus is for the line that says there is none instead.
 */
@Stable
internal class RowFocus<K> {

    /** The row whose heading takes the focus once it is shown; null for none. */
    var next by mutableStateOf<K?>(null)

    /** Whether the line that says there is none takes the focus, as no row is left; the line clears it once it has. */
    var none by mutableStateOf(false)

    /**
     * Readies the focus for a row that goes: to the row before it, or the one after when it is the first, or the line
     * that says there is none when it is the only one.
     */
    fun removing(rows: List<K>, row: K) {
        val index = rows.indexOf(row)
        next = rows.getOrNull(index - 1) ?: rows.getOrNull(index + 1)
        none = next == null
    }
}

/**
 * The line that says a page has no rows, which takes the focus when [RowFocus] asks, as the last row went. It is
 * shown exactly when no row is left.
 */
@Composable
internal fun NoneLine(text: String, focus: RowFocus<*>) {
    val requester = remember { FocusRequester() }
    LaunchedEffect(focus.none) {
        if (focus.none) {
            requester.requestFocus()
            focus.none = false
        }
    }
    Text(text, modifier = Modifier.focusRequester(requester).focusable())
}

/** A row's heading, such as "Array 2, east roof", which takes the focus when [RowFocus] names its row. */
@Composable
internal fun <K> RowHeading(text: String, style: TextStyle, focus: RowFocus<K>, row: K) {
    Text(text, style = style, modifier = Modifier.focusRequester(rowFocus(focus, row)).focusable()
        .semantics { heading() })
}

/**
 * A row's line that is no heading, such as a one-off away period in a list, which takes the focus when [RowFocus]
 * names its row.
 */
@Composable
internal fun <K> RowLine(text: String, focus: RowFocus<K>, row: K, modifier: Modifier = Modifier) {
    Text(text, modifier = modifier.focusRequester(rowFocus(focus, row)).focusable())
}

/** A requester that takes the focus when [focus] names [row], once. */
@Composable
private fun <K> rowFocus(focus: RowFocus<K>, row: K): FocusRequester {
    val requester = remember { FocusRequester() }
    LaunchedEffect(focus.next) {
        if (focus.next == row) {
            requester.requestFocus()
            focus.next = null
        }
    }
    return requester
}
