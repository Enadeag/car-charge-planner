package io.github.enadeag.carchargeplanner.engine.config;

/**
 * A percentage from 0 to 100, such as a charge level of the battery.
 *
 * @param value the percentage, from 0 to 100
 */
public record Percent(double value) {

    /**
     * Checks the range.
     *
     * @throws IllegalArgumentException when the value is below 0, above 100 or not a number
     */
    public Percent {
        if (!(value >= 0 && value <= 100)) {
            throw new IllegalArgumentException("a percentage must be from 0 to 100, not " + value);
        }
    }
}
