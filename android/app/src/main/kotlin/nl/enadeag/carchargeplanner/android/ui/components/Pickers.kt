package nl.enadeag.carchargeplanner.android.ui.components

import android.text.format.DateFormat
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DisplayMode
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDialog
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import java.time.LocalDate
import java.time.LocalTime
import nl.enadeag.carchargeplanner.android.R
import nl.enadeag.carchargeplanner.android.form.FieldProblem

/**
 * A time's or a day's button, such as one of part of the day's times: its name and the time or day, which opens the
 * picker; it may take the focus also in touch mode, so the focus comes back to it when the picker closes.
 *
 * @param name what it is, such as "Away from", read first
 * @param time the time or day, as the phone writes it
 * @param problem the problem it carries for a screen reader; null for none
 * @param modifier its place in the layout; it fills the width it gets
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
 * A time the owner chooses on a form, such as a start of the charging hours: its [TimeButton], which shows the time on
 * the phone's 12- or 24-hour clock and opens the [TimeDialog], and under it the problem that Next or Save found, which a
 * screen reader reads with the button. The focus comes back to the button when the picker closes, once the page's
 * window has it again.
 *
 * @param label what it is, such as "Start charging from": the button's name and the picker's title
 * @param problem the problem it shows; null for none
 * @param focus moves the focus to the button, as to the first problem after Next or Save
 * @param onChoose gets the time on the picker's OK
 */
@Composable
internal fun TimeChoice(label: String, time: LocalTime, problem: FieldProblem?, focus: FocusRequester,
    onChoose: (LocalTime) -> Unit) {
    var picking by rememberSaveable { mutableStateOf(false) }
    var returning by rememberSaveable { mutableStateOf(false) }
    val problemSentence = problem?.let { problemText(it) }
    val windowFocused = LocalWindowInfo.current.isWindowFocused
    LaunchedEffect(picking, windowFocused) {
        if (returning && !picking && windowFocused) {
            returning = false
            focus.requestFocus()
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        TimeButton(label, rememberClock().format(time), focus, problemSentence, enabled = true) {
            returning = true
            picking = true
        }
        problemSentence?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.clearAndSetSemantics { })
        }
    }
    if (picking) {
        TimeDialog(label, time, onChoose) { picking = false }
    }
}

/**
 * Material's time picker in its dialog, on the phone's 12- or 24-hour clock, with OK and Cancel; Back, or a tap beside
 * it, cancels. A text button switches between the dial and typing the time, which stays as it was when the phone
 * turns. Its content scrolls, so OK stays reachable at a large font size.
 *
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
 * Material's date picker in a dialog of the app's own, with OK and Cancel; Back, or a tap beside it, cancels. Only the
 * days offered can be chosen, and its years are only theirs; the picker's own toggle is off, and a text button under
 * it switches between the calendar and typing the date, which stays as it was when the phone turns. OK without a day,
 * as after the typed date was cleared or one outside the days offered was typed, keeps the day as it was. The picker
 * works in UTC midnights, which the day goes to and comes from as a date alone ([PickerDays]), never through a time
 * zone, so the home's zone cannot move it a day.
 *
 * The dialog is Material's date picker dialog's, but no wider than the screen: Material's is 360 dp wide whatever the
 * screen, which cuts its sides and OK off on a phone narrower than that. Here the picker keeps its 360 dp and scrolls
 * sideways under it, and up and down with the toggle at a large font size, while Cancel and OK stay in view below.
 *
 * @param chosen gets the day on OK
 * @param closed called as it closes, after OK or not
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun DateDialog(title: String, day: LocalDate, days: ClosedRange<LocalDate>, chosen: (LocalDate) -> Unit,
    closed: () -> Unit) {
    val offered = remember(days) {
        object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean = PickerDays.day(utcTimeMillis) in days
        }
    }
    // the picker lists only these years, so a year outside the days offered is never offered
    val state = rememberDatePickerState(initialSelectedDateMillis = PickerDays.millis(day),
        yearRange = days.start.year..days.endInclusive.year, selectableDates = offered)
    val colors = DatePickerDefaults.colors()
    BasicAlertDialog(onDismissRequest = closed, modifier = Modifier.wrapContentHeight(),
        properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.widthIn(max = PICKER_WIDTH).heightIn(max = PICKER_HEIGHT), shape = DatePickerDefaults.shape,
            color = colors.containerColor, tonalElevation = DatePickerDefaults.TonalElevation) {
            Column(verticalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.horizontalScroll(rememberScrollState())) {
                        DatePicker(state, modifier = Modifier.width(PICKER_WIDTH), showModeToggle = false,
                            colors = colors, title = {
                                Text(title, modifier = Modifier.padding(start = 24.dp, end = 12.dp, top = 16.dp)
                                    .semantics { heading() })
                            })
                    }
                    // as the time picker's: in words, under the picker, rather than Material's icon in the picker's
                    // corner
                    val typing = state.displayMode == DisplayMode.Input
                    AppTextButton(onClick = {
                        state.displayMode = if (typing) DisplayMode.Picker else DisplayMode.Input
                    }) {
                        Text(stringResource(if (typing) R.string.picker_calendar else R.string.one_off_keyboard))
                    }
                }
                FlowRow(Modifier.align(Alignment.End).padding(bottom = 8.dp, end = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    AppTextButton(onClick = closed) {
                        Text(stringResource(R.string.action_cancel))
                    }
                    AppTextButton(onClick = {
                        state.selectedDateMillis?.let(PickerDays::day)?.let(chosen)
                        closed()
                    }) {
                        Text(stringResource(R.string.action_ok))
                    }
                }
            }
        }
    }
}

/** The width of Material's date picker, which it never draws narrower. */
private val PICKER_WIDTH = 360.dp

/** The most height Material gives its date picker's dialog. */
private val PICKER_HEIGHT = 568.dp
