package nl.enadeag.carchargeplanner.engine.trip;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;

/**
 * What both apps hold a trip the owner enters to: the bounds of {@link Trip}, as problems instead of exceptions, and
 * beyond them that the trip must not have returned yet. The record keeps accepting a trip that has, so a stored one
 * still loads. Reading the typed times and distance, miles, and which field shows a problem in which words, stay with
 * each app.
 */
public final class TripEntryRules {

    /** The departure time the entry of a new trip opens with. */
    public static final LocalTime FIRST_DEPARTURE = LocalTime.of(8, 0);

    /** The return time the entry of a new trip opens with. */
    public static final LocalTime FIRST_RETURN = LocalTime.of(18, 0);

    /** How many days, from today, the entry offers for the departure and the return. */
    public static final int DAYS_OFFERED = 21;

    private TripEntryRules() {
    }

    /**
     * The trip as entered. A time that the clock skips when it is put forward counts as the time after the gap, and
     * one it repeats as its first.
     *
     * @param entered what the owner entered, read by the app
     * @param now the moment the owner saves, by which the trip must not have returned
     * @param zone the home's time zone, in which the days and times are read
     * @return the trip; or the first problem in the order of {@link TripProblem}, except that a return not before
     *     {@link Trip#INSTANTS_END} is {@link TripProblem#TOO_LONG} only after the distance's problems, as only a
     *     trip edited by hand, or a clock put forward to the end of the year 9999, offers such a day
     */
    public static Checked check(Entered entered, Instant now, ZoneId zone) {
        var departure = entered.departure().atZone(zone).toInstant();
        var back = entered.returnTime().atZone(zone).toInstant();
        if (departure.isBefore(Trip.EARLIEST_INSTANT)) {
            return refused(TripProblem.DEPARTURE_BEFORE_EARLIEST);
        }
        if (!back.isAfter(departure)) {
            return refused(TripProblem.RETURN_NOT_AFTER_DEPARTURE);
        }
        if (!back.isAfter(now)) {
            return refused(TripProblem.PASSED);
        }
        if (Duration.between(departure, back).compareTo(Trip.MAX_LENGTH) > 0) {
            return refused(TripProblem.TOO_LONG);
        }
        var km = entered.distanceKm();
        if (km <= 0) {
            return refused(TripProblem.DISTANCE_NOT_POSITIVE);
        }
        if (!Double.isFinite(km)) {
            return refused(TripProblem.DISTANCE_NOT_A_NUMBER);
        }
        if (km > Trip.MAX_DISTANCE_KM) {
            return refused(TripProblem.DISTANCE_TOO_FAR);
        }
        if (!back.isBefore(Trip.INSTANTS_END)) {
            return refused(TripProblem.TOO_LONG);
        }
        return new Checked.Valid(new Trip(departure, back, km, entered.canChargeAtDestination(),
                entered.notWithThisCar()));
    }

    private static Checked refused(TripProblem problem) {
        return new Checked.Refused(problem);
    }

    /**
     * What the owner entered for a trip, read by the app.
     *
     * @param departure the day and time the car leaves, in the home's time zone
     * @param returnTime the day and time it is back, in the home's time zone
     * @param distanceKm the distance there and back in km; NaN for a distance the app could not read as a number
     * @param canChargeAtDestination whether the owner can charge the car where the trip goes
     * @param notWithThisCar whether the owner makes the trip without this car
     */
    public record Entered(LocalDateTime departure, LocalDateTime returnTime, double distanceKm,
            boolean canChargeAtDestination, boolean notWithThisCar) {
    }

    /** What the owner entered, checked: a trip that can be saved, or why it cannot. */
    public sealed interface Checked {

        /**
         * A trip that can be saved.
         *
         * @param trip the trip entered
         */
        record Valid(Trip trip) implements Checked {
        }

        /**
         * Why the entered trip cannot be saved.
         *
         * @param problem the first problem found
         */
        record Refused(TripProblem problem) implements Checked {
        }
    }
}
