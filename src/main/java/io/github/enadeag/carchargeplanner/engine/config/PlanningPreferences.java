package io.github.enadeag.carchargeplanner.engine.config;

import io.github.enadeag.carchargeplanner.engine.setting.InvalidSetting;
import io.github.enadeag.carchargeplanner.engine.setting.Setting;

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
     * What a new owner starts with: charge to 80 % and never below 30 %, count on 85 % of the surplus, take up to
     * 0.2 kWh from the grid per window, windows of at least 45 minutes, and a notice 15 minutes ahead.
     */
    public static final PlanningPreferences RECOMMENDED = new PlanningPreferences(new Percent(30), new Percent(80),
            0.85, 0.2, 45, 15);

    /**
     * Checks the values.
     *
     * @throws InvalidSetting when a value is outside the range given for it, or the minimum is not below the target
     */
    public PlanningPreferences {
        if (minimumPct.value() >= targetPct.value()) {
            throw new InvalidSetting(Setting.MINIMUM_CHARGE, InvalidSetting.Rule.NOT_BELOW_TARGET,
                    "minimumPct and targetPct must satisfy 0 <= minimumPct < targetPct <= 100");
        }
        if (margin <= 0 || margin > 1 || Double.isNaN(margin)) {
            throw new InvalidSetting(Setting.PLANNING_MARGIN, InvalidSetting.Rule.OUT_OF_RANGE,
                    "margin must be more than 0 and at most 1");
        }
        if (importToleranceKwh < 0 || Double.isNaN(importToleranceKwh)) {
            throw new InvalidSetting(Setting.IMPORT_TOLERANCE, InvalidSetting.Rule.OUT_OF_RANGE,
                    "importToleranceKwh must not be negative");
        }
        if (minWindowMinutes <= 0) {
            throw new InvalidSetting(Setting.MINIMUM_WINDOW, InvalidSetting.Rule.OUT_OF_RANGE,
                    "minWindowMinutes must be more than 0");
        }
        if (notifyLeadMinutes < 0) {
            throw new InvalidSetting(Setting.NOTIFY_LEAD, InvalidSetting.Rule.OUT_OF_RANGE,
                    "notifyLeadMinutes must not be negative");
        }
    }
}
