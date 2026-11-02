package nl.enadeag.carchargeplanner.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import java.time.ZoneId
import nl.enadeag.carchargeplanner.android.form.NumberText
import nl.enadeag.carchargeplanner.android.ui.AppScreen
import nl.enadeag.carchargeplanner.android.ui.AppTheme
import nl.enadeag.carchargeplanner.android.ui.AppViewModel

/** The app's one activity: set-up, the problem of a settings file, or the home, as the settings file decides. */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AppTheme {
                val locale = LocalConfiguration.current.locales[0]
                val language = stringResource(R.string.place_search_language)
                val model = viewModel {
                    AppViewModel(application as CarChargePlannerApp, createSavedStateHandle(), NumberText(locale),
                        ZoneId.systemDefault(), language)
                }
                AppScreen(model.flow, model.phoneZone)
            }
        }
    }
}
