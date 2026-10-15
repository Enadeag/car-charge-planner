package io.github.enadeag.carchargeplanner.engine.config;

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

    /**
     * Checks the values.
     *
     * @throws IllegalArgumentException when a value is outside the range given for it
     */
    public Charger {
        if (minAmps <= 0) {
            throw new IllegalArgumentException("minAmps must be more than 0");
        }
        if (maxAmps < minAmps) {
            throw new IllegalArgumentException("maxAmps must be at least minAmps");
        }
        if (volts <= 0 || Double.isNaN(volts)) {
            throw new IllegalArgumentException("volts must be more than 0");
        }
        if (efficiency <= 0 || efficiency > 1 || Double.isNaN(efficiency)) {
            throw new IllegalArgumentException("efficiency must be more than 0 and at most 1");
        }
    }
}
