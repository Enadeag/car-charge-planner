package nl.enadeag.carchargeplanner.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import java.time.LocalDate
import java.time.ZoneId
import nl.enadeag.carchargeplanner.engine.config.Car

/** The app's one screen for now: what the engine planned for a made-up sunny day, and the JSON check. */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val zone = ZoneId.systemDefault()
        val windows = SunnyDayPlan.windows(LocalDate.now(zone), zone)
        val json = CarJson.write(Car.TYPICAL)
        val readBackSame = CarJson.read(json) == Car.TYPICAL
        setContent {
            SkeletonTheme {
                SkeletonScreen(windows, zone, json, readBackSame)
            }
        }
    }
}
