package nl.enadeag.carchargeplanner.engine.dashboard;

import nl.enadeag.carchargeplanner.engine.plan.TripGridCharge;
import nl.enadeag.carchargeplanner.engine.plan.Window;

/**
 * A trip's grid charge and when the owner starts it.
 *
 * @param window the window to charge in, never null, with the current and power to set
 * @param start why it starts when it does, never null: as planned, now with little time left, or now outside the
 *     charging hours
 * @param day the day of the window's start in the home's time zone, never null
 * @param when when the window starts as the owner is told it, never null; {@link When#TONIGHT} only on the day the plan
 *     was made
 */
public record TripGridStart(Window window, TripGridCharge.Start start, DayName day, When when) {

    /** When the window starts as the owner is told it; apart from {@link TripGridCharge.Start}, which says why. */
    public enum When {
        /** The window starts no later than the moment the plan was made, so the charge is due at once. */
        NOW,
        /** The window starts after the moment the plan was made, on that day, at or after 18:00 in the home's zone. */
        TONIGHT,
        /** The window starts later, at a time on its day. */
        LATER
    }

    /**
     * Checks that the start is tonight only on the day the plan was made.
     *
     * @throws IllegalArgumentException when the start is tonight on another day than the one the plan was made
     */
    public TripGridStart {
        if (when == When.TONIGHT && !(day instanceof DayName.Today)) {
            throw new IllegalArgumentException("only a start on the day the plan was made is tonight, not on " + day);
        }
    }

    /**
     * Whether the charge is due at once rather than at a time.
     *
     * @return true when the start is {@link When#NOW}
     */
    public boolean startsNow() {
        return when == When.NOW;
    }

    /**
     * Whether the start can be told as tonight.
     *
     * @return true when the start is {@link When#TONIGHT}
     */
    public boolean tonight() {
        return when == When.TONIGHT;
    }
}
