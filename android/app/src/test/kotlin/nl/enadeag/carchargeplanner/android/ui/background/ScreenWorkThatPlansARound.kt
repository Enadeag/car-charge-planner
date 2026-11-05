package nl.enadeag.carchargeplanner.android.ui.background

import nl.enadeag.carchargeplanner.android.store.EngineAccess
import nl.enadeag.carchargeplanner.engine.ForecastUse
import nl.enadeag.carchargeplanner.engine.Outlook

/**
 * A class in a `background` package that is not the app's own, which `AppArchitectureTest`'s rule on the round must
 * find, as only the app's `background` package may make a round.
 */
internal class ScreenWorkThatPlansARound(private val access: EngineAccess) {

    /** Makes a round, which only the app's own background work may. */
    suspend fun plan(): Outlook = access.round(ForecastUse.STORED_ONLY)
}
