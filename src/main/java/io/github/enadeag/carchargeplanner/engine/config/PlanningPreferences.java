package io.github.enadeag.carchargeplanner.engine.config;

/**
 * The owner's choices for how the plan is made.
 *
 * @param minimumPct the minimum charge level of the battery, below {@code targetPct}
 * @param targetPct the target charge level of the battery, above {@code minimumPct}
 * @param margin the share of the forecast surplus to count on, more than 0 and at most 1
 * @param importToleranceKwh the grid energy in kWh a charge window may take, 0 or more
 * @param minWindowMinutes the shortest charge window in minutes, more than 0
 * @param notifyLeadMinutes how long before a charge window the owner is told, in minutes, 0 or more
 */
public record PlanningPreferences(
        Percent minimumPct,
        Percent targetPct,
        double margin,
        double importToleranceKwh,
        int minWindowMinutes,
        int notifyLeadMinutes) {

    /**
     * Checks the values.
     *
     * @throws IllegalArgumentException when a value is outside the range given for it
     */
    public PlanningPreferences {
        Checks.require(minimumPct.value() < targetPct.value(),
                "minimumPct and targetPct must satisfy 0 <= minimumPct < targetPct <= 100");
        Checks.require(margin > 0 && margin <= 1, "margin must be more than 0 and at most 1");
        Checks.require(importToleranceKwh >= 0, "importToleranceKwh must not be negative");
        Checks.require(minWindowMinutes > 0, "minWindowMinutes must be more than 0");
        Checks.require(notifyLeadMinutes >= 0, "notifyLeadMinutes must not be negative");
    }
}
