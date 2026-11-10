package nl.enadeag.carchargeplanner.android.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalWindowInfo

/**
 * Moves the focus to a control of a dialog once the dialog's own window has the focus: a control can take it only
 * then, which comes after the dialog is drawn, and again when a picker or a question over the dialog closes. Called
 * inside the dialog's window.
 *
 * @param keys what moves the focus again when it changes, while the window has the focus
 * @param target the control that takes the focus, as it is when the focus moves; null for none
 */
@Composable
internal fun FocusOnWindow(vararg keys: Any?, target: () -> FocusRequester?) {
    val windowFocused = LocalWindowInfo.current.isWindowFocused
    val now by rememberUpdatedState(target)
    LaunchedEffect(*keys, windowFocused) {
        if (windowFocused) {
            now()?.requestFocus()
        }
    }
}
