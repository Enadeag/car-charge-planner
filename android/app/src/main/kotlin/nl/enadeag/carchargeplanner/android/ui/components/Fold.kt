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
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

/**
 * Settings that are seldom changed, folded under a row with an arrow, such as the panels' "▸ More settings" and the
 * location page's "Coordinates and time zone". The row is one button, at least 48 dp high, whose arrow points to the
 * end while folded and down while open.
 *
 * @param open whether the content shows, kept by the caller so it survives as the caller wants
 * @param onToggle called with the new state when the row is pressed
 * @param content what the row folds away, shown below it while open
 */
@Composable
internal fun Fold(label: String, open: Boolean, onToggle: (Boolean) -> Unit, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                .clickable(role = Role.Button) { onToggle(!open) }
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
internal class ProblemFold(private val folded: MutableState<Boolean?>, private val startFolded: Boolean,
    private val problem: Boolean) {

    val open: Boolean
        get() = !(folded.value ?: startFolded) || problem

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
 * @param known whether [startFolded] is known yet, such as only once the stored API keys are read; until then the fold
 *     follows it, and the value it has when it becomes known is kept, so the fold moves at most once
 */
@Composable
internal fun rememberProblemFold(problem: Boolean, startFolded: Boolean = true, known: Boolean = true): ProblemFold {
    // null until the start is known or the owner pressed the row
    val folded = rememberSaveable { mutableStateOf<Boolean?>(null) }
    if (known) {
        SideEffect {
            if (folded.value == null) {
                folded.value = startFolded
            }
        }
    }
    LaunchedEffect(problem) {
        if (problem) {
            folded.value = false
        }
    }
    return ProblemFold(folded, startFolded, problem)
}

/** A [Fold] that a problem in its fields opens, as [rememberProblemFold] says. */
@Composable
internal fun Fold(label: String, fold: ProblemFold, content: @Composable () -> Unit) =
    Fold(label, fold.open, fold::toggle, content)
