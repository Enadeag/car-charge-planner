package nl.enadeag.carchargeplanner.engine.dashboard;

/** Why a day without a charge window has none. */
public enum NoWindowReason {

    /** The day is the grid advice's day, whatever the trips. */
    GRID,

    /** A trip the plan advises on leaves that day; the dashboard says nothing more, the trip advice does. */
    DEPARTURE,

    /** The car is away that day on a stored trip made with it, by {@code Trip.covers} in the home's time zone. */
    AWAY,

    /**
     * None of the above, and the car needs no charging that day: no trip's grid charge starts on it, and the level the
     * day starts from, in whole percent as it is shown, is at or above the level the plan charges up to that day.
     */
    NOT_NEEDED,

    /**
     * None of the above, and the car could use a charge: the day starts below the level the plan charges up to, or a
     * trip's grid charge starts on it, and there is too little solar power while the car is home for a window.
     */
    TOO_LITTLE_SOLAR
}
