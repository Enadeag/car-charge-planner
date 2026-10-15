package io.github.enadeag.carchargeplanner.engine.config;

/** @param baseloadKw what the house uses on its own, on average, while the sun shines */
public record House(double baseloadKw) {

    public House {
        Checks.require(baseloadKw >= 0, "baseloadKw must not be negative");
    }
}
