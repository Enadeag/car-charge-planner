package nl.enadeag.carchargeplanner.android.store

import java.nio.file.Path
import java.time.Instant
import java.time.Year
import java.time.ZoneId
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
import nl.enadeag.carchargeplanner.engine.secrets.KeyChanges
import nl.enadeag.carchargeplanner.engine.trip.Trip

/**
 * All the app asks of the engine that reads or writes: the settings file, the API keys, the plan, the one-off away
 * periods and their changes, the trips and their changes, the battery level, the connection check and the place
 * search for the screens' logic; a planning round and when the next notice is due for the work in the background.
 * Each call suspends while it runs off the main thread, as the keychain and the API keys may block for about a second
 * and the others reach the disk or the network.
 * The screens never call the engine, the keys or the files themselves, nor the round (`AppArchitectureTest`).
 */
interface EngineAccess {

    /**
     * What the engine tells about the providers it knows; nothing is read.
     *
     * @return their information, in the order the app offers them
     */
    fun providers(): List<ProviderInfo>

    /**
     * The current year on the phone's clock, which a panel array's year placed may not be after; nothing is read.
     *
     * @param zone the phone's time zone as it is now
     * @return this year in that zone
     */
    fun thisYear(zone: ZoneId): Year

    /**
     * Whether a provider has a place search, so a screen can say so before the owner types a place; nothing is read.
     *
     * @param provider the provider; one the app does not know has none
     * @return true when [searchPlaces] asks it
     */
    fun hasPlaceSearch(provider: ProviderId): Boolean

    /**
     * Reads the settings file.
     *
     * @return ready with the config, or why there is none; never [StartState.Reading]
     */
    suspend fun readSettings(): StartState

    /**
     * Replaces the settings file by this config, whole.
     *
     * @param config the whole config to keep
     * @throws java.io.IOException when the file could not be written; it is then left as it was
     */
    suspend fun saveSettings(config: EngineConfig)

    /**
     * Moves the settings file aside to `engine.json.damaged-<time>`, for "Set up again"; the app is not set up then.
     *
     * @return where the file is kept now, so the owner can be told
     * @throws java.io.IOException when there is no file, or it could not be moved; it is then left as it was
     */
    suspend fun setSettingsAside(): Path

    /**
     * Reads the API keys.
     *
     * @return the keys, or that the keychain could not give them
     */
    suspend fun storedKeys(): StoredKeys

    /**
     * Applies the owner's changes to the stored API keys; every key they did not enter or empty stays.
     *
     * @param changes what the owner entered and emptied
     * @throws nl.enadeag.carchargeplanner.engine.secrets.SecretsProblem when the keychain could not store them
     */
    suspend fun applyKeyChanges(changes: KeyChanges)

    /**
     * Makes a plan from the settings file and a forecast, which may be fetched first; nobody is told about a window.
     *
     * @return the plan, or why there is none: not set up, a settings file that cannot be used, or no forecast
     */
    suspend fun outlook(): Outlook

    /**
     * Makes one planning round, for the work in the background: the plan, and the owner told what is due on it, each
     * notice once. Never for a screen, which shows an [outlook] and tells nobody.
     *
     * @param use where the forecast may come from: the providers as their rules allow, or only the stored forecast,
     *     which never uses the network
     * @return the plan, or why there is none: not set up, a settings file that cannot be used, or no forecast
     */
    suspend fun round(use: ForecastUse): Outlook

    /**
     * When the next notice is due on a plan a [round] made, for the work in the background to wake the device then.
     *
     * @param outlook what the round gave
     * @return the earliest moment, now when one is due now; null when nothing is to come, also without a plan
     */
    suspend fun nextNoticeDue(outlook: Outlook): Instant?

    /**
     * Reads the one-off away periods the owner entered; the weekly ones are in the settings file.
     *
     * @return the stored periods in order of their start, also those that ended in the last day; empty when there are
     *     none or the file cannot be read
     */
    suspend fun awayPeriods(): List<OneOffAwayPeriod>

    /**
     * Stores a one-off away period the owner entered, so the next plan leaves that time free; one stored already is
     * kept once. A failure is an outcome, never thrown.
     *
     * @param period the time the car is away
     * @return saved with the stored periods after the change, or failed with the system's or the engine's words, the
     *     stored periods then unchanged
     */
    suspend fun addAwayPeriod(period: OneOffAwayPeriod): AwaySave

    /**
     * Forgets a one-off away period, so the next plan may use that time again; one that is not stored changes nothing.
     * A failure is an outcome, never thrown.
     *
     * @param period the period as [awayPeriods] gave it
     * @return saved with the stored periods after the change, or failed with the system's or the engine's words, the
     *     stored periods then unchanged
     */
    suspend fun removeAwayPeriod(period: OneOffAwayPeriod): AwaySave

    /**
     * Reads the trips the owner entered.
     *
     * @return the stored trips in order of their departure, also those that returned in the last day; empty when there
     *     are none or the file cannot be read
     */
    suspend fun trips(): List<Trip>

    /**
     * Stores a trip the owner entered, so the next plan leaves its time free and charges for it; one stored already is
     * kept once. A failure is an outcome, never thrown.
     *
     * @param trip the trip
     * @return saved with the stored trips after the change, or failed with the system's or the engine's words, the
     *     stored trips then unchanged
     */
    suspend fun addTrip(trip: Trip): TripSave

    /**
     * Replaces a stored trip by the owner's changed version of it, such as with one of its choices changed. A failure
     * is an outcome, never thrown.
     *
     * @param trip the trip as [trips] gave it; when it is no longer stored, the changed one is added
     * @param changed the trip as the owner changed it
     * @return saved with the stored trips after the change, or failed with the system's or the engine's words, the
     *     stored trips then unchanged
     */
    suspend fun changeTrip(trip: Trip, changed: Trip): TripSave

    /**
     * Forgets a trip, so the next plan may use its time again; one that is not stored changes nothing. A failure is an
     * outcome, never thrown.
     *
     * @param trip the trip as [trips] gave it
     * @return saved with the stored trips after the change, or failed with the system's or the engine's words, the
     *     stored trips then unchanged
     */
    suspend fun removeTrip(trip: Trip): TripSave

    /**
     * Stores the battery level the owner read off the car, so the next plan starts from it instead of the assumed
     * level; it is kept until the owner enters another one. A failure is an outcome, never thrown.
     *
     * @param level the level the car shows now
     * @return saved, or failed with the system's or the engine's words, the stored level then unchanged
     */
    suspend fun saveBatteryLevel(level: Percent): BatteryLevelSave

    /**
     * Asks a provider for one forecast with these settings; nothing is cached or saved.
     *
     * @param provider the provider to ask
     * @param system the system as the owner entered it, which need not be saved
     * @param settings as the owner entered them; a secret one left out comes from the stored keys
     * @return whether it worked, the days of forecast, and the engine's message in English
     */
    suspend fun checkConnection(provider: ProviderId, system: HomeSystem, settings: Map<String, String>):
        ConnectionResult

    /**
     * Places for what the owner typed, from the provider's place search.
     *
     * @param provider the provider whose search to use
     * @param text a name or a postcode
     * @param language the language the names should come in, such as "nl"
     * @return the results; null when the provider is unknown or has no place search
     */
    suspend fun searchPlaces(provider: ProviderId, text: String, language: String): PlaceResults?
}
