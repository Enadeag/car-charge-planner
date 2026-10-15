package io.github.enadeag.carchargeplanner.instance;

/** What came of trying to become the one running copy of the app. */
public enum Claim {
    /** This is the running copy now: it listens for the commands of later starts. */
    FIRST,
    /** Another copy runs and confirmed it shows its dashboard; this one should exit. */
    HANDED_OVER,
    /** Another copy holds the socket but did not confirm; this one should exit. */
    NO_ANSWER,
    /** No private socket could be made; this copy runs without protection against a second one. */
    UNAVAILABLE
}
