package nl.enadeag.carchargeplanner.android.ui.components

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDialog
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import java.time.LocalTime
import nl.enadeag.carchargeplanner.android.R

/**
 * One of part of the day's times: a button with its name and the time, which opens the picker; it may take the focus
 * also in touch mode, so the focus comes back to it when the picker closes.
 */
@Composable
internal fun TimeButton(name: String, time: String, focus: FocusRequester, problem: String?, enabled: Boolean,
    onClick: () -> Unit) {
    AppOutlinedButton(onClick = onClick, enabled = enabled,
        modifier = Modifier.fillMaxWidth().focusProperties { canFocus = true }.focusRequester(focus).semantics {
            if (problem != null) {
                error(problem)
            }
        }) {
        Column(Modifier.fillMaxWidth()) {
            Text(name, style = MaterialTheme.typography.labelMedium)
            Text(time, style = MaterialTheme.typography.titleMedium)
        }
    }
}

/**
 * Material's time picker in its dialog, on the phone's 12- or 24-hour clock, with OK and Cancel; Back, or a tap beside
 * it, cancels. A text button switches between the dial and typing the time, which stays as it was when the phone
 * turns. Its content scrolls, so OK stays reachable at a large font size.
 *
 * @param title which time it is, the dialog's heading
 * @param time the time it opens on
 * @param chosen gets the time on OK
 * @param closed called as it closes, after OK or not
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TimeDialog(title: String, time: LocalTime, chosen: (LocalTime) -> Unit, closed: () -> Unit) {
    val state = rememberTimePickerState(time.hour, time.minute, DateFormat.is24HourFormat(LocalContext.current))
    var typing by rememberSaveable { mutableStateOf(false) }
    TimePickerDialog(
        onDismissRequest = closed,
        title = { Text(title, modifier = Modifier.semantics { heading() }) },
        confirmButton = {
            AppTextButton(onClick = {
                chosen(LocalTime.of(state.hour, state.minute))
                closed()
            }) {
                Text(stringResource(R.string.action_ok))
            }
        },
        dismissButton = {
            AppTextButton(onClick = closed) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    ) {
        Column(Modifier.verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
            if (typing) TimeInput(state) else TimePicker(state)
            // Material's mode toggle is an icon; this one says in words what it switches to. It sits under the
            // picker, not in the dialog's `modeToggleButton` slot: that slot shares one row, as wide as the dial, with
            // Cancel and OK, which has no room for all three at a large font size or in Dutch
            AppTextButton(onClick = { typing = !typing }) {
                Text(stringResource(if (typing) R.string.one_off_clock else R.string.one_off_keyboard))
            }
        }
    }
}
