package nl.enadeag.carchargeplanner.android.ui

import java.time.Instant
import nl.enadeag.carchargeplanner.android.store.EngineAccess
import nl.enadeag.carchargeplanner.android.store.EngineOnDispatcher
import nl.enadeag.carchargeplanner.engine.ForecastUse
import nl.enadeag.carchargeplanner.engine.Outlook

/** A screen that breaks `AppArchitectureTest`'s rule on the round on purpose, to show the rule finds it. */
internal class ScreenThatPlansARound(private val access: EngineAccess) {

    /** Makes a round, which tells the owner what is due, as a screen must not. */
    suspend fun plan(): Outlook = access.round(ForecastUse.ASK_PROVIDERS)

    /** Asks when the next notice is due, which only the work in the background may. */
    suspend fun due(outlook: Outlook): Instant? = access.nextNoticeDue(outlook)

    /** Makes a round through the class that runs the engine, not through its interface. */
    suspend fun planOnTheDispatcher(engine: EngineOnDispatcher): Outlook = engine.round(ForecastUse.STORED_ONLY)
}
