package nl.enadeag.carchargeplanner.engine.dashboard;

import nl.enadeag.carchargeplanner.engine.away.OneOffAwayPeriod;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * A one-off away period by kind, in the home's time zone, with its days named from the day the plan was made and the
 * stored period itself.
 */
public sealed interface ComingAway {

    /**
     * The period as the engine stores it.
     *
     * @return the stored period
     */
    OneOffAwayPeriod period();

    /**
     * One period by kind, whether or not it ended: the whole of one local day, part of one day, or from one day to a
     * later one.
     *
     * @param period the period, never null
     * @param zone the home's time zone, never null, in which its days are told
     * @param today the day the plan was made in {@code zone}, never null, from which its days are named
     * @return {@link AllDay} for a period from one midnight to the next, so 23 or 25 hours on a daylight saving day;
     *     {@link PartOfDay} for one whose end day is not after its start day, so also for one that ends on the day
     *     before, as a zone such as America/St_Johns once set its clock back from just after midnight; {@link Days}
     *     otherwise, also for one that ends at midnight
     */
    static ComingAway describe(OneOffAwayPeriod period, ZoneId zone, LocalDate today) {
        var startDay = period.start().atZone(zone).toLocalDate();
        var endDay = period.end().atZone(zone).toLocalDate();
        if (period.equals(OneOffAwayPeriod.allDay(startDay, zone))) {
            return new AllDay(DayName.of(startDay, today), period);
        }
        if (!endDay.isAfter(startDay)) {
            return new PartOfDay(DayName.of(startDay, today), period);
        }
        return new Days(DayName.of(startDay, today), DayName.of(endDay, today), period);
    }

    /**
     * The car is away the whole day.
     *
     * @param day the day, never null
     * @param period the period as the engine stores it, from the day's midnight to the next, never null
     */
    record AllDay(DayName day, OneOffAwayPeriod period) implements ComingAway {
    }

    /**
     * The car is away part of one day.
     *
     * @param day the day, never null
     * @param period the period as the engine stores it, within the day, never null
     */
    record PartOfDay(DayName day, OneOffAwayPeriod period) implements ComingAway {

        /**
         * When the car leaves.
         *
         * @return the period's start
         */
        public Instant start() {
            return period.start();
        }

        /**
         * When the car is back, the same day.
         *
         * @return the period's end
         */
        public Instant end() {
            return period.end();
        }
    }

    /**
     * The car is away from one day to a later one.
     *
     * @param startDay the day it leaves, never null
     * @param endDay the day it is back, never null, after {@code startDay}
     * @param period the period as the engine stores it, never null
     */
    record Days(DayName startDay, DayName endDay, OneOffAwayPeriod period) implements ComingAway {

        /**
         * Checks that the period ends on a later day than it starts.
         *
         * @throws IllegalArgumentException when the end day is not after the start day
         */
        public Days {
            if (!endDay.date().isAfter(startDay.date())) {
                throw new IllegalArgumentException("a period over days ends on a later day than it starts, not on "
                        + endDay.date() + " after " + startDay.date());
            }
        }

        /**
         * When the car leaves.
         *
         * @return the period's start
         */
        public Instant start() {
            return period.start();
        }

        /**
         * When the car is back.
         *
         * @return the period's end
         */
        public Instant end() {
            return period.end();
        }
    }
}
