package nl.enadeag.carchargeplanner.android.store

import java.io.IOException
import java.nio.file.Path
import kotlinx.coroutines.CompletableDeferred
import nl.enadeag.carchargeplanner.android.AppEngine
import nl.enadeag.carchargeplanner.engine.Outlook
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

    /** When true, the keychain cannot give the keys, as `StoredKeys.Unreadable` says. */
    var keysUnreadable = false
    var writeFails = false

    /** What a failing write of the settings says. */
    var writeProblem = "No space left on device"
    var moveFails = false

    /** When set, the move of the settings file throws it, as a fault in the engine would. */
    var moveThrows: RuntimeException? = null

    /** When set, the next move of the settings file waits for it. */
    var moveGate: CompletableDeferred<Unit>? = null

    /** When set, the next read of the API keys waits for it. */
    var keysGate: CompletableDeferred<Unit>? = null

    /** When set, the next read of the settings waits for it before it gives what the file held when it started. */
    var readGate: CompletableDeferred<Unit>? = null

    /** The connection checks asked for: the provider and the settings as typed, in the order asked. */
    val checked = mutableListOf<Pair<ProviderId, Map<String, String>>>()

    /** The providers whose place search was asked, in the order asked. */
    val searchedWith = mutableListOf<ProviderId>()

    /** The answers of the connection checks, which wait until completed, in the order asked. */
    val checks = mutableListOf<CompletableDeferred<ConnectionResult>>()

    /** The outlooks asked for, which wait until completed, in the order asked. */
    val outlooks = mutableListOf<CompletableDeferred<Outlook>>()

    /** The place searches asked for: the text and the language, in the order asked. */
    val searched = mutableListOf<Pair<String, String>>()

    /** The answers of the place searches, which wait until completed, in the order asked. */
    val searches = mutableListOf<CompletableDeferred<PlaceResults?>>()

    /** The app's own providers, whose information never reaches the network. */
    override fun providers(): List<ProviderInfo> = PROVIDERS

    /** As the app's providers: Open-Meteo has a place search, Forecast.Solar none. */
    override fun hasPlaceSearch(provider: ProviderId) = provider.value() == "open-meteo"

    override suspend fun outlook(): Outlook = CompletableDeferred<Outlook>().also { outlooks += it }.await()

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
            throw IOException(writeProblem)
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

    override suspend fun storedKeys(): StoredKeys {
        keysGate?.let {
            keysGate = null
            it.await()
        }
        return if (keysUnreadable) StoredKeys.Unreadable else StoredKeys.Read(keys)
    }

    override suspend fun applyKeyChanges(changes: KeyChanges) {
        if (keysFail) {
            throw SecretsProblem("The keychain could not be used")
        }
        written += "keys"
        keys = changes.applyTo(keys)
    }

    override suspend fun checkConnection(provider: ProviderId, system: HomeSystem, settings: Map<String, String>):
        ConnectionResult {
        checked += provider to settings
        return CompletableDeferred<ConnectionResult>().also { checks += it }.await()
    }

    override suspend fun searchPlaces(provider: ProviderId, text: String, language: String): PlaceResults? {
        searched += text to language
        searchedWith += provider
        return CompletableDeferred<PlaceResults?>().also { searches += it }.await()
    }

    private companion object {
        val PROVIDERS: List<ProviderInfo> = AppEngine.providers { throw IOException("no network in these tests") }
            .let { known -> known.ids().map { id -> known.find(id).get().info() } }
    }
}
