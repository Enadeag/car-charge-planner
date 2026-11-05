package nl.enadeag.carchargeplanner.android.background

import java.time.Instant
import nl.enadeag.carchargeplanner.engine.Engine
import nl.enadeag.carchargeplanner.engine.ForecastUse

/**
 * Work in the background that breaks `AppArchitectureTest`'s rule on the round on purpose: it calls the engine itself,
 * on whatever thread it runs, instead of through `EngineAccess`.
 */
internal class WorkThatCallsTheEngineItself(private val engine: Engine) {

    /** Makes a round on the stored forecast and asks when the next notice is due, both on the engine itself. */
    fun plan(): Instant? = engine.nextNoticeDue(engine.round(ForecastUse.STORED_ONLY)).orElse(null)
}
