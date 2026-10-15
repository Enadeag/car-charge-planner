package io.github.enadeag.carchargeplanner.engine.config;

import io.github.enadeag.carchargeplanner.engine.setting.InvalidSetting;
import io.github.enadeag.carchargeplanner.engine.setting.Setting;

/**
 * The wall charger, as far as it limits the charge power.
 *
 * @param phases the phases the charger charges on
 * @param minAmps the lowest current per phase the charger or car accepts in A, more than 0
 * @param maxAmps the highest current per phase the charger or car accepts in A, at least {@code minAmps}
 * @param volts the voltage per phase in V, more than 0
 * @param efficiency the share of the energy from the wall that ends up in the battery, more than 0 and at most 1
 */
public record Charger(Phases phases, int minAmps, int maxAmps, double volts, double efficiency) {

    /** A common home charger, for an owner who does not know the values: one phase, 6 to 16 A, 230 V, 90 %. */
    public static final Charger TYPICAL = new Charger(Phases.ONE, 6, 16, 230, 0.9);

    /**
     * Checks the values.
     *
     * @throws InvalidSetting when a value is outside the range given for it, or the highest current is below the lowest
     */
    public Charger {
        if (minAmps <= 0) {
            throw new InvalidSetting(Setting.CHARGER_MIN_CURRENT, InvalidSetting.Rule.OUT_OF_RANGE,
                    "minAmps must be more than 0");
        }
        if (maxAmps < minAmps) {
            throw new InvalidSetting(Setting.CHARGER_MAX_CURRENT, InvalidSetting.Rule.BELOW_MINIMUM_CURRENT,
                    "maxAmps must be at least minAmps");
        }
        if (volts <= 0 || Double.isNaN(volts)) {
            throw new InvalidSetting(Setting.CHARGER_VOLTAGE, InvalidSetting.Rule.OUT_OF_RANGE,
                    "volts must be more than 0");
        }
        if (efficiency <= 0 || efficiency > 1 || Double.isNaN(efficiency)) {
            throw new InvalidSetting(Setting.CHARGER_EFFICIENCY, InvalidSetting.Rule.OUT_OF_RANGE,
                    "efficiency must be more than 0 and at most 1");
        }
    }
}
