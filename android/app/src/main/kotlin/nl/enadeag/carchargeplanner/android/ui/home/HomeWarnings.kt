package nl.enadeag.carchargeplanner.android.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import nl.enadeag.carchargeplanner.android.R
import nl.enadeag.carchargeplanner.android.home.HomeOutlook

/**
 * A warning above the plan, with a warning sign and the words, in a container that stands out and a polite live
 * region, so a refresh that brings it is heard; the plan below stays in view. That the forecast could not be
 * refreshed, or that a provider refused its API key.
 */
@Composable
internal fun Warning(lines: List<String>) {
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
internal fun KeyRefused(names: List<String>) {
    if (names.isNotEmpty()) {
        Warning(names.map { stringResource(R.string.key_refused_warning, it) })
    }
}

/** The providers that refused their API key, by name, for the home without a plan; none while it is asked. */
internal fun keyRefusedBy(state: HomeOutlook.State): List<String> = when (state) {
    is HomeOutlook.State.NoForecast -> state.keyRefusedBy
    is HomeOutlook.State.NoDays -> state.keyRefusedBy
    is HomeOutlook.State.Planned -> state.dashboard.keyRefusedBy
    HomeOutlook.State.Asking -> listOf()
}
