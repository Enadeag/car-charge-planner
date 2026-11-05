package nl.enadeag.carchargeplanner.android.store

import java.io.IOException
import java.time.Instant
import kotlin.jvm.optionals.getOrNull
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import nl.enadeag.carchargeplanner.engine.Engine
import nl.enadeag.carchargeplanner.engine.ForecastUse
import nl.enadeag.carchargeplanner.engine.Outlook
import nl.enadeag.carchargeplanner.engine.away.OneOffAwayPeriod
import nl.enadeag.carchargeplanner.engine.config.ConfigProblem
import nl.enadeag.carchargeplanner.engine.config.EngineConfig
import nl.enadeag.carchargeplanner.engine.config.HomeSystem
import nl.enadeag.carchargeplanner.engine.config.Percent
import nl.enadeag.carchargeplanner.engine.config.ProviderId
import nl.enadeag.carchargeplanner.engine.secrets.KeyChanges
import nl.enadeag.carchargeplanner.engine.secrets.SecretsProblem
import org.slf4j.LoggerFactory

/**
 * The [EngineAccess] on the app's engine: every call that reads or writes runs on the dispatcher it is given,
 * `Dispatchers.IO` in the app and a test's own in the tests. Changes to the one-off away periods run one after the
 * other, so one started before the home is rebuilt cannot overlap a new one and lose it.
 *
 * @param engine the app's one engine
 * @param dispatcher where the engine's calls run; never the main thread's
 */
class EngineOnDispatcher(private val engine: Engine, private val dispatcher: CoroutineDispatcher) : EngineAccess {

    private companion object {
        val LOG = LoggerFactory.getLogger(EngineOnDispatcher::class.java)
    }

    /** Held by an away change while it runs, so a change started while another runs waits for it. */
    private val awayChange = Mutex()

    override fun providers() = engine.providers().toList()

    override fun hasPlaceSearch(provider: ProviderId) = engine.hasPlaceSearch(provider)

    override suspend fun readSettings(): StartState = withContext(dispatcher) {
        try {
            StartState.Ready(engine.config())
        } catch (problem: ConfigProblem) {
            StartState.of(problem)
        }
    }

    override suspend fun saveSettings(config: EngineConfig) = withContext(dispatcher) {
        engine.saveConfig(config)
    }

    override suspend fun setSettingsAside() = withContext(dispatcher) {
        engine.setConfigAside()
    }

    override suspend fun storedKeys(): StoredKeys = withContext(dispatcher) {
        try {
            StoredKeys.Read(engine.storedApiKeys())
        } catch (problem: SecretsProblem) {
            LOG.warn("The API keys could not be read for Settings: " + problem.message)
            StoredKeys.Unreadable
        }
    }

    override suspend fun applyKeyChanges(changes: KeyChanges) = withContext(dispatcher) {
        // the Android keychain has no locked state, so there is never a prompt to unlock it
        engine.applyKeyChanges(changes) { }
    }

    override suspend fun outlook(): Outlook = withContext(dispatcher) { engine.outlook() }

    override suspend fun round(use: ForecastUse): Outlook = withContext(dispatcher) { engine.round(use) }

    override suspend fun nextNoticeDue(outlook: Outlook): Instant? =
        withContext(dispatcher) { engine.nextNoticeDue(outlook).getOrNull() }

    override suspend fun awayPeriods(): List<OneOffAwayPeriod> = withContext(dispatcher) { engine.awayPeriods() }

    override suspend fun addAwayPeriod(period: OneOffAwayPeriod): AwaySave =
        changeAway("The away period could not be added") { engine.addAwayPeriod(period) }

    override suspend fun removeAwayPeriod(period: OneOffAwayPeriod): AwaySave =
        changeAway("The away period could not be removed") { engine.removeAwayPeriod(period) }

    private suspend fun changeAway(what: String, change: () -> List<OneOffAwayPeriod>): AwaySave =
        awayChange.withLock {
            withContext(dispatcher) {
                try {
                    AwaySave.Saved(change())
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: IOException) {
                    LOG.warn(what + ": " + failure.message)
                    AwaySave.Failed(failure.message ?: failure.javaClass.simpleName)
                } catch (failure: RuntimeException) {
                    LOG.warn(what, failure)
                    AwaySave.Failed(failure.message ?: failure.javaClass.simpleName)
                }
            }
        }

    override suspend fun saveBatteryLevel(level: Percent): BatteryLevelSave = withContext(dispatcher) {
        try {
            engine.setBatteryLevel(level)
            BatteryLevelSave.Saved
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: IOException) {
            LOG.warn("The battery level could not be saved: " + failure.message)
            BatteryLevelSave.Failed(failure.message ?: failure.javaClass.simpleName)
        } catch (failure: RuntimeException) {
            LOG.warn("The battery level could not be saved", failure)
            BatteryLevelSave.Failed(failure.message ?: failure.javaClass.simpleName)
        }
    }

    override suspend fun checkConnection(provider: ProviderId, system: HomeSystem, settings: Map<String, String>) =
        withContext(dispatcher) { engine.checkConnection(provider, system, settings) }

    override suspend fun searchPlaces(provider: ProviderId, text: String, language: String) =
        withContext(dispatcher) { engine.searchPlaces(provider, text, language).getOrNull() }
}
