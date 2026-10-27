package nl.enadeag.carchargeplanner.engine.calendar;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * The days the plan covers, from today to the last day of the forecast, in the home's time zone: the only days whose
 * events the calendar gives.
 *
 * @param first the first day, today
 * @param last the last day, the same as or after {@code first}
 * @param zone the home's time zone, in which the days begin and end
 */
public record CoveredDays(LocalDate first, LocalDate last, ZoneId zone) {

    /**
     * Checks the order of the days.
     *
     * @throws IllegalArgumentException when the last day is before the first
     */
    public CoveredDays {
        if (last.isBefore(first)) {
            throw new IllegalArgumentException("the last day " + last + " is before the first " + first);
        }
    }

    /**
     * When the first day begins.
     *
     * @return midnight at the start of the first day, in the home's time zone
     */
    public Instant start() {
        return first.atStartOfDay(zone).toInstant();
    }

    /**
     * When the last day ends.
     *
     * @return midnight after the last day, in the home's time zone
     */
    public Instant end() {
        return last.plusDays(1).atStartOfDay(zone).toInstant();
    }
}
