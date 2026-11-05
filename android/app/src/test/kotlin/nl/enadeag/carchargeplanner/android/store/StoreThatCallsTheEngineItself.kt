package nl.enadeag.carchargeplanner.android.store

import nl.enadeag.carchargeplanner.engine.Engine
import nl.enadeag.carchargeplanner.engine.ForecastUse
import nl.enadeag.carchargeplanner.engine.Outlook

/**
 * A class beside [EngineOnDispatcher] that calls the engine's round itself, which `AppArchitectureTest`'s rule on the
 * round must find: only [EngineOnDispatcher] may.
 */
internal class StoreThatCallsTheEngineItself(private val engine: Engine) {

    /** Makes a round on the engine itself. */
    fun plan(): Outlook = engine.round(ForecastUse.STORED_ONLY)
}
