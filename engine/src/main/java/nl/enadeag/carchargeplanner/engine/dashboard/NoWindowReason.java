package nl.enadeag.carchargeplanner.engine.dashboard;

/** Why a day without a charge window has none. */
public enum NoWindowReason {

    /** The day is the grid advice's day, whatever the trips. */
    GRID,

    /** A trip the plan advises on leaves that day; the dashboard says nothing more, the trip advice does. */
    DEPARTURE,

    /** The car is away that day on a stored trip made with it, by {@code Trip.covers} in the home's time zone. */
    AWAY,

    /** None of the above: the car needs no charging that day. */
    NOT_NEEDED
}
