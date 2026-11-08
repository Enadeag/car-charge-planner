package nl.enadeag.carchargeplanner.android.home

import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlinx.coroutines.CloseableCoroutineDispatcher
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.newSingleThreadContext

/** Logic that breaks `AppArchitectureTest`'s rule on threads on purpose. */
internal class LogicThatMakesThreads {

    /** Makes a thread of its own. */
    fun thread(): Thread = Thread {}

    /** Makes an executor of its own. */
    fun executor(): ExecutorService = Executors.newSingleThreadExecutor()

    /** Makes a dispatcher on a thread of its own. */
    @OptIn(DelicateCoroutinesApi::class, ExperimentalCoroutinesApi::class)
    fun dispatcher(): CloseableCoroutineDispatcher = newSingleThreadContext("own")
}
