package nl.enadeag.carchargeplanner.desktop.system;

/**
 * How much larger the system shows text than normal, apart from the display's scaling: Windows' Text size setting
 * (Settings, Accessibility), from 100 to 225%.
 *
 * @param percent the text's size against the normal size, from 100 to 225
 */
public record TextSize(int percent) {

    /** The smallest size: text as large as normal. */
    public static final int MIN_PERCENT = 100;
    /** The largest size Windows offers. */
    public static final int MAX_PERCENT = 225;
    /** Text as large as normal, as on a system without the setting. */
    public static final TextSize NORMAL = new TextSize(MIN_PERCENT);

    /**
     * Checks the range.
     *
     * @throws IllegalArgumentException when the percentage is below 100 or above 225
     */
    public TextSize {
        if (percent < MIN_PERCENT || percent > MAX_PERCENT) {
            throw new IllegalArgumentException("a text size must be from " + MIN_PERCENT + " to " + MAX_PERCENT
                    + "%, not " + percent);
        }
    }

    /**
     * The size a value of the system stands for, which may lie outside what its settings page offers.
     *
     * @param percent the text's size against the normal size as the system keeps it, any number
     * @return the size, at least 100 and at most 225%
     */
    public static TextSize clamped(int percent) {
        return new TextSize(Math.clamp(percent, MIN_PERCENT, MAX_PERCENT));
    }

    /**
     * A size at this text size.
     *
     * @param normal a size at normal text size, such as a font size in pixels
     * @return that size times this percentage
     */
    public double of(double normal) {
        return normal * percent / 100;
    }
}
