package nl.enadeag.carchargeplanner.engine.config;

import nl.enadeag.carchargeplanner.engine.setting.InvalidSetting;
import nl.enadeag.carchargeplanner.engine.setting.Setting;

/**
 * The home's own use of power.
 *
 * @param baseloadKw the average power the house uses on its own while the sun shines in kW, 0 or more
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
        if (baseloadKw < 0 || Double.isNaN(baseloadKw)) {
            throw new InvalidSetting(Setting.HOUSE_BASELOAD, InvalidSetting.Rule.OUT_OF_RANGE,
                    "baseloadKw must not be negative");
        }
    }
}
