package nl.enadeag.carchargeplanner.android.home

import android.os.SystemClock
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

/** Logic that breaks `AppArchitectureTest`'s rule on the clock on purpose. */
internal class LogicThatReadsTheClock {

    /** Takes the system's clock instead of the one the root hands in. */
    fun clock(): Clock = Clock.systemUTC()

    /** Takes the phone's time zone itself. */
    fun zone(): ZoneId = ZoneId.systemDefault()

    /** Reads the time itself. */
    fun now(): Instant = Instant.now()

    /** Reads the time since the phone started. */
    fun sinceStart(): Long = SystemClock.elapsedRealtime()
}
