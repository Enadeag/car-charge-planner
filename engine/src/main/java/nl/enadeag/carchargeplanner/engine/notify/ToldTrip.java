package nl.enadeag.carchargeplanner.engine.notify;

import java.time.Instant;
import java.util.Optional;

/**
 * What the owner has been told about one trip, which is known by its departure.
 *
 * @param departure the departure of the trip
 * @param chargeStartTold whether the start of the trip's grid charge was announced
 * @param askedGridPct when the owner was asked to check the battery level, how far below the level the trip needs the
 *     battery was at the departure on solar power alone then, in percentage points of the battery; negative when solar
 *     power was enough; empty when the owner was not asked
 * @param shortTold whether the owner was told that even the plan leaves the trip short
 */
public record ToldTrip(Instant departure, boolean chargeStartTold, Optional<Double> askedGridPct, boolean shortTold) {

    /**
     * Nothing told yet about a trip.
     *
     * @param departure the departure of the trip
     * @return the trip without any notice told
     */
    static ToldTrip nothing(Instant departure) {
        return new ToldTrip(departure, false, Optional.empty(), false);
    }
}
