package nl.enadeag.carchargeplanner.engine.calendar;

/**
 * The calendar could not be read. The message says why in plain English, and never holds the calendar's iCal address
 * nor anything of its events.
 */
public final class CalendarProblem extends Exception {

    /** Why the calendar could not be read. */
    public enum Kind {
        /** The calendar's server could not be reached, or did not send the whole calendar in time. */
        UNREACHABLE,
        /** The calendar's server answered, but did not send the calendar. */
        REFUSED,
        /** The calendar is larger than the app reads. */
        TOO_LARGE,
        /** The {@code .ics} file is not there or could not be read. */
        FILE_UNREADABLE,
        /** What came is not a calendar in the iCalendar format. */
        NOT_A_CALENDAR,
        /** The stored iCal address is not an https or webcal address, so it is not fetched. */
        NOT_HTTPS,
        /** The iCal address could not be read, as the keychain that holds it is locked. */
        KEYCHAIN_LOCKED,
        /** The iCal address could not be read from the keychain or the private file that holds it. */
        ADDRESS_UNREADABLE
    }

    private final Kind kind;

    /**
     * Makes the problem.
     *
     * @param kind why the calendar could not be read
     * @param message the same in plain English for the log and the owner; never the iCal address or an event's data
     */
    public CalendarProblem(Kind kind, String message) {
        super(message);
        this.kind = kind;
    }

    /**
     * Why the calendar could not be read.
     *
     * @return the kind of problem
     */
    public Kind kind() {
        return kind;
    }
}
