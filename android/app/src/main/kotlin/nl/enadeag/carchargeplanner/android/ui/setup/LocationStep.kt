package nl.enadeag.carchargeplanner.android.ui.setup

import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import nl.enadeag.carchargeplanner.android.form.LocationForm
import nl.enadeag.carchargeplanner.android.setup.PlaceSearch
import nl.enadeag.carchargeplanner.android.setup.SetUpSession
import nl.enadeag.carchargeplanner.android.ui.components.EntryField
import nl.enadeag.carchargeplanner.android.ui.components.FieldKind
import nl.enadeag.carchargeplanner.android.ui.components.NumberField
import nl.enadeag.carchargeplanner.android.ui.components.ProblemFocus
import nl.enadeag.carchargeplanner.android.ui.components.rememberFieldFocus
import nl.enadeag.carchargeplanner.engine.forecast.PlaceResults
import nl.enadeag.carchargeplanner.android.ui.components.AppButton
import nl.enadeag.carchargeplanner.android.ui.components.AppOutlinedButton

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
 * The place search with its results, or a line that the provider has none; once a place is chosen, a card with what
 * it filled in, which takes the focus; the coordinates; and the time zone under "Advanced".
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
    val choices by places.choices.collectAsStateWithLifecycle()
    // a choice made before the fields were shown, as before turning the phone or leaving the step, does not take
    // the focus again
    var seenChoices by remember(places) { mutableIntStateOf(places.choices.value) }
    val card = remember { FocusRequester() }
    LaunchedEffect(choices, chosen) {
        if (chosen != null && choices != seenChoices) {
            // the card is composed with the choice, which this waits for
            withFrameNanos { }
            card.requestFocus()
            seenChoices = choices
        }
    }

    if (hasSearch) {
        EntryField(stringResource(R.string.place_field), stringResource(R.string.place_hint, provider),
            edits.shown { places.text() }, { text -> edits.make { places.edit(text) } }, FieldKind.TEXT,
            shown[LocationForm.Field.PLACE], false,
            rememberFieldFocus(problems, 0, LocationForm.Field.PLACE in shown),
            imeAction = ImeAction.Search, onAction = { places.search() })
        AppButton(onClick = { places.search() }) {
            Text(stringResource(R.string.place_search))
        }
        chosen?.let { label ->
            val shownCoordinates = if (coordinates == Coordinates.SHOWN) null else edits.shown {
                location.text(LocationForm.Field.LATITUDE) to location.text(LocationForm.Field.LONGITUDE)
            }
            ChosenPlace(label, shownCoordinates, edits.shown { location.timeZone()?.id.orEmpty() }, card)
        }
        SearchResults(places) { result -> edits.make { onChoose(result) } }
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

/**
 * What a choice from the search filled in, on a card under the Search button: the place, its coordinates as the
 * fields hold them, and its time zone. It is one part for a screen reader, which takes the focus after a choice.
 *
 * @param label the place chosen, as the results showed it
 * @param coordinates the latitude and longitude as written in the fields; null where the fields are always shown
 * @param zone the time zone's id, such as Europe/Amsterdam
 * @param focus moves the focus to the card
 */
@Composable
private fun ChosenPlace(label: String, coordinates: Pair<String, String>?, zone: String, focus: FocusRequester) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) { }.focusRequester(focus).focusable()) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(16.dp)) {
            Icon(Icons.Filled.CheckCircle, contentDescription = null)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.place_chosen_label, label), style = MaterialTheme.typography.titleMedium)
                coordinates?.let { (latitude, longitude) ->
                    Text(stringResource(R.string.place_chosen_coordinates, latitude, longitude))
                }
                Text(stringResource(R.string.time_zone_now, zone))
            }
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
