package nl.enadeag.carchargeplanner.desktop.settings.form;

/**
 * What a number field allows and how it shows a value.
 *
 * @param min the lowest value
 * @param max the highest value, at least {@code min}
 * @param step how much the arrow keys and buttons change the value, more than 0
 * @param decimals the decimals shown, 0 or more
 */
public record NumberRange(double min, double max, double step, int decimals) {

    /**
     * Checks the values.
     *
     * @throws IllegalArgumentException when the maximum is below the minimum, the step is not more than 0 or the
     *     decimals are negative
     */
    public NumberRange {
        if (max < min) {
            throw new IllegalArgumentException("max must be at least min");
        }
        if (step <= 0) {
            throw new IllegalArgumentException("step must be more than 0");
        }
        if (decimals < 0) {
            throw new IllegalArgumentException("decimals must not be negative");
        }
    }

    /**
     * A value brought into the range.
     *
     * @param value any number but NaN
     * @return the value itself when it is in the range, otherwise the nearest end
     */
    public double clamp(double value) {
        return Math.clamp(value, min, max);
    }
}
