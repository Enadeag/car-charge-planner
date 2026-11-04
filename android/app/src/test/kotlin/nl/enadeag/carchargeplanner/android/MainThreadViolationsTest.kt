package nl.enadeag.carchargeplanner.android

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.slf4j.Marker
import org.slf4j.event.Level
import org.slf4j.helpers.LegacyAbstractLogger

/**
 * What the debug build does with a main-thread violation, decided on its stack: a crash for the app's own code, a
 * warning once for the system's or a vendor's. StrictMode's own violations cannot be made on the JVM, so these are
 * exceptions with the same stacks.
 */
class MainThreadViolationsTest {

    private data class Record(val level: Level, val thrown: Throwable?)

    private class KeptLog : LegacyAbstractLogger() {
        val records = mutableListOf<Record>()

        override fun isTraceEnabled() = true
        override fun isDebugEnabled() = true
        override fun isInfoEnabled() = true
        override fun isWarnEnabled() = true
        override fun isErrorEnabled() = true
        override fun getFullyQualifiedCallerName(): String? = null

        override fun handleNormalizedLoggingCall(
            level: Level,
            marker: Marker?,
            messagePattern: String?,
            arguments: Array<out Any>?,
            throwable: Throwable?,
        ) {
            records += Record(level, throwable)
        }
    }

    private val crashes = mutableListOf<Throwable>()
    private val log = KeptLog()
    private val violations = MainThreadViolations(crash = { crashes += it }, log = log)

    private fun violation(vararg classes: String) = RuntimeException("disk read").apply {
        stackTrace = classes.map { StackTraceElement(it, "run", null, -1) }.toTypedArray()
    }

    private fun vendors(number: Int) = violation("java.io.File", "com.vendor.Input$number", "android.os.Looper")

    @Test
    fun theAppsOwnCodeCrashesTheAppWithTheViolationAndIsNotLogged() {
        val own = violation("java.io.File", "nl.enadeag.carchargeplanner.android.MainActivity", "android.os.Looper")

        violations.decide(own)

        assertThat(crashes).containsExactly(own)
        assertThat(log.records).isEmpty()
    }

    @Test
    fun aVendorsCodeAloneIsAWarningWithTheViolationNotACrash() {
        val vendor = vendors(1)

        violations.decide(vendor)

        assertThat(log.records).containsExactly(Record(Level.WARN, vendor))
        assertThat(crashes).isEmpty()
    }

    @Test
    fun theSameVendorsViolationAgainIsNotLoggedAgain() {
        violations.decide(vendors(1))
        violations.decide(vendors(1))
        violations.decide(vendors(2))

        assertThat(log.records.map { it.thrown?.stackTrace?.get(1)?.className })
            .containsExactly("com.vendor.Input1", "com.vendor.Input2")
    }

    @Test
    fun theSameStackOfAnotherKindOfViolationIsLoggedAgain() {
        val read = vendors(1)
        val write = IllegalStateException("disk write").apply { stackTrace = read.stackTrace }

        violations.decide(read)
        violations.decide(write)

        assertThat(log.records.map { it.thrown }).containsExactly(read, write)
    }

    @Test
    fun afterTheMostDifferentViolationsFurtherOnesAreNotLogged() {
        repeat(MainThreadViolations.MOST_WARNED + 5) { violations.decide(vendors(it)) }

        assertThat(log.records).hasSize(MainThreadViolations.MOST_WARNED)
    }
}
