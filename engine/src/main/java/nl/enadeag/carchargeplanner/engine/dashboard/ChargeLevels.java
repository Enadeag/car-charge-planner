package nl.enadeag.carchargeplanner.engine.dashboard;

import nl.enadeag.carchargeplanner.engine.config.Percent;

/**
 * The plan's minimum and target charge levels, which every day's battery bar marks.
 *
 * @param minimum the minimum charge level, not above the target
 * @param target the target charge level
 */
public record ChargeLevels(Percent minimum, Percent target) {

    /**
     * Checks the order of the levels.
     *
     * @throws IllegalArgumentException when the minimum is above the target
     */
    public ChargeLevels {
        if (minimum.value() > target.value()) {
            throw new IllegalArgumentException("the minimum charge level must not be above the target, not "
                    + minimum.value() + "% over " + target.value() + "%");
        }
    }
}
