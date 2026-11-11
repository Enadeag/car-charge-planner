package nl.enadeag.carchargeplanner.engine.plan;

import nl.enadeag.carchargeplanner.engine.config.Percent;
import nl.enadeag.carchargeplanner.engine.trip.Trip;
import java.util.List;
import java.util.Optional;

/**
 * What the plan does so the car can make a trip: solar power before the departure first, the grid for the rest.
 *
 * @param trip the trip
 * @param neededPct the battery level to charge to by the departure, at most 100: the minimum charge level, the trip's
 *     energy with the trip surcharge, the daily distance of the days from the departure to the return, and what the
 *     trips after it need that the time between them cannot charge
 * @param solarWindows the charge windows on solar power from now or the return of the trip before, up to the departure,
 *     in time order; possibly none
 * @param solarLevelPct the battery level at the departure on solar power alone
 * @param grid the grid charge for the rest; empty when solar power is enough, or the car is not at home before the
 *     departure
 * @param departureLevelPct the battery level at the departure with the grid charge
 * @param kmShort how many km of the trip the battery cannot hold at the departure, more than 0 when even a full battery
 *     is not enough or there is too little time to charge; 0 when the car can make it
 * @param returnLevelPct the battery level left on return, before the daily distance of the days the trip lasts; 0 when
 *     the car is short
 */
public record TripAdvice(
        Trip trip,
        Percent neededPct,
        List<Window> solarWindows,
        Percent solarLevelPct,
        Optional<TripGridCharge> grid,
        Percent departureLevelPct,
        double kmShort,
        Percent returnLevelPct) {

    /**
     * Copies the list of solar windows.
     *
     * @throws NullPointerException when the windows are null or hold null
     */
    public TripAdvice {
        solarWindows = List.copyOf(solarWindows);
    }

    /**
     * The level to charge to by the departure, which is also the charge limit to set in the car or the charger.
     *
     * @return the needed level rounded up to a whole percentage, so the charge stops only once the trip's energy is in
     */
    public Percent chargeLimitPct() {
        return Charging.chargeLimit(neededPct.value());
    }
}
