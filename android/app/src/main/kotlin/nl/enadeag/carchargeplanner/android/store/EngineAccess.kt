package nl.enadeag.carchargeplanner.android.store

import java.nio.file.Path
import nl.enadeag.carchargeplanner.engine.Outlook
import nl.enadeag.carchargeplanner.engine.config.EngineConfig
import nl.enadeag.carchargeplanner.engine.config.HomeSystem
import nl.enadeag.carchargeplanner.engine.config.ProviderId
import nl.enadeag.carchargeplanner.engine.forecast.ConnectionResult
import nl.enadeag.carchargeplanner.engine.forecast.PlaceResults
import nl.enadeag.carchargeplanner.engine.forecast.ProviderInfo
import nl.enadeag.carchargeplanner.engine.secrets.KeyChanges

/**
 * All the screens' logic asks of the engine that reads or writes: the settings file, the API keys, the plan, the
 * connection check and the place search. Each call suspends while it runs off the main thread, as the keychain and
 * the API keys may block for about a second and the others reach the disk or the network (#310, A11). The screens
 * never call the engine, the keys or the files themselves (`AppArchitectureTest`).
 */
interface EngineAccess {

    /**
     * What the engine tells about the providers it knows; nothing is read.
     *
     * @return their information, in the order the app offers them
     */
    fun providers(): List<ProviderInfo>

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
