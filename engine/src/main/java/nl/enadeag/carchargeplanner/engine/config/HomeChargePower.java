package nl.enadeag.carchargeplanner.engine.config;

import java.util.Optional;

/**
 * The charge powers the owner chooses from for charging at home, each a common charger at 230 V. A choice sets the
 * charger's phases, its highest current and its voltage; its lowest current and its efficiency stay as they are. A
 * charger whose values are none of these, such as one at 240 V or of 25 A, is shown as "Other", never as the nearest
 * power.
 */
public enum HomeChargePower {

    /** 3.7 kW: one phase of 16 A. */
    KW_3_7(Phases.ONE, 16),

    /** 7.4 kW: one phase of 32 A. */
    KW_7_4(Phases.ONE, 32),

    /** 11 kW: three phases of 16 A. */
    KW_11(Phases.THREE, 16),

    /** 22 kW: three phases of 32 A. */
    KW_22(Phases.THREE, 32);

    /** The voltage per phase of every choice, in V. */
    public static final double VOLTS = 230;

    private final Phases phases;
    private final int maxAmps;

    HomeChargePower(Phases phases, int maxAmps) {
        this.phases = phases;
        this.maxAmps = maxAmps;
    }

    /**
     * The phases this choice sets.
     *
     * @return the phases, as {@link Charger#phases()} holds them
     */
    public Phases phases() {
        return phases;
    }

    /**
     * The highest current this choice sets.
     *
     * @return the current per phase in A, as {@link Charger#maxAmps()} holds it
     */
    public int maxAmps() {
        return maxAmps;
    }

    /**
     * The choice that values entered for a charger stand for; the lowest current and the efficiency play no part.
     *
     * @param phases the phases the charger charges on
     * @param maxAmps the highest current per phase in A
     * @param volts the voltage per phase in V, which must be {@link #VOLTS} exactly
     * @return the choice with these phases, this current and 230 V; empty when there is none, which is "Other"
     */
    public static Optional<HomeChargePower> matching(Phases phases, int maxAmps, double volts) {
        if (volts != VOLTS) {
            return Optional.empty();
        }
        for (var choice : values()) {
            if (choice.phases == phases && choice.maxAmps == maxAmps) {
                return Optional.of(choice);
            }
        }
        return Optional.empty();
    }

    /**
     * The choice a charger stands for.
     *
     * @param charger the charger, such as the stored one
     * @return the choice, as {@link #matching(Phases, int, double)} finds it; empty for "Other"
     */
    public static Optional<HomeChargePower> of(Charger charger) {
        return matching(charger.phases(), charger.maxAmps(), charger.volts());
    }
}
