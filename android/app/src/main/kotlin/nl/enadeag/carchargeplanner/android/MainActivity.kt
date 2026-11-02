package nl.enadeag.carchargeplanner.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import nl.enadeag.carchargeplanner.android.ui.AppNameScreen
import nl.enadeag.carchargeplanner.android.ui.AppTheme

/** The app's one screen for now: its name, until set-up and Settings come (#314, #315). */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AppTheme {
                AppNameScreen()
            }
        }
    }
}
