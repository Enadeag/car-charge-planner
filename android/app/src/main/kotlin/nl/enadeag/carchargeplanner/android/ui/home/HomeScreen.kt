package nl.enadeag.carchargeplanner.android.ui.home

import android.Manifest
import android.os.Build
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.ZoneId
import kotlinx.coroutines.launch
import nl.enadeag.carchargeplanner.android.R
import nl.enadeag.carchargeplanner.android.home.AwayEntry
import nl.enadeag.carchargeplanner.android.home.BatteryEntry
import nl.enadeag.carchargeplanner.android.home.Dashboard
import nl.enadeag.carchargeplanner.android.home.DayModel
import nl.enadeag.carchargeplanner.android.home.DayName
import nl.enadeag.carchargeplanner.android.home.EnergySource
import nl.enadeag.carchargeplanner.android.home.Footer
import nl.enadeag.carchargeplanner.android.home.GridAdviceModel
import nl.enadeag.carchargeplanner.android.home.HomeNotice
import nl.enadeag.carchargeplanner.android.home.HomeOutlook
import nl.enadeag.carchargeplanner.android.home.ListedTrip
import nl.enadeag.carchargeplanner.android.home.NotificationCard
import nl.enadeag.carchargeplanner.android.home.TripEntry
import nl.enadeag.carchargeplanner.android.store.SaveOutcome
import nl.enadeag.carchargeplanner.android.ui.LocalSourceColours
import nl.enadeag.carchargeplanner.android.ui.SourceColours
import nl.enadeag.carchargeplanner.android.ui.components.AppButton
import nl.enadeag.carchargeplanner.android.ui.components.AppOutlinedButton
import nl.enadeag.carchargeplanner.android.ui.components.AppTextButton
import nl.enadeag.carchargeplanner.android.ui.components.BarIconButton
import nl.enadeag.carchargeplanner.android.ui.components.BarScaffold
import nl.enadeag.carchargeplanner.android.ui.components.EnglishDetails
import nl.enadeag.carchargeplanner.android.ui.components.FailureVisuals
import nl.enadeag.carchargeplanner.android.ui.components.NoneLine
import nl.enadeag.carchargeplanner.android.ui.components.RowFocus
import nl.enadeag.carchargeplanner.android.ui.components.RowLine
import nl.enadeag.carchargeplanner.android.ui.components.inEnglish
import nl.enadeag.carchargeplanner.android.ui.notify.openNotificationSettings
import nl.enadeag.carchargeplanner.engine.away.OneOffAwayPeriod
import nl.enadeag.carchargeplanner.engine.trip.Trip

/**
 * The home after set-up: the dashboard. A tab per day of the plan, Today first, over a pager that swipes between them,
 * each day with its charge windows and figures; today's also with the grid advice; every day with its solar chart,
 * its battery bar, the coming one-off away periods and the forecast's footer. Refresh and Settings are the top app
 * bar's actions, and a pull asks for the plan too; coming back to the app asks again when the plan is old. The times
 * are in the home's time zone, named when it is not the phone's, on the phone's 12- or 24-hour clock. A Settings save
 * that no screen showed yet shows here until the owner closes it, in a live region. Today's battery card opens the
 * entry of the level the car shows; a level saved there says so in a snackbar, and when the entry closes the focus
 * goes back to the button that opened it. Each day's "Car away" list has Add, which opens the entry of a one-off away
 * period on that day, and a Remove per period, which acts at once; a change says so in a snackbar, Remove's with Undo,
 * and the list shows the stored periods at once while the plan is made again. Below the battery card, each day's
 * Trips card lists the trips the car is away for on that day, each with its two choices, which save at once, and its
 * Remove, which acts at once with Undo in a snackbar, as the away list's. A provider that refused its API key
 * gets a warning in the stale warning's place, also without a plan. While notifications are not allowed, a card asks
 * for them, after the warnings and before the day; it is looked at again each time the owner comes back to the app.
 *
 * @param outlook the plan it shows
 * @param notice the outcome of a Settings save not shown yet
 * @param battery the entry of the battery level
 * @param away the changes to the one-off away periods
 * @param trips the changes to the trips
 * @param phoneZone the phone's time zone as it is now, read each time the plan is shown
 * @param card the card that asks for notifications
 * @param onSettings opens Settings
 */
