package nl.enadeag.carchargeplanner.engine.config;

import nl.enadeag.carchargeplanner.engine.setting.ConfigSetting;
import nl.enadeag.carchargeplanner.engine.setting.InvalidSetting;

/**
 * The home's own use of power.
 *
 * @param baseloadKw the power the house uses without charging the car, in kW, {@link #MIN_BASELOAD_KW} or more: the
 *     average over the whole day, night included, such as its kWh in a day divided by 24
 */
public record House(double baseloadKw) {

    /** The lowest baseload, in kW: a house that uses nothing. There is no highest: a large one only plans less. */
    public static final int MIN_BASELOAD_KW = 0;

    /** A common household, for an owner who does not know: 0.5 kW, 12 kWh per day. */
    public static final House TYPICAL = new House(0.5);

    /**
     * Checks the value.
     *
     * @throws InvalidSetting when the baseload is negative
     */
    public House {
        if (baseloadKw < MIN_BASELOAD_KW || !Double.isFinite(baseloadKw)) {
            throw new InvalidSetting(ConfigSetting.HOUSE_BASELOAD, InvalidSetting.Rule.OUT_OF_RANGE,
                    "baseloadKw must not be negative");
        }
    }
}
