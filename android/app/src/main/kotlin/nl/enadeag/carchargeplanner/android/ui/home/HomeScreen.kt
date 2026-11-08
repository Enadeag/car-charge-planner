package nl.enadeag.carchargeplanner.android.ui.home

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
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
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
import nl.enadeag.carchargeplanner.android.ui.components.inEnglish
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
 * Remove, which acts at once with Undo in a snackbar, as the away list's; its Add opens the entry of a new trip on
 * that day, and each trip's Change the entry on that trip, a full-screen dialog after which the focus goes back to the
 * Change of the trip, or to Add, and a trip saved there says so in a snackbar. A provider that refused its API key
 * gets a warning in the stale warning's place, also without a plan. Below the day card, the advice of each trip that
 * leaves that day, and on today's tab of each that asks the owner to act, as the desktop's, with Adjust, which moves to
 * today's tab at once and opens the entry of the battery level; the day card tells a day the car is away on a trip,
 * and the battery the level after a trip on the day it leaves. While notifications are not allowed, a card asks
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
    val awayNamed = awayWords(shown)
    val resources = LocalResources.current
    ChangeOutcome(away.notice, away::noticeShown, snackbars, going, restoring, { notice ->
        when (notice) {
            is AwayEntry.Notice.Saved ->
                Said.Saved(resources.getString(R.string.one_off_saved, awayNamed(notice.period, notice.entered)))
            is AwayEntry.Notice.Removed ->
                Said.Removed(resources.getString(R.string.one_off_removed, awayNamed(notice.period, null)),
                    notice.period)
            is AwayEntry.Notice.NotRemoved ->
                Said.NotRemoved(resources.getString(R.string.one_off_not_removed), notice.detail)
            is AwayEntry.Notice.NotSaved ->
                Said.NotSaved(resources.getString(R.string.one_off_not_saved), notice.detail)
        }
    }, away::undo)
    val awayDraft by away.draft.collectAsStateWithLifecycle()
    AfterClose(awayDraft != null) {
        if (!add.requestFocus()) {
            heading.requestFocus()
        }
    }
    val busy by away.busy.collectAsStateWithLifecycle()
    val tripGoing = remember { Going<Trip>() }
    val tripRestoring = remember { Restoring<Trip>() }
    val tripKeys = remember { TripKeys() }
    val tripNamed = tripWords(shown)
    ChangeOutcome(trips.notice, trips::noticeShown, snackbars, tripGoing, tripRestoring, { notice ->
        when (notice) {
            is TripEntry.Notice.Saved ->
                Said.Saved(resources.getString(R.string.trip_saved, tripNamed(notice.trip, notice.entered)))
            is TripEntry.Notice.Removed ->
                Said.Removed(resources.getString(R.string.trip_removed, tripNamed(notice.trip, null)), notice.trip)
            is TripEntry.Notice.NotRemoved ->
                Said.NotRemoved(resources.getString(R.string.trip_not_removed), notice.detail)
            is TripEntry.Notice.NotSaved -> Said.NotSaved(resources.getString(R.string.trip_not_saved), notice.detail)
        }
    }, trips::undo)
    val tripsBusy by trips.busy.collectAsStateWithLifecycle()
    val pending by trips.pending.collectAsStateWithLifecycle()
    val tripAdd = remember { FocusRequester() }
    val tripOpeners = remember { TripOpeners() }
    val tripDraft by trips.draft.collectAsStateWithLifecycle()
    AfterClose(tripDraft != null) {
        // the Change of the trip the entry changed, when its page is in view; otherwise that page's Add
        val back = trips.closedOn?.let(tripOpeners::focus) == true
        if (!back && !tripAdd.requestFocus()) {
            heading.requestFocus()
        }
    }
    BarScaffold(stringResource(R.string.app_name), heading, onUp = null, snackbars = snackbars, actions = {
        BarIconButton(Icons.Filled.Refresh, stringResource(R.string.home_refresh),
            enabled = state != HomeOutlook.State.Asking && !refreshing) { outlook.refresh() }
        BarIconButton(Icons.Filled.Settings, stringResource(R.string.home_settings), onClick = onSettings)
    }) {
        val askCard: @Composable () -> Unit = { NotificationsCard(card, closed) }
        when (val now = state) {
            is HomeOutlook.State.Planned -> Days(now.dashboard, refreshing, phoneZone(), outlook::refresh,
                BatteryAction(battery::open, update), AwayAction(away, add, busy, going, restoring),
                TripAction(trips, tripsBusy, pending, tripGoing, tripRestoring, tripKeys, tripAdd, tripOpeners),
                askCard) {
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
    TripEntryDialog(trips, phoneZone())
}

/**
 * The plan's dashboard shown last, which drawing never reads: a change's snackbar names its period by it.
 *
 * @property dashboard the dashboard; null until a plan was shown
 */
internal class Shown(var dashboard: Dashboard? = null)

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
    // at once, not swiped across the days, so the entry opens over today's page and closes onto its Update battery
    // level; the entry's open ignores a second call while it is open
    val adjust: () -> Unit = {
        scope.launch {
            if (pager.currentPage != 0) {
                pager.scrollToPage(0)
            }
            battery.open()
        }
    }
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
                DayCard(day, dashboard, words)
                // by the trip, so a card keeps its place in the composition when a trip before it goes
                dashboard.tripAdviceOn(day).forEach {
                    key(it.departure, it.distanceKm) {
                        TripAdviceCard(words.tripAdvice(it, day.date, dashboard.today), it.source, adjust)
                    }
                }
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
private fun DayCard(day: DayModel, dashboard: Dashboard, words: DashboardWords) {
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
            val note = words.note(day, dashboard.noWindowWhy(day))
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
 * A trip's advice, below the day card, as the desktop's: its heading in the colours of where the charge comes from,
 * the trip, what to charge and when, and the battery level it starts from, with Adjust under it. A screen reader reads
 * the advice as one heading, one sentence, and Adjust apart as a button whose name starts with its visible word, and
 * which says what a double tap does.
 *
 * @param advice the advice's words for the tab it shows on
 * @param source where the trip's charge comes from, whose colours its heading has
 * @param adjust moves to today's tab and opens the entry of the battery level
 */
@Composable
private fun TripAdviceCard(advice: TripAdviceText, source: EnergySource, adjust: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // one part for a screen reader, the sentence; its words are not read apart
            val apart = Modifier.semantics { hideFromAccessibility() }
            Column(Modifier.fillMaxWidth().semantics {
                contentDescription = advice.spoken
                heading()
            }, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SourceChip(advice.label, LocalSourceColours.current.of(source), modifier = apart)
                Text(advice.title, style = MaterialTheme.typography.titleMedium, modifier = apart)
                Text(advice.plan, style = MaterialTheme.typography.bodyMedium, modifier = apart)
                Text(advice.level, style = MaterialTheme.typography.bodyMedium, modifier = apart)
            }
            val spoken = stringResource(R.string.trip_advice_adjust_spoken)
            val hint = stringResource(R.string.trip_advice_adjust_hint)
            AppTextButton(onClick = adjust, modifier = Modifier.align(Alignment.End).semantics {
                contentDescription = spoken
                onClick(label = hint) {
                    adjust()
                    true
                }
            }) {
                Text(stringResource(R.string.trip_advice_adjust))
            }
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
