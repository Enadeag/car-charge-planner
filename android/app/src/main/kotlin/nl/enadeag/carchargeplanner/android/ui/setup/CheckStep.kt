package nl.enadeag.carchargeplanner.android.ui.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import nl.enadeag.carchargeplanner.android.R
import nl.enadeag.carchargeplanner.android.setup.SetUpCheck
import nl.enadeag.carchargeplanner.android.setup.SetUpSession
import nl.enadeag.carchargeplanner.android.ui.components.EnglishDetails
import nl.enadeag.carchargeplanner.android.ui.components.AppOutlinedButton

/**
 * The last step: one forecast for what was entered, which does not block Finish when it fails, and what Finish's save
 * came to when it failed. Both are live regions, so a screen reader reads them as they come in.
 */
@Composable
internal fun CheckStep(session: SetUpSession) {
    val check by session.check.state.collectAsStateWithLifecycle()
    val finishing by session.finishing.collectAsStateWithLifecycle()
    val provider = session.providerName()
    Text(stringResource(R.string.setup_check_intro))
    Column(verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }) {
        when (val now = check) {
            SetUpCheck.State.NotAsked -> Unit
            SetUpCheck.State.Asking -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                Text(stringResource(R.string.check_asking, provider), modifier = Modifier.padding(start = 8.dp))
            }
            is SetUpCheck.State.Works -> Text(pluralStringResource(R.plurals.check_works, now.days, provider, now.days))
            is SetUpCheck.State.Failed -> {
                Text(stringResource(R.string.check_failed))
                EnglishDetails(now.detail)
                AppOutlinedButton(onClick = { session.checkAgain() }) {
                    Text(stringResource(R.string.check_again))
                }
            }
        }
    }
    val failed = finishing as? SetUpSession.Finishing.Failed
    if (failed != null) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Assertive }) {
            Text(stringResource(R.string.finish_failed), color = MaterialTheme.colorScheme.error)
            EnglishDetails(failed.detail)
            AppOutlinedButton(onClick = { session.finish() }) {
                Text(stringResource(R.string.action_try_again))
            }
        }
    }
}
