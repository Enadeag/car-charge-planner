package nl.enadeag.carchargeplanner.android.store

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Asks one question at a time where only the latest answer counts, such as a connection check or a place search: a
 * new question cancels the one before, whose answer, should it still come, is dropped. Called from the scope's own
 * thread, normally the main thread.
 *
 * @param T the kind of answer
 * @param scope where the questions run, normally the screen's
 */
class LatestAnswer<T>(private val scope: CoroutineScope) {

    private var asked = 0
    private var running: Job? = null

    /**
     * Asks, cancelling the question before.
     *
     * @param question asks the engine, off the main thread
     * @param answered gets the answer on the scope's thread, unless a newer question was asked meanwhile
     * @return the question's work
     */
    fun ask(question: suspend () -> T, answered: (T) -> Unit): Job {
        running?.cancel()
        val mine = ++asked
        return scope.launch {
            val answer = question()
            if (mine == asked) {
                answered(answer)
            }
        }.also { running = it }
    }

    /** Cancels the question that runs, if any; its answer is dropped. */
    fun cancel() {
        running?.cancel()
        asked++
    }
}
