package nl.enadeag.carchargeplanner.android.store

import java.io.IOException
import java.nio.file.Path
import kotlinx.coroutines.CompletableDeferred
import nl.enadeag.carchargeplanner.engine.config.EngineConfig
import nl.enadeag.carchargeplanner.engine.config.HomeSystem
import nl.enadeag.carchargeplanner.engine.config.ProviderId
import nl.enadeag.carchargeplanner.engine.forecast.ConnectionResult
import nl.enadeag.carchargeplanner.engine.forecast.PlaceResults
import nl.enadeag.carchargeplanner.engine.forecast.ProviderInfo
import nl.enadeag.carchargeplanner.engine.secrets.KeyChanges
import nl.enadeag.carchargeplanner.engine.secrets.Secrets
import nl.enadeag.carchargeplanner.engine.secrets.SecretsProblem

/** An [EngineAccess] in memory that keeps what is saved, in the order it came, and fails when told to. */
internal class FakeEngineAccess(var settings: StartState = StartState.NotSetUp) : EngineAccess {

    /** What reached the stores, in order, such as "keys" and "settings". */
    val written = mutableListOf<String>()

    var keys: Secrets = Secrets.NONE
    var keysFail = false
    var writeFails = false
    var moveFails = false

    /** When set, the move of the settings file throws it, as a fault in the engine would. */
    var moveThrows: RuntimeException? = null

    /** When set, the next move of the settings file waits for it. */
    var moveGate: CompletableDeferred<Unit>? = null

    /** When set, the next read of the settings waits for it before it gives what the file held when it started. */
    var readGate: CompletableDeferred<Unit>? = null

    /** The answers of the connection checks, which wait until completed, in the order asked. */
    val checks = mutableListOf<CompletableDeferred<ConnectionResult>>()

    override fun providers(): List<ProviderInfo> = emptyList()

    override suspend fun readSettings(): StartState {
        val read = settings
        readGate?.let {
            readGate = null
            it.await()
        }
        return read
    }

    override suspend fun saveSettings(config: EngineConfig) {
        if (writeFails) {
            throw IOException("No space left on device")
        }
        written += "settings"
        settings = StartState.Ready(config)
    }

    override suspend fun setSettingsAside(): Path {
        moveGate?.let {
            moveGate = null
            it.await()
        }
        moveThrows?.let { throw it }
        if (moveFails) {
            throw IOException("Permission denied")
        }
        settings = StartState.NotSetUp
        return Path.of("files", "engine.json.damaged-20261101-120000")
    }

    override suspend fun storedKeys(): StoredKeys = StoredKeys.Read(keys)

    override suspend fun applyKeyChanges(changes: KeyChanges) {
        if (keysFail) {
            throw SecretsProblem("The keychain could not be used")
        }
        written += "keys"
        keys = changes.applyTo(keys)
    }

    override suspend fun checkConnection(provider: ProviderId, system: HomeSystem, settings: Map<String, String>):
        ConnectionResult = CompletableDeferred<ConnectionResult>().also { checks += it }.await()

    override suspend fun searchPlaces(provider: ProviderId, text: String, language: String): PlaceResults? = null
}
