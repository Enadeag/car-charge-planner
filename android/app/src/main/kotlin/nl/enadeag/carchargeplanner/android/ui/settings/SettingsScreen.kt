package nl.enadeag.carchargeplanner.android.ui.settings

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.View
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import nl.enadeag.carchargeplanner.android.R
import nl.enadeag.carchargeplanner.android.settings.SettingsPage
import nl.enadeag.carchargeplanner.android.settings.SettingsSession
import nl.enadeag.carchargeplanner.android.ui.components.BarPage
import nl.enadeag.carchargeplanner.android.ui.components.EnglishDetails
import nl.enadeag.carchargeplanner.android.ui.components.ProblemFocus
import nl.enadeag.carchargeplanner.android.ui.setup.Edits

/**
 * Settings' screen (#315): the list of its pages, or one page with Up, its title and Save in the top app bar. System
 * Back and Up do the same: from a page with edits they ask whether to throw them away, from a page without they go to
 * the list, and from the list to the home. A new page moves the focus to its title; a Save that shows problems moves
 * it to the first field that has one.
 *
 * @param session the pages, their forms and their Save
 * @param page the page open; null for the list
 */
@Composable
internal fun SettingsScreen(session: SettingsSession, page: SettingsPage?) {
    val asked by session.discardAsked.collectAsStateWithLifecycle()
    BackHandler { session.back() }
    if (page == null) {
        SettingsList(session)
    } else {
        PageScreen(session, page)
    }
    if (asked) {
        DiscardDialog(session)
    }
}

/** The pages, each a row with its name and its values now in one line, the whole row a button. */
@Composable
private fun SettingsList(session: SettingsSession) {
    val config by session.config.collectAsStateWithLifecycle()
    val title = remember { FocusRequester() }
    LaunchedEffect(Unit) { title.requestFocus() }
    BarPage(stringResource(R.string.settings_title), title, onUp = { session.back() }, padded = false) {
        SettingsPage.entries.forEach { page ->
            ListItem(
                headlineContent = { Text(stringResource(page.title())) },
                supportingContent = {
                    Text(summary(page, config, session), maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                modifier = Modifier.heightIn(min = 56.dp).clickable(role = Role.Button) { session.open(page) },
            )
        }
    }
}

/** One page: its fields, what its Save came to, and Save. */
@Composable
private fun PageScreen(session: SettingsSession, page: SettingsPage) {
    val problemsShown by session.problemsShown.collectAsStateWithLifecycle()
    val loads by session.loads.collectAsStateWithLifecycle()
    val edits = remember(page) { Edits() }
    val problems = remember(page) { ProblemFocus() }
    val title = remember { FocusRequester() }
    var seenPage by rememberSaveable { mutableStateOf<SettingsPage?>(null) }
    var seenProblems by rememberSaveable { mutableIntStateOf(problemsShown) }
    // the forms were filled anew, as after a save: what they hold is shown again
    LaunchedEffect(loads) { edits.make { } }
    LaunchedEffect(page, problemsShown) {
        // the fields are noted once the page is composed, which this waits for
        withFrameNanos { }
        if (problemsShown != seenProblems) {
            seenProblems = problemsShown
            seenPage = page
            if (!problems.focusFirst()) {
                title.requestFocus()
            }
        } else if (seenPage != page) {
            seenPage = page
            title.requestFocus()
        }
    }
    if (page == SettingsPage.FORECAST) {
        SecureWindow()
    }
    BarPage(stringResource(page.title()), title, onUp = { session.back() }, actions = { SaveAction(session, page) }) {
        SaveStatus(session)
        val forms = session.forms
        when (page) {
            SettingsPage.SOLAR -> SolarPage(forms.panels, edits, problems)
            SettingsPage.LOCATION -> LocationPage(session, edits, problems)
            SettingsPage.CAR_AND_HOUSE -> CarAndHousePage(forms.car, forms.house, edits, problems)
            SettingsPage.CHARGER -> ChargerPage(forms.charger, edits, problems)
            SettingsPage.FORECAST -> ForecastPage(session, edits, problems)
            SettingsPage.AWAY -> AwayPage(forms.away, edits, problems)
            SettingsPage.PLANNING -> PlanningPage(forms.planning, edits, problems)
        }
    }
}

/**
 * Save, as the top app bar's action, which stays in view while the page scrolls and the keyboard is open; while it
 * saves, it is off and says so. On the Forecast page it waits for the API keys.
 */
@Composable
private fun SaveAction(session: SettingsSession, page: SettingsPage) {
    val saving by session.saving.collectAsStateWithLifecycle()
    val keys by session.keys.collectAsStateWithLifecycle()
    val busy = saving == SettingsSession.SaveState.Saving
    TextButton(onClick = { session.save() }, enabled = !busy && (page != SettingsPage.FORECAST || keys != null)) {
        if (busy) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            Text(stringResource(R.string.settings_saving), modifier = Modifier.padding(start = 8.dp))
        } else {
            Text(stringResource(R.string.action_save))
        }
    }
}

/** What the page's Save came to, or that a value needs attention, in a live region. */
@Composable
private fun SaveStatus(session: SettingsSession) {
    val saving by session.saving.collectAsStateWithLifecycle()
    val refused by session.refused.collectAsStateWithLifecycle()
    val failed = saving is SettingsSession.SaveState.Failed || saving == SettingsSession.SaveState.KeysNotSaved
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.semantics {
        liveRegion = if (failed) LiveRegionMode.Assertive else LiveRegionMode.Polite
    }) {
        if (refused) {
            Text(stringResource(R.string.setup_refused), color = MaterialTheme.colorScheme.error)
        }
        when (val now = saving) {
            SettingsSession.SaveState.Idle, SettingsSession.SaveState.Saving -> Unit
            SettingsSession.SaveState.Saved -> Text(stringResource(R.string.settings_saved))
            SettingsSession.SaveState.KeysNotSaved -> Text(stringResource(R.string.settings_keys_not_saved),
                color = MaterialTheme.colorScheme.error)
            is SettingsSession.SaveState.Failed -> {
                Text(stringResource(R.string.settings_save_failed), color = MaterialTheme.colorScheme.error)
                EnglishDetails(now.detail)
            }
        }
    }
}

