package nl.enadeag.carchargeplanner.android.ui.home

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
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
import nl.enadeag.carchargeplanner.engine.away.OneOffAwayPeriod

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
 * and the list shows the stored periods at once while the plan is made again.
 *
 * @param outlook the plan it shows
 * @param notice the outcome of a Settings save not shown yet
 * @param battery the entry of the battery level
 * @param away the changes to the one-off away periods
 * @param phoneZone the phone's time zone as it is now, read each time the plan is shown
 * @param onSettings opens Settings
 */
@Composable
internal fun HomeScreen(outlook: HomeOutlook, notice: HomeNotice, battery: BatteryEntry, away: AwayEntry,
    phoneZone: () -> ZoneId, onSettings: () -> Unit) {
    val state by outlook.state.collectAsStateWithLifecycle()
    val refreshing by outlook.refreshing.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_START) { outlook.returned() }
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
    val going = remember { Going() }
    val restoring = remember { Restoring() }
    AwayOutcome(away, snackbars, going, restoring, awayWords(shown)) {
        if (!add.requestFocus()) {
            heading.requestFocus()
        }
    }
    val busy by away.busy.collectAsStateWithLifecycle()
    BarScaffold(stringResource(R.string.app_name), heading, onUp = null, snackbars = snackbars, actions = {
        BarIconButton(Icons.Filled.Refresh, stringResource(R.string.home_refresh),
            enabled = state != HomeOutlook.State.Asking && !refreshing) { outlook.refresh() }
        BarIconButton(Icons.Filled.Settings, stringResource(R.string.home_settings), onClick = onSettings)
    }) {
        when (val now = state) {
            is HomeOutlook.State.Planned -> Days(now.dashboard, refreshing, phoneZone(), outlook::refresh,
                BatteryAction(battery::open, update), AwayAction(away, add, busy, going, restoring)) {
                Notice(notice, closed)
            }
            else -> Refreshable(refreshing, outlook::refresh) {
                Notice(notice, closed)
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
private fun AwayOutcome(away: AwayEntry, snackbars: SnackbarHostState, going: Going, restoring: Restoring,
    named: (OneOffAwayPeriod, AwayEntry.Entered?) -> String, closed: () -> Unit) {
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
                is AwayEntry.Notice.NotRemoved -> going.period = null
                is AwayEntry.Notice.NotSaved -> restoring.period = null
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
                            restoring.period = notice.period
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
private class AwayAction(val entry: AwayEntry, val focus: FocusRequester, val busy: Boolean, val going: Going,
    val restoring: Restoring)

/**
 * The period an Undo is adding back, which drawing never reads: the list on the page in view puts the focus on its row
 * once it shows; an Undo that failed forgets it.
 *
 * @property period the period; null when no Undo is under way
 */
private class Restoring(var period: OneOffAwayPeriod? = null)

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
    battery: BatteryAction, away: AwayAction, notice: @Composable () -> Unit) {
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
                    StaleWarning(words.time(dashboard.footer.fetchedAt))
                }
                if (dashboard.zone != phoneZone) {
                    Text(stringResource(R.string.home_zone, dashboard.zone.id))
                }
                DayCard(day, words)
                dashboard.adviceOn(day)?.let { GridAdviceCard(it, words) }
                SolarCard(day, words)
                BatteryCard(day, dashboard.levels, words, battery.takeIf { day.name == DayName.Today })
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
        val pressed = away.going.period
        if (pressed != null && pressed !in periods) {
            focus.removing(away.going.rows, pressed)
            away.going.period = null
        }
        val back = away.restoring.period
        if (back != null && back in periods) {
            focus.next = back
            away.restoring.period = null
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
            AppTextButton(onClick = { away.entry.open(day.date, dashboard.zone) }, enabled = !away.busy,
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
                    AppTextButton(onClick = {
                        away.going.period = coming.period
                        away.going.rows = periods
                        away.entry.remove(coming.period)
                    }, enabled = !away.busy, modifier = Modifier.semantics { contentDescription = spoken }) {
                        Text(stringResource(R.string.one_off_remove))
                    }
                }
            }
        }
    }
}

/**
 * The period whose Remove was pressed and the periods as they were then, which drawing never reads, so the focus moves
 * on once the list no longer has it; a Remove that failed forgets it, as its period stays in the list.
 *
 * @property period the period going; null when none is
 * @property rows the periods before it went, in order
 */
private class Going(var period: OneOffAwayPeriod? = null, var rows: List<OneOffAwayPeriod> = listOf())

/**
 * That the forecast could not be refreshed, with a warning sign and the words, in a container that stands out and a
 * polite live region, so a refresh that leaves the forecast stale is heard; the plan below stays in view.
 */
@Composable
private fun StaleWarning(fetched: String) {
    Surface(color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer, shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {
            liveRegion = LiveRegionMode.Polite
        }) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically) {
            // the words say it; the sign is for the eye, so a screen reader skips it
            Icon(Icons.Filled.Warning, contentDescription = null)
            Text(stringResource(R.string.stale_warning, fetched))
        }
    }
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

/** The dashboard's words in the app's language and the phone's clock, with the times in this zone. */
@Composable
internal fun dashboardWords(zone: ZoneId): DashboardWords {
    val resources = LocalResources.current
    val locale = LocalConfiguration.current.locales[0]
    val twentyFour = DateFormat.is24HourFormat(LocalContext.current)
    return remember(resources, locale, twentyFour, zone) { DashboardWords(resources, locale, twentyFour, zone) }
}
