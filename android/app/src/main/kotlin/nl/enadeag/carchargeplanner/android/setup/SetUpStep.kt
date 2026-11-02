package nl.enadeag.carchargeplanner.android.setup

/** The steps of set-up, one subject each, in the order the owner goes through them (#314). */
enum class SetUpStep {
    /** What the app does and what leaves the phone. */
    WELCOME,
    /** Where the home is; a place must be chosen. */
    LOCATION,
    /** The panel arrays. */
    PANELS,
    /** The car. */
    CAR,
    /** The wall charger. */
    CHARGER,
    /** What makes the plan better and may be skipped: the house's own use and the weekly away periods. */
    MORE,
    /** One forecast for the place entered, then Finish; a failure does not stop set-up. */
    CHECK;

    /**
     * The step's place, for "step 3 of 7".
     *
     * @return from 1 for the first step to the number of steps for the last
     */
    fun number(): Int = ordinal + 1

    /**
     * The step after this one.
     *
     * @return the next step; null for the last
     */
    fun next(): SetUpStep? = entries.getOrNull(ordinal + 1)

    /**
     * The step before this one.
     *
     * @return the previous step; null for the first, where Back leaves set-up
     */
    fun previous(): SetUpStep? = entries.getOrNull(ordinal - 1)
}