/** "Discard changes?", with Keep editing and Discard, which is named for what it does and drawn as a warning. */
@Composable
private fun DiscardDialog(session: SettingsSession) {
    AlertDialog(
        onDismissRequest = { session.keepEditing() },
        title = { Text(stringResource(R.string.settings_discard_question)) },
        text = { Text(stringResource(R.string.settings_discard_text)) },
        confirmButton = {
            TextButton(onClick = { session.discard() }) {
                Text(stringResource(R.string.settings_discard), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = { session.keepEditing() }) {
                Text(stringResource(R.string.settings_keep_editing))
            }
        },
    )
}

/**
 * While the Forecast page shows, its window allows no screenshot and no preview among the recent apps, and asks
 * autofill to leave its fields alone, as the API keys show there (#315); both end when the page closes.
 */
@Composable
private fun SecureWindow() {
    // Compose marks its own view as important for autofill itself, so the view that holds it says no for both
    val holder = LocalView.current.parent as? View
    val activity = LocalContext.current.activity()
    DisposableEffect(holder, activity) {
        val window = activity?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        val autofill = holder?.importantForAutofill
        holder?.importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            if (holder != null && autofill != null) {
                holder.importantForAutofill = autofill
            }
        }
    }
}

private tailrec fun Context.activity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.activity()
    else -> null
}

/**
 * The page's title, in the list and in its top app bar.
 *
 * @return the text's resource
 */
internal fun SettingsPage.title(): Int = when (this) {
    SettingsPage.SOLAR -> R.string.settings_solar
    SettingsPage.LOCATION -> R.string.settings_location
    SettingsPage.CAR_AND_HOUSE -> R.string.settings_car_house
    SettingsPage.CHARGER -> R.string.settings_charger
    SettingsPage.FORECAST -> R.string.settings_forecast
    SettingsPage.AWAY -> R.string.settings_away
    SettingsPage.PLANNING -> R.string.settings_planning
}

/** A row's progress while something is read, such as the API keys. */
@Composable
internal fun Waiting(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        Text(text, modifier = Modifier.padding(start = 8.dp))
    }
}
