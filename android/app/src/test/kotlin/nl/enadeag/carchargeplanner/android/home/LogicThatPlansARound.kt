package nl.enadeag.carchargeplanner.android.home

import nl.enadeag.carchargeplanner.android.store.EngineAccess
import nl.enadeag.carchargeplanner.engine.ForecastUse
import nl.enadeag.carchargeplanner.engine.Outlook

/** Logic outside the screens that breaks `AppArchitectureTest`'s rule on the round on purpose. */
internal class LogicThatPlansARound(private val access: EngineAccess) {

    /** Makes a round, which only the work in the background may. */
    suspend fun plan(): Outlook = access.round(ForecastUse.ASK_PROVIDERS)
}
