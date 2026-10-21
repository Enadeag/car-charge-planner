package nl.enadeag.carchargeplanner.engine.config;

import nl.enadeag.carchargeplanner.engine.setting.ConfigSetting;
import nl.enadeag.carchargeplanner.engine.setting.InvalidSetting;

/**
 * The home's own use of power.
 *
 * @param baseloadKw the power the house uses on its own in kW, 0 or more: the average over the whole day, night
 *     included, such as its kWh in a month divided by 730, the hours of an average month
 */
public record House(double baseloadKw) {

    /** A common household, for an owner who does not know: 0.5 kW. */
    public static final House TYPICAL = new House(0.5);

    /**
     * Checks the value.
     *
     * @throws InvalidSetting when the baseload is negative
     */
    public House {
        if (baseloadKw < 0 || !Double.isFinite(baseloadKw)) {
            throw new InvalidSetting(ConfigSetting.HOUSE_BASELOAD, InvalidSetting.Rule.OUT_OF_RANGE,
                    "baseloadKw must not be negative");
        }
    }
}
