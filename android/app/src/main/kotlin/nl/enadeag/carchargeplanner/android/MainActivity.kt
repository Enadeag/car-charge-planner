package nl.enadeag.carchargeplanner.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import nl.enadeag.carchargeplanner.android.ui.AppScreen
import nl.enadeag.carchargeplanner.android.ui.AppTheme
import nl.enadeag.carchargeplanner.android.ui.AppViewModel

/** The app's one activity: set-up, the problem of a settings file, or the home, as the settings file decides. */
class MainActivity : ComponentActivity() {

    private val model: AppViewModel by viewModels {
        viewModelFactory {
            initializer {
                val app = application as CarChargePlannerApp
                AppViewModel(app.engine, app.saves, app.scope, createSavedStateHandle(), locale(), searchLanguage())
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // a change of the app's language makes this activity again, but not the model
        model.speak(locale(), searchLanguage())
        setContent {
            AppTheme {
                AppScreen(model.flow)
            }
        }
    }

    private fun locale() = resources.configuration.locales[0]

    private fun searchLanguage() = getString(R.string.place_search_language)
}
