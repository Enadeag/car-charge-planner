package nl.enadeag.carchargeplanner.engine.config;

import nl.enadeag.carchargeplanner.engine.setting.ConfigSetting;
import nl.enadeag.carchargeplanner.engine.setting.InvalidSetting;

/**
 * The wall charger, as far as it limits the charge power.
 *
 * @param phases the phases the charger charges on
 * @param minAmps the lowest current per phase the charger and the car accept in A, from {@link #MIN_CURRENT_AMPS} to
 *     {@link #MAX_CURRENT_AMPS}
 * @param maxAmps the highest current per phase the charger, the car and the home connection allow in A, at least
 *     {@code minAmps} and at most {@link #MAX_CURRENT_AMPS}
 * @param volts the voltage per phase in V, from {@link #MIN_VOLTS} to {@link #MAX_VOLTS}
 * @param efficiency the share of the energy from the wall that ends up in the battery, from {@link #MIN_EFFICIENCY}
 *     to {@link #MAX_EFFICIENCY}
 */
public record Charger(Phases phases, int minAmps, int maxAmps, double volts, double efficiency) {

    /** The lowest current per phase either current may be, in A. */
    public static final int MIN_CURRENT_AMPS = 1;

    /**
     * The highest current per phase either current may be, in A: above what a home connection gives, and the planner
     * tries every current from the lowest to the highest.
     */
    public static final int MAX_CURRENT_AMPS = 80;

    /** The lowest voltage per phase, in V. */
    public static final int MIN_VOLTS = 100;

    /** The highest voltage per phase, in V: more than any home supply gives on one phase. */
    public static final int MAX_VOLTS = 400;

    /** The lowest efficiency, 1%: the energy that reaches the battery is divided by it. */
    public static final double MIN_EFFICIENCY = 0.01;

    /** The highest efficiency, 100%: a charger without losses. */
    public static final double MAX_EFFICIENCY = 1;

    /** A common home charger, for an owner who does not know the values: one phase, 6 to 16 A, 230 V, 90 %. */
    public static final Charger TYPICAL = new Charger(Phases.ONE, 6, 16, 230, 0.9);

    /**
     * Checks the values.
     *
     * @throws InvalidSetting when a value is outside the range given for it, or the highest current is below the lowest
     */
    public Charger {
        if (minAmps < MIN_CURRENT_AMPS || minAmps > MAX_CURRENT_AMPS) {
            throw new InvalidSetting(ConfigSetting.CHARGER_MIN_CURRENT, InvalidSetting.Rule.OUT_OF_RANGE,
                    "minAmps must be from " + MIN_CURRENT_AMPS + " to " + MAX_CURRENT_AMPS + ", not " + minAmps);
        }
        if (maxAmps < minAmps) {
            throw new InvalidSetting(ConfigSetting.CHARGER_MAX_CURRENT, InvalidSetting.Rule.BELOW_MINIMUM_CURRENT,
                    "maxAmps must be at least minAmps");
        }
        if (maxAmps > MAX_CURRENT_AMPS) {
            throw new InvalidSetting(ConfigSetting.CHARGER_MAX_CURRENT, InvalidSetting.Rule.OUT_OF_RANGE,
                    "maxAmps must be from " + MIN_CURRENT_AMPS + " to " + MAX_CURRENT_AMPS + ", not " + maxAmps);
        }
        // written as a negation, so that a NaN is refused
        if (!(volts >= MIN_VOLTS && volts <= MAX_VOLTS)) {
            throw new InvalidSetting(ConfigSetting.CHARGER_VOLTAGE, InvalidSetting.Rule.OUT_OF_RANGE,
                    "volts must be from " + MIN_VOLTS + " to " + MAX_VOLTS + ", not " + volts);
        }
        if (!(efficiency >= MIN_EFFICIENCY && efficiency <= MAX_EFFICIENCY)) {
            throw new InvalidSetting(ConfigSetting.CHARGER_EFFICIENCY, InvalidSetting.Rule.OUT_OF_RANGE,
                    "efficiency must be from " + MIN_EFFICIENCY + " to " + MAX_EFFICIENCY + ", not " + efficiency);
        }
    }
}
