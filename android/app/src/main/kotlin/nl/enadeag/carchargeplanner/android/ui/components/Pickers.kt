package nl.enadeag.carchargeplanner.android.ui.components

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DisplayMode
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDialog
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.LocalTime
import nl.enadeag.carchargeplanner.android.R

/**
 * A time's or a day's button, such as one of part of the day's times: its name and the time or day, which opens the
 * picker; it may take the focus also in touch mode, so the focus comes back to it when the picker closes.
 *
 * @param name what it is, such as "Away from", read first
 * @param time the time or day, as the phone writes it
 * @param focus moves the focus to it
 * @param problem the problem it carries for a screen reader; null for none
 * @param enabled false while it cannot be used, as while a save runs
 * @param modifier its place in the layout; it fills the width it gets
 * @param onClick opens the picker
 */
@Composable
internal fun TimeButton(name: String, time: String, focus: FocusRequester, problem: String?, enabled: Boolean,
    modifier: Modifier = Modifier, onClick: () -> Unit) {
    AppOutlinedButton(onClick = onClick, enabled = enabled,
        modifier = modifier.fillMaxWidth().focusProperties { canFocus = true }.focusRequester(focus).semantics {
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

/**
 * Material's date picker in its dialog, with OK and Cancel; Back, or a tap beside it, cancels. Only the days offered
 * can be chosen, and its years are only theirs; the picker's own toggle is off, and a text button under it switches
 * between the calendar and typing the date, which stays as it was when the phone turns. OK without a day, as after the
 * typed date was cleared, keeps the day as it was. The picker works in UTC midnights, which the day goes to and comes
 * from as a date alone ([PickerDays]), never through a time zone, so the home's zone cannot move it a day. Its content
 * scrolls, so the toggle stays reachable at a large font size.
 *
 * @param title which day it is, the dialog's heading
 * @param day the day it opens on
 * @param days the days that can be chosen
 * @param chosen gets the day on OK
 * @param closed called as it closes, after OK or not
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DateDialog(title: String, day: LocalDate, days: ClosedRange<LocalDate>, chosen: (LocalDate) -> Unit,
    closed: () -> Unit) {
    val years = days.start.year..days.endInclusive.year
    val offered = remember(days) {
        object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean = PickerDays.day(utcTimeMillis) in days

            override fun isSelectableYear(year: Int): Boolean = year in years
        }
    }
    val state = rememberDatePickerState(initialSelectedDateMillis = PickerDays.millis(day), yearRange = years,
        selectableDates = offered)
    DatePickerDialog(
        onDismissRequest = closed,
        confirmButton = {
            AppTextButton(onClick = {
                state.selectedDateMillis?.let(PickerDays::day)?.takeIf { it in days }?.let(chosen)
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
            DatePicker(state, showModeToggle = false, title = {
                Text(title, modifier = Modifier.padding(start = 24.dp, end = 12.dp, top = 16.dp)
                    .semantics { heading() })
            })
            // as the time picker's: in words, under the picker, rather than Material's icon in the picker's corner
            val typing = state.displayMode == DisplayMode.Input
            AppTextButton(onClick = { state.displayMode = if (typing) DisplayMode.Picker else DisplayMode.Input }) {
                Text(stringResource(if (typing) R.string.picker_calendar else R.string.one_off_keyboard))
            }
        }
    }
}
