package nl.enadeag.carchargeplanner.android.home

import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
 * throws it away at once, as its name says. While a save runs, nothing closes the entry, the field takes no typing
 * and another Save does nothing. Whether it is open, the typed text and the question survive turning the phone and
 * the system ending the app in the [SavedStateHandle], and are forgotten when it closes; what a save came to and the
 * problem shown are not kept, as Save shows them again. Called from the main thread.
 *
 * @param handle the screen's saved state
 * @param numbers reads the whole number typed
 * @param access stores the level
 * @param scope where the save runs, the home's
 * @param saved called once a level is stored, so a new plan is made from it
 */
class BatteryEntry(private val handle: SavedStateHandle, private val numbers: NumberText,
    private val access: EngineAccess, scope: CoroutineScope, private val saved: () -> Unit) {

    /** The entry's one field. */
    enum class Field {
        /** The battery level, in %. */
        LEVEL,
    }

    /** What a save came to, for the screen to say once. */
    sealed interface Notice {

        /** A level was stored. */
        data object Saved : Notice
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
    private val changes = ChangeQueue<Notice>(scope)
    private val flow = EntryFlow(handle, DISCARD, changes, { openNow.value }, { textNow.value.isNotEmpty() }, ::shut)

    /** True while the entry is open. */
    val open: StateFlow<Boolean> = openNow.asStateFlow()

    /** What the field holds. */
    val text: StateFlow<String> = textNow.asStateFlow()

    /** The problem the field shows, by field; each until the field is edited. */
    val problems: StateFlow<Map<Field, FieldProblem>> = form.problems

    /** True while Back asks whether to throw the typed level away. */
    val discardAsked: StateFlow<Boolean> = flow.discardAsked

    /** Where the entry's Save is. */
    val saving: StateFlow<SaveState> = flow.saving

    /** True while a save runs; Save is held then. */
    val busy: StateFlow<Boolean> = changes.busy

    /** What the last save came to, until the screen said so, as [noticeShown] tells; null when nothing is to say. */
    val notice: StateFlow<Notice?> = changes.notice

    /** How many times Save showed a problem, so the screen can move the focus to the field. */
    val refusals: StateFlow<Int> = flow.refusals

    /** Opens the entry, empty; one already open stays as it is. */
    fun open() {
        if (openNow.value) {
            return
        }
        clear()
        flow.opened()
        handle[OPEN] = true
        openNow.value = true
    }

    /**
     * Puts what the owner typed; the field's problem goes. While a save runs it does nothing.
     *
     * @param text what the field holds now
     */
    fun edit(text: String) {
        if (flow.takesEdits) {
            form.edit(Field.LEVEL, text)
            textNow.value = text
        }
    }

    /** Saves the typed level, or shows why it cannot be saved. While a save runs it does nothing. */
    fun save() = flow.save(::level) { level ->
        when (val outcome = access.saveBatteryLevel(level)) {
            BatteryLevelSave.Saved -> {
                flow.close()
                changes.say(Notice.Saved)
                saved()
                null
            }
            is BatteryLevelSave.Failed -> outcome.detail
        }
    }

    /** Cancel: closes the entry and throws away what was typed; while a save runs it does nothing. */
    fun cancel() = flow.discard()

    /**
     * Back, or a tap beside the entry: with a level typed, asks whether to throw it away; without, closes. While a save
     * runs it does nothing.
     */
    fun back() = flow.back()

    /** Throws the typed level away and closes, as the owner chose Discard; while a save runs it does nothing. */
    fun discard() = flow.discard()

    /** Stays in the entry with what was typed, as the owner chose Keep editing. */
    fun keepEditing() = flow.keepEditing()

    /** The screen said the level was saved, a message that goes by itself, so it is not said again after turning. */
    fun noticeShown() = changes.noticeShown()

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
        return null
    }

    private fun shut() {
        forget(handle)
        clear()
        openNow.value = false
    }

    private fun clear() {
        form.forget()
        textNow.value = ""
    }
}
