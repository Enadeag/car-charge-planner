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
 * @param startsNow true when the window starts no later than the moment the plan was made, so the charge is due at
 *     once rather than at a time; apart from {@link TripGridCharge.Start#NOW}, which is why it starts early
 * @param tonight true when the window starts after the moment the plan was made, on that day, at or after 18:00 in the
 *     home's time zone
 */
public record TripGridStart(Window window, TripGridCharge.Start start, DayName day, boolean startsNow,
        boolean tonight) {

    /**
     * Checks that the flags agree with each other and with the day.
     *
     * @throws IllegalArgumentException when the start is both due now and tonight, or tonight on another day than the
     *     one the plan was made
     */
    public TripGridStart {
        if (startsNow && tonight) {
            throw new IllegalArgumentException("a grid start due now is not tonight");
        }
        if (tonight && !(day instanceof DayName.Today)) {
            throw new IllegalArgumentException("only a start on the day the plan was made is tonight, not on " + day);
        }
    }
}
