package nl.enadeag.carchargeplanner.android.ui.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import nl.enadeag.carchargeplanner.android.R
import nl.enadeag.carchargeplanner.android.form.LocationForm
import nl.enadeag.carchargeplanner.android.setup.PlaceSearch
import nl.enadeag.carchargeplanner.android.setup.SetUpSession
import nl.enadeag.carchargeplanner.android.ui.components.EntryField
import nl.enadeag.carchargeplanner.android.ui.components.FieldKind
import nl.enadeag.carchargeplanner.android.ui.components.NumberField
import nl.enadeag.carchargeplanner.android.ui.components.ProblemFocus
import nl.enadeag.carchargeplanner.android.ui.components.rememberFieldFocus
import nl.enadeag.carchargeplanner.engine.forecast.PlaceResults

/**
 * Where the home is: the place search, on its button or the keyboard's search key, and its results; the coordinates
 * behind "Enter coordinates instead", which is open when coordinates were typed rather than found; and the time zone
 * under "Advanced".
 *
 * @param session set-up, with its place search and location form
 * @param edits counts each edit, so the fields show it
 * @param problems the step's fields in order, for the focus after Next
 */
@Composable
internal fun LocationStep(session: SetUpSession, edits: Edits, problems: ProblemFocus) {
    val chosen by session.places.chosen.collectAsStateWithLifecycle()
    val typed = chosen == null && session.flow.config().system().location().isChosen
    LocationFields(session.flow.location, session.places, session.providerName(), true, edits, problems,
        if (typed) Coordinates.OPEN else Coordinates.CLOSED, session::choose, session::typeCoordinate)
}

/** How a location page shows the coordinates. */
internal enum class Coordinates {
    /** Behind "Enter coordinates instead", closed. */
    CLOSED,
    /** Behind "Enter coordinates instead", open, as for coordinates typed before. */
    OPEN,
    /** Always shown, as Settings has coordinates already. */
    SHOWN,
}

/**
 * The place search with its results, or a line that the provider has none; the coordinates; and the time zone under
 * "Advanced".
 *
 * @param location the location's form
 * @param places the place search
 * @param provider the forecast provider's name, which the search's hint names
 * @param hasSearch whether that provider has a place search; without one only the coordinates are offered
 * @param edits counts each edit, so the fields show it
 * @param problems the page's fields in order, for the focus after Next or Save
 * @param coordinates whether the coordinates are shown, or behind their toggle and how it starts
 * @param onChoose takes a place chosen from the results
 * @param onType takes a coordinate the owner typed
 */
@Composable
internal fun LocationFields(location: LocationForm, places: PlaceSearch, provider: String, hasSearch: Boolean,
    edits: Edits, problems: ProblemFocus, coordinates: Coordinates, onChoose: (PlaceSearch.Result) -> Unit,
    onType: (LocationForm.Field, String) -> Unit) {
    val shown by location.problems.collectAsStateWithLifecycle()
    val chosen by places.chosen.collectAsStateWithLifecycle()
    val typedCoordinates = LocationForm.Field.LATITUDE in shown || LocationForm.Field.LONGITUDE in shown
    var open by rememberSaveable { mutableStateOf(coordinates == Coordinates.OPEN) }
    var advanced by rememberSaveable { mutableStateOf(false) }

    if (hasSearch) {
        EntryField(stringResource(R.string.place_field), stringResource(R.string.place_hint, provider),
            edits.shown { places.text() }, { text -> edits.make { places.edit(text) } }, FieldKind.TEXT,
            shown[LocationForm.Field.PLACE], false,
            rememberFieldFocus(problems, 0, LocationForm.Field.PLACE in shown),
            imeAction = ImeAction.Search, onAction = { places.search() })
        Button(onClick = { places.search() }) {
            Text(stringResource(R.string.place_search))
        }
        SearchResults(places) { result -> edits.make { onChoose(result) } }
        chosen?.let { Text(stringResource(R.string.place_chosen, it)) }
    } else {
        Text(stringResource(R.string.settings_no_place_search, provider))
    }

    val showCoordinates = coordinates == Coordinates.SHOWN || open || typedCoordinates
    if (coordinates != Coordinates.SHOWN) {
        Toggle(stringResource(R.string.coordinates_toggle), showCoordinates) { open = it }
    }
    if (showCoordinates) {
        listOf(
            LocationForm.Field.LATITUDE to (R.string.latitude to R.string.latitude_hint),
            LocationForm.Field.LONGITUDE to (R.string.longitude to R.string.longitude_hint),
        ).forEachIndexed { index, (field, texts) ->
            NumberField(stringResource(texts.first), stringResource(texts.second),
                edits.shown { location.text(field) }, { text -> edits.make { onType(field, text) } },
                FieldKind.SIGNED, shown[field], focus = rememberFieldFocus(problems, index + 1, field in shown),
                imeAction = if (field == LocationForm.Field.LONGITUDE) ImeAction.Done else ImeAction.Next)
        }
    }

    Toggle(stringResource(R.string.advanced), advanced) { advanced = it }
    if (advanced) {
        TimeZone(edits.shown { location.timeZone()?.id.orEmpty() }) { zone -> edits.make { location.choose(zone) } }
    }
}

/** What the search found, or why it found nothing, as a live region that a screen reader reads when it changes. */
@Composable
private fun SearchResults(places: PlaceSearch, onChoose: (PlaceSearch.Result) -> Unit) {
    val state by places.state.collectAsStateWithLifecycle()
    Column(verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
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
                now.results.forEach { result ->
                    TextButton(onClick = { onChoose(result) }, modifier = Modifier.fillMaxWidth()) {
                        Text(result.label, modifier = Modifier.fillMaxWidth())
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

/** A part of the step that opens and closes, as a check box a screen reader reads as on or off. */
@Composable
private fun Toggle(label: String, open: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
            .toggleable(value = open, role = Role.Checkbox, onValueChange = onChange),
    ) {
        Checkbox(checked = open, onCheckedChange = null)
        Text(label, modifier = Modifier.padding(start = 8.dp))
    }
}

/** The home's time zone, and the way to choose another from the list. */
@Composable
private fun TimeZone(zone: String, onChoose: (java.time.ZoneId) -> Unit) {
    var choosing by rememberSaveable { mutableStateOf(false) }
    Text(stringResource(R.string.time_zone_now, zone), style = MaterialTheme.typography.bodyLarge)
    OutlinedButton(onClick = { choosing = true }) {
        Text(stringResource(R.string.time_zone_change))
    }
    if (choosing) {
        TimeZoneDialog(zone, onChoose = { chosen ->
            onChoose(chosen)
            choosing = false
        }, onDismiss = { choosing = false })
    }
}
