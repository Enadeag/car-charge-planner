package nl.enadeag.carchargeplanner.android

import android.Manifest
import android.app.NotificationManager
import android.os.StrictMode
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import java.nio.file.Path
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import nl.enadeag.carchargeplanner.engine.ForecastUse
import nl.enadeag.carchargeplanner.engine.Outlook
import nl.enadeag.carchargeplanner.engine.config.ConfigProblem
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

/** How the app's Application puts the phone's preferences and the Problems notification together, on Robolectric. */
@RunWith(AndroidJUnit4::class)
class AppWiringTest {

    private val app = ApplicationProvider.getApplicationContext<CarChargePlannerApp>()
    private val manager = app.getSystemService(NotificationManager::class.java)

    /** Runs a look at the disk on the main thread, which the debug build's StrictMode otherwise refuses. */
    private fun <T> onDisk(look: () -> T): T {
        val policy = StrictMode.allowThreadDiskWrites()
        StrictMode.allowThreadDiskReads()
        return try {
            look()
        } finally {
            StrictMode.setThreadPolicy(policy)
        }
    }

    private fun stored(name: String): Boolean = onDisk { File(app.filesDir, "datastore/$name.preferences_pb").exists() }

    @Test
    fun theWifiOnlySwitchGoesToTheSettingsFileAndTheCardsStateToTheStateFile() {
        app.phoneSettings.switch(true)
        runBlocking { withTimeout(5_000) { app.phoneSettings.on.first { it } } }

        assertThat(stored("settings")).isTrue()
        assertThat(stored("state")).isFalse()

        runBlocking { app.phoneState.close() }

        assertThat(stored("state")).isTrue()
    }

    @Test
    fun theAppsStoresReplaceAFileThatCannotBeParsedSoTheirWritesAreKeptAgain() {
        onDisk {
            for (name in listOf("settings", "state")) {
                File(app.filesDir, "datastore/$name.preferences_pb").apply { parentFile?.mkdirs() }
                    .writeBytes(byteArrayOf(0x7f, 0x13, 0x00, 0x42, 0x55))
            }
        }

        app.phoneSettings.switch(true)
        runBlocking { app.phoneState.close() }

        runBlocking { withTimeout(5_000) { app.phoneSettings.on.first { it } } }
        assertThat(runBlocking { app.phoneState.closed() }).isTrue()
    }

    @Test
    fun theProblemsEpisodeIsKeptInTheStateFileAndShownByTheAppsNotifications() {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val unusable = Outlook.ConfigUnusable(ConfigProblem.Kind.INVALID, Path.of("engine.json"), "detail")

        runBlocking { app.problems.afterRound(unusable, ForecastUse.ASK_PROVIDERS) }

        assertThat(shadowOf(manager).getNotification("problems", 0)).isNotNull()
        assertThat(runBlocking { app.phoneState.episodeStarted() }).isTrue()
        assertThat(stored("state")).isTrue()
        assertThat(stored("settings")).isFalse()
    }
}
