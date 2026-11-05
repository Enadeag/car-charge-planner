package nl.enadeag.carchargeplanner.android.backgroundwork

import nl.enadeag.carchargeplanner.android.store.EngineAccess
import nl.enadeag.carchargeplanner.engine.ForecastUse
import nl.enadeag.carchargeplanner.engine.Outlook

/**
 * A class in a package whose name only starts as the app's `background` package does, which `AppArchitectureTest`'s
 * rule on the round must find.
 */
internal class WorkBesideTheBackgroundPackage(private val access: EngineAccess) {

    /** Makes a round, which only the app's own background package may. */
    suspend fun plan(): Outlook = access.round(ForecastUse.STORED_ONLY)
}
