package nl.enadeag.carchargeplanner.engine.dashboard;

import nl.enadeag.carchargeplanner.engine.config.Percent;

/**
 * The plan's minimum and target charge levels, which every day's battery bar marks.
 *
 * @param minimumPct the minimum charge level, never null and not above the target
 * @param targetPct the target charge level, never null
 */
public record ChargeLevels(Percent minimumPct, Percent targetPct) {

    /**
     * Checks the order of the levels.
     *
     * @throws IllegalArgumentException when the minimum is above the target
     */
    public ChargeLevels {
        if (minimumPct.value() > targetPct.value()) {
            throw new IllegalArgumentException("the minimum charge level must not be above the target, not "
                    + minimumPct.value() + "% over " + targetPct.value() + "%");
        }
    }
}
