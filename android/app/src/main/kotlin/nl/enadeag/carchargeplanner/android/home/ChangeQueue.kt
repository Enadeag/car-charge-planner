package nl.enadeag.carchargeplanner.android.home

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The dashboard's changes of one kind, such as the one-off away periods' or the trips', one at a time, with what the
 * last one came to until the screen said it. While a change runs, another does not start; an Undo waits instead, as
 * its snackbar has closed and would otherwise lose what it adds back, and runs once that change is done and the screen
 * said what it came to, so neither notice is lost. Called from the main thread.
 *
 * @param N what a change comes to, for the screen to say once
 * @param scope where the changes run, the home's
 */
class ChangeQueue<N : Any>(private val scope: CoroutineScope) {

    private val busyNow = MutableStateFlow(false)
    private val noticeNow = MutableStateFlow<N?>(null)
    private val waiting = ArrayDeque<() -> Unit>()

    /** True while a change runs. */
    val busy: StateFlow<Boolean> = busyNow.asStateFlow()

    /** What the last change came to, until the screen said so, as [noticeShown] tells; null when nothing is to say. */
    val notice: StateFlow<N?> = noticeNow.asStateFlow()

    /**
     * Starts a change, unless one runs.
     *
     * @param change the change, which may [say] what it came to; a change cut off as the home ends may still have
     *     stored what it was to store
     * @param done called once the change ended, however it ended, before the next may start
     */
    fun run(change: suspend () -> Unit, done: () -> Unit = {}) {
        if (busyNow.value) {
            return
        }
        busyNow.value = true
        scope.launch {
            try {
                change()
            } finally {
                done()
                busyNow.value = false
                next()
            }
        }
    }

    /**
     * Notes what the running change came to, for the screen to say.
     *
     * @param notice what it came to
     */
    fun say(notice: N) {
        noticeNow.value = notice
    }

    /**
     * Keeps a change, such as an Undo, until the running change is done and the screen said what it came to.
     *
     * @param change starts the change, as with [run]
     */
    fun later(change: () -> Unit) {
        waiting.addLast(change)
    }

    /** The screen said what the last change came to, so it is not said again after turning; a change kept runs now. */
    fun noticeShown() {
        noticeNow.value = null
        next()
    }

    private fun next() {
        if (!busyNow.value && noticeNow.value == null) {
            waiting.removeFirstOrNull()?.invoke()
        }
    }
}
