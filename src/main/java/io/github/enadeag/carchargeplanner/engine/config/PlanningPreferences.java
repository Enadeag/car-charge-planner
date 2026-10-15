package io.github.enadeag.carchargeplanner.engine.config;

/**
 * How the owner wants the plan made: choices, not facts about the equipment.
 *
 * @param margin the share of the forecast surplus the plan counts on, so a cloud does not mean charging from the grid
 * @param importToleranceKwh grid energy a charge window may take to run at a higher charge power
 */
public record PlanningPreferences(double targetPct, double margin, double importToleranceKwh, int minWindowMinutes) {

    public PlanningPreferences {
        Checks.require(targetPct > 0 && targetPct <= 100, "targetPct must be more than 0 and at most 100");
        Checks.require(margin > 0 && margin <= 1, "margin must be more than 0 and at most 1");
        Checks.require(importToleranceKwh >= 0, "importToleranceKwh must not be negative");
        Checks.require(minWindowMinutes > 0, "minWindowMinutes must be more than 0");
    }
}
