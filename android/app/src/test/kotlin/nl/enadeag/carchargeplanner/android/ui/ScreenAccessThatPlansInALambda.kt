package nl.enadeag.carchargeplanner.android.ui

import nl.enadeag.carchargeplanner.android.store.EngineAccess
import nl.enadeag.carchargeplanner.engine.ForecastUse
import nl.enadeag.carchargeplanner.engine.Outlook

/**
 * A screen that has [EngineAccess] itself and makes a round in a lambda, which `AppArchitectureTest`'s rule on the
 * round must find: the lambda is nested in the class that has `round`, yet it is no continuation of `round`.
 */
internal class ScreenAccessThatPlansInALambda(access: EngineAccess) : EngineAccess by access {

    /** Gives a lambda that makes a round on this access. */
    fun planLater(): suspend () -> Outlook = { round(ForecastUse.ASK_PROVIDERS) }
}
