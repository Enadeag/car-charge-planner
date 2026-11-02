package nl.enadeag.carchargeplanner.android.ui.setup

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import java.time.ZoneId
import nl.enadeag.carchargeplanner.android.R

/**
 * Chooses the home's time zone from Java's list of zone ids, which a typed text narrows; only an id from the list can
 * be chosen.
 *
 * @param current the zone's id now, marked in the list
 * @param onChoose gets the zone the owner chose
 * @param onDismiss closes the dialog without a choice
 */
@Composable
internal fun TimeZoneDialog(current: String, onChoose: (ZoneId) -> Unit, onDismiss: () -> Unit) {
    val all = remember { ZoneId.getAvailableZoneIds().sorted() }
    var filter by rememberSaveable { mutableStateOf("") }
    val shown = remember(filter) { all.filter { it.contains(filter.trim(), ignoreCase = true) } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.time_zone_title)) },
        text = {
            Column {
                OutlinedTextField(value = filter, onValueChange = { filter = it }, singleLine = true,
                    label = { Text(stringResource(R.string.time_zone_filter)) }, modifier = Modifier.fillMaxWidth())
                if (shown.isEmpty()) {
                    Text(stringResource(R.string.time_zone_none), modifier = Modifier.padding(top = 8.dp))
                }
                LazyColumn(modifier = Modifier.heightIn(max = 360.dp).selectableGroup()) {
                    items(shown, key = { it }) { id ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                                .selectable(selected = id == current, role = Role.RadioButton,
                                    onClick = { onChoose(ZoneId.of(id)) }),
                        ) {
                            RadioButton(selected = id == current, onClick = null)
                            Text(id, modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}
