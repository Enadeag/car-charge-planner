package nl.enadeag.carchargeplanner.android.ui.home

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import java.time.ZoneId
import nl.enadeag.carchargeplanner.android.R
import nl.enadeag.carchargeplanner.android.home.Dashboard
import nl.enadeag.carchargeplanner.android.home.ListedTrip
import nl.enadeag.carchargeplanner.android.home.TripChoice
import nl.enadeag.carchargeplanner.android.home.TripEntry
import nl.enadeag.carchargeplanner.android.ui.components.HeldButton
import nl.enadeag.carchargeplanner.android.ui.components.NoneLine
import nl.enadeag.carchargeplanner.android.ui.components.RowFocus
import nl.enadeag.carchargeplanner.android.ui.components.RowLine
import nl.enadeag.carchargeplanner.engine.dashboard.DashboardDay
import nl.enadeag.carchargeplanner.engine.trip.Trip

/**
 * A trip as the list names it, by the dashboard shown last; before any was shown, as the entry that saved it reads
 * it, in its zone and by the clock at Save; empty when neither is known.
 */
@Composable
internal fun tripWords(shown: Shown): (Trip, TripEntry.Entered?) -> String {
    val resources = LocalResources.current
    val locale = LocalConfiguration.current.locales[0]
    val twentyFour = DateFormat.is24HourFormat(LocalContext.current)
    return { trip, entered ->
        val dashboard = shown.dashboard
        when {
            dashboard != null ->
                DashboardWords(resources, locale, twentyFour, dashboard.zone).trip(dashboard.describe(trip))
            entered != null -> DashboardWords(resources, locale, twentyFour, entered.zone).trip(entered.trip)
            else -> ""
        }
    }
}

/**
 * What a day's Trips card changes the trips with.
 *
 * @property entry the choices and Remove
 * @property busy true while a change runs, so the choices and Remove are off
 * @property pending the choice being saved, shown at once in its new state; null while none is
 * @property going the trip whose Remove was pressed, whose neighbour takes the focus once it is out of the list
 * @property restoring the trip an Undo is adding back, whose row takes the focus once it is in the list
 * @property keys what each trip's row is keyed by
 * @property add moves the focus to the Add of the page in view
 * @property openers moves the focus to a trip's Change on the page in view
 */
internal class TripAction(val entry: TripEntry, val busy: Boolean, val pending: TripEntry.Pending?,
    val going: Going<Trip>, val restoring: Restoring<Trip>, val keys: TripKeys, val add: FocusRequester,
    val openers: TripOpeners)

/**
 * The Change of each trip on the page in view, which drawing never reads, so the focus goes back to it when the entry
 * closes.
 */
internal class TripOpeners {
    private val changes = mutableMapOf<Trip, FocusRequester>()

    /**
     * Notes a trip's Change as it shows on the page in view.
     *
     * @param trip the trip
     * @param focus moves the focus to its Change
     */
    fun shown(trip: Trip, focus: FocusRequester) {
        changes[trip] = focus
    }

    /**
     * Forgets a trip's Change that no longer shows.
     *
     * @param trip the trip
     * @param focus the focus it was noted with, so a later row of the same trip stays
     */
    fun gone(trip: Trip, focus: FocusRequester) {
        changes.remove(trip, focus)
    }

    /**
     * Moves the focus to a trip's Change.
     *
     * @param trip the trip
     * @return false when its Change is not on the page in view, so nothing moved
     */
    fun focus(trip: Trip): Boolean = changes[trip]?.requestFocus() == true
}

/**
 * What each trip's row is keyed by, which drawing reads but a choice pressed writes: a trip by itself, and a trip a
 * choice changed by the key of the trip it was. So the row and the focus on its checkbox stay as a choice saves, also
 * when the store puts the changed trip after another that leaves at the same time, and two trips that differ only in
 * a choice keep a row each. A trip a choice was to make but did not, as its save failed, may come later another way,
 * so a trip takes the key of the one it was only while that one is not listed too, and no two rows share a key. A
 * choice pressed forgets the trips its page no longer lists. The keys are not kept when the home is made again, such
 * as when the phone turns, which is fine, as the focus is lost then anyway.
 */
internal class TripKeys {
    private val before = mutableMapOf<Trip, Trip>()

    /**
     * The keys of a page's rows.
     *
     * @param trips the trips the page lists, each once
     * @return the key of each trip's row, in the same order, no two the same
     */
    fun of(trips: List<Trip>): List<Trip> {
        val taken = mutableSetOf<Trip>()
        return trips.map { trip -> before[trip]?.takeIf { it !in trips && taken.add(it) } ?: trip }
    }

    /**
     * A choice is being saved, so the changed trip takes the row of the trip it was.
     *
     * @param key the key of the row whose choice was pressed
     * @param changed the trip with the choice changed
     * @param listed the trips the page lists
     */
    fun changing(key: Trip, changed: Trip, listed: List<Trip>) {
        before.keys.retainAll(listed.toSet())
        before[changed] = key
    }
}

/**
 * The trips the car is away for on this page's day, under the heading "Trips", as the desktop's card: each with its
 * times and distance, its two choices as checkbox rows that save at once, and its Remove, which a screen reader says
 * with the trip; "No trip entered for this day." when there is none. After a Remove the focus goes, once the trip is
 * gone, to the trip before it, or the one after, or the line that says there is none, as in the away list.
 *
 * @param dashboard the dashboard, with the trips and the home's time zone
 * @param day the page's day, whose trips are listed
 * @param words the dashboard's words
 * @param trips the choices, Remove and whether a change runs
 * @param inView true on the page in view, where a change was made and the focus follows it
 */
