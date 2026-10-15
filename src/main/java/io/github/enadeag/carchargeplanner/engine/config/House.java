package io.github.enadeag.carchargeplanner.engine.config;

/**
 * The home's own use of power.
 *
 * @param baseloadKw the average power the house uses on its own while the sun shines in kW, 0 or more
 */
public record House(double baseloadKw) {

    /**
     * Checks the value.
     *
     * @throws IllegalArgumentException when the baseload is negative
     */
    public House {
        if (baseloadKw < 0 || Double.isNaN(baseloadKw)) {
            throw new IllegalArgumentException("baseloadKw must not be negative");
        }
    }
}
