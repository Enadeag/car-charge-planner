package io.github.enadeag.carchargeplanner.engine.config;

/** The phases the charger charges on. */
public enum Phases {

    /** One phase. */
    ONE(1),

    /** Three phases: each amp delivers three times the power of one phase. */
    THREE(3);

    private final int count;

    Phases(int count) {
        this.count = count;
    }

    /**
     * The number of phases, which is also the factor between the current per phase and the total power.
     *
     * @return 1 or 3
     */
    public int count() {
        return count;
    }

    /**
     * The phases for a number of phases, such as a count the owner entered.
     *
     * @param count the number of phases; only 1 and 3 are allowed
     * @return the phases with that number
     * @throws IllegalArgumentException when the number is not 1 or 3
     */
    public static Phases of(int count) {
        for (var phases : values()) {
            if (phases.count == count) {
                return phases;
            }
        }
        throw new IllegalArgumentException("phases must be 1 or 3");
    }
}
