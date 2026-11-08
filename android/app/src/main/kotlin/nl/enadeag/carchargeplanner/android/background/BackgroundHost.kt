package nl.enadeag.carchargeplanner.android.background

import kotlinx.coroutines.CoroutineScope

/**
 * What the work in the background gets from the app's Application, which is this host: Android makes the worker and
 * the receivers itself, without the app's constructors, so they find their parts through the application's context.
 */
interface BackgroundHost {

    /** The rounds that the worker and the receivers make. */
    val rounds: BackgroundRounds

    /** Sets the work in the background up, as after the phone's start. */
    val schedule: BackgroundSchedule

    /** Where a receiver's round runs after its `onReceive` returned; it is never cancelled. */
    val scope: CoroutineScope
}
