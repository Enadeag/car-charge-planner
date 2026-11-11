package nl.enadeag.carchargeplanner.android.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import nl.enadeag.carchargeplanner.android.R

/**
 * Settings that are seldom changed, folded under a row with an arrow, such as the panels' "▸ More settings" and the
 * location page's "Coordinates and time zone". The row is one button, at least 48 dp high, whose arrow points to the
 * end while folded and down while open; a screen reader reads its label and "Collapsed" or "Expanded".
 *
 * @param open whether the content shows, kept by the caller so it survives as the caller wants
 * @param onToggle called with the new state when the row is pressed
 * @param content what the row folds away, shown below it while open
 */
@Composable
internal fun Fold(label: String, open: Boolean, onToggle: (Boolean) -> Unit, content: @Composable () -> Unit) {
    val state = stringResource(if (open) R.string.fold_expanded else R.string.fold_collapsed)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                .clickable(role = Role.Button) { onToggle(!open) }
                .semantics(mergeDescendants = true) { stateDescription = state }
                .padding(vertical = 8.dp),
        ) {
            Icon(if (open) Icons.Filled.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null)
            Text(label, style = MaterialTheme.typography.titleSmall)
        }
        if (open) {
            content()
        }
    }
}

/**
 * Whether a fold whose fields can show a problem is open: a problem in one of them opens it and keeps it open, also
 * after the problem is put right, while a press on its row does nothing meanwhile.
 */
internal class ProblemFold(private val folded: MutableState<Boolean>, private val problem: Boolean) {

    val open: Boolean
        get() = !folded.value || problem

    fun toggle(opened: Boolean) {
        if (!problem) {
            folded.value = !opened
        }
    }

    /** Opens the fold as the owner's choice does, such as "Other" for the charge power. */
    fun unfold() {
        folded.value = false
    }
}

/**
 * The state of a fold that [problem] opens, kept across a configuration change.
 *
 * @param problem whether one of the fold's fields shows a problem now
 * @param startFolded whether the fold starts closed
 */
@Composable
internal fun rememberProblemFold(problem: Boolean, startFolded: Boolean = true): ProblemFold {
    val folded = rememberSaveable { mutableStateOf(startFolded) }
    LaunchedEffect(problem) {
        if (problem) {
            folded.value = false
        }
    }
    return ProblemFold(folded, problem)
}

/** A [Fold] that a problem in its fields opens, as [rememberProblemFold] says. */
@Composable
internal fun Fold(label: String, fold: ProblemFold, content: @Composable () -> Unit) =
    Fold(label, fold.open, fold::toggle, content)
