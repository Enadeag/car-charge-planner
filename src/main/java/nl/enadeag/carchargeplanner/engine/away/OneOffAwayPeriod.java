package nl.enadeag.carchargeplanner.engine.away;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

/**
 * A span of time in which the car is not at home and cannot be charged.
 *
 * @param start the first moment the car is away
 * @param end the moment the car is back, after the start
 */
public record OneOffAwayPeriod(Instant start, Instant end) {

    /**
     * Checks that the period has some length.
     *
     * @throws IllegalArgumentException when the end is not after the start
     */
    public OneOffAwayPeriod {
        if (!start.isBefore(end)) {
            throw new IllegalArgumentException("an away period must end after it starts");
        }
    }

    /**
     * The whole of one local day, from midnight to midnight, so 23 or 25 hours on a daylight saving day.
     *
     * @param date the day the car is away
     * @param zone the home's time zone, which decides when the day starts and ends
     * @return the period of that day
     */
    public static OneOffAwayPeriod allDay(LocalDate date, ZoneId zone) {
        return new OneOffAwayPeriod(date.atStartOfDay(zone).toInstant(),
                date.plusDays(1).atStartOfDay(zone).toInstant());
    }

    /**
     * Part of one local day.
     *
     * @param date the day the car is away
     * @param start the local time the car leaves
     * @param end the local time the car is back, after the start on the same day
     * @param zone the home's time zone, in which the times are read
     * @return the period from the start to the end on that day
     * @throws IllegalArgumentException when the end is not after the start
     */
    public static OneOffAwayPeriod on(LocalDate date, LocalTime start, LocalTime end, ZoneId zone) {
        if (!start.isBefore(end)) {
            throw new IllegalArgumentException("the end time must be after the start time");
        }
        return new OneOffAwayPeriod(date.atTime(start).atZone(zone).toInstant(),
                date.atTime(end).atZone(zone).toInstant());
    }
}
