package nl.enadeag.carchargeplanner.engine.config;

/** The tilts the owner chooses from for a panel array, from flat to upright. */
public enum TiltChoice {

    /** A flat roof, whose panels are set up at a small angle: 13 degrees. */
    FLAT(13),

    /** A pitched roof: 35 degrees. */
    PITCHED(35),

    /** A steep roof: 50 degrees. */
    STEEP(50),

    /** Upright, as on a wall or a fence: 90 degrees. */
    UPRIGHT(90);

    private final double degrees;

    TiltChoice(double degrees) {
        this.degrees = degrees;
    }

    /**
     * The tilt this choice stores.
     *
     * @return the tilt from the horizontal in degrees, as {@link PanelArray#tiltDeg()} holds it
     */
    public double degrees() {
        return degrees;
    }

    /**
     * The choice a stored tilt is shown as.
     *
     * @param tiltDeg a tilt from the horizontal in degrees, such as one a file holds
     * @return the choice nearest to it; of two equally near, the steeper
     */
    public static TiltChoice nearest(double tiltDeg) {
        var nearest = FLAT;
        for (var choice : values()) {
            // values() runs from flat to upright, so a tie goes to the later, steeper one
            if (Math.abs(choice.degrees - tiltDeg) <= Math.abs(nearest.degrees - tiltDeg)) {
                nearest = choice;
            }
        }
        return nearest;
    }
}
