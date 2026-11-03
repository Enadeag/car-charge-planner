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
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
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
import nl.enadeag.carchargeplanner.android.home.ComingAway
import nl.enadeag.carchargeplanner.android.home.Dashboard
import nl.enadeag.carchargeplanner.android.home.DayModel
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
import nl.enadeag.carchargeplanner.android.ui.components.BarIconButton
import nl.enadeag.carchargeplanner.android.ui.components.BarScaffold
import nl.enadeag.carchargeplanner.android.ui.components.EnglishDetails
import nl.enadeag.carchargeplanner.android.ui.components.inEnglish

/**
 * The home after set-up: the dashboard. A tab per day of the plan, Today first, over a pager that swipes between them,
 * each day with its charge windows and figures; today's also with the grid advice; every day with the coming one-off
 * away periods and the forecast's footer. Refresh and Settings are the top app bar's actions, and a pull asks for the
 * plan too; coming back to the app asks again when the plan is old. The times are in the home's time zone, named when
 * it is not the phone's, on the phone's 12- or 24-hour clock. A Settings save that no screen showed yet shows here
 * until the owner closes it, in a live region.
 *
 * @param outlook the plan it shows
 * @param notice the outcome of a Settings save not shown yet
 * @param phoneZone the phone's time zone as it is now, read each time the plan is shown
 * @param onSettings opens Settings
 */
@Composable
internal fun HomeScreen(outlook: HomeOutlook, notice: HomeNotice, phoneZone: () -> ZoneId, onSettings: () -> Unit) {
    val state by outlook.state.collectAsStateWithLifecycle()
    val refreshing by outlook.refreshing.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_START) { outlook.returned() }
    val heading = remember { FocusRequester() }
    LaunchedEffect(Unit) { heading.requestFocus() }
    val closed: () -> Unit = { heading.requestFocus() }
    BarScaffold(stringResource(R.string.app_name), heading, onUp = null, actions = {
        BarIconButton(Icons.Filled.Refresh, stringResource(R.string.home_refresh),
            enabled = state != HomeOutlook.State.Asking && !refreshing) { outlook.refresh() }
        BarIconButton(Icons.Filled.Settings, stringResource(R.string.home_settings), onClick = onSettings)
    }) {
        when (val now = state) {
            is HomeOutlook.State.Planned -> Days(now.dashboard, refreshing, phoneZone(), outlook::refresh) {
                Notice(notice, closed)
            }
            else -> Refreshable(refreshing, outlook::refresh) {
                Notice(notice, closed)
                NoPlan(now, outlook::refresh)
            }
        }
    }
}

/** A tab per day over a pager, kept in step: a tab's click moves the pager, a swipe moves the tab. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ColumnScope.Days(dashboard: Dashboard, refreshing: Boolean, phoneZone: ZoneId, refresh: () -> Unit,
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
                    StaleWarning(words.time(dashboard.footer.fetchedAt))
                }
                if (dashboard.zone != phoneZone) {
                    Text(stringResource(R.string.home_zone, dashboard.zone.id))
                }
                DayCard(day, words)
                dashboard.adviceOn(day)?.let { GridAdviceCard(it, words) }
                AwayList(dashboard.away, words)
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

/** The one-off away periods still to come, shown only. */
@Composable
private fun AwayList(away: List<ComingAway>, words: DashboardWords) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.one_off_heading), style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() })
        if (away.isEmpty()) {
            Text(stringResource(R.string.one_off_none))
        }
        away.forEach { Text(words.away(it)) }
    }
}

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
private fun dashboardWords(zone: ZoneId): DashboardWords {
    val resources = LocalResources.current
    val locale = LocalConfiguration.current.locales[0]
    val twentyFour = DateFormat.is24HourFormat(LocalContext.current)
    return remember(resources, locale, twentyFour, zone) { DashboardWords(resources, locale, twentyFour, zone) }
}
