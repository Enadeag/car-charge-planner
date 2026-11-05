package nl.enadeag.carchargeplanner.android.background

import java.time.Instant
import nl.enadeag.carchargeplanner.android.store.EngineAccess
import nl.enadeag.carchargeplanner.engine.ForecastUse
import nl.enadeag.carchargeplanner.engine.Outlook

/** Work in the background, which `AppArchitectureTest`'s rule on the round lets make a round. */
internal class WorkThatPlansARound(private val access: EngineAccess) {

    /** Makes a round on the stored forecast and asks when the next notice is due. */
    suspend fun plan(): Instant? = access.nextNoticeDue(access.round(ForecastUse.STORED_ONLY))
}
