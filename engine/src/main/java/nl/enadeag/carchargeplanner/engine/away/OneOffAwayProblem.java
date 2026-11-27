package nl.enadeag.carchargeplanner.engine.away;

/** Why a one-off away period the owner entered cannot be saved, in the order {@link OneOffAwayEntryRules} checks. */
public enum OneOffAwayProblem {
    /** The end is not after the start, also when the clock is put forward between them. */
    END_NOT_AFTER_START,
    /** The period has ended by now. */
    PASSED
}
