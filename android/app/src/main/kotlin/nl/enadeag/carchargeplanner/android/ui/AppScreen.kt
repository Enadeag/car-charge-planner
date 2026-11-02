package nl.enadeag.carchargeplanner.android.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import java.time.ZoneId
import nl.enadeag.carchargeplanner.android.CarChargePlannerApp
import nl.enadeag.carchargeplanner.android.form.NumberText
import nl.enadeag.carchargeplanner.android.start.AppFlow
import nl.enadeag.carchargeplanner.android.ui.home.HomeScreen
import nl.enadeag.carchargeplanner.android.ui.setup.SetUpScreen
import nl.enadeag.carchargeplanner.android.ui.start.NewerVersionScreen
import nl.enadeag.carchargeplanner.android.ui.start.ReadingScreen
import nl.enadeag.carchargeplanner.android.ui.start.UnusableScreen

/**
 * The app's screens for as long as the activity's screen lives, also when the phone is turned; set-up's step and
 * texts survive the system ending the app through the saved state.
 *
 * @param app the app, whose engine and saves the screens use
 * @param handle the screen's saved state
 * @param numbers how the screen's language writes numbers
 * @param phoneZone the phone's time zone
 * @param language the app's language as the place search takes it, such as "nl"
 */
internal class AppViewModel(app: CarChargePlannerApp, handle: SavedStateHandle, numbers: NumberText,
    val phoneZone: ZoneId, language: String) : ViewModel() {

    /** Which screen is shown. */
    val flow = AppFlow(app.engine, app.saves, app.scope, viewModelScope, handle, numbers, phoneZone, language)
}

/**
 * The screen the app shows now.
 *
 * @param flow which screen it is
 * @param phoneZone the phone's time zone, which the home names the home's against
 */
@Composable
internal fun AppScreen(flow: AppFlow, phoneZone: ZoneId) {
    val screen by flow.screen.collectAsStateWithLifecycle()
    when (val now = screen) {
        AppFlow.Screen.Reading -> ReadingScreen()
        is AppFlow.Screen.SetUp -> SetUpScreen(now.session)
        is AppFlow.Screen.Unusable -> UnusableScreen(now.again)
        is AppFlow.Screen.NewerVersion -> NewerVersionScreen()
        is AppFlow.Screen.Home -> HomeScreen(now.outlook, phoneZone)
    }
}
