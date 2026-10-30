package nl.enadeag.carchargeplanner.engine.trip;

import java.time.Instant;

/**
 * A drive from home and back. The car is away from the departure to the return.
 *
 * @param departure the moment the car leaves home
 * @param returnTime the moment the car is back home, after the departure
 * @param distanceKm the distance of the whole drive, there and back, in km, more than 0 and finite
 * @param canChargeAtDestination whether the owner can charge the car where the trip goes
 * @param notWithThisCar whether the owner makes the trip without this car, so it does not count for the plan
 */
public record Trip(Instant departure, Instant returnTime, double distanceKm, boolean canChargeAtDestination,
        boolean notWithThisCar) {

    /**
     * Checks that the trip takes some time and some distance.
     *
     * @throws IllegalArgumentException when the return is not after the departure, or the distance is not a finite
     *     number more than 0
     */
    public Trip {
        if (!departure.isBefore(returnTime)) {
            throw new IllegalArgumentException("a trip must return after its departure");
        }
        if (distanceKm <= 0 || !Double.isFinite(distanceKm)) {
            throw new IllegalArgumentException("a trip's distance must be more than 0 km, not " + distanceKm);
        }
    }

    /**
     * A trip made with this car.
     *
     * @param departure the moment the car leaves home
     * @param returnTime the moment the car is back home, after the departure
     * @param distanceKm the distance of the whole drive, there and back, in km, more than 0 and finite
     * @param canChargeAtDestination whether the owner can charge the car where the trip goes
     * @throws IllegalArgumentException when the return is not after the departure, or the distance is not a finite
     *     number more than 0
     */
    public Trip(Instant departure, Instant returnTime, double distanceKm, boolean canChargeAtDestination) {
        this(departure, returnTime, distanceKm, canChargeAtDestination, false);
    }
}
