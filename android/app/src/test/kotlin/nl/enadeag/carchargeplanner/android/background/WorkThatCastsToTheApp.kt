package nl.enadeag.carchargeplanner.android.background

import android.content.Context
import nl.enadeag.carchargeplanner.android.CarChargePlannerApp

/** Work that breaks `AppArchitectureTest`'s rule on the Application on purpose. */
internal class WorkThatCastsToTheApp {

    /** Finds the rounds through the Application itself instead of the host. */
    fun rounds(context: Context): BackgroundRounds = (context.applicationContext as CarChargePlannerApp).rounds
}
