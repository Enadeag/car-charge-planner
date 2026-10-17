package io.github.enadeag.carchargeplanner.instance;

/** What came of trying to become the one running copy of the app. */
public enum Claim {
    /** This is the running copy now: it holds the instance lock and listens for the commands of later starts. */
    FIRST,
    /**
     * This is the running copy now, as it holds the instance lock, but its socket could not be made: a later start
     * still does not run, but cannot ask this copy to show its dashboard or to quit.
     */
    FIRST_UNREACHABLE,
    /** Another copy runs and confirmed it shows its dashboard; this one should exit. */
    HANDED_OVER,
    /** Another copy holds the socket but did not confirm; this one should exit. */
    NO_ANSWER,
    /** Another copy holds the instance lock, but its socket cannot be reached; this one should exit. */
    UNREACHABLE,
    /**
     * The private folder for the lock and the socket is another user's, open to others, or a link: it is not used,
     * and no copy may run until it is put right, as it could otherwise let a second copy run.
     */
    NOT_PRIVATE,
    /**
     * The instance lock could not be taken for another reason, such as a folder that cannot be made or a file system
     * without locks; this copy runs without protection against a second one.
     */
    UNAVAILABLE
}
