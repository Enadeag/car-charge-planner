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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
 * A [BarScaffold] page: the bar's actions, such as Save, stay in view above the keyboard; below the bar a short status
 * that stays in view, such as that a save failed, and the column that scrolls, which opens with what the status is
 * about.
 *
 * @param title the page's title, which a screen reader reads as its heading
 * @param titleFocus moves the focus to the title, as on a new page; null for one that never takes it
 * @param onUp what Up does, the same as Back; null for a screen without Up
 * @param actions the bar's actions, at its end
 * @param padded false for a list whose rows have their own padding, such as Settings' pages
 * @param snackbars where a snackbar shows, such as "Saved."; null for a screen without
 * @param status what stays in view under the bar while the column scrolls: one short line at most, so the page keeps
 *     its room at any font size; nothing by default
 * @param lead what opens the column, without its spacing, such as why a save failed; nothing by default
 * @param scrollToTop scrolls the column to its top whenever it changes to something other than null, so what [lead]
 *     says is in view; null for none
 * @param content the page's parts, from top to bottom
 */
@Composable
internal fun BarPage(title: String, titleFocus: FocusRequester?, onUp: (() -> Unit)?,
    actions: @Composable RowScope.() -> Unit = {}, padded: Boolean = true, snackbars: SnackbarHostState? = null,
    status: @Composable ColumnScope.() -> Unit = {}, lead: @Composable ColumnScope.() -> Unit = {},
    scrollToTop: Any? = null, content: @Composable ColumnScope.() -> Unit) {
    val scroll = rememberScrollState()
    LaunchedEffect(scrollToTop) {
        if (scrollToTop != null) {
            scroll.animateScrollTo(0)
        }
    }
    BarScaffold(title, titleFocus, onUp, actions, snackbars) {
        Column(Modifier.padding(horizontal = 16.dp), content = status)
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(scroll)
                .padding(if (padded) PaddingValues(16.dp) else PaddingValues(vertical = 8.dp)),
        ) {
            lead()
            Column(verticalArrangement = Arrangement.spacedBy(if (padded) 16.dp else 0.dp), content = content)
        }
    }
}

/**
 * Material's top app bar over one column of at most [MOST_WIDTH], centred, which fills the rest of the screen: the
 * bar's title is the screen's heading, with Up when there is a screen to go back to and the screen's actions. The bar
 * keeps clear of the status bar and a display cutout, also one at the side; the column of those, the navigation bar
 * and the keyboard. A snackbar shows at the bottom.
 *
 * @param title the screen's title, which a screen reader reads as its heading
 * @param titleFocus moves the focus to the title, as on a new screen; null for one that never takes it
 * @param onUp what Up does, the same as Back; null for a screen without Up
 * @param actions the bar's actions, at its end
 * @param snackbars where a snackbar shows; null for a screen without
 * @param content the column's parts, which scroll on their own
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BarScaffold(title: String, titleFocus: FocusRequester?, onUp: (() -> Unit)?,
    actions: @Composable RowScope.() -> Unit = {}, snackbars: SnackbarHostState? = null,
    content: @Composable ColumnScope.() -> Unit) {
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
        snackbarHost = { if (snackbars != null) SnackbarHost(snackbars) },
        contentWindowInsets = WindowInsets.safeDrawing,
    ) { inner ->
        Box(Modifier.fillMaxSize().padding(inner).consumeWindowInsets(inner), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = MOST_WIDTH).fillMaxWidth(), content = content)
        }
    }
}

/** The top app bar's Up, Material's arrow, mirrored right to left, that a screen reader names "Back". */
@Composable
private fun UpButton(onUp: () -> Unit) {
    BarIconButton(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_up), onClick = onUp)
}

/**
 * An icon in the top app bar that acts, such as Up or the home's Settings, drawn at 48 dp.
 *
 * @param icon Material's icon
 * @param name what a screen reader says for it, as the icon has no text
 * @param enabled false while it cannot be used, as while the plan is made
 * @param onClick what it does
 */
@Composable
internal fun BarIconButton(icon: ImageVector, name: String, enabled: Boolean = true, onClick: () -> Unit) {
    // Material draws an icon button at 40 dp; a control is 48 dp at least
    IconButton(onClick = onClick, modifier = Modifier.size(48.dp), enabled = enabled) {
        Icon(icon, contentDescription = name)
    }
}
