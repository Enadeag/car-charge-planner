package nl.enadeag.carchargeplanner.android

import android.Manifest
import android.app.NotificationManager
import android.os.StrictMode
import androidx.datastore.preferences.core.PreferencesFileSerializer
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import java.nio.file.Path
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.TimeZone
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import nl.enadeag.carchargeplanner.android.store.BatteryLevelSave
import nl.enadeag.carchargeplanner.android.store.FakeEngineAccess
import nl.enadeag.carchargeplanner.android.store.StartState
import nl.enadeag.carchargeplanner.engine.ForecastUse
import nl.enadeag.carchargeplanner.engine.Outlook
import nl.enadeag.carchargeplanner.engine.battery.BatteryFile
import nl.enadeag.carchargeplanner.engine.config.ConfigProblem
import nl.enadeag.carchargeplanner.engine.config.Percent
import nl.enadeag.carchargeplanner.engine.store.FilePrivacy
import nl.enadeag.carchargeplanner.engine.store.PrivateFiles
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.entry
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

    private fun file(name: String) = File(app.filesDir, "datastore/$name.preferences_pb")

    private fun stored(name: String): Boolean = onDisk { file(name).exists() }

    /** What a store's file holds on the disk, by key name; empty while there is no file. */
    private fun held(name: String): Map<String, Any> = onDisk {
        if (!file(name).exists()) {
            emptyMap()
        } else {
            val preferences = runBlocking { file(name).inputStream().use { PreferencesFileSerializer.readFrom(it) } }
            preferences.asMap().mapKeys { it.key.name }
        }
    }

    @Test
    fun theWifiOnlySwitchGoesToTheSettingsFileAndTheCardsStateToTheStateFile() {
        app.phoneSettings.switch(true)
        // DataStore shows a write to its readers just before it moves the file into place, so the file is waited for
        runBlocking { withTimeout(5_000) { while (held("settings").isEmpty()) delay(10) } }
        runBlocking { app.phoneState.close() }

        assertThat(held("settings")).containsExactly(entry("wifi_only", true))
        assertThat(held("state")).containsExactly(entry("card_closed", true))
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
    fun aCancelWaitsForTheRoundThatRunsAsTheRoundsAndTheScheduleShareOneLock() {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val engine = FakeEngineAccess(StartState.NotSetUp)
        val gate = CompletableDeferred<Unit>()
        engine.roundGate = gate
        app.engine = engine

        runBlocking {
            withTimeout(5_000) {
                val round = launch { app.rounds.run(ForecastUse.ASK_PROVIDERS) }
                while (engine.rounds.isEmpty()) yield()
                // without a settings file the schedule cancels the work, once the round that runs has ended
                val refresh = launch { app.schedule.refresh() }
                repeat(10) { yield() }

                assertThat(refresh.isCompleted).`as`("the cancel waits for the round").isFalse()

                gate.complete(Unit)
                round.join()
                refresh.join()
            }
        }
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
            PrivateFiles(FilePrivacy.NONE), app.clock).reading() }
        assertThat(reading.map { it.enteredAt() }).contains(NOON)
    }
}
