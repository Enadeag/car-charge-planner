package nl.enadeag.carchargeplanner.engine.dashboard;

import nl.enadeag.carchargeplanner.engine.trip.Trip;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * A stored trip with the days of its departure and its return, in the home's time zone, named from the day the plan
 * was made. Which days the car is away for it is {@link Trip#covers}.
 *
 * @param trip the trip as the engine stores it, never null
 * @param departureDay the day of its departure, never null
 * @param returnDay the day of its return, never null, not before {@code departureDay}; the day after the last day it
 *     takes when it returns at midnight, and the day of the departure when the clock ran back over midnight in
 *     between, so the return's own date is the day before
 */
public record ListedTrip(Trip trip, DayName departureDay, DayName returnDay) {

    /**
     * Checks that the trip does not return before the day it leaves.
     *
     * @throws IllegalArgumentException when the day of the return is before the day of the departure
     */
    public ListedTrip {
        if (returnDay.date().isBefore(departureDay.date())) {
            throw new IllegalArgumentException("a trip returns on or after the day it leaves, not on "
                    + returnDay.date() + " before " + departureDay.date());
        }
    }

    /**
     * A trip with its days.
     *
     * @param trip the trip, never null
     * @param zone the home's time zone, never null, in which its days are told
     * @param today the day the plan was made in {@code zone}, never null, from which its days are named
     * @return the trip with the days of its departure and its return; the return's day is the later of the two
     *     dates, as a zone such as America/St_Johns once set its clock back from just after midnight to the day before
     */
    public static ListedTrip of(Trip trip, ZoneId zone, LocalDate today) {
        var departureDate = trip.firstDay(zone);
        var returnDate = trip.returnTime().atZone(zone).toLocalDate();
        var returnDay = returnDate.isBefore(departureDate) ? departureDate : returnDate;
        return new ListedTrip(trip, DayName.of(departureDate, today), DayName.of(returnDay, today));
    }

    /**
     * Whether the trip returns on the day it leaves, so its times can be told without their days.
     *
     * @return true when the days of the departure and the return are the same; false for a trip back at midnight
     */
    public boolean sameDay() {
        return departureDay.date().equals(returnDay.date());
    }
}
