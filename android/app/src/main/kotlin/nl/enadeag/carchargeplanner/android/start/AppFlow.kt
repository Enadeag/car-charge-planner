package nl.enadeag.carchargeplanner.android.start

import androidx.lifecycle.SavedStateHandle
import java.time.Clock
import java.time.ZoneId
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import nl.enadeag.carchargeplanner.android.form.NumberText
import nl.enadeag.carchargeplanner.android.home.HomeNotice
import nl.enadeag.carchargeplanner.android.home.HomeOutlook
import nl.enadeag.carchargeplanner.android.settings.SettingsPage
import nl.enadeag.carchargeplanner.android.settings.SettingsSession
import nl.enadeag.carchargeplanner.android.setup.SetUpAgain
import nl.enadeag.carchargeplanner.android.setup.SetUpSession
import nl.enadeag.carchargeplanner.android.store.EngineAccess
import nl.enadeag.carchargeplanner.android.store.SaveOutcome
import nl.enadeag.carchargeplanner.android.store.Saves
import nl.enadeag.carchargeplanner.android.store.StartState
import nl.enadeag.carchargeplanner.engine.config.EngineConfig

/**
 * Which screen the app shows, without a navigation library: it reads the settings file when it is made, then shows
 * set-up, the problem of a file that cannot be used, "update the app", or the home, and moves between them as set-up
 * ends, the file is set aside, or the owner opens and leaves Settings. Settings being open is kept in the saved state,
 * so it comes back after the system ended the app. Each screen gets a scope of its own, ended when the app moves on,
 * so nothing of a screen that is gone keeps running. Called from the main thread.
 *
 * @param access the engine's reads and writes
 * @param saves the app's saves, which outlive the screens
 * @param appScope the app's scope, where a move of the settings file runs so that it is never cut off
 * @param scope the scope of the screens, which ends with them
 * @param handle the saved state that set-up and Settings keep their place and texts in, each forgotten as it ends
 * @param numbers how the screen's language writes numbers, which follows a change of the app's language
 * @property phoneZone the phone's time zone as it is now; the screens read it from here only
 * @param language the app's language now, as the place search takes it, such as "nl"
 * @param mainThread the screens' own thread, where a Settings save reads its forms: `Dispatchers.Main.immediate`
 * @param clock tells the home how old the plan it shows is
 */
