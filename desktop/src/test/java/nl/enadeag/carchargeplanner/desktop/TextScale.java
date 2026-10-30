package nl.enadeag.carchargeplanner.desktop;

import nl.enadeag.carchargeplanner.desktop.system.TextSize;

/** The system's text made larger, as the owner can ask for on Windows and Linux, against the app's own 14 px. */
public enum TextScale {
    /** Text half as large again. */
    PERCENT_150(150),
    /** Text twice as large. */
    PERCENT_200(200);

    /** The headless screen's size, which a window cannot outgrow. */
    public static final double SCREEN = 1000;

    private final TextSize size;

    TextScale(int percent) {
        this.size = new TextSize(percent);
    }

    /** A size in pixels at this scale. */
    public double of(double pixels) {
        return size.of(pixels);
    }

    /** The system's text size this scale stands for, as the windows' theme follows it. */
    public TextSize size() {
        return size;
    }
}
