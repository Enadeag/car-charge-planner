package io.github.enadeag.carchargeplanner.engine.config;

/**
 * How the owner wants the plan made: choices, not facts about the equipment.
 *
 * @param minimumPct the battery level the car must not end a day below; under it the plan advises a grid charge
 * @param margin the share of the forecast surplus the plan counts on, so a cloud does not mean charging from the grid
 * @param importToleranceKwh grid energy a charge window may take to run at a higher charge power
 * @param notifyLeadMinutes how long before a charge window the owner is told
 */
public record PlanningPreferences(
        double minimumPct,
        double targetPct,
        double margin,
        double importToleranceKwh,
        int minWindowMinutes,
        int notifyLeadMinutes) {

    public PlanningPreferences {
        Checks.require(minimumPct >= 0 && minimumPct < targetPct && targetPct <= 100,
                "minimumPct and targetPct must satisfy 0 <= minimumPct < targetPct <= 100");
        Checks.require(margin > 0 && margin <= 1, "margin must be more than 0 and at most 1");
        Checks.require(importToleranceKwh >= 0, "importToleranceKwh must not be negative");
        Checks.require(minWindowMinutes > 0, "minWindowMinutes must be more than 0");
        Checks.require(notifyLeadMinutes >= 0, "notifyLeadMinutes must not be negative");
    }
}
