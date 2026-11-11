package nl.enadeag.carchargeplanner.engine.config;

import java.util.Optional;

/**
 * What the charge power at home shows and offers: one of the {@link HomeChargePower}s, or "Other" for a charger whose
 * values are none of them, such as one at 240 V or of 25 A, never the nearest power.
 */
public enum ChargePowerChoice {

    /** 3.7 kW: {@link HomeChargePower#KW_3_7}. */
    KW_3_7(HomeChargePower.KW_3_7),

    /** 7.4 kW: {@link HomeChargePower#KW_7_4}. */
    KW_7_4(HomeChargePower.KW_7_4),

    /** 11 kW: {@link HomeChargePower#KW_11}. */
    KW_11(HomeChargePower.KW_11),

    /** 22 kW: {@link HomeChargePower#KW_22}. */
    KW_22(HomeChargePower.KW_22),

    /** Other: the charger's values are none of the powers, or the owner chose to enter them. */
    OTHER(null);

    private final HomeChargePower power;

    ChargePowerChoice(HomeChargePower power) {
        this.power = power;
    }

    /**
     * The power this choice sets.
     *
     * @return the power; empty for {@link #OTHER}, which sets no value
     */
    public Optional<HomeChargePower> power() {
        return Optional.ofNullable(power);
    }

    /**
     * The choice of a power.
     *
     * @param power the power
     * @return the choice that sets it
     */
    public static ChargePowerChoice of(HomeChargePower power) {
        return switch (power) {
            case KW_3_7 -> KW_3_7;
            case KW_7_4 -> KW_7_4;
            case KW_11 -> KW_11;
            case KW_22 -> KW_22;
        };
    }

    /**
     * The choice that values entered for a charger stand for; the lowest current and the efficiency play no part.
     *
     * @param phases the phases the charger charges on
     * @param maxAmps the highest current per phase in A
     * @param volts the voltage per phase in V, which must be {@link HomeChargePower#VOLTS} exactly for a power
     * @return the power with these phases, this current and 230 V; {@link #OTHER} when there is none
     */
    public static ChargePowerChoice matching(Phases phases, int maxAmps, double volts) {
        if (volts != HomeChargePower.VOLTS) {
            return OTHER;
        }
        for (var power : HomeChargePower.values()) {
            if (power.phases() == phases && power.maxAmps() == maxAmps) {
                return of(power);
            }
        }
        return OTHER;
    }

    /**
     * The choice a charger stands for.
     *
     * @param charger the charger, such as the stored one
     * @return the choice, as {@link #matching(Phases, int, double)} finds it
     */
    public static ChargePowerChoice of(Charger charger) {
        return matching(charger.phases(), charger.maxAmps(), charger.volts());
    }
}
