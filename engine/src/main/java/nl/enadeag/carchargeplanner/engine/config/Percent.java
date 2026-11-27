package nl.enadeag.carchargeplanner.engine.config;

import nl.enadeag.carchargeplanner.engine.setting.ConfigSetting;
import nl.enadeag.carchargeplanner.engine.setting.InvalidSetting;

/**
 * A percentage from {@link #MIN} to {@link #MAX}, such as a charge level of the battery.
 *
 * @param value the percentage, from 0 to 100
 */
public record Percent(double value) {

    /** The lowest percentage. */
    public static final int MIN = 0;

    /** The highest percentage. */
    public static final int MAX = 100;

    /**
     * Checks the range.
     *
     * @throws IllegalArgumentException when the value is below 0, above 100 or not a number
     */
    public Percent {
        if (!(value >= MIN && value <= MAX)) {
            throw new IllegalArgumentException("a percentage must be from " + MIN + " to " + MAX + ", not " + value);
        }
    }

    /**
     * A percentage the owner set, refused as that setting's value when it is out of range, so a client can show the
     * problem at its field.
     *
     * @param setting the setting the percentage is the value of, such as the trip surcharge
     * @param value the percentage, from 0 to 100
     * @return the percentage
     * @throws InvalidSetting with {@link InvalidSetting.Rule#OUT_OF_RANGE} when the value is below 0, above 100 or
     *     not a number
     */
    public static Percent of(ConfigSetting setting, double value) {
        if (!(value >= MIN && value <= MAX)) {
            throw new InvalidSetting(setting, InvalidSetting.Rule.OUT_OF_RANGE,
                    "a percentage must be from " + MIN + " to " + MAX + ", not " + value);
        }
        return new Percent(value);
    }
}
