package nl.enadeag.carchargeplanner.android.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import nl.enadeag.carchargeplanner.android.R
import nl.enadeag.carchargeplanner.android.form.FieldProblem

/**
 * A choice of one from a fixed list, as Material's exposed drop-down menu: a field that shows the choice, or "Choose…"
 * before one is made, and opens the list on a tap. A screen reader reads it as a drop-down list with its label, its
 * choice and its problem. Shared by every drop-down of the forms; a few choices that all fit on one line may still be
 * radio buttons ([ChoiceRow]).
 *
 * @param options the options, in the order listed
 * @param chosen the option shown; null before a choice, which shows "Choose…"
 * @param name what the owner reads for an option, in the app's language
 * @param problem the problem it shows, under it and to a screen reader with the field; null for none
 * @param focus moves the focus to the field, as to the first problem after Save
 * @param hint what the choice is for, under the field after its problem; null for none
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun <T> ChoiceMenu(label: String, options: List<T>, chosen: T?, name: @Composable (T) -> String,
    onChoose: (T) -> Unit, problem: FieldProblem?, focus: FocusRequester, hint: String? = null) {
    var open by rememberSaveable { mutableStateOf(false) }
    val problemSentence = problem?.let { problemText(it) }
    val names = options.map { name(it) }
    ExposedDropdownMenuBox(expanded = open, onExpandedChange = { open = it }, modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = chosen?.let { names[options.indexOf(it)] } ?: stringResource(R.string.choose),
            onValueChange = { },
            readOnly = true,
            singleLine = false,
            label = { Text(label) },
            isError = problem != null,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = open) },
            supportingText = if (problemSentence == null && hint == null) null else {
                {
                    Column {
                        problemSentence?.let { Text(it) }
                        hint?.let { Text(it) }
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().focusRequester(focus)
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .semantics {
                    if (problemSentence != null) {
                        error(problemSentence)
                    }
                },
        )
        ExposedDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEachIndexed { index, option ->
                DropdownMenuItem(
                    text = { Text(names[index]) },
                    onClick = {
                        onChoose(option)
                        open = false
                    },
                )
            }
        }
    }
}
