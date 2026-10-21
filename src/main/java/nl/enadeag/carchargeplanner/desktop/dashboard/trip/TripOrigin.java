package nl.enadeag.carchargeplanner.desktop.dashboard.trip;

import nl.enadeag.carchargeplanner.desktop.text.TextKey;

/** Where a trip the dashboard shows comes from. */
public enum TripOrigin {
    /** The owner entered it on the dashboard. */
    BY_HAND(TextKey.TRIP_BY_HAND);

    private final TextKey text;

    TripOrigin(TextKey text) {
        this.text = text;
    }

    /**
     * How the dashboard names the origin.
     *
     * @return the text's key, such as the one of "Entered by hand"
     */
    public TextKey text() {
        return text;
    }
}
