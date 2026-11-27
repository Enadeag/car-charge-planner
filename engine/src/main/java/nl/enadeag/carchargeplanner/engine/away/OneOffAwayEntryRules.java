package nl.enadeag.carchargeplanner.engine.away;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

/**
 * What both apps hold a one-off away period the owner enters to, beyond what {@link OneOffAwayPeriod} itself holds:
 * the period must not have ended yet. The record keeps accepting a period that has, so a stored one still loads.
 * Reading the typed times, and which field shows a problem in which words, stay with each app.
 */
public final class OneOffAwayEntryRules {

    /** The start the entry of part of a day opens with. */
    public static final LocalTime FIRST_START = LocalTime.of(9, 0);

    /** The end the entry of part of a day opens with. */
    public static final LocalTime FIRST_END = LocalTime.of(17, 0);

    private OneOffAwayEntryRules() {
    }

    /**
     * The whole of one day, from its midnight to the next.
     *
     * @param day the day the car is away
     * @param now the moment the owner saves, by which the period must not have ended
     * @param zone the home's time zone, which decides when the day starts and ends
     * @return the period, or {@link OneOffAwayProblem#PASSED} when it ends at or before {@code now}
     */
    public static Checked allDay(LocalDate day, Instant now, ZoneId zone) {
        return notPassed(OneOffAwayPeriod.allDay(day, zone), now);
    }

    /**
     * Part of one day, from a start time to an end time. A time that the clock skips when it is put forward counts as
     * the time after the gap.
     *
     * @param day the day the car is away
     * @param start the local time the car leaves
     * @param end the local time the car is back
     * @param now the moment the owner saves, by which the period must not have ended
     * @param zone the home's time zone, in which the times are read
     * @return the period; or {@link OneOffAwayProblem#END_NOT_AFTER_START} when the end is not after the start, as
     *     times or as moments, then {@link OneOffAwayProblem#PASSED} when it ends at or before {@code now}
     */
    public static Checked partOfDay(LocalDate day, LocalTime start, LocalTime end, Instant now, ZoneId zone) {
        if (!start.isBefore(end)) {
            return new Checked.Refused(OneOffAwayProblem.END_NOT_AFTER_START);
        }
        var from = day.atTime(start).atZone(zone).toInstant();
        var to = day.atTime(end).atZone(zone).toInstant();
        if (!from.isBefore(to)) {
            return new Checked.Refused(OneOffAwayProblem.END_NOT_AFTER_START);
        }
        return notPassed(new OneOffAwayPeriod(from, to), now);
    }

    private static Checked notPassed(OneOffAwayPeriod period, Instant now) {
        return period.end().isAfter(now) ? new Checked.Valid(period)
                : new Checked.Refused(OneOffAwayProblem.PASSED);
    }

    /** What the owner entered, checked: a period that can be saved, or why it cannot. */
    public sealed interface Checked {

        /**
         * A period that can be saved.
         *
         * @param period the period entered
         */
        record Valid(OneOffAwayPeriod period) implements Checked {
        }

        /**
         * Why the entered period cannot be saved.
         *
         * @param problem the first problem found
         */
        record Refused(OneOffAwayProblem problem) implements Checked {
        }
    }
}
