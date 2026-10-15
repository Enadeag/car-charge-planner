package io.github.enadeag.carchargeplanner.engine.battery;

import io.github.enadeag.carchargeplanner.engine.config.Percent;
import java.time.Instant;

/**
 * A battery level the owner entered.
 *
 * @param level the charge level of the car's battery
 * @param enteredAt when the owner entered it
 */
public record BatteryReading(Percent level, Instant enteredAt) {

    /**
     * Checks that both values are there.
     *
     * @throws IllegalArgumentException when the level or the moment is null
     */
    public BatteryReading {
        if (level == null || enteredAt == null) {
            throw new IllegalArgumentException("a battery reading needs a level and the moment it was entered");
        }
    }
}
