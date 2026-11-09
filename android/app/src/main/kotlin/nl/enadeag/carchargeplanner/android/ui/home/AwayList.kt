package nl.enadeag.carchargeplanner.android.ui.home

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import nl.enadeag.carchargeplanner.android.R
import nl.enadeag.carchargeplanner.android.home.AwayEntry
import nl.enadeag.carchargeplanner.android.home.Dashboard
import nl.enadeag.carchargeplanner.android.ui.components.HeldButton
import nl.enadeag.carchargeplanner.android.ui.components.NoneLine
import nl.enadeag.carchargeplanner.android.ui.components.RowFocus
import nl.enadeag.carchargeplanner.android.ui.components.RowLine
import nl.enadeag.carchargeplanner.engine.away.OneOffAwayPeriod
import nl.enadeag.carchargeplanner.engine.dashboard.DashboardDay

/**
 * A one-off away period as the list names it, by the dashboard shown last; before any was shown, as the entry that
 * saved it reads it, in its zone and by the clock at Save; empty when neither is known.
 */
@Composable
internal fun awayWords(shown: Shown): (OneOffAwayPeriod, AwayEntry.Entered?) -> String {
    val resources = LocalResources.current
    val locale = LocalConfiguration.current.locales[0]
    val twentyFour = DateFormat.is24HourFormat(LocalContext.current)
    return { period, entered ->
        val dashboard = shown.dashboard
        when {
            dashboard != null ->
                DashboardWords(resources, locale, twentyFour, dashboard.zone).away(dashboard.describe(period))
            entered != null -> DashboardWords(resources, locale, twentyFour, entered.zone).away(entered.away)
            else -> ""
        }
    }
}

/**
 * What a day's "Car away" list changes the periods with.
 *
 * @property entry the entry and Remove
 * @property focus moves the focus to the Add of the page in view
 * @property busy true while a change runs, so Add and Remove are off
 * @property going the period whose Remove was pressed, whose neighbour takes the focus once it is out of the list
 * @property restoring the period an Undo is adding back, whose row takes the focus once it is in the list
 */
internal class AwayAction(val entry: AwayEntry, val focus: FocusRequester, val busy: Boolean,
    val going: Going<OneOffAwayPeriod>, val restoring: Restoring<OneOffAwayPeriod>)

/**
 * The one-off away periods still to come, under the heading "Car away" with Add at the end of its row, which opens
 * the entry on this page's day; each period with its Remove, which a screen reader says with the period. After a
 * Remove the focus goes, once the period is gone, to the period before it, or the one after, or the line that says
 * there is none, so it does not fall off the page with the button that went.
 *
 * @param dashboard the dashboard, with the periods and the home's time zone
 * @param day the page's day, on which Add enters a period
 * @param words the dashboard's words
 * @param away the entry, Remove and whether a change runs
 * @param inView true on the page in view, whose Add the focus comes back to when the entry closes
 */
@Composable
internal fun AwayList(dashboard: Dashboard, day: DashboardDay, words: DashboardWords, away: AwayAction,
    inView: Boolean) {
    val focus = remember { RowFocus<OneOffAwayPeriod>() }
    val periods = dashboard.away.map { it.period }
    LaunchedEffect(periods) {
        // only the page in view: the change was made on it, and a page beside it has its own rows
        if (!inView) {
            return@LaunchedEffect
        }
        val pressed = away.going.row
        if (pressed != null && pressed !in periods) {
            focus.removing(away.going.rows, pressed)
            away.going.row = null
        }
        val back = away.restoring.row
        if (back != null && back in periods) {
            focus.next = back
            away.restoring.row = null
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.one_off_heading), style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f).semantics { heading() })
            // it may take the focus also in touch mode, where a button takes none, so the focus comes back to it,
            // and a screen reader with it, when the entry closes
            val opener = if (inView) Modifier.focusProperties { canFocus = true }.focusRequester(away.focus)
                else Modifier
            val spoken = stringResource(R.string.one_off_add_spoken)
            HeldButton(busy = away.busy, onClick = { away.entry.open(day.name().date(), dashboard.zone) },
                modifier = opener.semantics { contentDescription = spoken }) {
                Text(stringResource(R.string.one_off_add))
            }
        }
        if (dashboard.away.isEmpty()) {
            NoneLine(stringResource(R.string.one_off_none), focus)
        }
        dashboard.away.forEach { coming ->
            key(coming.period) {
                val text = words.away(coming)
                val spoken = stringResource(R.string.one_off_remove_spoken, text)
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RowLine(text, focus, coming.period, Modifier.weight(1f))
                    HeldButton(busy = away.busy, onClick = {
                        away.going.row = coming.period
                        away.going.rows = periods
                        away.entry.remove(coming.period)
                    }, modifier = Modifier.semantics { contentDescription = spoken }) {
                        Text(stringResource(R.string.one_off_remove))
                    }
                }
            }
        }
    }
}
