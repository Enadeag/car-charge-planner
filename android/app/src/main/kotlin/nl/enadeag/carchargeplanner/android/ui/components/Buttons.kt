package nl.enadeag.carchargeplanner.android.ui.components

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import nl.enadeag.carchargeplanner.android.R

/**
 * The least height a button is drawn at. Material draws its buttons at 40 dp; every control of the app is drawn at
 * 48 dp at least (`docs/accessibility.md`), which wins over Material's default here.
 */
private val LEAST_HEIGHT = 48.dp

/** Material's filled button, for a screen's main action, drawn at least 48 dp high. */
@Composable
internal fun AppButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit) {
    Button(onClick = onClick, modifier = modifier.heightIn(min = LEAST_HEIGHT), enabled = enabled, content = content)
}

/** Material's outlined button, for an action beside the main one, drawn at least 48 dp high. */
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
 * @param colors its colours; Material's text button's by default, a snackbar's action has its own
 */
@Composable
internal fun AppTextButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    colors: ButtonColors = ButtonDefaults.textButtonColors(), content: @Composable RowScope.() -> Unit) {
    TextButton(onClick = onClick, modifier = modifier.heightIn(min = LEAST_HEIGHT), enabled = enabled, colors = colors,
        content = content)
}

/**
 * Save, as a text button: while it saves, it is off and says so with its progress.
 *
 * @param enabled false while it cannot be used for another reason, as while what it saves is read
 */
@Composable
internal fun SaveButton(busy: Boolean, onClick: () -> Unit, enabled: Boolean = true) {
    AppTextButton(onClick = onClick, enabled = enabled && !busy) {
        SaveLabel(busy)
    }
}

/**
 * A text button that is off while a change runs yet keeps the focus: Compose takes the focus from a disabled control,
 * so a keyboard or switch user who pressed it would start again at the top of the page. It looks off, a screen reader
 * says it is, and a press does nothing until the change is done.
 *
 * @param onClick what a press does while no change runs
 */
@Composable
internal fun HeldButton(busy: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit) {
    val colours = ButtonDefaults.textButtonColors()
    AppTextButton(onClick = { if (!busy) onClick() }, modifier = modifier.semantics {
        if (busy) {
            disabled()
        }
    }, colors = if (busy) colours.copy(contentColor = colours.disabledContentColor) else colours, content = content)
}

/**
 * An entry's Save, as a [HeldButton]: held while any of the entry's changes runs, so it keeps the focus, and showing
 * its progress while the entry's own save runs.
 */
@Composable
internal fun SaveAction(saving: Boolean, held: Boolean, onSave: () -> Unit) {
    HeldButton(busy = held, onClick = onSave) {
        SaveLabel(saving)
    }
}

@Composable
private fun SaveLabel(saving: Boolean) {
    if (saving) {
        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
        Text(stringResource(R.string.saving), modifier = Modifier.padding(start = 8.dp))
    } else {
        Text(stringResource(R.string.action_save))
    }
}
