package nl.enadeag.carchargeplanner.android

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import nl.enadeag.carchargeplanner.android.home.NotificationCard
import nl.enadeag.carchargeplanner.android.ui.AppScreen
import nl.enadeag.carchargeplanner.android.ui.AppTheme
import nl.enadeag.carchargeplanner.android.ui.AppViewModel
import nl.enadeag.carchargeplanner.android.ui.notify.allowed

/**
 * The app's one activity: set-up, the problem of a settings file, or the home, as the settings file decides. It is
 * single-top, so a notification's tap reaches the activity shown; a battery check's tap opens the battery level's
 * entry on the home, once: not again when the activity is made again, as after turning the phone, nor when the app is
 * opened from the recent apps with that tap's intent.
 */
class MainActivity : ComponentActivity() {

    companion object {
        /** The action of the intent of a battery check's notification, which opens the battery level's entry. */
        const val OPEN_BATTERY_ENTRY = "nl.enadeag.carchargeplanner.android.action.OPEN_BATTERY_ENTRY"
    }

    private val model: AppViewModel by viewModels {
        viewModelFactory {
            initializer {
                val app = application as CarChargePlannerApp
                val context = app.applicationContext
                AppViewModel(app.engine, app.saves, app.scope, createSavedStateHandle(), locale(), searchLanguage(),
                    app.problems::dashboardSaw) { scope ->
                    NotificationCard(app.phoneState, { allowed(context) },
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU, scope)
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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        follow(intent)
    }

    /** Opens what a notification's tap asks for; the app's start and a dashboard's tap ask for nothing more. */
    private fun follow(intent: Intent) {
        val fromRecents = intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0
        if (intent.action == OPEN_BATTERY_ENTRY && !fromRecents) {
            model.flow.openBatteryEntry()
        }
    }

    private fun locale() = resources.configuration.locales[0]

    private fun searchLanguage() = getString(R.string.place_search_language)
}
