package io.github.enadeag.carchargeplanner.engine.config;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * A percentage from 0 to 100, such as a charge level of the battery. In JSON it is the plain number.
 *
 * @param value the percentage, from 0 to 100
 */
public record Percent(@JsonValue double value) {

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

    /** Through Number rather than double, so Jackson reads "30" as it reads any other number in the config file. */
    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    private static Percent fromJson(Number number) {
        if (number == null) {
            throw new IllegalArgumentException("a percentage must be a number");
        }
        return new Percent(number.doubleValue());
    }
}
