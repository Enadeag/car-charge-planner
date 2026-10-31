package nl.enadeag.carchargeplanner.android

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.math.roundToInt
import nl.enadeag.carchargeplanner.engine.plan.Window

/**
 * Today's charge windows of the made-up sunny day, and the car's JSON round trip.
 *
 * @param windows today's charge windows in time order, possibly none
 * @param zone the time zone the windows are shown in
 * @param json the car as the engine wrote it to JSON
 * @param readBackSame whether reading [json] back gave the same car
 */
@Composable
internal fun SkeletonScreen(windows: List<Window>, zone: ZoneId, json: String, readBackSame: Boolean) {
    val time = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withZone(zone)
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Heading(stringResource(R.string.app_name), MaterialTheme.typography.headlineSmall)
            Text(
                stringResource(
                    R.string.skeleton_intro,
                    time.format(SunnyDayPlan.PLANNED_AT),
                    SunnyDayPlan.START_LEVEL.value().roundToInt(),
                ),
            )
            Heading(stringResource(R.string.todays_windows), MaterialTheme.typography.titleMedium)
            if (windows.isEmpty()) {
                Text(stringResource(R.string.no_window_today))
            }
            windows.forEach { window ->
                Text(
                    stringResource(
                        R.string.charge_window,
                        time.format(window.start()),
                        time.format(window.end()),
                        window.powerKw(),
                        (window.solarShare() * 100).roundToInt(),
                    ),
                )
            }
            Heading(stringResource(R.string.json_check), MaterialTheme.typography.titleMedium)
            Text(json, fontFamily = FontFamily.Monospace)
            Text(stringResource(if (readBackSame) R.string.json_read_back_same else R.string.json_read_back_changed))
        }
    }
}

/** A text that screen readers announce as a heading, so the owner can jump between the parts. */
@Composable
private fun Heading(text: String, style: TextStyle) {
    Text(text, style = style, modifier = Modifier.semantics { heading() })
}
