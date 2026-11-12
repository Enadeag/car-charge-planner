package nl.enadeag.carchargeplanner.android.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import nl.enadeag.carchargeplanner.android.R
import nl.enadeag.carchargeplanner.android.home.SaveState
import nl.enadeag.carchargeplanner.android.home.TripDraft
import nl.enadeag.carchargeplanner.android.home.TripEntry
import nl.enadeag.carchargeplanner.android.ui.components.BarPage
import nl.enadeag.carchargeplanner.android.ui.components.DateDialog
import nl.enadeag.carchargeplanner.android.ui.components.DiscardChangesDialog
import nl.enadeag.carchargeplanner.android.ui.components.DISTANCE_UNIT
import nl.enadeag.carchargeplanner.android.ui.components.FocusOnWindow
import nl.enadeag.carchargeplanner.android.ui.components.SaveAction
import nl.enadeag.carchargeplanner.android.ui.components.SaveFailure
import nl.enadeag.carchargeplanner.android.ui.components.TimeButton
import nl.enadeag.carchargeplanner.android.ui.components.TimeDialog
import nl.enadeag.carchargeplanner.engine.dashboard.DayName

/** Where the focus is in the entry, so it comes back there when a picker or the question over the entry closes. */
private enum class Spot {
    /** The title, after a save that failed. */
    TOP,

    /** The departure day's button, where the entry starts. */
    DEPARTURE_DAY,

    /** The departure time's button. */
    DEPARTURE_TIME,

    /** The return day's button, after a problem with the return. */
    RETURN_DAY,

    /** The return time's button. */
    RETURN_TIME,

    /** The distance field, after a problem with the distance. */
    DISTANCE,

    /** "I can charge at the destination". */
    CAN_CHARGE,
}

/** Which picker is open over the entry. */
private enum class Picking(val spot: Spot) {
    /** The departure day's date picker. */
    DEPARTURE_DAY(Spot.DEPARTURE_DAY),

    /** The departure time's time picker. */
    DEPARTURE_TIME(Spot.DEPARTURE_TIME),

    /** The return day's date picker. */
    RETURN_DAY(Spot.RETURN_DAY),

    /** The return time's time picker. */
    RETURN_TIME(Spot.RETURN_TIME),
}

/**
 * The entry of a trip, while it is open: Material's full-screen dialog, with the title ("Add trip" or "Change trip")
 * as the heading in the top app bar, Close in Up's place and Save at its end. Under the bar, when the home's time
 * zone is not the phone's, the line "Times in <zone>"; then the departure's day and time and the return's, each a
 * button that opens Material's date or time picker, two to a row that wraps to one per line at a large font; the
 * distance there and back, in km, whose keyboard's Done saves; and "I can charge at the destination". A day is named
 * as the Trips card names it, from the home's today when the entry opened; a time as the phone's 12- or 24-hour clock
 * writes it. Why Save refused the trip shows under the return's buttons, which carry it, or under the distance, in a
 * polite live region, and the focus goes there; a save that failed says so at the top, with the English details, in
 * a live region read at once, and the focus goes to the title. The focus starts on the departure day, and comes back
 * to the control that had it when a picker or "Discard changes?" closes. While a change of the trips runs, Save is
 * held, and while its own save runs it shows its progress and nothing can be changed or closed. Back and Close ask
 * "Discard changes?" when something changed; a tap beside the dialog does nothing, as there is no beside.
 *
 * @param phoneZone the phone's time zone, against which the home's is named
 */
