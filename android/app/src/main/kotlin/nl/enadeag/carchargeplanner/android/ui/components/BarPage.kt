package nl.enadeag.carchargeplanner.android.ui.components

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import nl.enadeag.carchargeplanner.android.R

/** The widest a bar page's column grows, as [Page]'s: lines stay easy to read on a tablet or a phone on its side. */
private val MOST_WIDTH = 600.dp

/**
 * A screen with Material's top app bar: the title as the page's heading, Up when there is a screen to go back to, and
 * the screen's actions, such as Save, which stay in view above the keyboard; below it a status that stays in view,
 * such as what a save came to, and one column of at most [MOST_WIDTH], centred, that scrolls. The bar keeps clear of
 * the status bar and a display cutout, also one at the side, the column of the navigation bar and the keyboard.
 *
 * @param title the page's title, which a screen reader reads as its heading
 * @param titleFocus moves the focus to the title, as on a new page; null for one that never takes it
 * @param onUp what Up does, the same as Back; null for a screen without Up
 * @param actions the bar's actions, at its end
 * @param padded false for a list whose rows have their own padding, such as Settings' pages
 * @param status what stays in view under the bar while the column scrolls; nothing by default
 * @param content the page's parts, from top to bottom
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BarPage(title: String, titleFocus: FocusRequester?, onUp: (() -> Unit)?,
    actions: @Composable RowScope.() -> Unit = {}, padded: Boolean = true,
    status: @Composable ColumnScope.() -> Unit = {}, content: @Composable ColumnScope.() -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    val focused = if (titleFocus == null) Modifier
                        else Modifier.focusRequester(titleFocus).focusable()
                    Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = focused.semantics { heading() })
                },
                navigationIcon = { if (onUp != null) UpButton(onUp) },
                actions = actions,
                windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
            )
        },
        contentWindowInsets = WindowInsets.safeDrawing,
    ) { inner ->
        Box(Modifier.fillMaxSize().padding(inner).consumeWindowInsets(inner), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = MOST_WIDTH).fillMaxWidth()) {
                Column(Modifier.padding(horizontal = 16.dp), content = status)
                Column(
                    modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                        .padding(if (padded) PaddingValues(16.dp) else PaddingValues(vertical = 8.dp)),
                    verticalArrangement = Arrangement.spacedBy(if (padded) 16.dp else 0.dp),
                    content = content,
                )
            }
        }
    }
}

/** The top app bar's Up, Material's arrow, mirrored right to left, that a screen reader names "Back". */
@Composable
private fun UpButton(onUp: () -> Unit) {
    BarIconButton(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_up), onUp)
}

/**
 * An icon in the top app bar that acts, such as Up or the home's Settings, drawn at 48 dp.
 *
 * @param icon Material's icon
 * @param name what a screen reader says for it, as the icon has no text
 * @param onClick what it does
 */
@Composable
internal fun BarIconButton(icon: ImageVector, name: String, onClick: () -> Unit) {
    // Material draws an icon button at 40 dp; a control is 48 dp at least
    IconButton(onClick = onClick, modifier = Modifier.size(48.dp)) {
        Icon(icon, contentDescription = name)
    }
}