@Composable
internal fun HomeScreen(outlook: HomeOutlook, notice: HomeNotice, battery: BatteryEntry, away: AwayEntry,
    trips: TripEntry, phoneZone: () -> ZoneId, card: NotificationCard, onSettings: () -> Unit) {
    val state by outlook.state.collectAsStateWithLifecycle()
    val refreshing by outlook.refreshing.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_START) {
        outlook.returned()
        card.recheck()
    }
    val heading = remember { FocusRequester() }
    LaunchedEffect(Unit) { heading.requestFocus() }
    val closed: () -> Unit = { heading.requestFocus() }
    val update = remember { FocusRequester() }
    val snackbars = remember { SnackbarHostState() }
    BatteryOutcome(battery, snackbars) {
        // the button is on today's page, which may be out of view; the title then takes the focus
        if (!update.requestFocus()) {
            heading.requestFocus()
        }
    }
    val add = remember { FocusRequester() }
    val shown = remember { Shown() }
    val planned = (state as? HomeOutlook.State.Planned)?.dashboard
    SideEffect { planned?.let { shown.dashboard = it } }
    val going = remember { Going<OneOffAwayPeriod>() }
    val restoring = remember { Restoring<OneOffAwayPeriod>() }
    AwayOutcome(away, snackbars, going, restoring, awayWords(shown)) {
        if (!add.requestFocus()) {
            heading.requestFocus()
        }
    }
    val busy by away.busy.collectAsStateWithLifecycle()
    val tripGoing = remember { Going<Trip>() }
    val tripRestoring = remember { Restoring<Trip>() }
    val tripKeys = remember { TripKeys() }
    TripOutcome(trips, snackbars, tripGoing, tripRestoring, tripWords(shown))
    val tripsBusy by trips.busy.collectAsStateWithLifecycle()
    val pending by trips.pending.collectAsStateWithLifecycle()
    BarScaffold(stringResource(R.string.app_name), heading, onUp = null, snackbars = snackbars, actions = {
        BarIconButton(Icons.Filled.Refresh, stringResource(R.string.home_refresh),
            enabled = state != HomeOutlook.State.Asking && !refreshing) { outlook.refresh() }
        BarIconButton(Icons.Filled.Settings, stringResource(R.string.home_settings), onClick = onSettings)
    }) {
        val askCard: @Composable () -> Unit = { NotificationsCard(card, closed) }
        when (val now = state) {
            is HomeOutlook.State.Planned -> Days(now.dashboard, refreshing, phoneZone(), outlook::refresh,
                BatteryAction(battery::open, update), AwayAction(away, add, busy, going, restoring),
                TripAction(trips, tripsBusy, pending, tripGoing, tripRestoring, tripKeys), askCard) {
                Notice(notice, closed)
            }
            else -> Refreshable(refreshing, outlook::refresh) {
                Notice(notice, closed)
                KeyRefused(keyRefusedBy(now))
                askCard()
                NoPlan(now, outlook::refresh)
            }
        }
    }
    BatteryEntryDialog(battery)
    AwayEntryDialog(away)
}

/**
 * The plan's dashboard shown last, which drawing never reads: a change's snackbar names its period by it.
 *
 * @property dashboard the dashboard; null until a plan was shown
 */
private class Shown(var dashboard: Dashboard? = null)

/**
 * A one-off away period as the list names it, by the dashboard shown last; before any was shown, as the entry that
 * saved it reads it, in its zone and by the clock at Save; empty when neither is known.
 */
