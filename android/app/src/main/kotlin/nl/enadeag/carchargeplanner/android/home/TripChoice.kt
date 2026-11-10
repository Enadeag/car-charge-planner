package nl.enadeag.carchargeplanner.android.home

import nl.enadeag.carchargeplanner.engine.trip.Trip

/** One of a trip's two choices, which the Trips card saves at once ([TripEntry.choose]). */
enum class TripChoice {
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
