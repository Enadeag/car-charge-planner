package nl.enadeag.carchargeplanner.android

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.launch
import nl.enadeag.carchargeplanner.android.home.NotificationCard
import nl.enadeag.carchargeplanner.android.ui.AppScreen
import nl.enadeag.carchargeplanner.android.ui.AppTheme
import nl.enadeag.carchargeplanner.android.ui.AppViewModel
import nl.enadeag.carchargeplanner.android.ui.notify.allowed

/**
 * The app's one activity: set-up, the problem of a settings file, or the home, as the settings file decides. It is
 * single-top, so a notification's tap reaches the activity shown. A tap takes its notification away, also a tap on its
 * action, and a battery check's tap opens the battery level's entry on the home; each once: not again when the
 * activity is made again, as after turning the phone, nor when the app is opened from the recent apps with that tap's
 * intent, which would take away a newer notification under the same tag. Each start of the activity, and the owner
 * allowing notifications on the dashboard's card, sets the work in the background up again, so it follows the
 * settings file and whether notifications are allowed.
 */
class MainActivity : ComponentActivity() {

    companion object {
        /** The action of the intent of a battery check's notification, which opens the battery level's entry. */
        const val OPEN_BATTERY_ENTRY = "nl.enadeag.carchargeplanner.android.action.OPEN_BATTERY_ENTRY"

        private const val NOTICE_SCHEME = "notice"

        /**
         * The data of a notification's intent, which names the notification.
         *
         * @param tag the notification's tag
         * @return such as `notice:battery_check:1780300800`
         */
        internal fun noticeUri(tag: String): Uri = Uri.fromParts(NOTICE_SCHEME, tag, null)
    }

    private val model: AppViewModel by viewModels {
        viewModelFactory {
            initializer {
                val app = application as CarChargePlannerApp
                val context = app.applicationContext
                AppViewModel(app.engine, app.saves, app.scope, createSavedStateHandle(), locale(), searchLanguage(),
                    app.problems::dashboardSaw) { scope ->
                    // Android's question only pauses this activity, so no start sets the work up after a grant
                    NotificationCard(app.phoneState, { allowed(context) },
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU, scope) {
                        app.scope.launch { app.schedule.refresh() }
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // a change of the app's language makes this activity again, but not the model
        model.speak(locale(), searchLanguage())
        if (savedInstanceState == null) {
            follow(intent)
        }
        val wifiOnly = (application as CarChargePlannerApp).phoneSettings
        setContent {
            AppTheme {
                AppScreen(model.flow, model.card, wifiOnly)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        val app = application as CarChargePlannerApp
        app.scope.launch { app.schedule.refresh() }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        follow(intent)
    }

    /**
     * Takes a tapped notification away and opens what its tap asks for; the app's start and a dashboard's tap ask for
     * nothing more. An intent from the recent apps was followed when it first came.
     */
    private fun follow(intent: Intent) {
        if (intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0) {
            return
        }
        intent.data?.takeIf { it.scheme == NOTICE_SCHEME }?.schemeSpecificPart?.let { tag ->
            NotificationManagerCompat.from(this).cancel(tag, 0)
        }
        if (intent.action == OPEN_BATTERY_ENTRY) {
            model.flow.openBatteryEntry()
        }
    }

    private fun locale() = resources.configuration.locales[0]

    private fun searchLanguage() = getString(R.string.place_search_language)
}
