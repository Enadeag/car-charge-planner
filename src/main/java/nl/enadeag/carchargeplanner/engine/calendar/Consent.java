package nl.enadeag.carchargeplanner.engine.calendar;

/**
 * One of the two consents the calendar needs, both of them, each given by ticking a box under a text that says in
 * plain words what happens. Each text has a version: when what happens changes, so does the text, and its version goes
 * up, so a consent given to an older text no longer counts and the owner is asked again.
 */
public enum Consent {
    /** Reading the calendar's events on this computer: titles, times and locations; none of it leaves the computer. */
    READ_CALENDAR(1),
    /**
     * Sending the events' locations and the home location to the route service, to find the place, the distance and
     * the travel time; online meetings are not sent.
     */
    SEND_LOCATIONS(1);

    private final int textVersion;

    Consent(int textVersion) {
        this.textVersion = textVersion;
    }

    /**
     * The version of the text the owner agrees to now. Raise it whenever the text changes what it says happens.
     *
     * @return 1 or more
     */
    public int textVersion() {
        return textVersion;
    }
}
