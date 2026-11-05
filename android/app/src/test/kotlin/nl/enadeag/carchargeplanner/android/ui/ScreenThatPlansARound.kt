package nl.enadeag.carchargeplanner.android.ui

import nl.enadeag.carchargeplanner.android.store.EngineAccess
import nl.enadeag.carchargeplanner.engine.ForecastUse
import nl.enadeag.carchargeplanner.engine.Outlook

/** A screen that breaks `AppArchitectureTest`'s rule on the round on purpose, to show the rule finds it. */
internal class ScreenThatPlansARound(private val access: EngineAccess) {

    /** Makes a round, which tells the owner what is due, as a screen must not. */
    suspend fun plan(): Outlook = access.round(ForecastUse.ASK_PROVIDERS)
}
