package nl.enadeag.carchargeplanner.android

import android.Manifest
import android.app.NotificationManager
import android.os.StrictMode
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import java.nio.file.Path
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.TimeZone
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import nl.enadeag.carchargeplanner.android.store.BatteryLevelSave
import nl.enadeag.carchargeplanner.engine.ForecastUse
import nl.enadeag.carchargeplanner.engine.Outlook
import nl.enadeag.carchargeplanner.engine.battery.BatteryFile
import nl.enadeag.carchargeplanner.engine.config.ConfigProblem
import nl.enadeag.carchargeplanner.engine.config.Percent
import nl.enadeag.carchargeplanner.engine.store.FilePrivacy
import nl.enadeag.carchargeplanner.engine.store.PrivateFiles
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * How the app's Application puts the phone's preferences, the Problems notification and the engine's clock together, on
 * Robolectric.
 */
@RunWith(AndroidJUnit4::class)
class AppWiringTest {

    private companion object {
        val NOON: Instant = Instant.parse("2026-06-07T12:00:00Z")
    }

    /** The app as Android makes it, on a clock that stands still at [NOON]. */
    class AppAtNoon : CarChargePlannerApp() {
        override val clock: Clock = Clock.fixed(NOON, ZoneOffset.UTC)
    }

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

    @Test
    fun theRoundsKeepTheirMissedCountInTheStateFile() {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        runBlocking { app.phoneState.missedRounds(2) }

        // without a settings file the round's outlook is NotSetUp, after which the count starts again
        runBlocking { app.rounds.run(ForecastUse.STORED_ONLY) }

        assertThat(runBlocking { app.phoneState.missedRounds() }).isZero()
    }

    @Test
    fun theAppsTimeZoneIsThePhonesWhenAsked() {
        val atStart = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Europe/Amsterdam"))
            assertThat(app.zone()).isEqualTo(ZoneId.of("Europe/Amsterdam"))

            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Tokyo"))

            assertThat(app.zone()).isEqualTo(ZoneId.of("Asia/Tokyo"))
        } finally {
            TimeZone.setDefault(atStart)
        }
    }

    @Test
    @Config(application = AppAtNoon::class)
    fun theEnginePlansFromTheAppsClock() {
        val saved = runBlocking { app.engine.saveBatteryLevel(Percent(55.0)) }

        assertThat(saved).isEqualTo(BatteryLevelSave.Saved)
        val reading = onDisk { BatteryFile(app.platform.dataFolder().resolve("battery.json"),
            PrivateFiles(FilePrivacy.NONE)).reading() }
        assertThat(reading.map { it.enteredAt() }).contains(NOON)
    }
}
