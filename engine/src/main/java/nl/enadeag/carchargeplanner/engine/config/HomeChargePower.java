package nl.enadeag.carchargeplanner.engine.config;

/**
 * The charge powers the owner chooses from for charging at home, each a common charger at 230 V. A choice sets the
 * charger's phases, its highest current and its voltage; its lowest current and its efficiency stay as they are.
 * {@link ChargePowerChoice} adds "Other" for a charger whose values are none of these.
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
}
