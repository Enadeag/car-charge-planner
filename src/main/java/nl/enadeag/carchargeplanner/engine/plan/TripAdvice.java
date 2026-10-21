package nl.enadeag.carchargeplanner.engine.plan;

import nl.enadeag.carchargeplanner.engine.config.Percent;
import nl.enadeag.carchargeplanner.engine.trip.Trip;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

/**
 * What the plan does so the car can make a trip: solar power before the departure first, the grid for the rest.
 *
 * @param trip the trip
 * @param neededPct the battery level the trip needs at its departure, at most 100: the minimum charge level, the trip's
 *     energy with the trip surcharge, and the other driving of the days from the departure to the return
 * @param solarWindows the charge windows on solar power from now or the return of the trip before, up to the departure,
 *     in time order; possibly none
 * @param solarLevelPct the battery level at the departure on solar power alone
 * @param grid the grid charge for the rest; empty when solar power is enough, or the car is not at home before the
 *     departure
 * @param departureLevelPct the battery level at the departure with the grid charge
 * @param kmShort how many km of the trip the battery cannot hold at the departure, more than 0 when even a full battery
 *     is not enough or there is too little time to charge; 0 when the car can make it
 * @param returnLevelPct the battery level left on return, before the other driving of the days the trip lasts; 0 when
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
     * Whether a day is one of a trip's: from the day of the departure to the day of the return.
     *
     * @param trip the trip
     * @param date the local date
     * @param zone the home's time zone, which decides the dates
     * @return true for the day of the departure, the day of the return and the days between; a return at midnight
     *     belongs to the day before
     */
    static boolean covers(Trip trip, LocalDate date, ZoneId zone) {
        return !date.isBefore(trip.departure().atZone(zone).toLocalDate())
                && !date.isAfter(trip.returnTime().minusNanos(1).atZone(zone).toLocalDate());
    }
}