class AppFlow(private val access: EngineAccess, private val saves: Saves, private val appScope: CoroutineScope,
    private val scope: CoroutineScope, private val handle: SavedStateHandle, private val numbers: NumberText,
    val phoneZone: () -> ZoneId, private val language: () -> String, private val mainThread: CoroutineDispatcher,
    private val clock: Clock = Clock.systemUTC()) {

    private companion object {
        const val SETTINGS = "app.settings"
    }

    /** A screen of the app. */
    sealed interface Screen {

        /** The settings file is being read. */
        data object Reading : Screen

        /**
         * Set-up, as there are no settings to use.
         *
         * @property session its steps, forms and Finish
         */
        data class SetUp(val session: SetUpSession) : Screen

        /**
         * The settings file cannot be used: its details and "Set up again".
         *
         * @property again the details and the move of the file
         */
        data class Unusable(val again: SetUpAgain) : Screen

        /**
         * A newer version of the app wrote the settings file: the owner updates the app.
         *
         * @property details the engine's words, in English
         */
        data class NewerVersion(val details: String) : Screen

        /**
         * The home: the dashboard, with the plan's days.
         *
         * @property outlook the plan it shows
         * @property notice the outcome of a Settings save that no screen showed yet
         */
        data class Home(val outlook: HomeOutlook, val notice: HomeNotice) : Screen

        /**
         * Settings: the list of its pages, or one page.
         *
         * @property session the pages, their forms and their Save
         * @property page the page open; null for the list
         */
        data class Settings(val session: SettingsSession, val page: SettingsPage?) : Screen
    }

    private val screenNow = MutableStateFlow<Screen>(Screen.Reading)
    private var screenScope: CoroutineScope? = null

    /** What the home showed when Settings opened, kept for its return when no save changed anything. */
    private var homeShown: HomeOutlook.Kept? = null
    private var changesSeen = 0

    /** The screen shown now. */
    val screen: StateFlow<Screen> = screenNow.asStateFlow()

    init {
        read()
    }

    private fun read() {
        val own = nextScope()
        screenNow.value = Screen.Reading
        own.launch { show(access.readSettings()) }
    }

    /** Opens Settings from the home, on the settings file read again. */
    fun openSettings() {
        val home = screenNow.value as? Screen.Home ?: return
        homeShown = home.outlook.kept()
        changesSeen = saves.changes.value
        handle[SETTINGS] = true
        read()
    }

    /**
     * Shows the screen for the settings file as read. Settings' mark in the handle means set-up has no draft there,
     * as Settings opens only from the home, which forgets set-up's: so Settings' keys, which share the prefixes of
     * set-up's forms, are forgotten only under that mark, and a restart during set-up keeps what was typed.
     *
     * @param state the settings file as read
     */
    private fun show(state: StartState) {
        // Settings ends here; without its mark no key of Settings is left, and set-up's must stay
        if (state !is StartState.Ready && handle.contains(SETTINGS)) {
            SettingsSession.forget(handle)
            handle.remove<Boolean>(SETTINGS)
        }
        val own = nextScope()
        screenNow.value = when (state) {
            is StartState.Ready -> if (handle.contains(SETTINGS)) settings(own, state.config) else home(own)
            StartState.NotSetUp -> setUp(own)
            is StartState.Unusable -> Screen.Unusable(SetUpAgain(state, access, appScope).also { again ->
                own.launch {
                    again.state.collect {
                        if (it is SetUpAgain.State.Moved) {
                            SetUpSession.forget(handle)
                            screenNow.value = setUp(nextScope())
                        }
                    }
                }
            })
            is StartState.NewerVersion -> Screen.NewerVersion(state.details)
            StartState.Reading -> throw IllegalStateException("a read of the settings file never gives Reading")
        }
    }

    /** The home, which keeps nothing of set-up in the handle. */
    private fun home(own: CoroutineScope, kept: HomeOutlook.Kept? = null): Screen.Home {
        SetUpSession.forget(handle)
        return Screen.Home(HomeOutlook(access, own, clock, saves.changes, kept, ::read),
            HomeNotice(saves, own, ::show))
    }

    private fun settings(own: CoroutineScope, config: EngineConfig): Screen.Settings {
        val session = SettingsSession(handle, numbers, phoneZone, language, access, saves, own, mainThread, config,
            ::closeSettings, ::show)
        own.launch { session.page.collect { screenNow.value = Screen.Settings(session, it) } }
        return Screen.Settings(session, session.page.value)
    }

    /** Back to the home, which asks for the plan again only when a save changed something since Settings opened. */
    private fun closeSettings() {
        SettingsSession.forget(handle)
        handle.remove<Boolean>(SETTINGS)
        val unchanged = saves.changes.value == changesSeen
        screenNow.value = home(nextScope(), if (unchanged) homeShown else null)
        homeShown = null
    }

    private fun setUp(own: CoroutineScope) = Screen.SetUp(SetUpSession(handle, numbers, phoneZone, language, access,
        saves, own) { outcome -> ended(outcome) })

    private fun ended(outcome: SaveOutcome) {
        when (outcome) {
            SaveOutcome.Saved -> screenNow.value = home(nextScope())
            is SaveOutcome.SettingsUnusable -> show(outcome.state)
            else -> Unit
        }
    }

    /** Ends the scope of the screen before, and gives the next screen one of its own. */
    private fun nextScope(): CoroutineScope {
        screenScope?.cancel()
        return CoroutineScope(scope.coroutineContext + SupervisorJob(scope.coroutineContext[Job]))
            .also { screenScope = it }
    }
}
