package nl.enadeag.carchargeplanner.engine.route;

import java.time.Duration;

/**
 * A drive by car one way, from home to a place.
 *
 * @param distanceKm the distance by road in km, 0 or more and finite
 * @param travelTime how long the drive takes, 0 or more
 */
public record Drive(double distanceKm, Duration travelTime) {

    /**
     * Checks the distance and the time.
     *
     * @throws IllegalArgumentException when the distance is negative or not a finite number, or the time is negative
     */
    public Drive {
        if (distanceKm < 0 || !Double.isFinite(distanceKm)) {
            throw new IllegalArgumentException("a drive's distance must be 0 km or more, not " + distanceKm);
        }
        if (travelTime.isNegative()) {
            throw new IllegalArgumentException("a drive's travel time must be 0 or more, not " + travelTime);
        }
    }
}