@Composable
internal fun TripEntryDialog(entry: TripEntry, phoneZone: ZoneId) {
    val shown by entry.draft.collectAsStateWithLifecycle()
    val draft = shown ?: return
    val problem by entry.problem.collectAsStateWithLifecycle()
    val refusals by entry.refusals.collectAsStateWithLifecycle()
    val saving by entry.saving.collectAsStateWithLifecycle()
    val asked by entry.discardAsked.collectAsStateWithLifecycle()
    val held by entry.busy.collectAsStateWithLifecycle()
    val savingNow = saving == SaveState.Saving
    val failed = saving as? SaveState.Failed
    val words = dashboardWords(draft.zone)
    var picking by rememberSaveable { mutableStateOf<Picking?>(null) }
    var spot by rememberSaveable { mutableStateOf(Spot.DEPARTURE_DAY) }
    val focus = remember { Spot.entries.associateWith { FocusRequester() } }
    val problemText = problem?.let { stringResource(problemId(it)) }
    val returnProblem = problemText.takeIf { problem?.field == TripEntry.Field.RETURN }
    val distanceProblem = problemText.takeIf { problem?.field == TripEntry.Field.DISTANCE }
    val fields = draft.fields
    Dialog(onDismissRequest = { entry.back() }, properties = DialogProperties(usePlatformDefaultWidth = false,
        dismissOnClickOutside = false, decorFitsSystemWindows = false)) {
        // in the dialog's own window, also when a picker or the question over it closes
        FocusOnWindow { focus.getValue(spot) }
        LaunchedEffect(refusals) {
            val field = problem?.field ?: return@LaunchedEffect
            spot = if (field == TripEntry.Field.RETURN) Spot.RETURN_DAY else Spot.DISTANCE
            focus.getValue(spot).requestFocus()
        }
        LaunchedEffect(failed) {
            if (failed != null) {
                spot = Spot.TOP
                focus.getValue(Spot.TOP).requestFocus()
            }
        }
        val noted = { at: Spot -> Modifier.onFocusChanged { if (it.isFocused) spot = at } }
        BarPage(stringResource(if (draft.mode == TripDraft.Mode.ADD) R.string.trip_add_title
            else R.string.trip_change_title), focus.getValue(Spot.TOP), onUp = null,
            actions = { SaveAction(saving = savingNow, held = held) { entry.save() } },
            lead = {
                Column(Modifier.padding(bottom = if (failed != null) 16.dp else 0.dp)) {
                    SaveFailure(stringResource(R.string.trip_not_saved), failed?.detail)
                }
            },
            scrollToTop = failed, onClose = { entry.back() }, closeEnabled = !savingNow) {
            if (draft.zone != phoneZone) {
                Text(stringResource(R.string.home_zone, draft.zone.id))
            }
            Moment(Named(stringResource(R.string.trip_departure_day), dayName(words, draft.today, fields.departureDay),
                focus.getValue(Spot.DEPARTURE_DAY), noted(Spot.DEPARTURE_DAY)) { picking = Picking.DEPARTURE_DAY },
                Named(stringResource(R.string.trip_departure_time), words.time(fields.departureTime),
                    focus.getValue(Spot.DEPARTURE_TIME), noted(Spot.DEPARTURE_TIME)) {
                    picking = Picking.DEPARTURE_TIME
                }, null, !savingNow)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Moment(Named(stringResource(R.string.trip_return_day), dayName(words, draft.today, fields.returnDay),
                    focus.getValue(Spot.RETURN_DAY), noted(Spot.RETURN_DAY)) { picking = Picking.RETURN_DAY },
                    Named(stringResource(R.string.trip_return_time), words.time(fields.returnTime),
                        focus.getValue(Spot.RETURN_TIME), noted(Spot.RETURN_TIME)) {
                        picking = Picking.RETURN_TIME
                    }, returnProblem, !savingNow)
                // under the return's buttons; the live region stays while empty, so a refusal that comes in is read
                // while the focus moves to the return day
                Column(Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }) {
                    returnProblem?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            DistanceField(fields.distance, distanceProblem, savingNow, focus.getValue(Spot.DISTANCE),
                noted(Spot.DISTANCE), entry::distance) { entry.save() }
            CanCharge(fields.canCharge, !savingNow, focus.getValue(Spot.CAN_CHARGE).let {
                noted(Spot.CAN_CHARGE).focusRequester(it)
            }, entry::canCharge)
        }
    }
    when (val open = picking) {
        Picking.DEPARTURE_DAY, Picking.RETURN_DAY -> {
            val departure = open == Picking.DEPARTURE_DAY
            DateDialog(stringResource(if (departure) R.string.trip_departure_day else R.string.trip_return_day),
                if (departure) fields.departureDay else fields.returnDay, draft.days,
                { if (departure) entry.departureDay(it) else entry.returnDay(it) }) {
                spot = open.spot
                picking = null
            }
        }
        Picking.DEPARTURE_TIME, Picking.RETURN_TIME -> {
            val departure = open == Picking.DEPARTURE_TIME
            TimeDialog(stringResource(if (departure) R.string.trip_departure_time else R.string.trip_return_time),
                if (departure) fields.departureTime else fields.returnTime,
                { time: LocalTime -> if (departure) entry.departureTime(time) else entry.returnTime(time) }) {
                spot = open.spot
                picking = null
            }
        }
        null -> Unit
    }
    if (asked) {
        DiscardChangesDialog(stringResource(R.string.trip_discard_text), entry::keepEditing, entry::discard)
    }
}

