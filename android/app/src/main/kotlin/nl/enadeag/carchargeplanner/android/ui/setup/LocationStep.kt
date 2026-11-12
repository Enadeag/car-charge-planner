package nl.enadeag.carchargeplanner.android.ui.setup

import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import nl.enadeag.carchargeplanner.android.R
import nl.enadeag.carchargeplanner.android.form.FieldProblem
import nl.enadeag.carchargeplanner.android.form.LocationForm
import nl.enadeag.carchargeplanner.android.setup.PlaceSearch
import nl.enadeag.carchargeplanner.android.setup.SetUpSession
import nl.enadeag.carchargeplanner.android.ui.components.EntryField
import nl.enadeag.carchargeplanner.android.ui.components.FieldKind
import nl.enadeag.carchargeplanner.android.ui.components.Fold
import nl.enadeag.carchargeplanner.android.ui.components.NumberField
import nl.enadeag.carchargeplanner.android.ui.components.ProblemFocus
import nl.enadeag.carchargeplanner.android.ui.components.rememberFieldFocus
import nl.enadeag.carchargeplanner.engine.forecast.PlaceResults
import nl.enadeag.carchargeplanner.android.ui.components.AppButton
import nl.enadeag.carchargeplanner.android.ui.components.AppOutlinedButton

/**
 * Where the home is, as Settings' Location page shows it too.
 *
 * @param problems the step's fields in order, for the focus after Next
 */
@Composable
internal fun LocationStep(session: SetUpSession, edits: Edits, problems: ProblemFocus) {
    LocationFields(session.flow.location, session.places, session.providerName(), true, edits, problems,
        session::choose, session::typeCoordinate)
}

/**
 * The location page, the same in set-up and Settings. A problem in a coordinate opens the fold, which stays open also
 * after the problem is put right, so the field being corrected does not fold away.
 */
@Composable
internal fun LocationFields(location: LocationForm, places: PlaceSearch, provider: String, hasSearch: Boolean,
    edits: Edits, problems: ProblemFocus, onChoose: (PlaceSearch.Result) -> Unit,
    onType: (LocationForm.Field, String) -> Unit) {
    val shown by location.problems.collectAsStateWithLifecycle()
    val name = edits.shown { location.placeName() }
    val coordinateProblem = LocationForm.Field.LATITUDE in shown || LocationForm.Field.LONGITUDE in shown
    var folded by rememberSaveable { mutableStateOf(name != null) }
    val chosenCount by places.chosenCount.collectAsStateWithLifecycle()
    // a choice made before the page was shown, as before turning the phone or leaving the step, neither folds the
    // coordinates nor takes the focus again; each one after it does, also of the same place
    val firstSeen = remember(places) { places.chosenCount.value }
    val line = remember { FocusRequester() }
    LaunchedEffect(chosenCount) {
        if (chosenCount != firstSeen) {
            folded = true
            // the line shows the new place by the next frame at the latest, which this waits for to be sure
            withFrameNanos { }
            line.requestFocus()
        }
    }
    LaunchedEffect(coordinateProblem) {
        // open after the problem too, so the field the owner is putting right does not fold away under them
        if (coordinateProblem) {
            folded = false
        }
    }

    if (!hasSearch) {
        Text(stringResource(R.string.settings_no_place_search, provider))
        CoordinateFields(location, edits, problems, shown, onType)
        return
    }
    Text(name?.let { stringResource(R.string.place_chosen, it.text()) } ?: stringResource(R.string.place_not_chosen),
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.fillMaxWidth().focusRequester(line).focusable())
    val hint = stringResource(R.string.place_hint, provider)
    EntryField(stringResource(R.string.place_field),
        if (name == null) hint else stringResource(R.string.place_another) + "\n" + hint,
        edits.shown { places.text() }, { text -> edits.make { places.edit(text) } }, FieldKind.TEXT,
        shown[LocationForm.Field.PLACE], rememberFieldFocus(problems, 0, LocationForm.Field.PLACE in shown),
        imeAction = ImeAction.Search, onAction = { places.search() })
    AppButton(onClick = { places.search() }) {
        Text(stringResource(R.string.place_search))
    }
    SearchResults(places) { result -> edits.make { onChoose(result) } }
    Fold(stringResource(R.string.coordinates_fold), !folded || coordinateProblem,
        { open -> if (!coordinateProblem) folded = !open }) {
        CoordinateFields(location, edits, problems, shown, onType)
    }
}

@Composable
private fun CoordinateFields(location: LocationForm, edits: Edits, problems: ProblemFocus,
    shown: Map<LocationForm.Field, FieldProblem>, onType: (LocationForm.Field, String) -> Unit) {
    listOf(
        LocationForm.Field.LATITUDE to (R.string.latitude to R.string.latitude_hint),
        LocationForm.Field.LONGITUDE to (R.string.longitude to R.string.longitude_hint),
    ).forEachIndexed { index, (field, texts) ->
        NumberField(stringResource(texts.first), stringResource(texts.second),
            edits.shown { location.text(field) }, { text -> edits.make { onType(field, text) } },
            FieldKind.SIGNED, shown[field], focus = rememberFieldFocus(problems, index + 1, field in shown),
            imeAction = if (field == LocationForm.Field.LONGITUDE) ImeAction.Done else ImeAction.Next)
    }
    TimeZone(edits.shown { location.timeZone()?.id.orEmpty() }) { zone -> edits.make { location.choose(zone) } }
}

/** What the search found, or why it found nothing, as a live region that a screen reader reads when it changes. */
@Composable
private fun SearchResults(places: PlaceSearch, onChoose: (PlaceSearch.Result) -> Unit) {
    val state by places.state.collectAsStateWithLifecycle()
    Column(verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }) {
        when (val now = state) {
            PlaceSearch.State.Idle -> Unit
            PlaceSearch.State.Searching -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                Text(stringResource(R.string.place_searching), modifier = Modifier.padding(start = 8.dp))
            }
            is PlaceSearch.State.Found -> if (now.results.isEmpty()) {
                Text(stringResource(R.string.place_none, now.text))
            } else {
                Text(stringResource(R.string.place_choose))
                Column {
                    now.results.forEachIndexed { index, result ->
                        if (index > 0) {
                            HorizontalDivider()
                        }
                        ListItem(headlineContent = { Text(result.label) },
                            modifier = Modifier.fillMaxWidth().clickable(role = Role.Button) { onChoose(result) })
                    }
                }
            }
            PlaceSearch.State.NoSearch -> Text(stringResource(R.string.place_no_search))
            is PlaceSearch.State.Failed -> Text(stringResource(when (now.problem) {
                PlaceResults.Problem.TOO_SHORT -> R.string.place_too_short
                PlaceResults.Problem.UNAVAILABLE -> R.string.place_unavailable
                PlaceResults.Problem.RATE_LIMITED -> R.string.place_busy
                PlaceResults.Problem.BAD_RESPONSE -> R.string.place_bad_response
            }))
        }
    }
}

/** The home's time zone, and the way to choose another from the list. */
@Composable
private fun TimeZone(zone: String, onChoose: (java.time.ZoneId) -> Unit) {
    var choosing by rememberSaveable { mutableStateOf(false) }
    Text(stringResource(R.string.time_zone_now, zone), style = MaterialTheme.typography.bodyLarge)
    AppOutlinedButton(onClick = { choosing = true }) {
        Text(stringResource(R.string.time_zone_change))
    }
    if (choosing) {
        TimeZoneDialog(zone, onChoose = { chosen ->
            onChoose(chosen)
            choosing = false
        }, onDismiss = { choosing = false })
    }
}
