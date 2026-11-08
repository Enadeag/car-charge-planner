package nl.enadeag.carchargeplanner.android

import java.time.Duration

/**
 * The one place of the tests that sleeps (`AppArchitectureTest` forbids `Thread.sleep` in every other test class): a
 * test that waits for work on another thread polls here, and one that must give another thread time to do the wrong
 * thing pauses here. Tests of Compose screens wait with the rule's own `waitUntil` instead.
 */
object Waiting {

    private const val STEP_MILLIS = 10L

    /**
     * Polls [condition] every few milliseconds until it holds or [timeout] has passed.
     *
     * @param between run before each poll after the first and once after the last, for a test that has to let a
     *     looper or a clock run while it waits
     * @return whether the condition held in time
     */
    fun waitUntil(timeout: Duration = Duration.ofSeconds(10), between: () -> Unit = {}, condition: () -> Boolean): Boolean {
        val end = System.nanoTime() + timeout.toNanos()
        while (!condition() && System.nanoTime() < end) {
            between()
            Thread.sleep(STEP_MILLIS)
        }
        between()
        return condition()
    }

    /** Lets other threads run for [duration], where there is no signal to wait for: that nothing happens. */
    fun pause(duration: Duration) {
        Thread.sleep(duration.toMillis())
    }
}