@Composable
private fun awayWords(shown: Shown): (OneOffAwayPeriod, AwayEntry.Entered?) -> String {
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
 * What a change to the one-off away periods came to, beside the entry itself, each said once in a snackbar, which a
 * screen reader reads when it comes: an added period; a removed one, with Undo, which adds it back; a Remove or an Undo
 * that failed, with the English details and a button to close it. When the entry closes, however it does, the focus
 * moves on.
 *
 * @param away the changes
 * @param snackbars where the snackbar shows
 * @param going the Remove that was pressed, forgotten when it failed
 * @param restoring gets the period an Undo adds back, so the list puts the focus on its row, forgotten when it failed
 * @param named a period as the list names it, given how the entry that saved it read it, when it did
 * @param closed moves the focus once the entry closed
 */
@Composable
private fun AwayOutcome(away: AwayEntry, snackbars: SnackbarHostState, going: Going<OneOffAwayPeriod>,
    restoring: Restoring<OneOffAwayPeriod>, named: (OneOffAwayPeriod, AwayEntry.Entered?) -> String,
    closed: () -> Unit) {
    val resources = LocalResources.current
    val name by rememberUpdatedState(named)
    LaunchedEffect(away) {
        away.notice.collect { notice ->
            if (notice == null) {
                return@collect
            }
            away.noticeShown()
            // a change that failed leaves the list as it was, so no later change of the list moves the focus for it
            when (notice) {
                is AwayEntry.Notice.NotRemoved -> going.row = null
                is AwayEntry.Notice.NotSaved -> restoring.row = null
                else -> Unit
            }
            snackbars.currentSnackbarData?.dismiss()
            launch {
                when (notice) {
                    is AwayEntry.Notice.Saved -> snackbars.showSnackbar(resources.getString(R.string.one_off_saved,
                        name(notice.period, notice.entered)))
                    is AwayEntry.Notice.Removed -> {
                        // it stays until the next notice or Close: Material takes one with an action away after about
                        // 10 s, also with TalkBack, which is too soon to find Undo
                        val undo = snackbars.showSnackbar(resources.getString(R.string.one_off_removed,
                            name(notice.period, null)), resources.getString(R.string.action_undo),
                            withDismissAction = true, duration = SnackbarDuration.Indefinite)
                        if (undo == SnackbarResult.ActionPerformed) {
                            restoring.row = notice.period
                            away.undo(notice.period)
                        }
                    }
                    is AwayEntry.Notice.NotRemoved -> snackbars.showSnackbar(
                        FailureVisuals(resources.getString(R.string.one_off_not_removed), notice.detail))
                    is AwayEntry.Notice.NotSaved -> snackbars.showSnackbar(
                        FailureVisuals(resources.getString(R.string.one_off_not_saved), notice.detail))
                }
            }
        }
    }
    val draft by away.draft.collectAsStateWithLifecycle()
    AfterClose(draft != null, closed)
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
private class AwayAction(val entry: AwayEntry, val focus: FocusRequester, val busy: Boolean,
    val going: Going<OneOffAwayPeriod>, val restoring: Restoring<OneOffAwayPeriod>)

/**
 * The row an Undo is adding back, a one-off away period or a trip, which drawing never reads: the list on the page in
 * view puts the focus on it once it shows; an Undo that failed forgets it.
 *
 * @param K what names a row
 * @property row the row; null when no Undo is under way
 */
private class Restoring<K>(var row: K? = null)

/**
 * What the battery entry's save came to, beside the entry itself: a level saved says so in a snackbar, which a screen
 * reader reads when it comes, once, also after turning the phone; and when the entry closes, however it does, the
 * focus moves on.
 *
 * @param battery the entry
 * @param snackbars where the snackbar shows
 * @param closed moves the focus once the entry closed
 */
@Composable
private fun BatteryOutcome(battery: BatteryEntry, snackbars: SnackbarHostState, closed: () -> Unit) {
    val saved = stringResource(R.string.battery_saved)
    LaunchedEffect(battery) {
        battery.savedToShow.collect { now ->
            if (now) {
                battery.savedShown()
                snackbars.currentSnackbarData?.dismiss()
                launch { snackbars.showSnackbar(saved) }
            }
        }
    }
    val open by battery.open.collectAsStateWithLifecycle()
    AfterClose(open, closed)
}

/**
 * Calls [closed] once an entry dialog that was open is gone, however it closed.
 *
 * @param open whether the entry is open
 * @param closed moves the focus on
 */
@Composable
private fun AfterClose(open: Boolean, closed: () -> Unit) {
    val seen = remember { Seen(open) }
    LaunchedEffect(open) {
        if (seen.open && !open) {
            // the dialog's window gives the focus back once it is gone, which this waits for
            withFrameNanos { }
            closed()
        }
        seen.open = open
    }
}

/**
 * Whether an entry dialog was open when last looked at, which drawing never reads.
 *
 * @property open true when it was
 */
private class Seen(var open: Boolean)

/** A tab per day over a pager, kept in step: a tab's click moves the pager, a swipe moves the tab. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ColumnScope.Days(dashboard: Dashboard, refreshing: Boolean, phoneZone: ZoneId, refresh: () -> Unit,
    battery: BatteryAction, away: AwayAction, trips: TripAction, card: @Composable () -> Unit,
    notice: @Composable () -> Unit) {
    val words = dashboardWords(dashboard.zone)
    val days = dashboard.days
    val pager = rememberPagerState { days.size }
    val scope = rememberCoroutineScope()
    val selected = pager.currentPage.coerceIn(0, days.lastIndex)
    PrimaryScrollableTabRow(selectedTabIndex = selected) {
        days.forEachIndexed { index, day ->
            val name = words.title(day.name)
            Tab(selected = index == selected, onClick = { scope.launch { pager.animateScrollToPage(index) } },
                text = { Text(words.tab(day)) }, modifier = Modifier.semantics { contentDescription = name })
        }
    }
    PullToRefreshBox(refreshing, refresh, Modifier.weight(1f).fillMaxWidth()) {
        HorizontalPager(pager, Modifier.fillMaxSize(), verticalAlignment = Alignment.Top) { index ->
            val day = days[index]
            Page {
                // once, on the day in view: a pager may draw its neighbours too, and the notice is one live region
                // with one Close
                if (index == selected) {
                    notice()
                }
                // as the notice: once, on the day in view
                if (index == selected && dashboard.footer.stale) {
                    Warning(listOf(stringResource(R.string.stale_warning, words.time(dashboard.footer.fetchedAt))))
                }
                if (index == selected) {
                    KeyRefused(dashboard.keyRefusedBy)
                }
                if (index == selected) {
                    card()
                }
                if (dashboard.zone != phoneZone) {
                    Text(stringResource(R.string.home_zone, dashboard.zone.id))
                }
                DayCard(day, words)
                dashboard.adviceOn(day)?.let { GridAdviceCard(it, words) }
                SolarCard(day, words)
                BatteryCard(day, dashboard.levels, words, battery.takeIf { day.name == DayName.Today })
                TripsCard(dashboard, day, words, trips, inView = index == selected)
                AwayList(dashboard, day, words, away, inView = index == selected)
                FooterLine(dashboard.footer, words)
            }
        }
    }
}

/** A page that a pull refreshes, for the home without a plan. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ColumnScope.Refreshable(refreshing: Boolean, refresh: () -> Unit,
    content: @Composable ColumnScope.() -> Unit) {
    PullToRefreshBox(refreshing, refresh, Modifier.weight(1f).fillMaxWidth()) {
        Page(content)
    }
}

/** One column that scrolls, with the app's spacing. */
@Composable
private fun Page(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp), content = content)
}

