package io.github.enadeag.carchargeplanner.engine.config;

/**
 * The wall charger, as far as it limits the charge power.
 *
 * @param phases the number of phases the charger charges on: 1 or 3
 * @param minAmps the lowest current per phase the charger or car accepts in A, more than 0
 * @param maxAmps the highest current per phase the charger or car accepts in A, at least {@code minAmps}
 * @param volts the voltage per phase in V, more than 0
 * @param efficiency the share of the energy from the wall that ends up in the battery, more than 0 and at most 1
 */
public record Charger(int phases, int minAmps, int maxAmps, double volts, double efficiency) {

    /**
     * Checks the values.
     *
     * @throws IllegalArgumentException when a value is outside the range given for it
     */
    public Charger {
        Checks.require(phases == 1 || phases == 3, "phases must be 1 or 3");
        Checks.require(minAmps > 0, "minAmps must be more than 0");
        Checks.require(maxAmps >= minAmps, "maxAmps must be at least minAmps");
        Checks.require(volts > 0, "volts must be more than 0");
        Checks.require(efficiency > 0 && efficiency <= 1, "efficiency must be more than 0 and at most 1");
    }
}