/**
 * @property name what it is, such as "Departure day"
 * @property noted notes that it has the focus
 */
private class Named(val name: String, val value: String, val focus: FocusRequester, val noted: Modifier,
    val onClick: () -> Unit)

/**
 * A moment of the trip, the departure or the return: its day's button and its time's, side by side while both fit
 * their words, otherwise each on a line of its own.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Moment(day: Named, time: Named, problem: String?, enabled: Boolean) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(day, time).forEach { button ->
            TimeButton(button.name, button.value, button.focus, problem, enabled,
                Modifier.weight(1f).then(button.noted), button.onClick)
        }
    }
}

/**
 * The distance there and back: Material's field with its label, "km" after it and a decimal keyboard, whose Done
 * saves; a screen reader reads its label, what is typed and the unit, as the field shows them. Why Save refused it
 * shows under it, in a polite live region of its own, and the field carries it. While the trip is saved, it keeps the
 * focus but takes no typing.
 */
@Composable
private fun DistanceField(text: String, problem: String?, saving: Boolean, focus: FocusRequester, noted: Modifier,
    onEdit: (String) -> Unit, onDone: () -> Unit) {
    OutlinedTextField(
        value = text,
        onValueChange = onEdit,
        label = { Text(stringResource(R.string.trip_distance_field)) },
        suffix = { Text(DISTANCE_UNIT) },
        singleLine = true,
        readOnly = saving,
        isError = problem != null,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        supportingText = {
            // a node of its own, outside the field's, which stays while empty, so a problem that comes in is read
            Column(Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }) {
                problem?.let { Text(it) }
            }
        },
        modifier = Modifier.fillMaxWidth().focusRequester(focus).then(noted).semantics {
            if (problem != null) {
                error(problem)
            }
        },
    )
}

/**
 * "I can charge at the destination", as Material's checkbox in a row that toggles as a whole, at least 48 dp high,
 * its words wrapping.
 */
@Composable
private fun CanCharge(checked: Boolean, enabled: Boolean, modifier: Modifier, onChange: (Boolean) -> Unit) {
    Row(modifier.fillMaxWidth().heightIn(min = 48.dp).focusProperties { canFocus = true }
        .toggleable(value = checked, enabled = enabled, role = Role.Checkbox, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = null, enabled = enabled)
        Text(stringResource(R.string.trip_can_charge), modifier = Modifier.padding(start = 16.dp))
    }
}

/** A day on its button, as the Trips card names it: "Today", "Tomorrow" or the short date, from the entry's today. */
private fun dayName(words: DashboardWords, today: LocalDate, day: LocalDate): String =
    words.date(DayName.of(day, today))

private fun problemId(problem: TripDraft.Problem): Int = when (problem) {
    TripDraft.Problem.RETURN_NOT_AFTER_DEPARTURE -> R.string.trip_return_before_departure
    TripDraft.Problem.PASSED -> R.string.trip_passed
    TripDraft.Problem.DISTANCE_NOT_A_NUMBER -> R.string.trip_distance_not_a_number
    TripDraft.Problem.DISTANCE_NOT_POSITIVE -> R.string.trip_distance_not_positive
}
