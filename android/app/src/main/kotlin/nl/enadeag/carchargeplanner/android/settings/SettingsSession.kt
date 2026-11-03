package nl.enadeag.carchargeplanner.android.settings

import androidx.lifecycle.SavedStateHandle
import java.time.ZoneId
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import nl.enadeag.carchargeplanner.android.form.NumberText
import nl.enadeag.carchargeplanner.android.form.forget
import nl.enadeag.carchargeplanner.android.setup.PlaceSearch
import nl.enadeag.carchargeplanner.android.setup.SetUpCheck
import nl.enadeag.carchargeplanner.android.store.EngineAccess
import nl.enadeag.carchargeplanner.android.store.SaveOutcome
import nl.enadeag.carchargeplanner.android.store.Saves
import nl.enadeag.carchargeplanner.android.store.StartState
import nl.enadeag.carchargeplanner.android.store.StoredKeys
import nl.enadeag.carchargeplanner.engine.config.EngineConfig
import nl.enadeag.carchargeplanner.engine.config.ProviderId
import nl.enadeag.carchargeplanner.engine.forecast.ProviderInfo

/**
 * Settings as its screen goes through it (#315): the list of pages, the page open with its forms, its Save, which
 * keeps the page open, and Back, which asks before it throws edits away. The open page and the typed texts are kept
 * in the [SavedStateHandle], so they survive turning the phone and the system ending the app; the API keys and the
 * problems are not. The page shows each save's outcome as [Saves] gives it; a settings file a save finds unusable is
 * handed on. Back waits while a save runs, as set-up's Finish does. Called from the main thread.
 *
 * @param handle the screen's saved state
 * @param numbers how the screen's language writes numbers, which follows a change of the app's language
 * @param phoneZone the phone's time zone as it is now
 * @param language the app's language now, as the place search takes it, such as "nl"
 * @param access the engine's reads, the place search and the connection check
 * @param saves the app's saves, which outlive the screen
 * @param scope where the reads, the search and the checks run, the screen's
 * @param screen the screen's own thread, where a save reads the forms: `Dispatchers.Main.immediate`, never a scope
 * @param stored the settings as read when Settings opened
 * @param closed called when Back or Up leaves the list
 * @param unusable gets the state of a settings file that a save or a read found unusable or of a newer version
 */
