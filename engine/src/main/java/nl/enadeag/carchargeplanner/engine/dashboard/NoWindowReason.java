package nl.enadeag.carchargeplanner.engine.dashboard;

/** Why a day without a charge window has none, as its card says. */
public enum NoWindowReason {

    /** Solar power is not enough to stay above the minimum charge level: the grid advice says what to do. */
    GRID,

    /**
     * A trip the plan advises on leaves that day: the card says nothing, as the trip's advice tells what the battery
     * needs.
     */
    DEPARTURE,

    /** The car is away on a trip made with it, or there is too little solar power while it is home. */
    AWAY,

    /** The car needs no charging, or there is too little solar power for it. */
    NOT_NEEDED
}
