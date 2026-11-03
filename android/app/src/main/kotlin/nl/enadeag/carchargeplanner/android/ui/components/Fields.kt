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
import androidx.compose.material3.Surface
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

/**
 * What a field takes, so the keyboard offers the keys it needs and a screen reader names the same kind.
 *
 * @property keyboard the keyboard Android shows for it
 */
internal enum class FieldKind(val keyboard: KeyboardType) {
    /** A whole number that is never below 0, such as amps. */
    WHOLE(KeyboardType.Number),
    /** A number that may have decimals and is never below 0, such as kWh. */
    DECIMAL(KeyboardType.Decimal),
    /** A number that may be below 0, such as a longitude; the field has a minus key, which a number pad may lack. */
    SIGNED(KeyboardType.Decimal),
    /** A time of day such as 07:30, whose colon a number pad lacks. */
    TIME(KeyboardType.Ascii),
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
     * Notes a field, as it is shown now.
     *
     * @param order its place on the screen, from the top; unique on the screen
     * @param focus moves the focus to it
     * @param hasProblem whether it shows a problem now
     */
    fun note(order: Int, focus: FocusRequester, hasProblem: Boolean) {
        fields[order] = Entry(focus, hasProblem)
    }

    /**
     * Forgets a field that is no longer shown.
     *
     * @param order its place on the screen
     */
    fun forget(order: Int) {
        fields.remove(order)
    }

    /**
     * Moves the focus to the first field that shows a problem.
     *
     * @return false when none does, so nothing moved
     */
    fun focusFirst(): Boolean {
        val first = fields.values.firstOrNull { it.hasProblem } ?: return false
        first.focus.requestFocus()
        return true
    }
}

/**
 * A field's focus, noted in [problems] at its place on the screen.
 *
 * @param problems the screen's fields, or null when its problems are not looked for
 * @param order the field's place on the screen, from the top
 * @param hasProblem whether it shows a problem now
 * @return the field's focus
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

/**
 * A number field: its label, what it holds, a one-line hint, the problem under it after Next, and the typical mark.
 *
 * @param label the field's name, which a screen reader reads as its label
 * @param hint what to enter, with the unit and the usual range
 * @param text what it holds
 * @param onEdit gets what the owner typed
 * @param kind what it takes; [FieldKind.SIGNED] adds a key for the minus
 * @param problem the problem it shows; null for none
 * @param typical whether it holds a typical value
 * @param focus moves the focus to it
 * @param imeAction the keyboard's action key: Next moves on to the next field; Done, on a page's last, closes the
 *     keyboard
 */
@Composable
internal fun NumberField(label: String, hint: String, text: String, onEdit: (String) -> Unit, kind: FieldKind,
    problem: FieldProblem?, typical: Boolean = false, focus: FocusRequester = remember { FocusRequester() },
    imeAction: ImeAction = ImeAction.Next) {
    EntryField(label, hint, text, onEdit, kind, problem, typical, focus, imeAction = imeAction)
}

/**
 * A time field, such as 08:00, as hours and minutes on a 24-hour clock.
 *
 * @param label the field's name, which a screen reader reads as its label
 * @param text what it holds
 * @param onEdit gets what the owner typed
 * @param problem the problem it shows; null for none
 * @param focus moves the focus to it
 * @param imeAction the keyboard's action key: Next moves on to the next field; Done, on a page's last, closes the
 *     keyboard
 */
@Composable
internal fun TimeField(label: String, text: String, onEdit: (String) -> Unit, problem: FieldProblem?,
    focus: FocusRequester = remember { FocusRequester() }, imeAction: ImeAction = ImeAction.Next) {
    EntryField(label, stringResource(R.string.time_hint), text, onEdit, FieldKind.TIME, problem, false, focus,
        imeAction = imeAction)
}

/**
 * A field the owner types into.
 *
 * @param label the field's name
 * @param hint what to enter
 * @param text what it holds
 * @param onEdit gets what the owner typed
 * @param kind what it takes
 * @param problem the problem it shows; null for none
 * @param typical whether it holds a typical value
 * @param focus moves the focus to it
 * @param modifier the field's place in the layout
 * @param imeAction the keyboard's action key
 * @param onAction what the action key does; null to move on to the next field
 * @param announceProblem whether a screen reader reads a problem as it comes, for a field that keeps the focus while
 *     its problem shows; otherwise the problem is read with the field once the focus moves to it
 */
@Composable
internal fun EntryField(label: String, hint: String, text: String, onEdit: (String) -> Unit, kind: FieldKind,
    problem: FieldProblem?, typical: Boolean, focus: FocusRequester, modifier: Modifier = Modifier,
    imeAction: ImeAction = ImeAction.Next, onAction: (() -> Unit)? = null, announceProblem: Boolean = false) {
    val problemSentence = problem?.let { problemText(it) }
    Column(modifier = modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = text,
            onValueChange = onEdit,
            label = { Text(label) },
            singleLine = true,
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
                    Text(hint)
                }
            },
            modifier = Modifier.fillMaxWidth().focusRequester(focus).semantics {
                if (problemSentence != null) {
                    error(problemSentence)
                }
            },
        )
        if (typical) {
            TypicalMark(Modifier.padding(start = 16.dp))
        }
    }
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
 * The mark of a typical value: a word, never a colour alone, with a sentence for a screen reader.
 *
 * @param modifier its place in the layout
 */
@Composable
internal fun TypicalMark(modifier: Modifier = Modifier) {
    val description = stringResource(R.string.typical_mark_description)
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = modifier.padding(top = 4.dp).clearAndSetSemantics { contentDescription = description },
    ) {
        Text(stringResource(R.string.typical_mark), style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
    }
}

/**
 * A choice of one from a few, as radio buttons a screen reader reads as a group.
 *
 * @param T what is chosen
 * @param label the choice's name
 * @param hint what to choose; null for none
 * @param options the options, in the order shown
 * @param chosen the option chosen; null for none
 * @param name an option's name as shown
 * @param onChoose gets the option the owner chose
 * @param typical whether the choice holds a typical value
 * @param problem the problem it shows, under it and to a screen reader with the group; null for none
 * @param focus moves the focus to the group, as to the first problem after Save; null for a choice that never shows
 *     a problem, whose group then takes no focus of its own
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun <T> ChoiceRow(label: String, hint: String?, options: List<T>, chosen: T?, name: @Composable (T) -> String,
    onChoose: (T) -> Unit, typical: Boolean = false, problem: FieldProblem? = null,
    focus: FocusRequester? = null) {
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
        if (typical) {
            TypicalMark()
        }
    }
}
