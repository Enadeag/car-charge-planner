package nl.enadeag.carchargeplanner.android.setup

import androidx.lifecycle.SavedStateHandle
import java.time.ZoneId
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import nl.enadeag.carchargeplanner.android.form.CarForm
import nl.enadeag.carchargeplanner.android.form.ChargerForm
import nl.enadeag.carchargeplanner.android.form.ConfigForm
import nl.enadeag.carchargeplanner.android.form.HouseForm
import nl.enadeag.carchargeplanner.android.form.LocationForm
import nl.enadeag.carchargeplanner.android.form.NumberText
import nl.enadeag.carchargeplanner.android.form.PanelsForm
import nl.enadeag.carchargeplanner.android.form.WeeklyAwayForm
import nl.enadeag.carchargeplanner.android.store.Saves
import nl.enadeag.carchargeplanner.engine.config.EngineConfig
import nl.enadeag.carchargeplanner.engine.config.ProviderId

/**
 * Set-up's steps and their forms: Next checks the step's values as the engine's records do and goes on only when
 * they are accepted, Back goes to the step before, and Finish saves the whole config once; nothing is saved before.
 * The step, the typed texts and the typical marks are kept in the [SavedStateHandle], so they survive turning the
 * phone and the system ending the app. Called from the main thread.
 *
 * @param handle the set-up screen's saved state
 * @param numbers how the screen's language writes numbers
 * @param draft the config set-up starts from, [draft] for the phone's time zone; made again the same way after the
 *     system ended the app
 * @param phoneZone the phone's time zone as it is now, which typed coordinates get
 */
class SetUpFlow(private val handle: SavedStateHandle, numbers: NumberText, private val draft: EngineConfig,
    phoneZone: () -> ZoneId) {

    companion object {

        private const val STEP = "setUp.step"

        /**
         * What set-up starts from: no place chosen yet, the typical home with its values marked as typical, the
         * recommended planning preferences, and Open-Meteo without a fallback.
         *
         * @param phoneZone the phone's time zone, which the home has until a place is chosen
         * @return the draft
         */
        fun draft(phoneZone: ZoneId): EngineConfig = EngineConfig.setUpDraft(phoneZone, ProviderId("open-meteo"))
    }

    /** Where the home is; set-up needs a place chosen. */
    val location = LocationForm(handle, numbers, placeRequired = true, phoneZone = phoneZone)

    /** The panel arrays. */
    val panels = PanelsForm(handle, numbers)

    /** The car. */
    val car = CarForm(handle, numbers)

    /** The wall charger. */
    val charger = ChargerForm(handle, numbers)

    /** The house's own use, on the step with more. */
    val house = HouseForm(handle, numbers)

    /** The weekly away periods, on the step with more. */
    val away = WeeklyAwayForm(handle)

    private val forms: Map<SetUpStep, List<ConfigForm>> = mapOf(
        SetUpStep.LOCATION to listOf(location),
        SetUpStep.PANELS to listOf(panels),
        SetUpStep.CAR to listOf(car),
        SetUpStep.CHARGER to listOf(charger),
        SetUpStep.MORE to listOf(house, away),
    )

    private val stepNow: MutableStateFlow<SetUpStep>

    /** The step shown now. */
    val step: StateFlow<SetUpStep>

    init {
        val kept = handle.get<String>(STEP)?.let { name -> SetUpStep.entries.firstOrNull { it.name == name } }
        if (kept == null) {
            forms.values.flatten().forEach { it.load(draft) }
        }
        stepNow = MutableStateFlow(kept ?: SetUpStep.WELCOME)
        step = stepNow.asStateFlow()
        handle[STEP] = stepNow.value.name
    }

    /**
     * Checks the step's values, showing each refused one at its field, and goes to the next step when none is.
     *
     * @return true when it went on; false when a value is refused, a place is not chosen, or this is the last step
     */
    fun next(): Boolean {
        val now = stepNow.value
        if (refuses(now)) {
            return false
        }
        val following = now.next() ?: return false
        go(following)
        return true
    }

    /**
     * Goes to the step before.
     *
     * @return false on the first step, where Back leaves set-up
     */
    fun back(): Boolean {
        val previous = stepNow.value.previous() ?: return false
        go(previous)
        return true
    }

    /**
     * The whole config as entered so far: the draft, with each step's accepted values laid over it in order. A step
     * with a refused value adds none of its values. Shows nothing.
     *
     * @return the config, such as for the check of the forecast
     */
    fun config(): EngineConfig = forms.values.flatten().fold(draft) { config, form -> form.read(config) ?: config }

    /**
     * Saves the whole config, once every step's values are accepted; otherwise goes to the first step with a refused
     * value and shows its problems.
     *
     * @param saves the app's saves, which outlive the screen
     * @return the save; null when a value is refused
     */
    fun finish(saves: Saves): Job? {
        val refused = SetUpStep.entries.firstOrNull { refuses(it) }
        if (refused != null) {
            go(refused)
            return null
        }
        return saves.finishSetUp(config())
    }

    /**
     * Whether the owner changed something since set-up started.
     *
     * @return true when leaving would throw edits away
     */
    fun isEdited(): Boolean = forms.values.flatten().any { it.isEdited() }

    private fun refuses(step: SetUpStep): Boolean {
        var refused = false
        for (form in forms[step].orEmpty()) {
            refused = form.check(draft) == null || refused
        }
        return refused
    }

    private fun go(step: SetUpStep) {
        stepNow.value = step
        handle[STEP] = step.name
    }
}