class SettingsSession(private val handle: SavedStateHandle, private val numbers: NumberText,
    private val phoneZone: () -> ZoneId, private val language: () -> String, private val access: EngineAccess,
    private val saves: Saves, private val scope: CoroutineScope, private val screen: CoroutineDispatcher,
    stored: EngineConfig, private val closed: () -> Unit,
    private val unusable: (StartState.WithoutSettings) -> Unit) {

    /** Where the page's Save is. */
    sealed interface SaveState {

        /** Not saved since the page opened. */
        data object Idle : SaveState

        /** The page is being saved; Save and Back wait. */
        data object Saving : SaveState

        /** Saved, or nothing differed from what was stored. */
        data object Saved : SaveState

        /** The API keys could not be stored, so nothing was; the page keeps what was typed. */
        data object KeysNotSaved : SaveState

        /**
         * The settings file could not be written; what was stored stays.
         *
         * @property detail the system's or the engine's words, in English
         */
        data class Failed(val detail: String) : SaveState
    }

    /** Which provider a connection check asks. */
    enum class Role {
        /** The provider asked first. */
        PRIMARY,
        /** The provider asked when the primary has no forecast. */
        FALLBACK,
    }

    companion object {
        private const val PAGE = "settings.page"
        private const val DISCARD = "settings.discard"
        private const val PLACE = "settings.place"

        /** Every key of Settings in the handle: the page, the question, the place search and the forms' texts. */
        private val KEYS = listOf("settings") + SettingsForms.PREFIXES

        /**
         * Forgets everything Settings kept in the handle, as it closes, so the next Settings opens on its list and
         * set-up, whose forms share the prefixes, starts from its own draft.
         *
         * @param handle the screen's saved state
         */
        fun forget(handle: SavedStateHandle) = handle.forget(KEYS)
    }

    private val configNow = MutableStateFlow(stored)
    private val pageNow = MutableStateFlow(handle.get<String>(PAGE)?.let { name ->
        SettingsPage.entries.firstOrNull { it.name == name }
    })
    private val discardNow = MutableStateFlow(handle.get<Boolean>(DISCARD) == true)
    private val savingNow = MutableStateFlow<SaveState>(SaveState.Idle)
    private val refusedNow = MutableStateFlow(false)
    private val problemsShownNow = MutableStateFlow(0)
    private val keysNow = MutableStateFlow<StoredKeys?>(null)
    private val loadsNow = MutableStateFlow(0)
    private var keysRead: Job? = null

    /** The settings as stored, read when Settings opened and again after each save; the list sums them up. */
    val config: StateFlow<EngineConfig> = configNow.asStateFlow()

    /** The page open; null for the list. */
    val page: StateFlow<SettingsPage?> = pageNow.asStateFlow()

    /** True while Back asks whether to throw the page's edits away. */
    val discardAsked: StateFlow<Boolean> = discardNow.asStateFlow()

    /** Where the page's Save is. */
    val saving: StateFlow<SaveState> = savingNow.asStateFlow()

    /** True after a Save that found a refused value, until the page saves or closes. */
    val refused: StateFlow<Boolean> = refusedNow.asStateFlow()

    /** How many times Save showed problems, so the page can move the focus to the first. */
    val problemsShown: StateFlow<Int> = problemsShownNow.asStateFlow()

    /** The API keys as read for the Forecast page; null while they are read, or on another page. */
    val keys: StateFlow<StoredKeys?> = keysNow.asStateFlow()

    /** How many times the page's forms were filled from the settings, so the screen shows their texts anew. */
    val loads: StateFlow<Int> = loadsNow.asStateFlow()

    /** The forms of the page open, made anew for each page opened. */
    var forms = newForms()
        private set

    /** The Location page's place search, with the stored primary provider's search. */
    var places = newPlaces()
        private set

    /** The Forecast page's check of the primary provider, with the values as typed. */
    var primaryCheck = SetUpCheck(access, scope)
        private set

    /** The Forecast page's check of the fallback provider, with the values as typed. */
    var fallbackCheck = SetUpCheck(access, scope)
        private set

    init {
        if (pageNow.value == SettingsPage.FORECAST) {
            readKeys()
        }
        scope.launch {
            saves.lastOutcome.filterNotNull().collect { show(it) }
        }
    }

    /**
     * What the known providers tell about themselves.
     *
     * @return their information, in the order offered
     */
    fun providers(): List<ProviderInfo> = access.providers()

    /**
     * A provider's name as the app shows it.
     *
     * @param provider the provider
     * @return such as "Open-Meteo"; its id when the app does not know it
     */
    fun providerName(provider: ProviderId): String =
        access.providers().firstOrNull { it.id() == provider }?.name() ?: provider.value()

    /**
     * Whether a provider has a place search, so the Location page can say so before anything is typed.
     *
     * @param provider the provider, such as the stored primary
     * @return true when its search is asked
     */
    fun hasPlaceSearch(provider: ProviderId): Boolean = access.hasPlaceSearch(provider)

    /**
     * Opens a page from the list, its forms filled with the stored settings.
     *
     * @param page the page
     */
    fun open(page: SettingsPage) {
        if (pageNow.value != null) {
            return
        }
        prepare()
        handle[PAGE] = page.name
        load(page, configNow.value)
        pageNow.value = page
    }

    /**
     * Back and Up: from a page with edits, asks whether to throw them away; from a page without, to the list; from the
     * list, out of Settings. While a save runs it waits.
     */
    fun back() {
        val page = pageNow.value
        when {
            savingNow.value == SaveState.Saving -> Unit
            page == null -> closed()
            forms.of(page).any { it.isEdited() } -> ask(true)
            else -> leave()
        }
    }

    /** Throws the page's edits away and goes to the list, as the owner chose Discard. */
    fun discard() {
        ask(false)
        leave()
    }

    /** Stays on the page with its edits, as the owner chose Keep editing. */
    fun keepEditing() = ask(false)

    /**
     * Saves the page: its values are checked, each refused one showing its problem, and when none is refused only the
     * page's part is laid over the settings file as it is then. The page stays open. On the Forecast page it waits
     * until the API keys are read.
     */
    fun save() {
        val page = pageNow.value ?: return
        if (savingNow.value == SaveState.Saving || page == SettingsPage.FORECAST && keysNow.value == null) {
            return
        }
        refusedNow.value = false
        // before the save starts, as its outcome may come at once
        savingNow.value = SaveState.Saving
        if (SettingsEdit(forms.of(page), saves, screen).save(configNow.value) == null) {
            savingNow.value = SaveState.Idle
            refusedNow.value = true
            problemsShownNow.value++
        }
    }

    /**
     * Asks a provider for one forecast with the Forecast page's values as typed, keys included; nothing is saved.
     *
     * @param role the primary or the fallback as chosen; nothing is asked for a fallback of none
     */
    fun checkConnection(role: Role) {
        val forecast = forms.forecast
        val provider = (if (role == Role.PRIMARY) forecast.primary() else forecast.fallback()) ?: return
        val check = if (role == Role.PRIMARY) primaryCheck else fallbackCheck
        check.check(provider, configNow.value.system(), forecast.plainOf(provider) + forecast.secretsOf(provider))
    }

    private fun ask(asking: Boolean) {
        handle[DISCARD] = asking
        discardNow.value = asking
    }

    private fun leave() {
        prepare()
        handle.remove<String>(PAGE)
        pageNow.value = null
    }

    /** Readies new forms, search and checks, dropping what the page before had. */
    private fun prepare() {
        primaryCheck.cancel()
        fallbackCheck.cancel()
        places.cancel()
        keysRead?.cancel()
        keysNow.value = null
        savingNow.value = SaveState.Idle
        refusedNow.value = false
        handle.remove<String>("$PLACE.text")
        handle.remove<String>("$PLACE.chosen")
        forms = newForms()
        places = newPlaces()
        primaryCheck = SetUpCheck(access, scope)
        fallbackCheck = SetUpCheck(access, scope)
    }

    private fun load(page: SettingsPage, config: EngineConfig) {
        forms.of(page).forEach { it.load(config) }
        if (page == SettingsPage.FORECAST) {
            readKeys()
        }
        loadsNow.value++
    }

    private fun readKeys() {
        keysRead?.cancel()
        keysNow.value = null
        keysRead = scope.launch {
            val read = access.storedKeys()
            forms.forecast.loadKeys(read)
            keysNow.value = read
            loadsNow.value++
        }
    }

    private fun show(outcome: SaveOutcome) {
        saves.shown(outcome)
        when (outcome) {
            SaveOutcome.Saved -> scope.launch { reread() }
            SaveOutcome.KeysNotSaved -> savingNow.value = SaveState.KeysNotSaved
            is SaveOutcome.Failed -> savingNow.value = SaveState.Failed(outcome.detail)
            is SaveOutcome.SettingsUnusable -> unusable(outcome.state)
        }
    }

    /** After a save: the settings as stored now fill the page again, so it shows no edits; the list sums them up. */
    private suspend fun reread() {
        when (val now = access.readSettings()) {
            is StartState.Ready -> {
                configNow.value = now.config
                // only after this page's own save: an outcome of another never throws the page's edits away
                if (savingNow.value == SaveState.Saving) {
                    pageNow.value?.let { load(it, now.config) }
                    savingNow.value = SaveState.Saved
                }
            }
            is StartState.WithoutSettings -> unusable(now)
            StartState.Reading -> throw IllegalStateException("a read of the settings file never gives Reading")
        }
    }

    private fun newForms() = SettingsForms(handle, numbers, phoneZone, access.providers())

    private fun newPlaces() = PlaceSearch(handle, access, scope, configNow.value.forecast().primary(), PLACE, language)
}
