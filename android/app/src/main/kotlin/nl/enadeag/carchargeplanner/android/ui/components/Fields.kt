package nl.enadeag.carchargeplanner.android.ui.components

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import nl.enadeag.carchargeplanner.android.R
import nl.enadeag.carchargeplanner.android.form.FieldProblem

/** What a field takes, so the keyboard offers the keys it needs and a screen reader names the same kind. */
internal enum class FieldKind(val keyboard: KeyboardType) {
    /** A whole number that is never below 0, such as amps. */
    WHOLE(KeyboardType.Number),
    /** A number that may have decimals and is never below 0, such as kWh. */
    DECIMAL(KeyboardType.Decimal),
    /** A number that may be below 0, such as a longitude; the field has a minus key, which a number pad may lack. */
    SIGNED(KeyboardType.Decimal),
    /** Any text, such as a place's name. */
    TEXT(KeyboardType.Text),
    /** A server's address, such as https://example.org, on a keyboard with its slashes and dots. */
    ADDRESS(KeyboardType.Uri),
}

/**
 * The fields of a screen in their order on it, so Next can move the focus to the first that shows a problem.
 */
internal class ProblemFocus {

    private class Entry(val focus: FocusRequester, val hasProblem: Boolean)

    private val fields = sortedMapOf<Int, Entry>()

    /**
     * @param order its place on the screen, from the top; unique on the screen
     */
    fun note(order: Int, focus: FocusRequester, hasProblem: Boolean) {
        fields[order] = Entry(focus, hasProblem)
    }

    fun forget(order: Int) {
        fields.remove(order)
    }

    /**
     * @return false when none does, so nothing moved
     */
    fun focusFirst(): Boolean {
        val first = fields.values.firstOrNull { it.hasProblem } ?: return false
        first.focus.requestFocus()
        return true
    }
}

/**
 * @param problems the screen's fields, or null when its problems are not looked for
 */
@Composable
internal fun rememberFieldFocus(problems: ProblemFocus?, order: Int, hasProblem: Boolean): FocusRequester {
    val focus = remember { FocusRequester() }
    if (problems != null) {
        SideEffect { problems.note(order, focus, hasProblem) }
        DisposableEffect(problems, order) { onDispose { problems.forget(order) } }
    }
    return focus
}

/** The unit of a distance the owner types, after it in its field; kilometres only, as the app has no miles yet. */
internal const val DISTANCE_UNIT = "km"

/**
 * @param hint what to enter, with the unit and the usual range, unless [suffix] gives the unit
 * @param kind what it takes; [FieldKind.SIGNED] adds a key for the minus
 * @param imeAction the keyboard's action key: Next moves on to the next field; Done, on a page's last, closes the
 *     keyboard
 * @param suffix the unit after what is typed, which a screen reader reads with it, such as "km"; null for none
 */
@Composable
internal fun NumberField(label: String, hint: String, text: String, onEdit: (String) -> Unit, kind: FieldKind,
    problem: FieldProblem?, focus: FocusRequester = remember { FocusRequester() },
    imeAction: ImeAction = ImeAction.Next, suffix: String? = null) {
    EntryField(label, hint, text, onEdit, kind, problem, focus, imeAction = imeAction, suffix = suffix)
}

/**
 * @param onAction what the action key does; null to move on to the next field
 * @param announceProblem whether a screen reader reads a problem as it comes, for a field that keeps the focus while
 *     its problem shows; otherwise the problem is read with the field once the focus moves to it
 * @param readOnly true while it keeps the focus but takes no typing, as while what it holds is saved
 * @param placeholder what the empty field shows in its place, such as an estimate the app uses; null for nothing
 */
@Composable
internal fun EntryField(label: String, hint: String, text: String, onEdit: (String) -> Unit, kind: FieldKind,
    problem: FieldProblem?, focus: FocusRequester, modifier: Modifier = Modifier,
    imeAction: ImeAction = ImeAction.Next, onAction: (() -> Unit)? = null, announceProblem: Boolean = false,
    readOnly: Boolean = false, placeholder: String? = null, suffix: String? = null) {
    val problemSentence = problem?.let { problemText(it) }
    OutlinedTextField(
        value = text,
        onValueChange = onEdit,
        label = { Text(label) },
        placeholder = placeholder?.let { shown -> { Text(shown) } },
        suffix = suffix?.let { unit -> { Text(unit) } },
        singleLine = true,
        readOnly = readOnly,
        isError = problem != null,
        keyboardOptions = KeyboardOptions(keyboardType = kind.keyboard, imeAction = imeAction),
        keyboardActions = if (onAction == null) KeyboardActions.Default
            else KeyboardActions(onAny = { onAction() }),
        trailingIcon = if (kind == FieldKind.SIGNED) {
            { SignKey(text, onEdit) }
        } else null,
        supportingText = {
            Column {
                if (announceProblem) {
                    // a node of its own, outside the field's, which stays while empty, so a problem that comes in
                    // is read while the focus stays in the field
                    Column(Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }) {
                        problemSentence?.let { Text(it) }
                    }
                } else if (problemSentence != null) {
                    Text(problemSentence)
                }
                if (hint.isNotEmpty()) {
                    Text(hint)
                }
            }
        },
        modifier = modifier.fillMaxWidth().focusRequester(focus).semantics {
            if (problemSentence != null) {
                error(problemSentence)
            }
        },
    )
}

/** The key that puts a minus before the number, or takes it away. */
@Composable
private fun SignKey(text: String, onEdit: (String) -> Unit) {
    val description = stringResource(R.string.sign_toggle)
    IconButton(
        onClick = { onEdit(if (text.startsWith("-")) text.removePrefix("-") else "-$text") },
        // Material draws an icon button at 40 dp; a control is 48 dp at least
        modifier = Modifier.size(48.dp).semantics { contentDescription = description },
    ) {
        Text("±", style = MaterialTheme.typography.titleLarge, modifier = Modifier.clearAndSetSemantics { })
    }
}

/**
 * A choice of one from a few, as radio buttons a screen reader reads as a group.
 *
 * @param options the options, in the order shown
 * @param problem the problem it shows, under it and to a screen reader with the group; null for none
 * @param focus moves the focus to the group, as to the first problem after Save; null for a choice that never shows
 *     a problem, whose group then takes no focus of its own
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun <T> ChoiceRow(label: String, hint: String?, options: List<T>, chosen: T?, name: @Composable (T) -> String,
    onChoose: (T) -> Unit, problem: FieldProblem? = null, focus: FocusRequester? = null) {
    val problemSentence = problem?.let { problemText(it) }
    val focused = if (focus == null) Modifier else Modifier.focusRequester(focus).focusable()
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.titleSmall)
        FlowRow(modifier = Modifier.selectableGroup().then(focused).semantics {
            contentDescription = label
            if (problemSentence != null) {
                error(problemSentence)
            }
        },
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { option ->
                val selected = option == chosen
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                        .selectable(selected = selected, role = Role.RadioButton, onClick = { onChoose(option) })
                        .padding(end = 12.dp),
                ) {
                    RadioButton(selected = selected, onClick = null)
                    Text(name(option), modifier = Modifier.padding(start = 4.dp))
                }
            }
        }
        if (problemSentence != null) {
            Text(problemSentence, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error,
                modifier = Modifier.clearAndSetSemantics { })
        }
        if (hint != null) {
            Text(hint, style = MaterialTheme.typography.bodySmall)
        }
    }
}