/** The home while the first plan is made, or why there is none, with Try again. */
@Composable
private fun NoPlan(state: HomeOutlook.State, tryAgain: () -> Unit) {
    when (state) {
        HomeOutlook.State.Asking -> Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            Text(stringResource(R.string.home_asking), modifier = Modifier.padding(start = 8.dp))
        }
        is HomeOutlook.State.NoForecast -> NoForecast(tryAgain) {
            Text(stringResource(R.string.no_forecast_why))
            EnglishDetails(state.reason)
        }
        is HomeOutlook.State.NoDays -> {
            NoForecast(tryAgain) { Text(stringResource(R.string.no_forecast_no_days)) }
            FooterLine(state.footer, dashboardWords(state.zone))
        }
        is HomeOutlook.State.Planned -> Unit
    }
}

/** A card that says there is no forecast, why, and Try again. */
@Composable
private fun NoForecast(tryAgain: () -> Unit, why: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.no_forecast), style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { heading() })
            why()
            AppButton(onClick = tryAgain) { Text(stringResource(R.string.action_try_again)) }
        }
    }
}

/**
 * A day's card: its name and what it holds, the main charge window and what to set for it, the figures by source,
 * and its note.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DayCard(day: DayModel, words: DashboardWords) {
    val sources = LocalSourceColours.current
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(words.title(day.name), style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { heading() })
            Text(words.heading(day), style = MaterialTheme.typography.titleMedium)
            day.main?.let { main ->
                val spoken = words.spokenSpan(main.window)
                Text(words.span(main.window), style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.semantics { contentDescription = spoken })
                FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(stringResource(R.string.day_set, main.window.amps(), words.decimal(main.window.powerKw())))
                    Text(stringResource(R.string.day_energy, words.decimal(main.energyKwh)))
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                day.figures.forEach {
                    SourceChip(words.figure(it), sources.of(it), spoken = words.spokenFigure(it))
                }
            }
            val note = words.note(day)
            if (note.isNotEmpty()) {
                Text(note, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/** Grid advice, on today's tab: how much, when, why and what to set. */
