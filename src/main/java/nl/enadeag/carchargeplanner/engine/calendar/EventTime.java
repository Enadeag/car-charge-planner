package nl.enadeag.carchargeplanner.engine.calendar;

import java.time.Instant;
import java.time.LocalDate;

/** When an occurrence of an event takes place: between two moments, or on whole days. */
public sealed interface EventTime {

    /**
     * An event between two moments, which may lie on different days.
     *
     * @param start when it begins
     * @param end when it ends, not before {@code start}
     */
    record Timed(Instant start, Instant end) implements EventTime {

        /**
         * Checks the order of the moments.
         *
         * @throws IllegalArgumentException when the end is before the start
         */
        public Timed {
            if (end.isBefore(start)) {
                throw new IllegalArgumentException("an event's end " + end + " is before its start " + start);
            }
        }
    }

    /**
     * An all-day event, on one day or on several days in a row.
     *
     * @param first its first day
     * @param last its last day, the same as or after {@code first}
     */
    record AllDay(LocalDate first, LocalDate last) implements EventTime {

        /**
         * Checks the order of the days.
         *
         * @throws IllegalArgumentException when the last day is before the first
         */
        public AllDay {
            if (last.isBefore(first)) {
                throw new IllegalArgumentException("an event's last day " + last + " is before its first " + first);
            }
        }
    }
}
