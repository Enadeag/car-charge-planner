package nl.enadeag.carchargeplanner.android.start

import androidx.lifecycle.SavedStateHandle
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import nl.enadeag.carchargeplanner.android.form.NumberText
import nl.enadeag.carchargeplanner.android.home.HomeOutlook
import nl.enadeag.carchargeplanner.android.setup.SetUpAgain
import nl.enadeag.carchargeplanner.android.setup.SetUpSession
import nl.enadeag.carchargeplanner.android.store.EngineAccess
import nl.enadeag.carchargeplanner.android.store.SaveOutcome
import nl.enadeag.carchargeplanner.android.store.Saves
import nl.enadeag.carchargeplanner.android.store.StartState

/**
 * Which screen the app shows, without a navigation library: it reads the settings file when it is made, then shows
 * set-up, the problem of a file that cannot be used, "update the app", or the home, and moves between them as set-up
 * ends or the file is set aside. Each screen gets a scope of its own, ended when the app moves on, so nothing of a
 * screen that is gone keeps running. Called from the main thread.
 *
 * @param access the engine's reads and writes
 * @param saves the app's saves, which outlive the screens
 * @param appScope the app's scope, where a move of the settings file runs so that it is never cut off
 * @param scope the scope of the screens, which ends with them
 * @param handle the saved state that set-up keeps its step and texts in
 * @param numbers how the screen's language writes numbers, which follows a change of the app's language
 * @property phoneZone the phone's time zone as it is now; the screens read it from here only
 * @param language the app's language now, as the place search takes it, such as "nl"
 */
class AppFlow(private val access: EngineAccess, private val saves: Saves, private val appScope: CoroutineScope,
    private val scope: CoroutineScope, private val handle: SavedStateHandle, private val numbers: NumberText,
    val phoneZone: () -> ZoneId, private val language: () -> String) {

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
         * The home, with today's charge windows.
         *
         * @property outlook the plan it shows
         */
        data class Home(val outlook: HomeOutlook) : Screen
    }

    private val screenNow = MutableStateFlow<Screen>(Screen.Reading)
    private var screenScope: CoroutineScope? = null

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

    private fun show(state: StartState) {
        val own = nextScope()
        screenNow.value = when (state) {
            is StartState.Ready -> home(own)
            StartState.NotSetUp -> setUp(own)
            is StartState.Unusable -> Screen.Unusable(SetUpAgain(state, access, appScope).also { again ->
                own.launch {
                    again.state.collect { if (it is SetUpAgain.State.Moved) screenNow.value = setUp(nextScope()) }
                }
            })
            is StartState.NewerVersion -> Screen.NewerVersion(state.details)
            StartState.Reading -> throw IllegalStateException("a read of the settings file never gives Reading")
        }
    }

    private fun home(own: CoroutineScope) = Screen.Home(HomeOutlook(access, own, ::read))

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