@Composable
internal fun TripsCard(dashboard: Dashboard, day: DashboardDay, words: DashboardWords, trips: TripAction,
    inView: Boolean) {
    val focus = remember { RowFocus<Trip>() }
    val listed = dashboard.tripsOn(day)
    val rows = listed.map { it.trip }
    LaunchedEffect(rows) {
        // only the page in view: the change was made on it, and a page beside it has its own rows
        if (!inView) {
            return@LaunchedEffect
        }
        val pressed = trips.going.row
        if (pressed != null && pressed !in rows) {
            focus.removing(trips.going.rows, pressed)
            trips.going.row = null
        }
        val back = trips.restoring.row
        if (back != null && back in rows) {
            focus.next = back
            trips.restoring.row = null
        }
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.trips_heading), style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f).semantics { heading() })
                // as the away list's Add: it may take the focus also in touch mode, so the focus comes back to it
                val opener = if (inView) Modifier.focusProperties { canFocus = true }.focusRequester(trips.add)
                    else Modifier
                val spoken = stringResource(R.string.trip_add_spoken)
                HeldButton(busy = trips.busy, onClick = { trips.entry.add(day.name().date(), dashboard.zone) },
                    modifier = opener.semantics { contentDescription = spoken }) {
                    Text(stringResource(R.string.trip_add))
                }
            }
            if (listed.isEmpty()) {
                NoneLine(stringResource(R.string.trips_none), focus)
            }
            listed.zip(trips.keys.of(rows)).forEach { (trip, row) ->
                key(row) {
                    TripRow(trip, row, words, trips, focus, rows, dashboard.zone, inView)
                }
            }
        }
    }
}

/**
 * One trip of the Trips card: its line, its two choices, its Change, which opens the entry on it, and its Remove,
 * side by side at the end while they fit.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TripRow(listed: ListedTrip, key: Trip, words: DashboardWords, trips: TripAction,
    focus: RowFocus<Trip>, rows: List<Trip>, zone: ZoneId, inView: Boolean) {
    val trip = listed.trip
    val spoken = words.spokenTrip(listed)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        RowLine(words.trip(listed), focus, trip, Modifier.semantics { contentDescription = spoken })
        TripChoice.entries.forEach { choice ->
            val pending = trips.pending?.takeIf { it.trip == trip && it.choice == choice }
            ChoiceRow(stringResource(when (choice) {
                TripChoice.NOT_WITH_THIS_CAR -> R.string.trip_not_with_this_car
                TripChoice.CAN_CHARGE -> R.string.trip_can_charge
            }), spoken, pending?.on ?: choice.of(trip), trips.busy) {
                trips.keys.changing(key, choice.put(trip, it), rows)
                trips.entry.choose(trip, choice, it)
            }
        }
        val change = stringResource(R.string.trip_change_spoken, spoken)
        val remove = stringResource(R.string.trip_remove_spoken, spoken)
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
            // on the page in view it may take the focus also in touch mode, so the focus comes back to it, and a
            // screen reader with it, when the entry closes
            val opener = remember { FocusRequester() }
            if (inView) {
                DisposableEffect(trip, opener) {
                    trips.openers.shown(trip, opener)
                    onDispose { trips.openers.gone(trip, opener) }
                }
            }
            val reachable = if (inView) Modifier.focusProperties { canFocus = true }.focusRequester(opener)
                else Modifier
            HeldButton(busy = trips.busy, onClick = { trips.entry.change(trip, zone) },
                modifier = reachable.semantics { contentDescription = change }) {
                Text(stringResource(R.string.trip_change))
            }
            HeldButton(busy = trips.busy, onClick = {
                trips.going.row = trip
                trips.going.rows = rows
                trips.entry.remove(trip)
            }, modifier = Modifier.semantics { contentDescription = remove }) {
                Text(stringResource(R.string.trip_remove))
            }
        }
    }
}

/**
 * A choice as Material's checkbox in a row that toggles as a whole, at least 48 dp high, its words wrapping; a screen
 * reader says it with the trip, then whether it is checked. While a change runs it is off as [HeldButton] is, so it
 * keeps the focus.
 *
 * @param label the choice's words
 * @param trip the trip as a screen reader reads it
 * @param checked whether it is ticked
 * @param busy true while a change runs
 * @param onChange gets the new state
 */
@Composable
private fun ChoiceRow(label: String, trip: String, checked: Boolean, busy: Boolean, onChange: (Boolean) -> Unit) {
    val spoken = stringResource(R.string.trip_choice_spoken, label, trip)
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp)
        .toggleable(value = checked, role = Role.Checkbox, onValueChange = { if (!busy) onChange(it) })
        .semantics {
            contentDescription = spoken
            if (busy) {
                disabled()
            }
        }, verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = null, enabled = !busy)
        val colour = LocalContentColor.current
        Text(label, modifier = Modifier.padding(start = 16.dp),
            color = if (busy) colour.copy(alpha = DISABLED_ALPHA) else colour)
    }
}

/** Material's alpha for a disabled control's content, as its [Checkbox] draws when it is off. */
private const val DISABLED_ALPHA = 0.38f
