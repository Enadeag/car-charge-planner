package nl.enadeag.carchargeplanner.engine.plan;

/**
 * The part of a trip's energy that comes from the grid, because the solar power before the departure is not enough.
 *
 * @param window the window to charge in, whose solar and import energy together are the grid part
 * @param start when the owner starts it
 */
public record TripGridCharge(Window window, Start start) {

    /** When the owner starts a trip's grid charge. */
    public enum Start {
        /** At the window's start: as late as possible within the charging hours, done an hour before the departure. */
        PLANNED,
        /**
         * Now, as there is too little time left to finish an hour before the departure; or as soon as the car is at
         * home or the charging hours begin, which the window's start says.
         */
        NOW,
        /**
         * Now, although it is outside the charging hours: waiting for them would leave less time to charge before the
         * departure.
         */
        NOW_OUTSIDE_CHARGING_HOURS
    }
}
