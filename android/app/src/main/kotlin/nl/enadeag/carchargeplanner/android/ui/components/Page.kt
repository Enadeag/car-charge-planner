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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/** The widest a page's column grows on a tablet or a phone on its side, so lines stay easy to read. */
private val MOST_WIDTH = 600.dp

/**
 * A screen's page: one column of at most [MOST_WIDTH], centred, that scrolls, so every screen fits at any font size,
 * clear of the system bars and the keyboard.
 *
 * @param content the page's parts, from top to bottom
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
 * @param text the heading
 * @param focus moves the focus to it; null for a heading that never takes it
 */
@Composable
internal fun Heading(text: String, focus: FocusRequester? = null) {
    val focused = if (focus == null) Modifier else Modifier.focusRequester(focus).focusable()
    Text(text, style = MaterialTheme.typography.headlineSmall, modifier = focused.semantics { heading() })
}

/**
 * A section's heading within a page.
 *
 * @param text the heading
 */
@Composable
internal fun SectionHeading(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
}
