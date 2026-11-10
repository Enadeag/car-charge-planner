package nl.enadeag.carchargeplanner.android.ui.home

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import nl.enadeag.carchargeplanner.android.R
import nl.enadeag.carchargeplanner.android.home.NotificationCard
import nl.enadeag.carchargeplanner.android.ui.components.AppButton
import nl.enadeag.carchargeplanner.android.ui.notify.openNotificationSettings

/**
 * The card that asks for notifications while they are not allowed: what they are for and where to change it later,
 * and one button that asks Android, or, when Android will not ask, opens the app's notification settings, with a line
 * that says why. It is one card that scrolls with the page and is no live region; a screen reader reads its title, its
 * text, its button and last its ×, which closes it for good, after which the focus goes on, as the card is gone. When
 * a refusal swaps the button for the other one, the focus goes to the card's title, as the button it was on is gone.
 *
 * @param closed moves the focus once the card is closed
 */
@Composable
internal fun NotificationsCard(card: NotificationCard, closed: () -> Unit) {
    val shown by card.shown.collectAsStateWithLifecycle()
    if (shown == NotificationCard.Shown.NONE) {
        return
    }
    val context = LocalContext.current
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission(), card::answered)
    val title = remember { FocusRequester() }
    val before = remember { ShownBefore(shown) }
    LaunchedEffect(shown) {
        if (before.shown == NotificationCard.Shown.ASK && shown == NotificationCard.Shown.OPEN_SETTINGS) {
            title.requestFocus()
        }
        before.shown = shown
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp).semantics { isTraversalGroup = true },
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.notification_card_title), style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f).focusRequester(title).focusable().semantics { heading() })
                // beside the title for the eye, but read last, after the button; drawn at 48 dp, not Material's 40
                IconButton(onClick = {
                    card.close()
                    closed()
                }, modifier = Modifier.size(48.dp).semantics { traversalIndex = 1f }) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_close))
                }
            }
            Text(stringResource(R.string.notification_card_body))
            if (shown == NotificationCard.Shown.ASK) {
                AppButton(onClick = {
                    // the card asks only where Android has the permission, Android 13 and later
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        ask.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }) {
                    Text(stringResource(R.string.notification_card_ask))
                }
            } else {
                Text(stringResource(R.string.notification_card_no_ask))
                AppButton(onClick = { openNotificationSettings(context) }) {
                    Text(stringResource(R.string.notification_card_open_settings))
                }
            }
        }
    }
}

/** What the notification card showed when last looked at, which drawing never reads. */
private class ShownBefore(var shown: NotificationCard.Shown)
