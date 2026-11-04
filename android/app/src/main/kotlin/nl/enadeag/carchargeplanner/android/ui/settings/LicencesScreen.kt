package nl.enadeag.carchargeplanner.android.ui.settings

import android.content.res.AssetManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.collapse
import androidx.compose.ui.semantics.expand
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import nl.enadeag.carchargeplanner.android.R
import nl.enadeag.carchargeplanner.android.licences.LicenceFiles
import nl.enadeag.carchargeplanner.android.licences.LicenceIndex
import nl.enadeag.carchargeplanner.android.ui.components.BarPage

/** Where the app's sources are, shown as plain text: the screen links to no website (#331). */
private const val SOURCE_CODE = "github.com/Enadeag/car-charge-planner"

/**
 * The licence texts of the app and its libraries, as the build put them in the assets.
 *
 * @param assets the app's assets
 */
internal class AssetLicenceFiles(private val assets: AssetManager) : LicenceFiles {
    override fun read(name: String): String =
        assets.open(LicenceIndex.FOLDER + name).use { it.readBytes().toString(Charsets.UTF_8) }
}

/** What the screen has of a text or of the index: still reading, what was read, or that it could not be read. */
private sealed interface Reading<out T> {
    /** Still reading. */
    data object Busy : Reading<Nothing>

    /**
     * Read.
     *
     * @property value what was read
     */
    data class Read<T>(val value: T) : Reading<T>

    /** Missing, or not readable. */
    data object Failed : Reading<Nothing>
}

/**
 * The read-only screen of the open source licences (#331), opened from Settings' last row and not one of its pages:
 * the app's name, its copyright line and where its source code is, then one row per text, the app's own first, each
 * by its title. A row is one button that shows its text under it or hides it again, and says which it does; only a
 * text shown is read and drawn. The texts are read off the main thread; one that cannot be read says so. Back and Up
 * return to Settings' list.
 *
 * @param files where the texts are read from
 * @param onClose goes back to Settings' list
 */
@Composable
internal fun LicencesScreen(files: LicenceFiles, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    val title = remember { FocusRequester() }
    LaunchedEffect(Unit) { title.requestFocus() }
    val index by produceState<Reading<LicenceIndex>>(Reading.Busy, files) {
        value = read(files) { LicenceIndex.parse(it.read(LicenceIndex.INDEX)) }
    }
    // the texts shown, by asset; kept while the screen lives, also when the phone is turned
    var shown by rememberSaveable { mutableStateOf(listOf<String>()) }
    BarPage(stringResource(R.string.settings_licences), title, onUp = onClose, padded = false) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleMedium)
            (index as? Reading.Read)?.let { Text(it.value.copyright) }
            Text(stringResource(R.string.licences_source, SOURCE_CODE))
            Text(stringResource(R.string.licences_in_english))
        }
        when (val now = index) {
            Reading.Busy -> Column(Modifier.padding(16.dp)) { Waiting(stringResource(R.string.licences_reading)) }
            Reading.Failed -> NotRead()
            is Reading.Read -> now.value.parts.forEach { part ->
                HorizontalDivider()
                val open = part.asset in shown
                PartRow(part.title, open) { shown = if (open) shown - part.asset else shown + part.asset }
                if (open) {
                    PartText(files, part.asset)
                }
            }
        }
    }
}

/**
 * A text's row: its title, and an arrow that points down while the text is hidden and up while it shows, which is
 * decoration as the row says the same to a screen reader.
 */
@Composable
private fun PartRow(title: String, open: Boolean, toggle: () -> Unit) {
    val state = stringResource(if (open) R.string.licences_expanded else R.string.licences_collapsed)
    ListItem(
        headlineContent = { Text(title) },
        trailingContent = {
            Icon(if (open) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown, contentDescription = null)
        },
        modifier = Modifier.heightIn(min = 56.dp).clickable(role = Role.Button, onClick = toggle).semantics {
            stateDescription = state
            if (open) {
                collapse { toggle(); true }
            } else {
                expand { toggle(); true }
            }
        },
    )
}

/** A text shown under its row, in the body's font, wrapping, read off the main thread once it shows. */
@Composable
private fun PartText(files: LicenceFiles, asset: String) {
    val text by produceState<Reading<String>>(Reading.Busy, files, asset) { value = read(files) { it.read(asset) } }
    when (val now = text) {
        Reading.Busy -> Column(Modifier.padding(16.dp)) { Waiting(stringResource(R.string.licences_reading)) }
        Reading.Failed -> NotRead()
        // a paragraph per part, so a screen reader moves through a long licence a paragraph at a time
        is Reading.Read -> Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            now.value.split(Regex("\n\\s*\n")).filter { it.isNotBlank() }.forEach { paragraph ->
                Text(paragraph.trimEnd(), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/** That a text, or the list of texts, could not be read, in the error colour. */
@Composable
private fun NotRead() {
    Text(stringResource(R.string.licences_not_read), color = MaterialTheme.colorScheme.error,
        modifier = Modifier.padding(16.dp))
}

/** Reads off the main thread; a text that is missing or cannot be read, or an index the build never wrote, fails. */
private suspend fun <T> read(files: LicenceFiles, what: (LicenceFiles) -> T): Reading<T> =
    withContext(Dispatchers.IO) {
        try {
            Reading.Read(what(files))
        } catch (ignored: IOException) {
            Reading.Failed
        }
    }
