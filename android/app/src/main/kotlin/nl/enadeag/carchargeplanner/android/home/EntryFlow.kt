package nl.enadeag.carchargeplanner.android.home

import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The steps an entry of the dashboard goes through, the same for the trip, away and battery entries, which each
 * compose one: Save, run as one of the entry's changes, and where it is; "Discard changes?" with Back, Keep editing
 * and Discard; and how many times Save refused what was entered. What is entered stays the entry's own, as do how it
 * is kept in the saved state, when it counts as changed and what a save that worked does next. While a save runs,
 * nothing closes the entry and edits are ignored. The question survives turning the phone and the system ending the
 * app in the [SavedStateHandle], and comes back only with an entry that came back open. A save cut off as the home
 * ends may still have stored its value. Called from the main thread.
 *
 * @param handle the screen's saved state
 * @param discardKey the key under which the question is kept, one of the entry's own; the flow forgets it as the
 *     entry closes, and as the flow is made without an open entry
 * @param changes the entry's changes, on which a save runs as one of them
 * @param isOpen tells whether the entry is open now; asked once as the flow is made, so the entry's open state is
 *     declared before the flow
 * @param edited tells whether something was changed since the entry opened, so Back asks before throwing it away
 * @param onClosed closes the entry: forgets what it kept in the handle and what it holds
 */
class EntryFlow(private val handle: SavedStateHandle, private val discardKey: String,
    private val changes: ChangeQueue<*>, private val isOpen: () -> Boolean, private val edited: () -> Boolean,
    private val onClosed: () -> Unit) {

    private val savingNow = MutableStateFlow<SaveState>(SaveState.Idle)
    private val discardNow = MutableStateFlow(isOpen() && handle.get<Any?>(discardKey) == true)
    private val refusalsNow = MutableStateFlow(0)

    init {
        if (!isOpen()) {
            handle.remove<Any?>(discardKey)
        }
    }

    /** Where the entry's Save is. */
    val saving: StateFlow<SaveState> = savingNow.asStateFlow()

    /** True while Back asks whether to throw what was entered away. */
    val discardAsked: StateFlow<Boolean> = discardNow.asStateFlow()

    /** How many times Save refused what was entered, so the screen moves the focus to the problem each time. */
    val refusals: StateFlow<Int> = refusalsNow.asStateFlow()

    /** False while a save runs, when what is entered stays as it is. */
    val takesEdits: Boolean get() = savingNow.value != SaveState.Saving

    /**
     * Saves what was entered, or counts a refusal. While the entry is closed, or one of its changes runs, it does
     * nothing.
     *
     * @param T what is stored
     * @param entered what was entered, checked; null when Save refuses it, the entry then showing why
     * @param store stores it and, when that worked, does what follows, after which the flow closes the entry; gives
     *     the system's or the engine's words, in English, when it failed, or null when it worked. A store that
     *     throws, or is cut off, leaves the entry open and its Save Idle
     */
    fun <T : Any> save(entered: () -> T?, store: suspend (T) -> String?) {
        if (!isOpen() || changes.busy.value) {
            return
        }
        val value = entered()
        if (value == null) {
            refusalsNow.value++
            return
        }
        savingNow.value = SaveState.Saving
        changes.run({
            when (val failed = store(value)) {
                null -> close()
                else -> savingNow.value = SaveState.Failed(failed)
            }
        }, done = {
            if (savingNow.value == SaveState.Saving) {
                savingNow.value = SaveState.Idle
            }
        })
    }

    /**
     * Back: with something changed, asks whether to throw it away; without, closes. While the entry is closed, or a
     * save runs, it does nothing.
     */
    fun back() {
        when {
            !isOpen() || savingNow.value == SaveState.Saving -> Unit
            edited() -> ask(true)
            else -> close()
        }
    }

    /**
     * Throws what was entered away and closes, as the owner chose Discard, or Cancel. While the entry is closed, or a
     * save runs, it does nothing.
     */
    fun discard() {
        if (isOpen() && savingNow.value != SaveState.Saving) {
            close()
        }
    }

    /** Stays in the entry with what was entered, as the owner chose Keep editing. */
    fun keepEditing() = ask(false)

    /**
     * Closes the entry, its question and its Save with it, also in the handle; the entry is left Idle for its next
     * opening.
     */
    fun close() {
        savingNow.value = SaveState.Idle
        handle.remove<Any?>(discardKey)
        discardNow.value = false
        onClosed()
    }

    private fun ask(asking: Boolean) {
        if (asking) handle[discardKey] = true else handle.remove<Boolean>(discardKey)
        discardNow.value = asking
    }
}
