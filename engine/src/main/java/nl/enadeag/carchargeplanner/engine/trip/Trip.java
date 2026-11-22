package nl.enadeag.carchargeplanner.engine.trip;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;

/**
 * A drive from home and back. The car is away from the departure to the return.
 *
 * @param departure the moment the car leaves home, {@link #EARLIEST_INSTANT} or later
 * @param returnTime the moment the car is back home, after the departure and at most {@link #MAX_LENGTH} after it,
 *     before {@link #INSTANTS_END}
 * @param distanceKm the distance of the whole drive, there and back, in km, more than 0 and at most
 *     {@link #MAX_DISTANCE_KM}
 * @param canChargeAtDestination whether the owner can charge the car where the trip goes
 * @param notWithThisCar whether the owner makes the trip without this car, so it does not count for the plan
 */
public record Trip(Instant departure, Instant returnTime, double distanceKm, boolean canChargeAtDestination,
        boolean notWithThisCar) {

    /** The longest distance of a trip, there and back, in km: more than a drive across a continent and back. */
    public static final int MAX_DISTANCE_KM = 20_000;

    /** The longest time from the departure to the return: the planner goes through a trip day by day. */
    public static final Duration MAX_LENGTH = Duration.ofDays(366);

    /** The earliest departure or return: a stored moment before it is not one the owner entered. */
    public static final Instant EARLIEST_INSTANT = Instant.EPOCH;

    /**
     * The first moment that is not allowed, so the end is exclusive: a return must be before it. It is the start of
     * the year 10000 in UTC, the first year with five digits, well before the end of what a date can hold, where
     * turning a moment into a date in a time zone fails.
     */
    public static final Instant INSTANTS_END = LocalDate.of(10_000, 1, 1).atStartOfDay(ZoneOffset.UTC).toInstant();

    /**
     * Checks that the trip falls within the range of moments, takes some time but not too much, and has a distance
     * that is neither nothing nor too far.
     *
     * @throws IllegalArgumentException when the departure is before {@link #EARLIEST_INSTANT}, the return is not
     *     before {@link #INSTANTS_END}, the return is not after the departure or more than {@link #MAX_LENGTH} after
     *     it, or the distance is not more than 0 and at most {@link #MAX_DISTANCE_KM}
     */
    public Trip {
        if (departure.isBefore(EARLIEST_INSTANT)) {
            throw new IllegalArgumentException("a trip's departure must be " + EARLIEST_INSTANT + " or later, not "
                    + departure);
        }
        if (!returnTime.isBefore(INSTANTS_END)) {
            throw new IllegalArgumentException("a trip's return must be before " + INSTANTS_END + ", not "
                    + returnTime);
        }
        if (!departure.isBefore(returnTime)) {
            throw new IllegalArgumentException("a trip must return after its departure");
        }
        if (Duration.between(departure, returnTime).compareTo(MAX_LENGTH) > 0) {
            throw new IllegalArgumentException("a trip must return at most " + MAX_LENGTH.toDays()
                    + " days after its departure, not " + Duration.between(departure, returnTime));
        }
        // written as a negation, so that a NaN is refused
        if (!(distanceKm > 0 && distanceKm <= MAX_DISTANCE_KM)) {
            throw new IllegalArgumentException("a trip's distance must be more than 0 km and at most "
                    + MAX_DISTANCE_KM + " km, not " + distanceKm);
        }
    }

    /**
     * A trip made with this car.
     *
     * @param departure the moment the car leaves home, {@link #EARLIEST_INSTANT} or later
     * @param returnTime the moment the car is back home, after the departure and at most {@link #MAX_LENGTH} after
     *     it, before {@link #INSTANTS_END}
     * @param distanceKm the distance of the whole drive, there and back, in km, more than 0 and at most
     *     {@link #MAX_DISTANCE_KM}
     * @param canChargeAtDestination whether the owner can charge the car where the trip goes
     * @throws IllegalArgumentException when the departure is before {@link #EARLIEST_INSTANT}, the return is not
     *     before {@link #INSTANTS_END}, the return is not after the departure or more than {@link #MAX_LENGTH} after
     *     it, or the distance is not more than 0 and at most {@link #MAX_DISTANCE_KM}
     */
    public Trip(Instant departure, Instant returnTime, double distanceKm, boolean canChargeAtDestination) {
        this(departure, returnTime, distanceKm, canChargeAtDestination, false);
    }

    /**
     * The day the trip leaves on.
     *
     * @param zone the home's time zone, which decides the dates
     * @return the local date of the departure
     */
    public LocalDate firstDay(ZoneId zone) {
        return departure.atZone(zone).toLocalDate();
    }

    /**
     * The last day the trip takes time of: the day of the return, or the day before for a return at the very start of
     * a day, such as midnight.
     *
     * @param zone the home's time zone, which decides the dates
     * @return the local date of the last moment before the return; never before {@link #firstDay(ZoneId)}
     */
    public LocalDate lastDay(ZoneId zone) {
        return returnTime.minusNanos(1).atZone(zone).toLocalDate();
    }

    /**
     * Whether a day is one of the trip's: from the day of the departure to the day of the return.
     *
     * @param date the local date
     * @param zone the home's time zone, which decides the dates
     * @return true from {@link #firstDay(ZoneId)} to {@link #lastDay(ZoneId)}, both included
     */
    public boolean covers(LocalDate date, ZoneId zone) {
        return !date.isBefore(firstDay(zone)) && !date.isAfter(lastDay(zone));
    }
}
