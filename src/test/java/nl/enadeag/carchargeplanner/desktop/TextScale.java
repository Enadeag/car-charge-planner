package nl.enadeag.carchargeplanner.desktop;

/** The system's text made larger, as the owner can ask for on Windows and Linux, against the app's own 14 px. */
public enum TextScale {
    /** Text half as large again. */
    PERCENT_150(1.5),
    /** Text twice as large. */
    PERCENT_200(2.0);

    /** The headless screen's size, which a window cannot outgrow. */
    public static final double SCREEN = 1000;

    private final double factor;

    TextScale(double factor) {
        this.factor = factor;
    }

    /** A size in pixels at this scale. */
    public double of(double pixels) {
        return pixels * factor;
    }

    /** The root style that makes every text this much larger: AtlantaFX sets 14 px, and the app sizes in em. */
    public String rootStyle() {
        return "-fx-font-size: %.1fpx;".formatted(14 * factor);
    }
}
