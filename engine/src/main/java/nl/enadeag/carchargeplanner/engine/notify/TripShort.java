package nl.enadeag.carchargeplanner.engine.notify;

import nl.enadeag.carchargeplanner.engine.trip.Trip;
import java.time.ZoneId;

/**
 * Even with the plan the battery cannot hold a trip: the owner needs a charging stop.
 *
 * @param trip the trip
 * @param kmShort how many km of the trip the battery cannot hold at the departure, more than 0 and finite
 * @param zone the time zone of the owner's home, in which the times are meant
 */
public record TripShort(Trip trip, double kmShort, ZoneId zone) implements Notice {

    /**
     * Checks the distance.
     *
     * @throws IllegalArgumentException when the distance short is not a finite number more than 0
     */
    public TripShort {
        if (kmShort <= 0 || !Double.isFinite(kmShort)) {
            throw new IllegalArgumentException("kmShort must be a finite number more than 0, not " + kmShort);
        }
    }
}
