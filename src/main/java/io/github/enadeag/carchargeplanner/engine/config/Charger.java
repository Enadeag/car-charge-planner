package io.github.enadeag.carchargeplanner.engine.config;

/**
 * @param phases 1 or 3: each phase carries the current, so three phases deliver three times the power
 * @param minAmps the lowest current the charger (or car) accepts, per phase
 * @param efficiency the share of the energy from the wall that ends up in the battery
 */
public record Charger(int phases, int minAmps, int maxAmps, double volts, double efficiency) {

    public Charger {
        Checks.require(phases == 1 || phases == 3, "phases must be 1 or 3");
        Checks.require(minAmps > 0, "minAmps must be more than 0");
        Checks.require(maxAmps >= minAmps, "maxAmps must be at least minAmps");
        Checks.require(volts > 0, "volts must be more than 0");
        Checks.require(efficiency > 0 && efficiency <= 1, "efficiency must be more than 0 and at most 1");
    }
}
