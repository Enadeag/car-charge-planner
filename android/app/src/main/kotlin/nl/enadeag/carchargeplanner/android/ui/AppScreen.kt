package nl.enadeag.carchargeplanner.android.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import java.time.ZoneId
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import nl.enadeag.carchargeplanner.android.form.NumberText
import nl.enadeag.carchargeplanner.android.home.NotificationCard
import nl.enadeag.carchargeplanner.android.prefs.WifiOnly
import nl.enadeag.carchargeplanner.android.start.AppFlow
import nl.enadeag.carchargeplanner.android.store.EngineAccess
import nl.enadeag.carchargeplanner.android.store.Saves
import nl.enadeag.carchargeplanner.android.ui.home.HomeScreen
import nl.enadeag.carchargeplanner.android.ui.settings.SettingsScreen
import nl.enadeag.carchargeplanner.android.ui.setup.SetUpScreen
import nl.enadeag.carchargeplanner.android.ui.start.NewerVersionScreen
import nl.enadeag.carchargeplanner.android.ui.start.ReadingScreen
import nl.enadeag.carchargeplanner.android.ui.start.UnusableScreen
import nl.enadeag.carchargeplanner.engine.Outlook

/**
 * The app's screens for as long as the activity's screen lives, also when the phone is turned or the app's language
 * changes; set-up's step and texts survive the system ending the app through the saved state. The language is not
 * kept here but set by each activity as it is made, as a change of the app's language makes the activity again while
 * this model stays.
 *
 * @param access the engine's reads and writes
 * @param saves the app's saves, which outlive the screens
 * @param appScope the app's scope, where work that must outlive a screen runs
 * @param handle the screen's saved state
 * @param locale the language of the activity that made this model
 * @param language that language as the place search takes it, such as "nl"
 * @param seen gets each plan the dashboard asked for, so a Problems notification it no longer shows goes
 * @param card makes the dashboard's notification card in this model's scope
 */
internal class AppViewModel(access: EngineAccess, saves: Saves, appScope: CoroutineScope, handle: SavedStateHandle,
    private var locale: Locale, private var language: String, seen: suspend (Outlook) -> Unit = {},
    card: (CoroutineScope) -> NotificationCard) : ViewModel() {

    /** Which screen is shown. */
    val flow = AppFlow(access, saves, appScope, viewModelScope, handle, NumberText { locale }, ZoneId::systemDefault,
        { language }, Dispatchers.Main.immediate, seen = seen)

    /** The dashboard's card that asks for notifications while they are not allowed. */
    val card: NotificationCard = card(viewModelScope)

    /**
     * Takes the language of an activity made again, so what the screens write and search from now on is in it.
     *
     * @param locale the activity's language
     * @param language that language as the place search takes it, such as "nl"
     */
    fun speak(locale: Locale, language: String) {
        this.locale = locale
        this.language = language
    }
}

/**
 * The screen the app shows now.
 *
 * @param flow which screen it is, the way into Settings, and the phone's time zone, which the home names the home's
 *     against
 * @param card the dashboard's card that asks for notifications
 * @param wifiOnly the Forecast page's "Wi-Fi only" switch
 */
@Composable
internal fun AppScreen(flow: AppFlow, card: NotificationCard, wifiOnly: WifiOnly) {
    val screen by flow.screen.collectAsStateWithLifecycle()
    when (val now = screen) {
        AppFlow.Screen.Reading -> ReadingScreen()
        is AppFlow.Screen.SetUp -> SetUpScreen(now.session)
        is AppFlow.Screen.Unusable -> UnusableScreen(now.again)
        is AppFlow.Screen.NewerVersion -> NewerVersionScreen()
        is AppFlow.Screen.Home -> HomeScreen(now.outlook, now.notice, now.battery, now.away, flow.phoneZone,
            card, flow::openSettings)
        is AppFlow.Screen.Settings -> SettingsScreen(now.session, now.page, wifiOnly)
    }
}
