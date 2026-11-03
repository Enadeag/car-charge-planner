package nl.enadeag.carchargeplanner.android.home

import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import nl.enadeag.carchargeplanner.android.form.FieldProblem
import nl.enadeag.carchargeplanner.android.form.FormState
import nl.enadeag.carchargeplanner.android.form.NumberText
import nl.enadeag.carchargeplanner.android.form.forget
import nl.enadeag.carchargeplanner.android.store.BatteryLevelSave
import nl.enadeag.carchargeplanner.android.store.EngineAccess
import nl.enadeag.carchargeplanner.engine.config.Percent
import nl.enadeag.carchargeplanner.engine.setting.InvalidSetting

/**
 * The entry of the battery level the car shows, which today's battery card opens (#337). It opens empty and takes a
 * whole number in % from 0 to 100, as the car shows it (maintainer, #337): a number with decimals, a text that is no
 * number, or one outside that range is refused with its problem, never rounded or clamped, and nothing is saved. A
 * save that worked closes the entry, says so once and has the plan made again; one that failed keeps the entry open
 * with what was typed and why. Back, or a tap beside the entry, asks before it throws a typed level away; Cancel
 * throws it away at once, as its name says. While a save runs, nothing closes the entry. Whether it is open, the typed
 * text and the question survive turning the phone and the system ending the app in the [SavedStateHandle], and are
 * forgotten when it closes; what a save came to and the problem shown are not kept, as Save shows them again. Called
 * from the main thread.
 *
 * @param handle the screen's saved state
 * @param numbers reads the whole number typed
 * @param access stores the level
 * @param scope where the save runs, the home's
 * @param saved called once a level is stored, so a new plan is made from it
 */
class BatteryEntry(private val handle: SavedStateHandle, private val numbers: NumberText,
    private val access: EngineAccess, private val scope: CoroutineScope, private val saved: () -> Unit) {

    /** The entry's one field. */
    enum class Field {
        /** The battery level, in %. */
        LEVEL,
    }

    /** Where the entry's Save is. */
    sealed interface SaveState {

        /** Not saving, and no save failed since the entry opened or the last save. */
        data object Idle : SaveState

        /** The level is being saved; Save, Cancel and Back wait. */
        data object Saving : SaveState

        /**
         * The level could not be stored; the entry keeps what was typed.
         *
         * @property detail the system's or the engine's words, in English
         */
        data class Failed(val detail: String) : SaveState
    }

    companion object {
        private const val PREFIX = "battery"
        private const val OPEN = "$PREFIX.open"
        private const val DISCARD = "$PREFIX.discard"
        private val TOO_LARGE = FieldProblem.Refused(InvalidSetting.Rule.OUT_OF_RANGE)

        /**
         * Forgets everything the entry kept in the handle, as the home it belongs to ends.
         *
         * @param handle the screen's saved state
         */
        fun forget(handle: SavedStateHandle) = handle.forget(listOf(PREFIX))
    }

    private val form = FormState(handle, PREFIX, Field.entries)
    private val openNow = MutableStateFlow(handle.get<Boolean>(OPEN) == true)
    private val textNow = MutableStateFlow(form.text(Field.LEVEL))
    private val discardNow = MutableStateFlow(handle.get<Boolean>(DISCARD) == true)
    private val savingNow = MutableStateFlow<SaveState>(SaveState.Idle)
    private val savedNow = MutableStateFlow(false)
    private val problemsShownNow = MutableStateFlow(0)

    /** True while the entry is open. */
    val open: StateFlow<Boolean> = openNow.asStateFlow()

    /** What the field holds. */
    val text: StateFlow<String> = textNow.asStateFlow()

    /** The problem the field shows, by field; each until the field is edited. */
    val problems: StateFlow<Map<Field, FieldProblem>> = form.problems

    /** True while Back asks whether to throw the typed level away. */
    val discardAsked: StateFlow<Boolean> = discardNow.asStateFlow()

    /** Where the entry's Save is. */
    val saving: StateFlow<SaveState> = savingNow.asStateFlow()

    /** True once a level was stored, until the screen said so, as [savedShown] tells. */
    val savedToShow: StateFlow<Boolean> = savedNow.asStateFlow()

    /** How many times Save showed a problem, so the screen can move the focus to the field. */
    val problemsShown: StateFlow<Int> = problemsShownNow.asStateFlow()

    /** Opens the entry, empty; one already open stays as it is. */
    fun open() {
        if (openNow.value) {
            return
        }
        clear()
        handle[OPEN] = true
        openNow.value = true
    }

    /**
     * Puts what the owner typed; the field's problem goes.
     *
     * @param text what the field holds now
     */
    fun edit(text: String) {
        form.edit(Field.LEVEL, text)
        textNow.value = text
    }

    /**
     * Saves the typed level, or shows why it cannot be saved. While a save runs it does nothing.
     */
    fun save() {
        if (!openNow.value || savingNow.value == SaveState.Saving) {
            return
        }
        val level = level() ?: return
        savingNow.value = SaveState.Saving
        // a save cut off as the home ends may still have stored the level
        scope.launch {
            when (val outcome = access.saveBatteryLevel(level)) {
                BatteryLevelSave.Saved -> {
                    close()
                    savedNow.value = true
                    saved()
                }
                is BatteryLevelSave.Failed -> savingNow.value = SaveState.Failed(outcome.detail)
            }
        }
    }

    /** Cancel: closes the entry and throws away what was typed; while a save runs it does nothing. */
    fun cancel() {
        if (savingNow.value != SaveState.Saving) {
            close()
        }
    }

    /**
     * Back, or a tap beside the entry: with a level typed, asks whether to throw it away; without, closes. While a save
     * runs it does nothing.
     */
    fun back() {
        when {
            savingNow.value == SaveState.Saving -> Unit
            textNow.value.isNotEmpty() -> ask(true)
            else -> close()
        }
    }

    /** Throws the typed level away and closes, as the owner chose Discard. */
    fun discard() = close()

    /** Stays in the entry with what was typed, as the owner chose Keep editing. */
    fun keepEditing() = ask(false)

    /** The screen said the level was saved, a message that goes by itself, so it is not said again after turning. */
    fun savedShown() {
        savedNow.value = false
    }

    /** The typed level; null when it is refused, its problem then shown. */
    private fun level(): Percent? {
        val problem = when (val typed = numbers.readWhole(form.text(Field.LEVEL))) {
            // a number too large to read is outside the range too
            is NumberText.WholeNumber.Wrong -> if (typed.problem == TOO_LARGE) FieldProblem.NotFrom0To100
                else typed.problem
            is NumberText.WholeNumber.Read -> if (typed.value in 0..100) return Percent(typed.value.toDouble())
                else FieldProblem.NotFrom0To100
        }
        form.show(mapOf(Field.LEVEL to problem))
        problemsShownNow.value++
        return null
    }

    private fun ask(asking: Boolean) {
        if (asking) handle[DISCARD] = true else handle.remove<Boolean>(DISCARD)
        discardNow.value = asking
    }

    private fun close() {
        forget(handle)
        clear()
        discardNow.value = false
        openNow.value = false
    }

    private fun clear() {
        form.forget()
        textNow.value = ""
        savingNow.value = SaveState.Idle
    }
}
