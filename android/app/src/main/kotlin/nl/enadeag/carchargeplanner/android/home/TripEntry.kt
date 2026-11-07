package nl.enadeag.carchargeplanner.android.home

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import nl.enadeag.carchargeplanner.android.store.EngineAccess
import nl.enadeag.carchargeplanner.android.store.TripSave
import nl.enadeag.carchargeplanner.engine.trip.Trip

/**
 * The dashboard's changes to the trips: a trip's two choices, "Not with this car" and "I can charge at the
 * destination", each saved at once as the owner changes it, and Remove of a trip with Undo, as the one-off away
 * periods' ([AwayEntry]). A choice shows its new state at once while it is saved; one whose save failed springs back
 * and says so once with the engine's words; one saved says nothing, as the choice shows it. Remove acts at once and
 * says so once, with the trip, so Undo can add it back; a Remove or an Undo that failed says so once with the engine's
 * words. Only one change runs at a time: while one does, a choice or a Remove does nothing, as the screen has the
 * controls off, and an Undo waits for it and then runs, as its snackbar has closed and would otherwise lose the trip.
 * Called from the main thread.
 *
 * @param access stores the changes
 * @param scope where the changes run, the home's
 * @param changed gets the stored trips after each change that worked, so the dashboard shows them and plans again
 */
class TripEntry(private val access: EngineAccess, private val scope: CoroutineScope,
    private val changed: (List<Trip>) -> Unit) {

    /** One of a trip's two choices. */
    enum class Choice {
        /** The owner makes the trip without this car, so it does not count for the plan. */
        NOT_WITH_THIS_CAR,

        /** The owner can charge the car where the trip goes. */
        CAN_CHARGE,
        ;

        /**
         * Whether a trip has this choice on.
         *
         * @param trip the trip
         * @return true when it is ticked
         */
        fun of(trip: Trip): Boolean = when (this) {
            NOT_WITH_THIS_CAR -> trip.notWithThisCar()
            CAN_CHARGE -> trip.canChargeAtDestination()
        }

        /**
         * The trip with this choice put on or off, the rest as it was.
         *
         * @param trip the trip
         * @param on whether the choice is ticked
         * @return the changed trip
         */
        fun put(trip: Trip, on: Boolean): Trip = when (this) {
            NOT_WITH_THIS_CAR -> Trip(trip.departure(), trip.returnTime(), trip.distanceKm(),
                trip.canChargeAtDestination(), on)
            CAN_CHARGE -> Trip(trip.departure(), trip.returnTime(), trip.distanceKm(), on, trip.notWithThisCar())
        }
    }

    /**
     * A choice being saved, which the row shows at once in its new state.
     *
     * @property trip the trip as the dashboard listed it, before the change
     * @property choice the choice
     * @property on its new state
     */
    data class Pending(val trip: Trip, val choice: Choice, val on: Boolean)

    /** What a change came to, for the screen to say once. */
    sealed interface Notice {

        /**
         * A removed trip was added back by Undo.
         *
         * @property trip the trip
         */
        data class Saved(val trip: Trip) : Notice

        /**
         * A trip was removed; Undo adds it back.
         *
         * @property trip the trip
         */
        data class Removed(val trip: Trip) : Notice

        /**
         * Remove failed, so the trip stays.
         *
         * @property detail the system's or the engine's words, in English
         */
        data class NotRemoved(val detail: String) : Notice

        /**
         * A choice or an Undo failed, so the trip stays as it was, or removed.
         *
         * @property detail the system's or the engine's words, in English
         */
        data class NotSaved(val detail: String) : Notice
    }

    private val busyNow = MutableStateFlow(false)
    private val pendingNow = MutableStateFlow<Pending?>(null)
    private val noticeNow = MutableStateFlow<Notice?>(null)
    private val waiting = ArrayDeque<Trip>()

    /** True while a change runs: a choice, a Remove or an Undo; the card's controls are off then. */
    val busy: StateFlow<Boolean> = busyNow.asStateFlow()

    /** The choice being saved; null while none is. */
    val pending: StateFlow<Pending?> = pendingNow.asStateFlow()

    /** What the last change came to, until the screen said so, as [noticeShown] tells; null when nothing is to say. */
    val notice: StateFlow<Notice?> = noticeNow.asStateFlow()

    /**
     * Puts one of a trip's choices on or off and saves it at once. While a change runs it does nothing.
     *
     * @param trip the trip as the dashboard lists it
     * @param choice the choice
     * @param on its new state
     */
    fun choose(trip: Trip, choice: Choice, on: Boolean) {
        if (busyNow.value) {
            return
        }
        pendingNow.value = Pending(trip, choice, on)
        run({ access.changeTrip(trip, choice.put(trip, on)) }, null, Notice::NotSaved)
    }

    /**
     * Removes a trip at once. While a change runs it does nothing.
     *
     * @param trip the trip as the dashboard lists it
     */
    fun remove(trip: Trip) {
        if (!busyNow.value) {
            run({ access.removeTrip(trip) }, Notice.Removed(trip), Notice::NotRemoved)
        }
    }

    /**
     * Adds a removed trip back, as Undo does. While a change runs it waits for it and then runs.
     *
     * @param trip the trip that was removed
     */
    fun undo(trip: Trip) {
        if (busyNow.value) {
            waiting.addLast(trip)
        } else {
            restore(trip)
        }
    }

    /** The screen said what the last change came to, so it is not said again after turning. */
    fun noticeShown() {
        noticeNow.value = null
    }

    private fun restore(trip: Trip) = run({ access.addTrip(trip) }, Notice.Saved(trip), Notice::NotSaved)

    private fun run(store: suspend () -> TripSave, saved: Notice?, failed: (String) -> Notice) {
        busyNow.value = true
        scope.launch {
            try {
                when (val outcome = store()) {
                    is TripSave.Saved -> {
                        saved?.let { noticeNow.value = it }
                        changed(outcome.trips)
                    }
                    is TripSave.Failed -> noticeNow.value = failed(outcome.detail)
                }
            } finally {
                pendingNow.value = null
                busyNow.value = false
                waiting.removeFirstOrNull()?.let(::restore)
            }
        }
    }
}
