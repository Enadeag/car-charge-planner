package nl.enadeag.carchargeplanner.android.ui.components

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import nl.enadeag.carchargeplanner.android.R

/**
 * The least height a button is drawn at. Material draws its buttons at 40 dp; every control of the app is drawn at
 * 48 dp at least (`docs/accessibility.md`), which wins over Material's default here.
 */
private val LEAST_HEIGHT = 48.dp

/**
 * Material's filled button, for a screen's main action, drawn at least 48 dp high.
 *
 * @param onClick what it does
 * @param modifier its place in the layout
 * @param enabled false while it cannot be used, as while a save runs
 * @param content its text, and a progress indicator while it waits
 */
@Composable
internal fun AppButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit) {
    Button(onClick = onClick, modifier = modifier.heightIn(min = LEAST_HEIGHT), enabled = enabled, content = content)
}

/**
 * Material's outlined button, for an action beside the main one, drawn at least 48 dp high.
 *
 * @param onClick what it does
 * @param modifier its place in the layout
 * @param enabled false while it cannot be used
 * @param content its text
 */
@Composable
internal fun AppOutlinedButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit) {
    OutlinedButton(onClick = onClick, modifier = modifier.heightIn(min = LEAST_HEIGHT), enabled = enabled,
        content = content)
}

/**
 * Material's text button, for a dialog's actions, the top app bar's and the lesser ones on a page, drawn at least
 * 48 dp high.
 *
 * @param onClick what it does
 * @param modifier its place in the layout
 * @param enabled false while it cannot be used
 * @param content its text
 */
@Composable
internal fun AppTextButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit) {
    TextButton(onClick = onClick, modifier = modifier.heightIn(min = LEAST_HEIGHT), enabled = enabled,
        content = content)
}

/**
 * Save, as a text button: while it saves, it is off and says so with its progress.
 *
 * @param busy true while the save runs
 * @param onClick saves
 * @param enabled false while it cannot be used for another reason, as while what it saves is read
 */
@Composable
internal fun SaveButton(busy: Boolean, onClick: () -> Unit, enabled: Boolean = true) {
    AppTextButton(onClick = onClick, enabled = enabled && !busy) {
        if (busy) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            Text(stringResource(R.string.saving), modifier = Modifier.padding(start = 8.dp))
        } else {
            Text(stringResource(R.string.action_save))
        }
    }
}
