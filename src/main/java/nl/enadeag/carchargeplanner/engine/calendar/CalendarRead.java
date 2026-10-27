package nl.enadeag.carchargeplanner.engine.calendar;

import java.time.Instant;

/** How the last reading of the calendar went, for a client that shows it. */
public sealed interface CalendarRead {

    /**
     * The calendar was read.
     *
     * @param at when it was read
     * @param events how many occurrences of events fall on the days the plan covers, 0 or more
     * @param unreadable how many events could not be read and are left out, 0 or more
     */
    record Read(Instant at, int events, int unreadable) implements CalendarRead {

        /**
         * Checks the counts.
         *
         * @throws IllegalArgumentException when a count is negative
         */
        public Read {
            if (events < 0 || unreadable < 0) {
                throw new IllegalArgumentException("the counts must be 0 or more, not " + events + " and "
                        + unreadable);
            }
        }
    }

    /**
     * The calendar could not be read.
     *
     * @param at when it was tried
     * @param kind why
     * @param detail why in plain English; never the calendar's iCal address or anything of its events
     */
    record Failed(Instant at, CalendarProblem.Kind kind, String detail) implements CalendarRead {
    }

    /**
     * The calendar was not read, and nothing of it is kept.
     *
     * @param reason why it was not read
     */
    record NotRead(Reason reason) implements CalendarRead {
    }

    /** Why the calendar was not read. */
    enum Reason {
        /** The owner did not give both consents, or one was given to an older text. */
        NO_CONSENT,
        /** Neither an iCal address nor an {@code .ics} file is set. */
        NO_SOURCE,
        /** There is no plan, so no days to read the calendar for. */
        NO_PLAN
    }
}
