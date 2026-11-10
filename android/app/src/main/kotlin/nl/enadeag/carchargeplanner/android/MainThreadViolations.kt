package nl.enadeag.carchargeplanner.android

import android.os.Handler
import android.os.Looper
import android.os.StrictMode
import android.os.strictmode.Violation
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Which code is the app's own: every class of the app and the engine, and of any later package of the project, as
 * Kotlin's lambdas and D8's synthetic classes keep their outer class's package. The application ID is not a package,
 * and Compose, coroutines and AndroidX are not the app's own.
 */
internal object OwnCode {

    /** The start of every own class's name; the dot keeps out a look-alike such as `nl.enadeag.carchargeplanner2`. */
    const val PACKAGE_PREFIX = "nl.enadeag.carchargeplanner."

    /**
     * Whether the app's own code is anywhere in a stack, so that vendor code called from one of the app's own lambdas
     * counts as own too.
     *
     * @param stack the frames to look through, innermost first; may be empty, which is not own
     */
    fun isIn(stack: Array<out StackTraceElement>): Boolean = stack.any { it.className.startsWith(PACKAGE_PREFIX) }
}

/**
 * What the debug build does with a disk read, disk write or network call on the main thread. One from the app's own
 * code ([OwnCode]) ends the app; one from the system's or a vendor's code alone, such as a phone's input code that
 * reads a file on the first key, is logged as a warning, once for each kind and stack, as the app cannot change it.
 * It is called on the main thread only, by StrictMode, which lifts the policy while it runs, and does no disk or
 * network work itself.
 *
 * @param crash ends the app for a violation of its own code, without throwing from inside the call
 * @param log where a violation of the system's or a vendor's code is warned of
 */
internal class MainThreadViolations(
    private val crash: (Throwable) -> Unit = ::crashOnTheMainLooper,
    private val log: Logger = LoggerFactory.getLogger(MainThreadViolations::class.java),
) : StrictMode.OnThreadViolationListener {

    /** A violation's kind and stack, which is what makes two violations the same. */
    private data class Signature(val kind: String, val stack: List<StackTraceElement>)

    /** The violations already warned of, each by its [Signature]; at most [MOST_WARNED]. */
    private val warned = HashSet<Signature>()

    /** StrictMode's call for each violation, on the main thread. */
    override fun onThreadViolation(v: Violation) = decide(v)

    /**
     * Ends the app for a violation of the app's own code; warns once of one from the system's or a vendor's code
     * alone, until [MOST_WARNED] different ones were warned of, then says so once and is silent after that. It takes a
     * Throwable and not StrictMode's Violation, which cannot be made on the JVM, so that it can be tested there.
     */
    fun decide(violation: Throwable) {
        if (OwnCode.isIn(violation.stackTrace)) {
            crash(violation)
            return
        }
        val signature = Signature(violation.javaClass.name, violation.stackTrace.toList())
        if (warned.size < MOST_WARNED && warned.add(signature)) {
            log.warn(
                "StrictMode: {} on the main thread in the system's or a vendor's code",
                violation.javaClass.simpleName,
                violation,
            )
            if (warned.size == MOST_WARNED) log.warn(NO_MORE_WARNINGS)
        }
    }

    internal companion object {

        /** How many different violations are warned of at most, so the set of those warned of stays small. */
        const val MOST_WARNED = 32

        const val NO_MORE_WARNINGS = "further violations of the system's or a vendor's code are not logged"

        /**
         * Ends the app on the main looper's next message, with the violation as the cause, so the crash shows the
         * violation's whole stack and is not thrown inside StrictMode or the code that broke the policy.
         */
        fun crashOnTheMainLooper(violation: Throwable) {
            Handler(Looper.getMainLooper()).post {
                throw IllegalStateException("StrictMode: the app's own code broke the main thread's policy", violation)
            }
        }
    }
}
