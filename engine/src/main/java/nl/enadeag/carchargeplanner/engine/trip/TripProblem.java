package nl.enadeag.carchargeplanner.engine.trip;

/** Why a trip the owner entered cannot be saved, in the order {@link TripEntryRules} checks. */
public enum TripProblem {
    /** The departure is before a trip's earliest moment, {@link Trip#EARLIEST_INSTANT}: such a trip is over. */
    DEPARTURE_BEFORE_EARLIEST,
    /** The return is not after the departure. */
    RETURN_NOT_AFTER_DEPARTURE,
    /** The return is at or before now. */
    PASSED,
    /**
     * The return is more than {@link Trip#MAX_LENGTH} after the departure, or not before {@link Trip#INSTANTS_END}.
     */
    TOO_LONG,
    /** The distance is 0 or less. */
    DISTANCE_NOT_POSITIVE,
    /** The distance is not a number, or too large to be one: NaN or infinite. */
    DISTANCE_NOT_A_NUMBER,
    /** The distance is more than {@link Trip#MAX_DISTANCE_KM}. */
    DISTANCE_TOO_FAR
}
