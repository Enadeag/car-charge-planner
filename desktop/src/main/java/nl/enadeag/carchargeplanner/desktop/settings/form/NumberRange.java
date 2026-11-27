package nl.enadeag.carchargeplanner.desktop.settings.form;

/**
 * What a number field allows and how it shows a value: the engine's limit, which its arrows stop at, and its own step
 * and decimals.
 *
 * @param limit what the engine allows, in the field's unit
 * @param step how much the arrow keys and buttons change the value, more than 0
 * @param decimals the decimals shown, 0 or more
 * @param whole whether the field takes only whole numbers, such as a current in A; then it shows no decimals
 */
public record NumberRange(Limit limit, double step, int decimals, boolean whole) {

    /**
     * Checks the values.
     *
     * @throws IllegalArgumentException when the step is not more than 0, the decimals are negative, or a whole-number
     *     field shows decimals
     */
    public NumberRange {
        if (!(step > 0)) {
            throw new IllegalArgumentException("step must be more than 0");
        }
        if (decimals < 0) {
            throw new IllegalArgumentException("decimals must not be negative");
        }
        if (whole && decimals != 0) {
            throw new IllegalArgumentException("a whole-number field shows no decimals");
        }
    }

    /**
     * A field that takes any number.
     *
     * @param limit what the engine allows, in the field's unit
     * @param step how much the arrows change the value, more than 0
     * @param decimals the decimals shown, 0 or more
     * @return the range
     */
    public static NumberRange of(Limit limit, double step, int decimals) {
        return new NumberRange(limit, step, decimals, false);
    }

    /**
     * A field that takes only whole numbers, without decimals.
     *
     * @param limit what the engine allows, in the field's unit
     * @param step how much the arrows change the value, more than 0
     * @return the range
     */
    public static NumberRange whole(Limit limit, double step) {
        return new NumberRange(limit, step, 0, true);
    }

    /**
     * Where the arrows stop going down.
     *
     * @return the lowest value the limit allows at the field's decimals
     */
    public double lowest() {
        return limit.lowest(decimals);
    }

    /**
     * Where the arrows stop going up.
     *
     * @return the highest value the limit allows at the field's decimals; positive infinity when it has no highest
     *     end, and the arrows do not stop
     */
    public double highest() {
        return limit.highest(decimals);
    }
}