@Composable
private fun GridAdviceCard(advice: GridAdviceModel, words: DashboardWords) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SourceChip(stringResource(R.string.advice_label), LocalSourceColours.current.of(EnergySource.GRID),
                modifier = Modifier.semantics { heading() })
            Text(words.adviceTitle(advice), style = MaterialTheme.typography.titleMedium)
            Text(words.adviceDetail(advice), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

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
private fun AwayList(dashboard: Dashboard, day: DayModel, words: DashboardWords, away: AwayAction, inView: Boolean) {
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
            HeldButton(busy = away.busy, onClick = { away.entry.open(day.date, dashboard.zone) },
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

/**
 * A trip as the list names it, by the dashboard shown last; empty before any was shown, which a change of a trip
 * never is, as the trips are listed only with a plan.
 */
@Composable
private fun tripWords(shown: Shown): (Trip) -> String {
    val resources = LocalResources.current
    val locale = LocalConfiguration.current.locales[0]
    val twentyFour = DateFormat.is24HourFormat(LocalContext.current)
    return { trip ->
        shown.dashboard?.let { DashboardWords(resources, locale, twentyFour, it.zone).trip(it.describe(trip)) }
            .orEmpty()
    }
}

/**
 * What a change to the trips came to, each said once in a snackbar, which a screen reader reads when it comes: a
 * removed trip, with Undo, which adds it back; a trip added back; a choice, a Remove or an Undo that failed, with the
 * English details and a button to close it. A choice saved says nothing, as its checkbox shows it.
 *
 * @param trips the changes
 * @param snackbars where the snackbar shows
 * @param going the Remove that was pressed, forgotten when it failed
 * @param restoring gets the trip an Undo adds back, so the list puts the focus on its row, forgotten when it failed
 * @param named a trip as the list names it
 */
@Composable
private fun TripOutcome(trips: TripEntry, snackbars: SnackbarHostState, going: Going<Trip>,
    restoring: Restoring<Trip>, named: (Trip) -> String) {
    val resources = LocalResources.current
    val name by rememberUpdatedState(named)
    LaunchedEffect(trips) {
        trips.notice.collect { notice ->
            if (notice == null) {
                return@collect
            }
            trips.noticeShown()
            // a change that failed leaves the list as it was, so no later change of the list moves the focus for it
            when (notice) {
                is TripEntry.Notice.NotRemoved -> going.row = null
                is TripEntry.Notice.NotSaved -> restoring.row = null
                else -> Unit
            }
            snackbars.currentSnackbarData?.dismiss()
            launch {
                when (notice) {
                    is TripEntry.Notice.Saved ->
                        snackbars.showSnackbar(resources.getString(R.string.trip_saved, name(notice.trip)))
                    is TripEntry.Notice.Removed -> {
                        // as the away list's: it stays until the next notice or Close, so Undo can be found
                        val undo = snackbars.showSnackbar(resources.getString(R.string.trip_removed,
                            name(notice.trip)), resources.getString(R.string.action_undo),
                            withDismissAction = true, duration = SnackbarDuration.Indefinite)
                        if (undo == SnackbarResult.ActionPerformed) {
                            restoring.row = notice.trip
                            trips.undo(notice.trip)
                        }
                    }
                    is TripEntry.Notice.NotRemoved -> snackbars.showSnackbar(
                        FailureVisuals(resources.getString(R.string.trip_not_removed), notice.detail))
                    is TripEntry.Notice.NotSaved -> snackbars.showSnackbar(
                        FailureVisuals(resources.getString(R.string.trip_not_saved), notice.detail))
                }
            }
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
 */
private class TripAction(val entry: TripEntry, val busy: Boolean, val pending: TripEntry.Pending?,
    val going: Going<Trip>, val restoring: Restoring<Trip>, val keys: TripKeys)

/**
 * What each trip's row is keyed by, which drawing reads but a choice pressed writes: a trip by itself, and a trip a
 * choice changed by the key of the trip it was. So the row and the focus on its checkbox stay as a choice saves, also
 * when the store puts the changed trip after another that leaves at the same time, and two trips that differ only in
 * a choice keep a row each.
 */
private class TripKeys {
    private val before = mutableMapOf<Trip, Trip>()

    /**
     * The key of a trip's row.
     *
     * @param trip the trip as the dashboard lists it
     * @return the trip its first row was drawn for
     */
    fun of(trip: Trip): Trip = before[trip] ?: trip

    /**
     * A choice is being saved, so the changed trip takes the row of the trip it was.
     *
     * @param trip the trip as the dashboard lists it
     * @param changed the trip with the choice changed
     */
    fun changing(trip: Trip, changed: Trip) {
        before[changed] = of(trip)
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
private fun TripsCard(dashboard: Dashboard, day: DayModel, words: DashboardWords, trips: TripAction,
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
            Text(stringResource(R.string.trips_heading), style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() })
            if (listed.isEmpty()) {
                NoneLine(stringResource(R.string.trips_none), focus)
            }
            listed.forEach { trip ->
                key(trips.keys.of(trip.trip)) {
                    TripRow(trip, words, trips, focus, rows)
                }
            }
        }
    }
}

/** One trip of the Trips card: its line, its two choices and its Remove. */
@Composable
private fun TripRow(listed: ListedTrip, words: DashboardWords, trips: TripAction, focus: RowFocus<Trip>,
    rows: List<Trip>) {
    val trip = listed.trip
    val spoken = words.spokenTrip(listed)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        RowLine(words.trip(listed), focus, trip, Modifier.semantics { contentDescription = spoken })
        TripEntry.Choice.entries.forEach { choice ->
            val pending = trips.pending?.takeIf { it.trip == trip && it.choice == choice }
            ChoiceRow(stringResource(when (choice) {
                TripEntry.Choice.NOT_WITH_THIS_CAR -> R.string.trip_not_with_this_car
                TripEntry.Choice.CAN_CHARGE -> R.string.trip_can_charge
            }), spoken, pending?.on ?: choice.of(trip), trips.busy) {
                trips.keys.changing(trip, choice.put(trip, it))
                trips.entry.choose(trip, choice, it)
            }
        }
        val remove = stringResource(R.string.trip_remove_spoken, spoken)
        HeldButton(busy = trips.busy, onClick = {
            trips.going.row = trip
            trips.going.rows = rows
            trips.entry.remove(trip)
        }, modifier = Modifier.align(Alignment.End).semantics { contentDescription = remove }) {
            Text(stringResource(R.string.trip_remove))
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

/**
 * A text button that is off while a change runs yet keeps the focus: Compose takes the focus from a disabled control,
 * so a keyboard or switch user who pressed it would start again at the top of the page. It looks off, a screen reader
 * says it is, and a press does nothing until the change is done.
 *
 * @param busy true while a change runs
 * @param onClick what a press does while no change runs
 * @param modifier its place, focus and name
 * @param content its words
 */
@Composable
private fun HeldButton(busy: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit) {
    val colours = ButtonDefaults.textButtonColors()
    AppTextButton(onClick = { if (!busy) onClick() }, modifier = modifier.semantics {
        if (busy) {
            disabled()
        }
    }, colors = if (busy) colours.copy(contentColor = colours.disabledContentColor) else colours, content = content)
}

/**
 * The row whose Remove was pressed, a one-off away period or a trip, and the rows as they were then, which drawing
 * never reads, so the focus moves on once the list no longer has it; a Remove that failed forgets it, as its row stays
 * in the list.
 *
 * @param K what names a row
 * @property row the row going; null when none is
 * @property rows the rows before it went, in order
 */
private class Going<K>(var row: K? = null, var rows: List<K> = listOf())

/**
 * A warning above the plan, with a warning sign and the words, in a container that stands out and a polite live
 * region, so a refresh that brings it is heard; the plan below stays in view. That the forecast could not be
 * refreshed, or that a provider refused its API key.
 *
 * @param lines the warning's sentences, each on a line of its own
 */
@Composable
private fun Warning(lines: List<String>) {
    Surface(color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer, shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {
            liveRegion = LiveRegionMode.Polite
        }) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically) {
            // the words say it; the sign is for the eye, so a screen reader skips it
            Icon(Icons.Filled.Warning, contentDescription = null)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                lines.forEach { Text(it) }
            }
        }
    }
}

/**
 * The warning that providers refused their API key, a line per provider, also without a plan; nothing when none did.
 *
 * @param names the providers' names, in the order they were asked
 */
@Composable
private fun KeyRefused(names: List<String>) {
    if (names.isNotEmpty()) {
        Warning(names.map { stringResource(R.string.key_refused_warning, it) })
    }
}

/** The providers that refused their API key, by name, for the home without a plan; none while it is asked. */
private fun keyRefusedBy(state: HomeOutlook.State): List<String> = when (state) {
    is HomeOutlook.State.NoForecast -> state.keyRefusedBy
    is HomeOutlook.State.NoDays -> state.keyRefusedBy
    is HomeOutlook.State.Planned -> state.dashboard.keyRefusedBy
    HomeOutlook.State.Asking -> listOf()
}

/** The forecast's credit, which stays in the provider's English, its source and freshness. */
@Composable
private fun FooterLine(footer: Footer, words: DashboardWords) {
    Text(inEnglish(words.footer(footer), footer.credit), style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/**
 * A figure about an energy source in its fixed colours, which are never its only sign: the text names the source.
 *
 * @param text the figure, such as "92% solar"
 * @param colours the source's colours
 * @param modifier such as a heading
 * @param spoken what a screen reader says instead, when the text reads badly aloud; null for the text
 */
@Composable
private fun SourceChip(text: String, colours: SourceColours.Pair, modifier: Modifier = Modifier,
    spoken: String? = null) {
    val said = if (spoken == null) modifier else modifier.semantics { contentDescription = spoken }
    Text(text, color = colours.text, style = MaterialTheme.typography.labelLarge,
        modifier = said.background(colours.container, MaterialTheme.shapes.small)
            .padding(horizontal = 8.dp, vertical = 4.dp))
}

/**
 * What a Settings save came to, when no screen showed it, in a live region read at once for a failure, with Close
 * beside it as a button of its own; after Close the focus goes on, as the notice is gone.
 */
@Composable
private fun Notice(notice: HomeNotice, closed: () -> Unit) {
    val shown by notice.notice.collectAsStateWithLifecycle()
    val outcome = shown ?: return
    val failed = outcome != SaveOutcome.Saved
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val live = Modifier.semantics(mergeDescendants = true) {
            liveRegion = if (failed) LiveRegionMode.Assertive else LiveRegionMode.Polite
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = live) {
            when (outcome) {
                SaveOutcome.Saved -> Text(stringResource(R.string.settings_saved))
                SaveOutcome.KeysNotSaved -> Text(stringResource(R.string.settings_keys_not_saved),
                    color = MaterialTheme.colorScheme.error)
                is SaveOutcome.Failed -> {
                    Text(stringResource(R.string.settings_save_failed), color = MaterialTheme.colorScheme.error)
                    EnglishDetails(outcome.detail)
                }
                is SaveOutcome.SettingsUnusable -> Unit
            }
        }
        AppOutlinedButton(onClick = {
            notice.close()
            closed()
        }) {
            Text(stringResource(R.string.action_close))
        }
    }
}

/**
 * The card that asks for notifications while they are not allowed: what they are for and where to change it later,
 * and one button that asks Android, or, when Android will not ask, opens the app's notification settings, with a line
 * that says why. It is one card that scrolls with the page and is no live region; a screen reader reads its title, its
 * text, its button and last its ×, which closes it for good, after which the focus goes on, as the card is gone. When
 * a refusal swaps the button for the other one, the focus goes to the card's title, as the button it was on is gone.
 *
 * @param card what the card shows, and its answers
 * @param closed moves the focus once the card is closed
 */
@Composable
private fun NotificationsCard(card: NotificationCard, closed: () -> Unit) {
    val shown by card.shown.collectAsStateWithLifecycle()
    if (shown == NotificationCard.Shown.NONE) {
        return
    }
    val context = LocalContext.current
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission(), card::answered)
    val title = remember { FocusRequester() }
    val before = remember { ShownBefore(shown) }
    LaunchedEffect(shown) {
        if (before.shown == NotificationCard.Shown.ASK && shown == NotificationCard.Shown.OPEN_SETTINGS) {
            title.requestFocus()
        }
        before.shown = shown
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp).semantics { isTraversalGroup = true },
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.notification_card_title), style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f).focusRequester(title).focusable().semantics { heading() })
                // beside the title for the eye, but read last, after the button; drawn at 48 dp, not Material's 40
                IconButton(onClick = {
                    card.close()
                    closed()
                }, modifier = Modifier.size(48.dp).semantics { traversalIndex = 1f }) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_close))
                }
            }
            Text(stringResource(R.string.notification_card_body))
            if (shown == NotificationCard.Shown.ASK) {
                AppButton(onClick = {
                    // the card asks only where Android has the permission, Android 13 and later
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        ask.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }) {
                    Text(stringResource(R.string.notification_card_ask))
                }
            } else {
                Text(stringResource(R.string.notification_card_no_ask))
                AppButton(onClick = { openNotificationSettings(context) }) {
                    Text(stringResource(R.string.notification_card_open_settings))
                }
            }
        }
    }
}

/**
 * What the notification card showed when last looked at, which drawing never reads.
 *
 * @property shown what it showed
 */
private class ShownBefore(var shown: NotificationCard.Shown)

/** The dashboard's words in the app's language and the phone's clock, with the times in this zone. */
@Composable
internal fun dashboardWords(zone: ZoneId): DashboardWords {
    val resources = LocalResources.current
    val locale = LocalConfiguration.current.locales[0]
    val twentyFour = DateFormat.is24HourFormat(LocalContext.current)
    return remember(resources, locale, twentyFour, zone) { DashboardWords(resources, locale, twentyFour, zone) }
}
