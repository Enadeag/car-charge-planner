package nl.enadeag.carchargeplanner.android.store

import java.io.IOException
import java.nio.file.Path
import java.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.yield
import nl.enadeag.carchargeplanner.engine.ForecastUse
import nl.enadeag.carchargeplanner.engine.Outlook
import nl.enadeag.carchargeplanner.engine.away.OneOffAwayPeriod
import nl.enadeag.carchargeplanner.engine.config.EngineConfig
import nl.enadeag.carchargeplanner.engine.config.HomeSystem
import nl.enadeag.carchargeplanner.engine.config.Percent
import nl.enadeag.carchargeplanner.engine.config.ProviderId
import nl.enadeag.carchargeplanner.engine.forecast.ConnectionResult
import nl.enadeag.carchargeplanner.engine.forecast.PlaceResults
import nl.enadeag.carchargeplanner.engine.forecast.ProviderInfo
import nl.enadeag.carchargeplanner.engine.forecast.Providers
import nl.enadeag.carchargeplanner.engine.secrets.KeyChanges
import nl.enadeag.carchargeplanner.engine.secrets.Secrets
import nl.enadeag.carchargeplanner.engine.secrets.SecretsProblem
import nl.enadeag.carchargeplanner.engine.trip.Trip

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

    /** The providers offered, in order: the app's own, whose information never reaches the network. */
    var offered: List<ProviderInfo> = PROVIDERS

    override fun providers(): List<ProviderInfo> = offered

    /** As the app's providers: Open-Meteo has a place search, Forecast.Solar none. */
    override fun hasPlaceSearch(provider: ProviderId) = provider.value() == "open-meteo"

    override suspend fun outlook(): Outlook = CompletableDeferred<Outlook>().also { outlooks += it }.await()

    /** Where the forecast of each round asked for may come from, in the order asked. */
    val rounds = mutableListOf<ForecastUse>()

    /** What each round gives. */
    var roundOutlook: Outlook = Outlook.NotSetUp(Path.of("files", "engine.json"))

    /** When set, each round throws it, as a fault in the engine would. */
    var roundThrows: RuntimeException? = null

    /** When set, the next round waits for it after it is counted. */
    var roundGate: CompletableDeferred<Unit>? = null

    override suspend fun round(use: ForecastUse): Outlook {
        rounds += use
        roundGate?.let {
            roundGate = null
            it.await()
        }
        roundThrows?.let { throw it }
        return roundOutlook
    }

    /** The outlooks whose next due notice was asked for, in the order asked. */
    val dueAskedFor = mutableListOf<Outlook>()

    /** When the next notice is due, whatever the outlook; null for none. */
    var noticeDue: Instant? = null

    // suspends as the engine's dispatcher does, which a stopped coroutine does not get past
    override suspend fun nextNoticeDue(outlook: Outlook): Instant? {
        dueAskedFor += outlook
        yield()
        return noticeDue
    }

    /** The one-off away periods stored, in order of their start. */
    var away: List<OneOffAwayPeriod> = listOf()

    /** When set, the read of the one-off away periods throws it, as a fault in the engine would. */
    var awayThrows: RuntimeException? = null

    override suspend fun awayPeriods(): List<OneOffAwayPeriod> {
        awayThrows?.let { throw it }
        return away
    }

    /** The changes to the one-off away periods asked for, in order, such as "add" and "remove", with the period. */
    val awayChanges = mutableListOf<Pair<String, OneOffAwayPeriod>>()

    /** When set, a change of the one-off away periods fails with these words, as the engine's would. */
    var awayFails: String? = null

    /** When set, the next change of the one-off away periods waits for it. */
    var awayGate: CompletableDeferred<Unit>? = null

    override suspend fun addAwayPeriod(period: OneOffAwayPeriod): AwaySave =
        changeAway("add", period) { (it + period).distinct().sortedBy(OneOffAwayPeriod::start) }

    override suspend fun removeAwayPeriod(period: OneOffAwayPeriod): AwaySave =
        changeAway("remove", period) { it - period }

    private suspend fun changeAway(what: String, period: OneOffAwayPeriod,
        change: (List<OneOffAwayPeriod>) -> List<OneOffAwayPeriod>): AwaySave {
        awayChanges += what to period
        awayGate?.let {
            awayGate = null
            it.await()
        }
        awayFails?.let { return AwaySave.Failed(it) }
        away = change(away)
        return AwaySave.Saved(away)
    }

    /** The trips stored, in order of their departure. */
    var trips: List<Trip> = listOf()

    override suspend fun trips(): List<Trip> = trips

    /** The changes to the trips asked for, in order, such as "add", "change" and "remove", with the trip as given. */
    val tripChanges = mutableListOf<Pair<String, Trip>>()

    /** When set, a change of the trips fails with these words, as the engine's would. */
    var tripFails: String? = null

    /** When set, the next change of the trips waits for it. */
    var tripGate: CompletableDeferred<Unit>? = null

    override suspend fun addTrip(trip: Trip): TripSave =
        changeTrips("add", trip) { (it + trip).distinct().sortedBy(Trip::departure) }

    // as the engine: a trip no longer stored is added in its changed form
    override suspend fun changeTrip(trip: Trip, changed: Trip): TripSave =
        changeTrips("change", changed) { ((it - trip) + changed).distinct().sortedBy(Trip::departure) }

    override suspend fun removeTrip(trip: Trip): TripSave = changeTrips("remove", trip) { it - trip }

    private suspend fun changeTrips(what: String, trip: Trip, change: (List<Trip>) -> List<Trip>): TripSave {
        tripChanges += what to trip
        tripGate?.let {
            tripGate = null
            it.await()
        }
        tripFails?.let { return TripSave.Failed(it) }
        trips = change(trips)
        return TripSave.Saved(trips)
    }

    /** The battery levels stored, in the order saved. */
    val batteryLevels = mutableListOf<Percent>()

    /** When set, a save of the battery level fails with these words, as the engine's would. */
    var batteryFails: String? = null

    /** When set, the next save of the battery level waits for it. */
    var batteryGate: CompletableDeferred<Unit>? = null

    override suspend fun saveBatteryLevel(level: Percent): BatteryLevelSave {
        batteryGate?.let {
            batteryGate = null
            it.await()
        }
        batteryFails?.let { return BatteryLevelSave.Failed(it) }
        batteryLevels += level
        return BatteryLevelSave.Saved
    }

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
        val PROVIDERS: List<ProviderInfo> = Providers.standard { throw IOException("no network in these tests") }
            .let { known -> known.ids().map { id -> known.find(id).get().info() } }
    }
}
