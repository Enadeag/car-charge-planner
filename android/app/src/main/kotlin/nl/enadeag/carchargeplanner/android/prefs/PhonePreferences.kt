package nl.enadeag.carchargeplanner.android.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import nl.enadeag.carchargeplanner.android.home.CardMemory
import nl.enadeag.carchargeplanner.android.notify.EpisodeFlag
import org.slf4j.LoggerFactory

private val LOG = LoggerFactory.getLogger("nl.enadeag.carchargeplanner.android.prefs.PhonePreferences")

/** The file names of the phone's own preferences stores, which `data_extraction_rules.xml` names too. */
object PhoneStoreFiles {

    /** What goes along to the owner's next phone: the "Wi-Fi only" switch. */
    const val SETTINGS = "settings"

    /** What stays on this phone: the notification card's state and the Problems notification's episode. */
    const val STATE = "state"
}

/**
 * One of the phone's preferences stores on its file; one store per file in the process, as DataStore needs. Nothing is
 * read until it is asked. A file that cannot be parsed is replaced by an empty one, so its defaults are used and a
 * write succeeds again, rather than every write failing until the app's data is cleared.
 *
 * @param scope where the store reads and writes, the app's
 * @param file the store's file, such as `files/datastore/state.preferences_pb`
 * @return the store
 */
fun phoneStore(scope: CoroutineScope, file: () -> File): DataStore<Preferences> = PreferenceDataStoreFactory.create(
    corruptionHandler = ReplaceFileCorruptionHandler { failure ->
        LOG.warn("A preferences store could not be parsed; it starts again empty", failure)
        emptyPreferences()
    },
    scope = scope,
    produceFile = file,
)

/** Whether the hourly update while the app is closed waits for Wi-Fi, which the Forecast page switches. */
interface WifiOnly {

    /** Whether it waits for Wi-Fi, or another network without a data limit; false, any network, until switched. */
    val on: Flow<Boolean>

    /**
     * Switches it at once, outside a page's Save; a write that fails is logged and leaves it as it was.
     *
     * @param on true to wait for Wi-Fi
     */
    fun switch(on: Boolean)
}

/**
 * The phone's own settings, which the engine's settings file does not hold, as they are no matter of the engine
 * (ADR 0010): only the "Wi-Fi only" switch, which goes along to the owner's next phone. A read that fails gives the
 * default; a write runs in the app's scope, so it ends also when the page that started it closes.
 *
 * @param store the `settings` store
 * @param scope where a switch is written, the app's
 */
class PhoneSettings(private val store: DataStore<Preferences>, private val scope: CoroutineScope) : WifiOnly {

    private companion object {
        val WIFI_ONLY = booleanPreferencesKey("wifi_only")
    }

    override val on: Flow<Boolean> = store.data.readable().map { it[WIFI_ONLY] == true }

    override fun switch(on: Boolean) {
        scope.launch { store.write("The Wi-Fi only switch") { it[WIFI_ONLY] = on } }
    }
}

/**
 * The state of the notifications that belongs to this phone and stays out of a transfer to the next: whether the
 * notification card was closed, whether the owner refused Android's question once, and whether an episode of the
 * Problems notification is going on. A read that fails gives false; a write that fails is logged and leaves it.
 *
 * @param store the `state` store
 */
class PhoneState(private val store: DataStore<Preferences>) : CardMemory, EpisodeFlag {

    private companion object {
        val CARD_CLOSED = booleanPreferencesKey("card_closed")
        val ASK_REFUSED = booleanPreferencesKey("ask_refused")
        val PROBLEMS_EPISODE = booleanPreferencesKey("problems_episode")
    }

    override suspend fun closed(): Boolean = read(CARD_CLOSED)

    override suspend fun close() = store.write("The card's close") { it[CARD_CLOSED] = true }

    override suspend fun refused(): Boolean = read(ASK_REFUSED)

    override suspend fun refuse() = store.write("The refusal") { it[ASK_REFUSED] = true }

    override suspend fun episodeStarted(): Boolean = read(PROBLEMS_EPISODE)

    override suspend fun episodeStarted(started: Boolean) =
        store.write("The Problems episode") { it[PROBLEMS_EPISODE] = started }

    private suspend fun read(key: Preferences.Key<Boolean>): Boolean = store.data.readable().first()[key] == true
}

/** The store's preferences, empty when the file cannot be read, as DataStore's guide does. */
private fun Flow<Preferences>.readable(): Flow<Preferences> = catch { failure ->
    if (failure !is IOException) {
        throw failure
    }
    LOG.warn("A preferences store could not be read; its defaults are used", failure)
    emit(emptyPreferences())
}

/** Writes to the store; a failure to write the file is logged, and the store stays as it was. */
private suspend fun DataStore<Preferences>.write(what: String, change: (MutablePreferences) -> Unit) {
    try {
        edit(change)
    } catch (failure: IOException) {
        LOG.warn("{} could not be written", what, failure)
    }
}
