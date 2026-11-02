package nl.enadeag.carchargeplanner.android.ui.home

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import nl.enadeag.carchargeplanner.android.R
import nl.enadeag.carchargeplanner.android.form.NumberText
import nl.enadeag.carchargeplanner.android.home.HomeOutlook
import nl.enadeag.carchargeplanner.android.home.TodayPlan
import nl.enadeag.carchargeplanner.android.ui.components.EnglishDetails
import nl.enadeag.carchargeplanner.android.ui.components.Heading
import nl.enadeag.carchargeplanner.android.ui.components.Page
import nl.enadeag.carchargeplanner.engine.plan.Window

/**
 * The plain home after set-up: today's charge windows, else the next one, else that there is none today, with
 * Refresh, and Settings, which comes in a later version. The times follow the phone's 12- or 24-hour clock, in the
 * home's time zone, which is named when it is not the phone's.
 *
 * @param outlook the plan it shows
 * @param phoneZone the phone's time zone as it is now, read each time the plan is shown
 */
@Composable
internal fun HomeScreen(outlook: HomeOutlook, phoneZone: () -> ZoneId) {
    val state by outlook.state.collectAsStateWithLifecycle()
    val heading = remember { FocusRequester() }
    LaunchedEffect(Unit) { heading.requestFocus() }
    Page {
        Heading(stringResource(R.string.home_title), heading)
        when (val now = state) {
            HomeOutlook.State.Asking -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                Text(stringResource(R.string.home_asking), modifier = Modifier.padding(start = 8.dp))
            }
            is HomeOutlook.State.Planned -> Today(now.today, phoneZone())
            is HomeOutlook.State.NoForecast -> {
                Text(stringResource(R.string.home_no_forecast))
                EnglishDetails(now.reason)
            }
        }
        Button(onClick = { outlook.refresh() }, enabled = state != HomeOutlook.State.Asking) {
            Text(stringResource(if (state is HomeOutlook.State.NoForecast) R.string.action_try_again
                else R.string.home_refresh))
        }
        SettingsLater()
    }
}

/** What the plan says about today. */
@Composable
private fun Today(today: TodayPlan, phoneZone: ZoneId) {
    val locale = LocalConfiguration.current.locales[0]
    val clock = clockOf(DateFormat.is24HourFormat(LocalContext.current), locale).withZone(today.zone)
    val numbers = NumberText(locale)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (today.zone != phoneZone) {
            Text(stringResource(R.string.home_zone, today.zone.id))
        }
        when (val windows = today.windows) {
            is TodayPlan.Windows.Today -> windows.windows.forEach { window ->
                Text(stringResource(R.string.home_window, clock.format(window.start()), clock.format(window.end()),
                    window.amps(), numbers.write(window.powerKw(), 1)))
            }
            is TodayPlan.Windows.Next -> Text(nextWindow(windows.window, today.zone, clock, numbers, locale))
            TodayPlan.Windows.None -> Text(stringResource(R.string.home_no_window))
        }
        today.assumedLevel?.let { Text(stringResource(R.string.home_assumed, numbers.write(it.value(), 0))) }
    }
}

@Composable
private fun nextWindow(window: Window, zone: ZoneId, clock: DateTimeFormatter, numbers: NumberText, locale: Locale) =
    stringResource(R.string.home_next_window,
        window.start().atZone(zone).dayOfWeek.getDisplayName(TextStyle.FULL, locale), clock.format(window.start()),
        clock.format(window.end()), window.amps(), numbers.write(window.powerKw(), 1))

/** Settings, shown but off until it comes, with the reason, which a screen reader reads with the button. */
@Composable
private fun SettingsLater() {
    val reason = stringResource(R.string.home_settings_later)
    OutlinedButton(onClick = {}, enabled = false, modifier = Modifier.semantics { stateDescription = reason }) {
        Text(stringResource(R.string.home_settings))
    }
    Text(reason, modifier = Modifier.clearAndSetSemantics { })
}

/**
 * Hours and minutes as the phone's clock shows them.
 *
 * @param twentyFour whether the phone uses a 24-hour clock
 * @param locale the app's language
 * @return such as 13:05 or 1:05 PM
 */
internal fun clockOf(twentyFour: Boolean, locale: Locale): DateTimeFormatter =
    DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, if (twentyFour) "Hm" else "hma"), locale)
