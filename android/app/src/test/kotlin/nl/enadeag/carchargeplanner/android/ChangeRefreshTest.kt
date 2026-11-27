package nl.enadeag.carchargeplanner.android

import java.nio.file.Files
import java.nio.file.Path
import java.time.Clock
import java.time.Instant
import kotlin.io.path.copyTo
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import nl.enadeag.carchargeplanner.android.background.BackgroundSchedule
import nl.enadeag.carchargeplanner.android.background.FakeBackground
import nl.enadeag.carchargeplanner.android.secrets.SoftwareKeys
import nl.enadeag.carchargeplanner.android.store.BatteryLevelSave
import nl.enadeag.carchargeplanner.android.store.EngineAccess
import nl.enadeag.carchargeplanner.android.store.EngineOnDispatcher
import nl.enadeag.carchargeplanner.engine.away.OneOffAwayPeriod
import nl.enadeag.carchargeplanner.engine.config.ConfigFile
import nl.enadeag.carchargeplanner.engine.config.Percent
import nl.enadeag.carchargeplanner.engine.notify.Notifier
import nl.enadeag.carchargeplanner.engine.secrets.KeyChanges
import nl.enadeag.carchargeplanner.engine.secrets.Secrets
import nl.enadeag.carchargeplanner.engine.store.FilePrivacy
import nl.enadeag.carchargeplanner.engine.store.PrivateFiles
import nl.enadeag.carchargeplanner.engine.trip.Trip
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

/**
 * Every change the app makes through [EngineAccess], on the engine as the app builds it, reaches
 * [BackgroundSchedule.refresh] through the engine's change listener, wired as the Application wires it, whose own
 * wiring `BackgroundLifecycleTest` checks: the app keeps no list of the changes of its own.
 */
class ChangeRefreshTest {

    private companion object {
        /** The docs' example settings file, a declared input of these tests (app/build.gradle.kts). */
        val EXAMPLE: Path = Path.of(requireNotNull(System.getProperty("example.config")))
        val PERIOD = OneOffAwayPeriod(Instant.parse("2026-06-08T06:00:00Z"), Instant.parse("2026-06-08T18:00:00Z"))
        val TRIP = Trip(Instant.parse("2026-06-08T06:00:00Z"), Instant.parse("2026-06-08T18:00:00Z"), 300.0, false)

        /** A refresh with the settings ready and notifications allowed: the hourly round and one now. */
        val SCHEDULED = listOf("hourly any", "once any")
    }

    /** Each change the app makes through [EngineAccess], and what the refresh after it does. */
    enum class Change(val refreshed: List<String>) {
        SETTINGS_SAVED(SCHEDULED) {
            override suspend fun make(access: EngineAccess) =
                access.saveSettings(ConfigFile(EXAMPLE, PrivateFiles(FilePrivacy.NONE), Clock.systemUTC()).read())
        },
        // the settings are gone then, so the refresh stops the work
        SETTINGS_SET_ASIDE(listOf("alarm cancelled", "work cancelled")) {
            override suspend fun make(access: EngineAccess) {
                access.setSettingsAside()
            }
        },
        KEYS_CHANGED(SCHEDULED) {
            override suspend fun make(access: EngineAccess) = access.applyKeyChanges(KeyChanges.entering(Secrets.NONE))
        },
        BATTERY_LEVEL(SCHEDULED) {
            override suspend fun make(access: EngineAccess) {
                access.saveBatteryLevel(Percent(40.0))
            }
        },
        AWAY_PERIOD_ADDED(SCHEDULED) {
            override suspend fun make(access: EngineAccess) {
                access.addAwayPeriod(PERIOD)
            }
        },
        AWAY_PERIOD_REMOVED(SCHEDULED) {
            override suspend fun make(access: EngineAccess) {
                access.removeAwayPeriod(PERIOD)
            }
        },
        TRIP_ADDED(SCHEDULED) {
            override suspend fun make(access: EngineAccess) {
                access.addTrip(TRIP)
            }
        },
        TRIP_CHANGED(SCHEDULED) {
            override suspend fun make(access: EngineAccess) {
                access.changeTrip(TRIP, TRIP)
            }
        },
        TRIP_REMOVED(SCHEDULED) {
            override suspend fun make(access: EngineAccess) {
                access.removeTrip(TRIP)
            }
        };

        abstract suspend fun make(access: EngineAccess)
    }

    @TempDir
    lateinit var root: Path

    private val filesFolder by lazy { root.resolve("files") }
    private val background = FakeBackground()

    /** The app's engine on the example settings, its access and its schedule wired as the Application does. */
    private fun TestScope.appAccess(): EngineAccess {
        Files.createDirectories(filesFolder)
        EXAMPLE.copyTo(filesFolder.resolve("engine.json"))
        val platform = AndroidPlatform(filesFolder.toFile(), root.resolve("no_backup").toFile(), SoftwareKeys(),
            Clock.systemUTC())
        val engine = AppEngine.build(platform, Notifier { }, Clock.systemUTC())
        val access = EngineOnDispatcher(engine, StandardTestDispatcher(testScheduler))
        val schedule = BackgroundSchedule({ access }, background.work, background.alarm, flowOf(false),
            background.oneRoundAtATime) { true }
        engine.addChangeListener { launch { schedule.refresh() } }
        return access
    }

    @ParameterizedTest
    @EnumSource(Change::class)
    fun aChangeReachesTheRefresh(change: Change) = runTest {
        val access = appAccess()

        change.make(access)
        advanceUntilIdle()

        assertThat(background.done).isEqualTo(change.refreshed)
    }

    @Test
    fun aReadReachesNoRefresh() = runTest {
        val access = appAccess()

        access.readSettings()
        access.trips()
        access.awayPeriods()
        advanceUntilIdle()

        assertThat(background.done).isEmpty()
    }

    @Test
    fun aBatteryLevelThatCouldNotBeSavedReachesTheRefreshToo() = runTest {
        val access = appAccess()
        Files.createDirectories(filesFolder.resolve("battery.json").resolve("in-the-way"))

        assertThat(access.saveBatteryLevel(Percent(40.0))).isInstanceOf(BatteryLevelSave.Failed::class.java)
        advanceUntilIdle()

        assertThat(background.done).isEqualTo(SCHEDULED)
    }
}
